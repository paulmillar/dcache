/*
 * Handler.java
 *
 * Created on October 17, 2003, 11:52 AM
 */

package dmg.cells.nucleus.protocols.cell;

import java.lang.reflect.Constructor;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;

import static org.dcache.util.Exceptions.Behaviour.RETURNS_RUNTIMEEXCEPTION;
import static org.dcache.util.Exceptions.unwrapInvocationTargetException;

/**
 *
 * @author  timur
 */
public class Handler extends URLStreamHandler
{
    @Override
    protected URLConnection openConnection(URL u) {
        try {
            ClassLoader threadLoader = Thread.currentThread().getContextClassLoader();
            if (threadLoader != null) {
                Class<? extends URLConnection> cls = threadLoader.loadClass(
                   "dmg.cells.nucleus.CellUrl.DomainUrlConnection").asSubclass(URLConnection.class);
                Constructor<? extends URLConnection> constr=cls.getConstructor(new Class[]
                    {URL.class,String.class});
                return constr.newInstance(u,"cell");


            }
        }
        catch(Exception e)
        {
            Exception cause = unwrapInvocationTargetException(e,
                    RETURNS_RUNTIMEEXCEPTION);
            cause.printStackTrace();
        }
        return null;
    }

}
