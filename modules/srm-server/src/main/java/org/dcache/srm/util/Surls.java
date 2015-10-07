package org.dcache.srm.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;

import diskCacheV111.util.FsPath;

import org.dcache.srm.SRMInvalidPathException;
import org.dcache.srm.SRMInvalidRequestException;

/**
 * Utility class to work with SURLs.
 */
public class Surls
{
    private Surls()
    {
        // prevent instantiation
    }

    public static URI getParent(URI url) throws SRMInvalidPathException
    {
        String path = url.getPath();

        if (!path.startsWith("/")) {
            throw new SRMInvalidPathException("Badly formed SURL");
        }

        int previous = path.length();
        int index = path.lastIndexOf('/', previous-1);
        while (previous-index == 1 && index > -1) {
            previous = index;
            index = path.lastIndexOf('/', previous-1);
        }

        if (index == -1) {
            throw new SRMInvalidPathException("Cannot operation on root");
        }

        while (index > 0 && path.charAt(index-1) == '/') {
            index--;
        }

        if (index == 0) {
            path = "/";
        } else {
            path = path.substring(0, index);
        }

        try {
            return new URI(url.getScheme(), url.getUserInfo(), url.getHost(),
                    url.getPort(), path, url.getQuery(), url.getFragment());
        } catch (URISyntaxException e) {
            // This should never happen.
            throw new RuntimeException("Unable to get parent: " + url, e);
        }
    }

}
