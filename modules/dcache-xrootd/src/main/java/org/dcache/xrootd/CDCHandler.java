package org.dcache.xrootd;

import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.SimpleChannelHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dmg.cells.nucleus.CDC;


/**
 * This class provides a ChannelHandler that accepts the CDCEvent and updates
 * the ChannelPipeline so that subsequent events will be processed with this
 * CDC.  Any CDCEvent is not propagated beyond this handler.
 */
public class CDCHandler extends SimpleChannelHandler
{
    Logger LOG = LoggerFactory.getLogger(CDCHandler.class);

    @Override
    public void handleUpstream(ChannelHandlerContext ctx, ChannelEvent event)
            throws Exception
    {
        if (event instanceof CDCEvent) {
            updatePipeline(ctx.getPipeline(), (CDCEvent)event);
        } else {
            super.handleUpstream(ctx, event);
        }
    }

    @Override
    public void handleDownstream(ChannelHandlerContext ctx, ChannelEvent event)
            throws Exception
    {
        if (event instanceof CDCEvent) {
            updatePipeline(ctx.getPipeline(), (CDCEvent)event);
        } else {
            super.handleDownstream(ctx, event);
        }
    }

    private void updatePipeline(ChannelPipeline pipeline, CDCEvent event)
    {
        CDC cdc = event.getCDC();

        if (pipeline instanceof CDCAwareChannelPipeline) {
            ((CDCAwareChannelPipeline)pipeline).setCDC(cdc);
        } else {
            LOG.warn("CDCEvent received but ChannelPipeline is not CDC-aware");
        }
    }
}
