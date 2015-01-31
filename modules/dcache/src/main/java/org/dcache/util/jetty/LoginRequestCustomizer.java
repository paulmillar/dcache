package org.dcache.util.jetty;

import org.eclipse.jetty.io.ssl.SslConnection;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.Request;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLSession;

/**
 *
 */
public class LoginRequestCustomizer  implements HttpConfiguration.Customizer
{
    public static final String SSL_SESSION_SUBJECT = "org.dcache.ssl-session.subject";
    public static final String SSL_SESSION_ATTRIBUTES = "org.dcache.ssl-session.attributes";
    public static final String SSL_SESSION_STATE = "org.dcache.ssl-session.state";
    public static final String SSL_SESSION_MESSAGE = "org.dcache.ssl-session.message";

    @Override
    public void customize(Connector connector, HttpConfiguration config, Request request)
    {
        if (request.getHttpChannel().getEndPoint() instanceof SslConnection.DecryptedEndPoint) {
            SslConnection.DecryptedEndPoint ssl_endp = (SslConnection.DecryptedEndPoint)request.getHttpChannel().getEndPoint();
            SslConnection sslConnection = ssl_endp.getSslConnection();
            SSLEngine sslEngine=sslConnection.getSSLEngine();
            customize(sslEngine,request);
        }
    }

    private void customize(SSLEngine engine, Request request)
    {
        SSLSession sslSession = engine.getSession();
        request.setAttribute(SSL_SESSION_SUBJECT, sslSession.getValue(LoginEngine.SUBJECT));
        request.setAttribute(SSL_SESSION_ATTRIBUTES, sslSession.getValue(LoginEngine.ATTRIBUTES));
        request.setAttribute(SSL_SESSION_STATE, sslSession.getValue(LoginEngine.STATE));
        request.setAttribute(SSL_SESSION_MESSAGE, sslSession.getValue(LoginEngine.MESSAGE));
    }
}
