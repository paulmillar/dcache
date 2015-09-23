package org.dcache.webdav.macaroons;

import org.apache.commons.compress.utils.Charsets;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.security.auth.Subject;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

import diskCacheV111.util.CacheException;
import diskCacheV111.util.FileNotFoundCacheException;
import diskCacheV111.util.FsPath;
import diskCacheV111.util.PermissionDeniedCacheException;
import diskCacheV111.util.PnfsHandler;
import diskCacheV111.util.PnfsId;

import org.dcache.acl.enums.AccessMask;
import org.dcache.auth.LoginReply;
import org.dcache.auth.LoginStrategy;
import org.dcache.auth.Origin;
import org.dcache.auth.PasswordCredential;
import org.dcache.auth.Subjects;
import org.dcache.cells.CellStub;
import org.dcache.macaroons.MacaroonRequestMessage;
import org.dcache.namespace.FileType;
import org.dcache.util.CertificateFactories;
import org.dcache.vehicles.FileAttributes;

import static java.util.Arrays.asList;
import static org.dcache.namespace.FileAttribute.PNFSID;
import static org.dcache.namespace.FileAttribute.TYPE;


/**
 * Handle HTTP-based requests to create a macaroon.
 */
public class MacaroonMintingService extends AbstractHandler
{
    private static final Logger LOG = LoggerFactory.getLogger(MacaroonMintingService.class);

    public static final String X509_CERTIFICATE_ATTRIBUTE =
        "javax.servlet.request.X509Certificate";

    private static final String REQUEST_MIMETYPE = "application/x-macaroon-request";
    private static final String RESPONSE_MIMETYPE = "application/x-macaroon";
    private static final Set<AccessMask> READ_ACCESS_MASK =
            EnumSet.of(AccessMask.READ_DATA);

    private PnfsHandler _pnfs;
    private final CertificateFactory _cf = CertificateFactories.newX509CertificateFactory();
    private CellStub _macaroons;
    private LoginStrategy _loginStrategy;

    public void setMacaroonsStub(CellStub stub)
    {
        _macaroons = stub;
    }

    public void setLoginStrategy(LoginStrategy loginStrategy)
    {
        _loginStrategy = loginStrategy;
    }

    public void setPnfsStub(CellStub stub)
    {
        _pnfs = new PnfsHandler(stub);
    }

    @Override
    public void handle(String target, Request baseRequest,
            HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException
    {
        if (baseRequest.getMethod().equals("POST") &&
                Objects.equals(request.getContentType(), REQUEST_MIMETYPE)) {
            try {
                String macaroon = buildMacaroon(target, baseRequest, response);

                response.setStatus(200);
                response.setContentType(RESPONSE_MIMETYPE);
                try (PrintWriter w = response.getWriter()) {
                    w.println(macaroon);
                }
            } catch (ErrorResponseException e) {
                response.setStatus(e.getStatus());
                try (PrintWriter w = response.getWriter()) {
                    w.println(e.getMessage());
                }
            } catch (InterruptedException e) {
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                try (PrintWriter w = response.getWriter()) {
                    w.println("Service shutting down");
                }
            } catch (CacheException e) {
                LOG.error("Failed to generate macaroon: {}", e.getMessage());
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                try (PrintWriter w = response.getWriter()) {
                    w.println("Internal error: " + e.toString());
                }
            } catch (RuntimeException e) {
                LOG.error("Bug detected while processing macaroon minting request", e);
                response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                try (PrintWriter w = response.getWriter()) {
                    w.println("Internal error: " + e.toString());
                }
            } finally {
                baseRequest.setHandled(true);
            }
        }
    }

    private String buildMacaroon(String target, Request request, HttpServletResponse response)
            throws CacheException, InterruptedException, IOException, ErrorResponseException
    {
        checkValidRequest(request.isSecure(), "Not secure transport.");

        Subject subject = authenticate(request);
        checkValidRequest(!Subjects.isNobody(subject), "User not authenticated.");

        String path = new FsPath(target).toString();

        PnfsId id = authorizeRead(subject, path);

        MacaroonRequestMessage message = new MacaroonRequestMessage(path, id);
        message.setSubject(subject);
        return _macaroons.sendAndWait(message).getMacaroon();
   }

    private static void checkValidRequest(boolean isOK, String message)
            throws ErrorResponseException
    {
        if (!isOK) {
            throw new ErrorResponseException(HttpServletResponse.SC_BAD_REQUEST, message);
        }
    }


    private Subject authenticate(Request request) throws ErrorResponseException, CacheException
    {
        Subject subject = new Subject();

        addX509ChainToSubject(request, subject);
        addOriginToSubject(request, subject);
        addPasswordCredentialToSubject(request, subject);

        LoginReply login = _loginStrategy.login(subject);
        subject = login.getSubject();

        return subject;
    }

    private void addX509ChainToSubject(HttpServletRequest request, Subject subject)
            throws CacheException
    {
        Object object = request.getAttribute(X509_CERTIFICATE_ATTRIBUTE);
        if (object instanceof X509Certificate[]) {
            try {
                subject.getPublicCredentials().add(_cf.generateCertPath(asList((X509Certificate[]) object)));
            } catch (CertificateException e) {
                throw new CacheException("Failed to generate X.509 certificate path: " + e.getMessage(), e);
            }
        }
    }

    private void addOriginToSubject(Request request, Subject subject)
    {
        String address = request.getRemoteAddr();
        try {
            Origin origin = new Origin(Origin.AuthType.ORIGIN_AUTHTYPE_STRONG,
                    InetAddress.getByName(address));
            subject.getPrincipals().add(origin);
        } catch (UnknownHostException e) {
            LOG.warn("Failed to resolve " + address + ": " + e.getMessage());
        }
    }

    private void addPasswordCredentialToSubject(Request request, Subject subject) throws ErrorResponseException
    {
        String authz = request.getHeader("Authorization");

        // FIXME check if BASIC auth is enabled.
        if (authz != null && authz.startsWith("Basic ")) {
            String id = authz.substring(6);
            byte[] raw = Base64.getMimeDecoder().decode(id);
            String credential = Charsets.UTF_8.decode(ByteBuffer.wrap(raw)).toString();
            int idx = credential.indexOf(':');
            checkValidRequest(idx != -1, "Malformed BASIC authentication: missing ':'");
            checkValidRequest(idx != 0, "Malformed BASIC authentication: missing username");
            checkValidRequest(idx < credential.length(), "Malformed BASIC authentication: missing password");
            String username = credential.substring(0, idx);
            String password = credential.substring(idx+1);
            subject.getPrivateCredentials().add(new PasswordCredential(username, password));
        }
    }

    private PnfsId authorizeRead(Subject subject, String path) throws CacheException, ErrorResponseException
    {
        PnfsHandler pnfs = new PnfsHandler(_pnfs, subject);

        FileAttributes attributes;
        try {
            attributes = pnfs.getFileAttributes(path, EnumSet.of(PNFSID, TYPE),
                    READ_ACCESS_MASK, false);
        } catch (FileNotFoundCacheException e) {
            throw new ErrorResponseException(HttpServletResponse.SC_NOT_FOUND, "No such file");
        } catch (PermissionDeniedCacheException e) {
            throw new ErrorResponseException(HttpServletResponse.SC_FORBIDDEN, "Permission denied");
        }

        checkValidRequest(attributes.getFileType() == FileType.REGULAR, "Not a file");

        return attributes.getPnfsId();
    }
}