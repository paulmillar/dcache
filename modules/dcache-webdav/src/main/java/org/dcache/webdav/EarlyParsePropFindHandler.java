package org.dcache.webdav;

import io.milton.http.HttpManager;
import io.milton.http.Request;
import io.milton.http.Response;
import io.milton.http.exceptions.BadRequestException;
import io.milton.http.exceptions.ConflictException;
import io.milton.http.exceptions.NotAuthorizedException;
import io.milton.http.exceptions.NotFoundException;
import io.milton.http.webdav.PropFindHandler;
import io.milton.http.webdav.PropFindRequestFieldParser;
import io.milton.property.PropertyAuthoriser;
import io.milton.property.PropertyHandler;
import io.milton.resource.Resource;

import java.io.IOException;
import java.lang.reflect.Field;

/**
 * A wrapper class that provides the PropertyHandler for handling PROPFIND
 * requests.  This class wraps an existing PropFindHandler, which it both
 * uses for the bulk of the activity and modifies by wrapping the parser
 * with a CachingPropFindRequestFieldParser.  This PropertyHandler is
 * distinct from the PropFindHandler because it parses the XML request
 * before obtaining the Resource.  This, along with the
 * CachingProFindRequestFieldParser ensures that the ResourceFactory can
 * discover the PropertiesRequest when building the Resource.
 */
public class EarlyParsePropFindHandler implements PropertyHandler
{
    private final PropFindHandler _inner;
    private final CachingPropFindRequestFieldParser _parser;

    public EarlyParsePropFindHandler(PropFindHandler inner)
    {
        _inner = inner;

        try {
            Field field = PropFindHandler.class
                    .getDeclaredField("requestFieldParser");
            field.setAccessible(true);
            PropFindRequestFieldParser innerParser =
                    (PropFindRequestFieldParser)field.get(inner);
            _parser = new CachingPropFindRequestFieldParser(innerParser);
            field.set(inner, _parser);
        } catch (IllegalAccessException|SecurityException|NoSuchFieldException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public PropertyAuthoriser getPermissionService()
    {
        return _inner.getPermissionService();
    }

    @Override
    public String[] getMethods()
    {
        return _inner.getMethods();
    }

    @Override
    public void process(HttpManager httpManager, Request request,
            Response response) throws ConflictException, NotAuthorizedException,
            BadRequestException, NotFoundException
    {
        try {
            _parser.parse(request.getInputStream());

            _inner.process(httpManager, request, response);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            _parser.clear();
        }
    }

    @Override
    public boolean isCompatible(Resource resource)
    {
        return _inner.isCompatible(resource);
    }


}
