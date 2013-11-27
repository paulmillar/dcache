package org.dcache.webdav;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import io.milton.http.Auth;
import io.milton.http.Range;
import io.milton.http.Request;
import io.milton.http.exceptions.BadRequestException;
import io.milton.http.exceptions.ConflictException;
import io.milton.http.exceptions.NotAuthorizedException;
import io.milton.property.PropertySource.PropertyMetaData;
import io.milton.property.PropertySource.PropertySetException;
import io.milton.resource.DeletableResource;
import io.milton.resource.GetableResource;
import io.milton.resource.MultiNamespaceCustomPropertyResource;

import javax.xml.namespace.QName;

import java.io.IOException;
import java.io.OutputStream;
import java.net.FileNameMap;
import java.net.URISyntaxException;
import java.net.URLConnection;
import java.util.List;
import java.util.Map;

import diskCacheV111.util.AccessLatency;
import diskCacheV111.util.CacheException;
import diskCacheV111.util.FileNotFoundCacheException;
import diskCacheV111.util.FsPath;
import diskCacheV111.util.NotInTrashCacheException;
import diskCacheV111.util.PermissionDeniedCacheException;
import diskCacheV111.util.RetentionPolicy;

import org.dcache.vehicles.FileAttributes;

import static io.milton.property.PropertySource.PropertyAccessibility.READ_ONLY;
import static org.dcache.util.Checksums.TO_RFC3230;
import static org.dcache.webdav.DcacheResourceFactory.SRM_NAMESPACE_URI;
import static org.dcache.webdav.DcacheResourceFactory.DCACHE_NAMESPACE_URI;
import static org.dcache.webdav.DcacheResourceFactory.PROPERTY_ACCESS_LATENCY;
import static org.dcache.webdav.DcacheResourceFactory.PROPERTY_RETENTION_POLICY;
import static org.dcache.webdav.DcacheResourceFactory.PROPERTY_CHECKSUMS;
import static org.dcache.webdav.DcacheResourceFactory.ACCESS_LATENCY_QNAME;
import static org.dcache.webdav.DcacheResourceFactory.RETENTION_POLICY_QNAME;
import static org.dcache.webdav.DcacheResourceFactory.CHECKSUMS_QNAME;

/**
 * Exposes regular dCache files as resources in the Milton WebDAV
 * framework.
 */
public class DcacheFileResource
    extends DcacheResource
    implements GetableResource, DeletableResource,
    MultiNamespaceCustomPropertyResource
{
    private static final FileNameMap MIME_TYPE_MAP =
        URLConnection.getFileNameMap();

    private static final ImmutableMap<QName,PropertyMetaData> PROPERTY_METADATA =
            new ImmutableMap.Builder<QName,PropertyMetaData>()
                    .put(ACCESS_LATENCY_QNAME, new PropertyMetaData(READ_ONLY,
                            AccessLatency.class))
                    .put(RETENTION_POLICY_QNAME, new PropertyMetaData(READ_ONLY,
                            RetentionPolicy.class))
                    .put(CHECKSUMS_QNAME, new PropertyMetaData(READ_ONLY,
                            String.class))
                    .build();

    public DcacheFileResource(DcacheResourceFactory factory,
                              FsPath path, FileAttributes attributes)
    {
        super(factory, path, attributes);
    }

    @Override
    public void sendContent(OutputStream out, Range range,
                            Map<String,String> params, String contentType)
        throws IOException, NotAuthorizedException
    {
        try {
            _factory.readFile(new FsPath(_path), _attributes.getPnfsId(),
                              out, range);
        } catch (PermissionDeniedCacheException e) {
            throw new NotAuthorizedException(this);
        } catch (FileNotFoundCacheException | NotInTrashCacheException e) {
            throw new ForbiddenException(e.getMessage(), e, this);
        } catch (CacheException e) {
            throw new WebDavException(e.getMessage(), e, this);
        } catch (InterruptedException e) {
            throw new WebDavException("Transfer was interrupted", e, this);
        } catch (URISyntaxException e) {
            throw new WebDavException("Invalid request URI: " + e.getMessage(), e, this);
        }
    }

    @Override
    public Long getMaxAgeSeconds(Auth auth)
    {
        return null;
    }

    @Override
    public String getContentType(String accepts)
    {
        return MIME_TYPE_MAP.getContentTypeFor(_path.toString());
    }

    @Override
    public Long getContentLength()
    {
        return _attributes.getSize();
    }

    @Override
    public String checkRedirect(Request request)
    {
        try {
            if (_factory.shouldRedirect(request)) {
                return _factory.getReadUrl(_path, _attributes.getPnfsId());
            }
            return null;
        } catch (PermissionDeniedCacheException e) {
            throw new UnauthorizedException(e.getMessage(), e, this);
        } catch (CacheException | InterruptedException e) {
            throw new WebDavException(e.getMessage(), e, this);
        } catch (URISyntaxException e) {
            throw new WebDavException("Invalid request URI: " + e.getMessage(), e, this);
        }
    }

    @Override
    public void delete()
        throws NotAuthorizedException, ConflictException, BadRequestException
    {
        try {
            _factory.deleteFile(_attributes.getPnfsId(), _path);
        } catch (PermissionDeniedCacheException e) {
            throw new NotAuthorizedException(this);
        } catch (CacheException e) {
            throw new WebDavException(e.getMessage(), e, this);
        }
    }

    public String getRfc3230Digest()
    {
        return _attributes.getChecksumsIfPresent().transform(TO_RFC3230).or("");
    }

    @Override
    public Object getProperty(QName qname)
    {
        switch (qname.getNamespaceURI()) {
        case DCACHE_NAMESPACE_URI:
            return getDcacheProperty(qname.getLocalPart());
        case SRM_NAMESPACE_URI:
            return getSrmProperty(qname.getLocalPart());
        }

        // Milton filters out unknown properties by checking with the
        // PropertyMetaData, so if we get here then it's a bug.
        throw new RuntimeException("unknown property " + qname);
    }

    private Object getDcacheProperty(String localPart)
    {
        switch(localPart) {
        case PROPERTY_CHECKSUMS:
            return _attributes.getChecksumsIfPresent().transform(TO_RFC3230).orNull();
        }

        throw new RuntimeException("unknown dCache property " + localPart);
    }

    private Object getSrmProperty(String localPart)
    {
        switch(localPart) {
        case PROPERTY_ACCESS_LATENCY:
            return _attributes.getAccessLatencyIfPresent().orNull();
        case PROPERTY_RETENTION_POLICY:
            return _attributes.getRetentionPolicyIfPresent().orNull();
        }

        throw new RuntimeException("unknown SRM property " + localPart);
    }

    @Override
    public void setProperty(QName qname, Object o) throws PropertySetException,
            NotAuthorizedException
    {
        // Handle any updates here.

        // We should not see any read-only or unknown properties as Milton
        // discovers them from PropertyMetaData and filters out any attempt by
        // end-users.
        throw new RuntimeException("Attempt to update " +
                (PROPERTY_METADATA.containsKey(qname) ? "read-only" : "unknown") +
                "property " + qname);
    }

    @Override
    public PropertyMetaData getPropertyMetaData(QName qname)
    {
        // Milton accepts null and PropertyMetaData.UNKNOWN to mean the
        // property is unknown.
        return PROPERTY_METADATA.get(qname);
    }

    @Override
    public List<QName> getAllPropertyNames()
    {
        return PROPERTY_METADATA.keySet().asList();
    }
}
