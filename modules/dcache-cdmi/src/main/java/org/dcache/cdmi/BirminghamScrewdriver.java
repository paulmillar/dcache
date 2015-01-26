package org.dcache.cdmi;

import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.util.Attributes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.EventListener;

/**
 *
 */
public class BirminghamScrewdriver extends ServletContextHandler
{
    private static final Logger LOG = LoggerFactory.getLogger(BirminghamScrewdriver.class);

    @Override
    public String setInitParameter(String key, String value)
    {
        LOG.error("BirminghamScrewdriver.setInitParameter: {}, {}", key, value);
        return super.setInitParameter(key, value);
    }

    @Override
    protected void doStart() throws Exception
    {
        LOG.error("BirminghamScrewdriver.doStart");
        super.doStart();
    }

    @Override
    public void setEventListeners(EventListener[] eventListeners)
    {
        LOG.error("BirminghamScrewdriver.setEventListeners: {}", Arrays.toString(eventListeners));
        super.setEventListeners(eventListeners);
    }

    @Override
    public void setAttributes(Attributes attributes)
    {
        LOG.error("BirminghamScrewdriver.setAttributes: {}", attributes);
        super.setAttributes(attributes);
    }

    @Override
    public void setServletHandler(ServletHandler servletHandler)
    {
        LOG.error("BirminghamScrewdriver.setServletHandler: {}", servletHandler);
        super.setServletHandler(servletHandler);
    }
}
