package org.dcache.webdav;

import io.milton.config.HttpManagerBuilder;
import io.milton.http.Handler;
import io.milton.http.HttpManager;
import io.milton.http.webdav.DefaultPropPatchParser;
import io.milton.http.webdav.DefaultWebDavResponseHandler;
import io.milton.http.webdav.PropFindHandler;
import io.milton.http.webdav.WebDavProtocol;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Iterator;
import java.util.Set;

public class HttpManagerFactory extends HttpManagerBuilder implements FactoryBean
{
    private Resource _templateResource;
    private String _staticContentPath;

    @Override
    public Object getObject() throws Exception
    {
        DcacheResponseHandler dcacheResponseHandler = new DcacheResponseHandler();
        setWebdavResponseHandler(dcacheResponseHandler);

        init();

        Set<Handler> handlers = getWebDavHandlers();
        PropFindHandler handler = extractPropFindHandler(handlers);
        handlers.add(new EarlyParsePropFindHandler(handler));

        // Late initialization of DcacheResponseHandler because AuthenticationService and other collaborators
        // have to be created first.
        dcacheResponseHandler.setAuthenticationService(getAuthenticationService());
        dcacheResponseHandler.setWrapped(
            new DefaultWebDavResponseHandler(getHttp11ResponseHandler(), getResourceTypeHelper(),
                                             getPropFindXmlGenerator()));
        dcacheResponseHandler.setTemplateResource(_templateResource);
        dcacheResponseHandler.setStaticContentPath(_staticContentPath);
        dcacheResponseHandler.setBuffering(getBuffering());

        return buildHttpManager();
    }

    @SuppressWarnings("unchecked")
    private Set<Handler> getWebDavHandlers()
    {
        try {
            Field field = WebDavProtocol.class.getDeclaredField("handlers");
            field.setAccessible(true);
            return (Set<Handler>) field.get(getWebDavProtocol());
        } catch (NoSuchFieldException|IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }

    private PropFindHandler extractPropFindHandler(Set<Handler> handlers)
    {
        PropFindHandler handler = null;

        Iterator<Handler> iterator = handlers.iterator();
        while (iterator.hasNext()) {
            Handler h = iterator.next();
            if (h instanceof PropFindHandler) {
                handler = (PropFindHandler) h;
                iterator.remove();
            }
        }

        if (handler == null) {
            throw new RuntimeException("Unable to find PropFindHandler");
        }

        return handler;
    }

    @Override
    public Class<?> getObjectType()
    {
        return HttpManager.class;
    }

    @Override
    public boolean isSingleton()
    {
        return true;
    }

    /**
     * Sets the resource containing the StringTemplateGroup for
     * directory listing.
     */
    public void setTemplateResource(Resource resource)
        throws IOException
    {
        _templateResource = resource;
    }

    /**
     * The static content path is the path under which the service
     * exports the static content. This typically contains stylesheets
     * and image files.
     */
    public void setStaticContentPath(String path)
    {
        _staticContentPath = path;
    }
}
