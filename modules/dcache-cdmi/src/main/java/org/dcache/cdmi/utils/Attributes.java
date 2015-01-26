package org.dcache.cdmi.utils;

import javax.servlet.ServletContext;
import javax.servlet.ServletRequest;

import java.security.cert.X509Certificate;

import org.dcache.cells.CellStub;
import org.dcache.util.list.ListDirectoryHandler;

/**
 *  Utility methods for handling servlet attributes.
 */
public class Attributes
{
    public static final String NAME_PNFSMANAGER_CELLSTUB = "org.dcache.cdmi.pnfsstub";
    public static final String NAME_LISTDIRECTORYHANDLER = "org.dcache.cdmi.lister";
    public static final String NAME_POOL_CELLSTUB = "org.dcache.cdmi.poolstub";
    public static final String NAME_POOLMANAGER_CELLSTUB = "org.dcache.cdmi.poolmgrstub";
    public static final String NAME_BILLING_CELLSTUB = "org.dcache.cdmi.billingstub";

    public static CellStub getPnfsManager(ServletContext context)
    {
        return getAttributeValue(context, NAME_PNFSMANAGER_CELLSTUB, CellStub.class);
    }

    public static ListDirectoryHandler getListDirectoryHandler(ServletContext context)
    {
        return getAttributeValue(context, NAME_LISTDIRECTORYHANDLER, ListDirectoryHandler.class);
    }

    public static CellStub getPool(ServletContext context)
    {
        return getAttributeValue(context, NAME_POOL_CELLSTUB, CellStub.class);
    }

    public static CellStub getPoolManager(ServletContext context)
    {
        return getAttributeValue(context, NAME_POOLMANAGER_CELLSTUB, CellStub.class);
    }

    public static CellStub getBilling(ServletContext context)
    {
        return getAttributeValue(context, NAME_BILLING_CELLSTUB, CellStub.class);
    }

    private static <T> T getAttributeValue(ServletContext context, String name, Class<T> type)
    {
        Object attribute = context.getAttribute(name);
        if (attribute == null) {
            throw new RuntimeException("Attribute " + name + " not found");
        }

        if (!type.isInstance(attribute)) {
            throw new RuntimeException("Attribute " + name + " not of type " + type.getCanonicalName());
        }
        return type.cast(attribute);
    }

    public static X509Certificate[] getClientCertificates(ServletRequest request)
    {
        Object object = request.getAttribute("javax.servlet.request.X509Certificate");
        return (object instanceof X509Certificate[]) ? (X509Certificate[]) object : null;
    }
}
