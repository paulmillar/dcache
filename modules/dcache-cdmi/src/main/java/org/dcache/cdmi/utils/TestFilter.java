package org.dcache.cdmi.utils;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.core.Request;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.container.PreMatching;
import javax.ws.rs.core.SecurityContext;
import javax.ws.rs.ext.Provider;

import java.io.IOException;

@PreMatching
@Provider
public class TestFilter implements ContainerRequestFilter
{
    @Override
    public void filter(ContainerRequestContext crc) throws IOException
    {
        Request request = crc.getRequest();
        SecurityContext context = crc.getSecurityContext();
        System.out.println("TestFilter: crc=" + crc + ", request=" + request +
                ", context=" + context);
    }
}
