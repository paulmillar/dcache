package org.dcache.srm.util;

import org.apache.axis.MessageContext;
import org.apache.axis.transport.http.HTTPConstants;
import org.globus.gsi.bc.BouncyCastleUtil;
import org.ietf.jgss.GSSCredential;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Optional;

import org.dcache.srm.AbstractStorageElement;
import org.dcache.srm.SRM;

import static org.apache.axis.transport.http.HTTPConstants.MC_HTTP_SERVLET;
import static org.apache.axis.transport.http.HTTPConstants.MC_HTTP_SERVLETREQUEST;
import static org.globus.axis.gsi.GSIConstants.GSI_CREDENTIALS;


/**
 * Utility class with methods for working with Axis
 */
public class Axis
{
    public static final String ATTRIBUTE_NAME_SRM= "org.dcache.srm.srm";
    public static final String ATTRIBUTE_NAME_STORAGE = "org.dcache.srm.storage";
    public static final String ATTRIBUTE_NAME_CONFIG = "org.dcache.srm.config";

    /**
     * Obtain an object from the set of attributes in the ServletContext.
     * @param key The name of the attribute
     * @param type The expected kind of the attribute
     * @return the value of the attribute
     * @throws IllegalStateException if the attribute isn't set
     * @throws RuntimeException if the attribute isn't the expected type
     */
    public static <T> T getAttribute(String key, Class<T> type)
    {
        MessageContext msgContext = MessageContext.getCurrentContext();
        HttpServlet servlet =
                (HttpServlet) msgContext.getProperty(MC_HTTP_SERVLET);
        ServletContext context = servlet.getServletContext();
        return castAttribute(key, context.getAttribute(key), type);
    }

    public static Optional<X509Certificate[]> getCertificateChain()
    {
        return Optional.ofNullable(Axis.getRequestAttribute("javax.servlet.request.X509Certificate", X509Certificate[].class));
    }

    public static Optional<String> getDN()
    {
        return Axis.getCertificateChain().flatMap(t -> {
            try {
                X509Certificate cert = BouncyCastleUtil.getIdentityCertificate(t);
                return Optional.ofNullable(BouncyCastleUtil.getIdentity(cert));
            } catch (CertificateException e) {
                return Optional.empty();
            }
        });
    }

    public static Optional<GSSCredential> getDelegatedCredential()
    {
        return Optional.ofNullable(Axis.getRequestAttribute(GSI_CREDENTIALS, GSSCredential.class));
    }

    /**
     * Obtain an object from the set of attributes in the HttpServletRequest
     * @param key The name of the attribute
     * @param type The expected kind of attribute
     * @return the value of the attribute
     * @throws IllegalStateException if the attribute isn't set
     * @throws RuntimeException if the attribute isn't the expected type
     */
    public static <T> T getRequestAttribute(String key, Class<T> type)
    {
        MessageContext msgContext = MessageContext.getCurrentContext();
        HttpServletRequest request = (HttpServletRequest)
                msgContext.getProperty(MC_HTTP_SERVLETREQUEST);
        Object item = request.getAttribute(key);
        return item == null ? null : castAttribute(key, item, type);
    }

    public static String getRemoteAddress()
    {
        MessageContext msgContext = MessageContext.getCurrentContext();
        HttpServletRequest request = (HttpServletRequest)
                msgContext.getProperty(MC_HTTP_SERVLETREQUEST);
        return request.getRemoteAddr();
    }

    public static String getUserAgent()
    {
        MessageContext msgContext = MessageContext.getCurrentContext();
        HttpServletRequest request = (HttpServletRequest)
                msgContext.getProperty(MC_HTTP_SERVLETREQUEST);
        return request.getHeader(HTTPConstants.HEADER_USER_AGENT);
    }

    private static <T> T castAttribute(String key, Object item, Class<T> type)
    {
        if (item == null) {
            throw new IllegalStateException("Attribute " + key + " not found");
        }

        if (!type.isInstance(item)) {
            throw new RuntimeException("Attribute " + key + " not of type " + type);
        }

        return type.cast(item);
    }

    public static SRM getSRM()
    {
        return getAttribute(ATTRIBUTE_NAME_SRM, SRM.class);
    }

    public static AbstractStorageElement getStorage()
    {
        return getAttribute(ATTRIBUTE_NAME_STORAGE, AbstractStorageElement.class);
    }

    public static Configuration getConfiguration()
    {
        return getAttribute(ATTRIBUTE_NAME_CONFIG, Configuration.class);
    }
}
