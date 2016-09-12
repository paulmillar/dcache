package org.dcache.webdav;

import com.google.common.net.InetAddresses;

import dmg.cells.nucleus.CDC;

import org.dcache.util.NetLoggerBuilder;

import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.handler.HandlerWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.security.auth.Subject;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.security.cert.X509Certificate;

import static org.dcache.webdav.Attributes.getSubject;
import static org.dcache.webdav.Attributes.getTransaction;

public class LoggingHandler extends HandlerWrapper {

    private final Logger ACCESS_LOGGER =
            LoggerFactory.getLogger("org.dcache.access.webdav");

    @Override
    public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        if (isStarted() && !baseRequest.isHandled()) {
            super.handle(target, baseRequest, request, response);

            int statusCode = response.getStatus();

            NetLoggerBuilder log = new NetLoggerBuilder(logLevel(statusCode),
                    "org.dcache.webdav.request").omitNullValues();
            log.add("session", CDC.getSession());
            log.add("transaction", getTransaction(request));
            log.add("request.method", request.getMethod());
            log.add("request.url", request.getRequestURL());
            log.add("response.code", statusCode);
            log.add("response.reason", getReason(response));
            log.add("location", response.getHeader("Location"));
            InetAddress addr = InetAddresses.forString(request.getRemoteAddr());
            log.add("socket.remote", new InetSocketAddress(addr, request.getRemotePort()));
            log.add("user-agent", request.getHeader("User-Agent"));

            log.add("user.dn", getCertificateName(request));
            log.add("user.mapped", getSubject(request));
            log.toLogger(ACCESS_LOGGER);
        }
    }

    private static String getReason(HttpServletResponse response)
    {
        if (response instanceof Response) {
            return ((Response) response).getReason();
        } else {
            return HttpStatus.getMessage(response.getStatus());
        }
    }

    private static NetLoggerBuilder.Level logLevel(int code) {
        if (code >= 500) {
            return NetLoggerBuilder.Level.ERROR;
        } else if (code >= 400) {
            return NetLoggerBuilder.Level.WARN;
        } else {
            return NetLoggerBuilder.Level.INFO;
        }
    }

    private static String getCertificateName(HttpServletRequest request)
    {
        X509Certificate[] chain = Attributes.getX509Certificate(request);
        return (chain == null || chain.length == 0) ? null : chain [0].getSubjectX500Principal().getName();
    }
}
