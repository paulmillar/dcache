package org.dcache.webdav;

import com.google.common.base.Joiner;
import com.google.common.primitives.Longs;
import io.milton.http.Auth;
import io.milton.http.Filter;
import io.milton.http.FilterChain;
import io.milton.http.Request;
import io.milton.http.Response;
import io.milton.http.Response.Status;
import io.milton.servlet.ServletRequest;
import io.milton.servlet.ServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.security.auth.Subject;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.security.cert.X509Certificate;
import java.util.List;
import java.util.NoSuchElementException;

import dmg.cells.nucleus.CDC;
import org.dcache.auth.Subjects;
import org.dcache.util.NetLoggerBuilder;

import static org.dcache.webdav.DcacheResourceFactory.TRANSACTION_ATTRIBUTE;

/**
 * Request logging for WebDAV door following the NetLogger format. Interim
 * solution until we switch to a better logging framework with direct support
 * for Jetty.
 */
public class LoggingFilter implements Filter
{
    private final Logger ACCESS_LOGGER =
            LoggerFactory.getLogger("org.dcache.access.webdav");

    private static final String X509_CERTIFICATE_ATTRIBUTE =
        "javax.servlet.request.X509Certificate";

    @Override
    public void process(final FilterChain filterChain,
                        final Request request,
                        final Response response)
    {
        filterChain.process(request, response);

        NetLoggerBuilder log = new NetLoggerBuilder(getLevel(response),
                "org.dcache.webdav.request").omitNullValues();

        log.add("request.method", request.getMethod());
        log.add("request.url", request.getAbsoluteUrl());
        Status status = response.getStatus();
        if (status != null) {
            log.add("response.code", response.getStatus().code);
            log.add("response.explanation", response.getStatus().text);
        } else {
            // Redirection
            HttpServletResponse servletResponse = ServletResponse.getResponse();
            log.add("response.code", servletResponse.getStatus());
            log.add("location", servletResponse.getHeader("Location"));
        }
        log.add("host.remote", request.getFromAddress());
        log.add("user-agent", request.getUserAgentHeader());

        log.add("dn", getCertificateName());

        log.add("user", getSubjectName(request));
        log.add("session", CDC.getSession());
        log.add("transaction", getTransaction());
        log.toLogger(ACCESS_LOGGER);
    }

    private NetLoggerBuilder.Level getLevel(Response response)
    {
        Status status = response.getStatus();

        if (status == null) {
            return NetLoggerBuilder.Level.INFO;
        } else if (status.code >= 500) {
            return NetLoggerBuilder.Level.ERROR;
        } else if (status.code >= 400) {
            return NetLoggerBuilder.Level.WARN;
        } else {
            return NetLoggerBuilder.Level.INFO;
        }
    }

    private String getCertificateName()
    {
        HttpServletRequest servletRequest = ServletRequest.getRequest();

        Object object =
            servletRequest.getAttribute(X509_CERTIFICATE_ATTRIBUTE);

        if (object instanceof X509Certificate[]) {
            X509Certificate[] chain = (X509Certificate[]) object;

            if (chain.length >= 1) {
                return chain[0].getSubjectX500Principal().getName();
            }
        }

        return null;
    }

    private String getTransaction()
    {
        HttpServletRequest servletRequest = ServletRequest.getRequest();

        Object object =
            servletRequest.getAttribute(TRANSACTION_ATTRIBUTE);

        return object == null ? null : String.valueOf(object);
    }

    private String getSubjectName(Request request)
    {
        Auth auth = request.getAuthorization();

        if(auth == null) {
            return null;
        }

        Subject subject = (Subject) auth.getTag();

        if(subject == null) {
            return null;
        }

        if(subject.equals(Subjects.NOBODY)) {
            return "NOBODY";
        }

        if(subject.equals(Subjects.ROOT)) {
            return "ROOT";
        }

        String uid;
        try {
            uid = Long.toString(Subjects.getUid(subject));
        } catch(NoSuchElementException e) {
            uid="unknown";
        }

        List<Long> gids = Longs.asList(Subjects.getGids(subject));

        return "uid=" + uid + ", gid={" + Joiner.on(",").join(gids)+"}";
    }
}
