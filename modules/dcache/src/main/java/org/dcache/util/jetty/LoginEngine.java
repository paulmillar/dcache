package org.dcache.util.jetty;

import com.google.common.collect.ImmutableSet;
import org.globus.gsi.gssapi.jaas.GlobusPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLSession;
import javax.security.auth.Subject;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import diskCacheV111.util.CacheException;
import diskCacheV111.util.PermissionDeniedCacheException;

import org.dcache.auth.LoginReply;
import org.dcache.auth.LoginStrategy;
import org.dcache.auth.Origin;
import org.dcache.auth.Origin.AuthType;
import org.dcache.auth.Subjects;
import org.dcache.auth.attributes.LoginAttribute;
import org.dcache.util.CertificateFactories;

import static java.util.Arrays.asList;

/**
 * An SSLEngine that calls out to gPlazma after the client sends the X.509
 * chain.  The results of this login are available through SSLSession.
 */
public class LoginEngine extends ForwardingSSLEngine
{
    private static final Logger LOGGER = LoggerFactory.getLogger(LoginEngine.class);

    public static final String SUBJECT = "org.dcache.login.subject";
    public static final String ATTRIBUTES = "org.dcache.login.attributes";
    public static final String STATE = "org.dcache.login.state";
    public static final String MESSAGE = "org.dcache.login.message";

    public enum LoginState {
        NOT_ATTEMPTED,
        SUCCESS,
        AUTHENTICATION_FAILURE,
        INTERNAL_FAILURE,
        CREDENTIAL_FAILURE,
    }

    private final SSLEngine delegate;
    private final LoginStrategy loginStrategy;
    private final CertificateFactory cf = CertificateFactories.newX509CertificateFactory();

    private LoginState state = LoginState.NOT_ATTEMPTED;

    public LoginEngine(SSLEngine delegate, LoginStrategy login)
    {
        this.delegate = delegate;
        this.loginStrategy = login;
    }

    @Override
    protected SSLEngine delegate()
    {
        return delegate;
    }

    @Override
    public SSLEngineResult unwrap(ByteBuffer src, ByteBuffer[] dsts,
                                  int offset, int length) throws SSLException
    {
        SSLEngineResult result = super.unwrap(src, dsts, offset, length);

        if (result.getStatus() == SSLEngineResult.Status.OK &&
                result.getHandshakeStatus() == SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING &&
                state == LoginState.NOT_ATTEMPTED) {
            try {
                Subject user = buildLoginUser();
                LoginReply reply = loginStrategy.login(user);

                SSLSession session = getSession();
                session.putValue(ATTRIBUTES, ImmutableSet.copyOf(reply.getLoginAttributes()));
                session.putValue(SUBJECT, reply.getSubject());
                setState(LoginState.SUCCESS, "login successful");
            } catch (CertificateException | SSLPeerUnverifiedException e) {
                setState(LoginState.CREDENTIAL_FAILURE, e.getMessage());
            } catch (PermissionDeniedCacheException e) {
                setState(LoginState.AUTHENTICATION_FAILURE, e.getMessage());
            } catch (CacheException | IllegalArgumentException e) {
                setState(LoginState.INTERNAL_FAILURE, e.getMessage());
            }
        }

        return result;
    }

    private void setState(LoginState state, String why)
    {
        SSLSession session = getSession();
        session.putValue(MESSAGE, why);
        session.putValue(STATE, LoginState.SUCCESS);
        this.state = state;
    }

    private Subject buildLoginUser() throws CertificateException, SSLPeerUnverifiedException
    {
        Subject user = new Subject();

        X509Certificate[] chain = (X509Certificate[]) getSession().getPeerCertificates();
        user.getPublicCredentials().add(cf.generateCertPath(asList(chain)));

        String host = getSession().getPeerHost();
        if (host != null) {
            try {
                InetAddress address = InetAddress.getByName(host);
                user.getPrincipals().add(new Origin(AuthType.ORIGIN_AUTHTYPE_STRONG,
                                                      address));
                LOGGER.debug("User connected from the following IP, setting as origin: {}.",
                        host);
            } catch (UnknownHostException uhex) {
                LOGGER.info("Could not add the remote-IP {} as an origin principal.",
                        host);
            }
        } else {
            LOGGER.info("Peer hostname could not be determined");
        }

        return user;
    }
}
