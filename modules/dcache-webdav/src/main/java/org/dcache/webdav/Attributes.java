/* dCache - http://www.dcache.org/
 *
 * Copyright (C) 2016 Deutsches Elektronen-Synchrotron
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.dcache.webdav;

import io.milton.servlet.ServletRequest;

import javax.security.auth.Subject;
import javax.servlet.http.HttpServletRequest;

import java.security.cert.X509Certificate;
import java.util.EnumSet;
import java.util.Set;

import org.dcache.auth.attributes.LoginAttribute;
import org.dcache.auth.attributes.Restriction;

import static org.dcache.webdav.AuthenticationHandler.*;

/**
 * Helper class for Attributes
 */
public class Attributes
{
    public static final String X509_CERTIFICATE_ATTRIBUTE =
            "javax.servlet.request.X509Certificate";
    public static final String DCACHE_SUBJECT_ATTRIBUTE =
            "org.dcache.subject";
    public static final String DCACHE_RESTRICTION_ATTRIBUTE =
            "org.dcache.restriction";
    public static final String DCACHE_LOGIN_ATTRIBUTES =
            "org.dcache.login";
    public static final String DCACHE_SUPPLIED_AUTHN_ATTRIBUTES =
            "org.dcache.supplied-authn";
    public static final String DCACHE_LOGOUT_ID =
            "org.dcache.logout";
    public static final String TRANSACTION_ATTRIBUTE =
            "org.dcache.transaction";

    private Attributes()
    {
    }

    public static void setTransaction(HttpServletRequest request, String transaction)
    {
        request.setAttribute(TRANSACTION_ATTRIBUTE, transaction);
    }

    public static void setSubject(HttpServletRequest request, Subject subject)
    {
        request.setAttribute(DCACHE_SUBJECT_ATTRIBUTE, subject);
    }

    public static void setRestriction(HttpServletRequest request, Restriction restriction)
    {
        request.setAttribute(DCACHE_RESTRICTION_ATTRIBUTE, restriction);
    }

    public static void setLoginAttributes(HttpServletRequest request, Set<LoginAttribute>  attributes)
    {
        request.setAttribute(DCACHE_LOGIN_ATTRIBUTES, attributes);
    }

    public static void setAuthenticationTypes(HttpServletRequest request, EnumSet<AuthenticationType> types)
    {
        request.setAttribute(DCACHE_SUPPLIED_AUTHN_ATTRIBUTES, types);
    }

    public static void setLogoutId(HttpServletRequest request, String id)
    {
        request.setAttribute(DCACHE_LOGOUT_ID, id);
    }

    private static <T> T getAttribute(HttpServletRequest request, String name, Class<T> type)
    {
        Object attribute = request.getAttribute(name);
        return attribute == null ? null : type.cast(attribute);
    }

    public static String getTransaction(HttpServletRequest request)
    {
        return getAttribute(request, TRANSACTION_ATTRIBUTE, String.class);
    }

    public static X509Certificate[] getX509Certificate(HttpServletRequest request)
    {
        return getAttribute(request, X509_CERTIFICATE_ATTRIBUTE, X509Certificate[].class);
    }

    public static Subject getSubject(HttpServletRequest request)
    {
        return getAttribute(request, DCACHE_SUBJECT_ATTRIBUTE, Subject.class);
    }

    public static Restriction getRestriction(HttpServletRequest request)
    {
        return getAttribute(request, DCACHE_RESTRICTION_ATTRIBUTE, Restriction.class);
    }

    public static Set<LoginAttribute> getLoginAttributes(HttpServletRequest request)
    {
        return getAttribute(request, DCACHE_LOGIN_ATTRIBUTES, Set.class);
    }

    public static String getLogoutId(HttpServletRequest request)
    {
        return getAttribute(request, DCACHE_LOGOUT_ID, String.class);
    }

    public static EnumSet<AuthenticationType> getSuppliedAuthenticationTypes(HttpServletRequest request)
    {
        return getAttribute(request, DCACHE_SUPPLIED_AUTHN_ATTRIBUTES, EnumSet.class);
    }


    /* The following methods are for use within Milton. */

    public static void setTransaction(String transaction)
    {
        setTransaction(ServletRequest.getRequest(), transaction);
    }


    public static Restriction getRestriction()
    {
        return getRestriction(ServletRequest.getRequest());
    }

    public static Set<LoginAttribute> getLoginAttributes()
    {
        return getLoginAttributes(ServletRequest.getRequest());
    }


    public static String getLogoutId()
    {
         return getLogoutId(ServletRequest.getRequest());
    }

    public static EnumSet<AuthenticationType> getSuppliedAuthenticationTypes()
    {
         return getSuppliedAuthenticationTypes(ServletRequest.getRequest());
    }
}
