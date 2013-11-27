package org.dcache.webdav;

import io.milton.http.webdav.PropFindRequestFieldParser;
import io.milton.http.webdav.PropertiesRequest;

import java.io.InputStream;

/**
 * Simple wrapper to hold the result of getRequestedFields for later inspection.
 */
public class CachingPropFindRequestFieldParser implements PropFindRequestFieldParser
{
    private final PropFindRequestFieldParser _inner;
    private static final ThreadLocal<PropertiesRequest> _request = new ThreadLocal<>();

    public CachingPropFindRequestFieldParser(PropFindRequestFieldParser inner)
    {
        _inner = inner;
    }

    public void parse(InputStream in)
    {
        _request.set(_inner.getRequestedFields(in));
    }

    @Override
    public PropertiesRequest getRequestedFields(InputStream in)
    {
        PropertiesRequest request = _request.get();

        if (request == null) {
            request = _inner.getRequestedFields(in);
            _request.set(request);
        }

        return request;
    }

    public static PropertiesRequest get()
    {
        return _request.get();
    }

    public void clear()
    {
        _request.remove();
    }
}
