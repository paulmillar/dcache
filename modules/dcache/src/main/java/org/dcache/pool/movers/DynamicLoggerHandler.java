package org.dcache.pool.movers;

import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.SimpleChannelHandler;

import dmg.cells.nucleus.CDC;

/**
 * A Netty ChannelHandler that ensures that the ChannelPipeline has a
 * LoggingHandler if diagnose is enabled.
 */
public class DynamicLoggerHandler extends SimpleChannelHandler
{
    private final DynamicLoggingChannelPipelineFactory _factory;

    public DynamicLoggerHandler(DynamicLoggingChannelPipelineFactory factory)
    {
        _factory = factory;
    }

    @Override
    public void handleUpstream(ChannelHandlerContext context, ChannelEvent event)
            throws Exception
    {
        if (CDC.isDiagnoseEnabled()) {
            _factory.addLogging(context.getPipeline());
        }

        super.handleUpstream(context, event);
    }

    @Override
    public void handleDownstream(ChannelHandlerContext context,
            ChannelEvent event) throws Exception
    {
        if (CDC.isDiagnoseEnabled()) {
            _factory.addLogging(context.getPipeline());
        }

        super.handleDownstream(context, event);
    }
}
