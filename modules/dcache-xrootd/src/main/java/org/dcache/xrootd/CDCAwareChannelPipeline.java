package org.dcache.xrootd;

import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.DefaultChannelPipeline;

import dmg.cells.nucleus.CDC;

/**
 * A ChannelPipeline that ensures that each event is processed in isolation, so
 * any changes to the CDC when processing one event picked up by the same
 * thread processing a ChannelPipeline.  It also supports setting the CDC to
 * some stored CDC.  The stored CDC may be updated dynamically, either when
 * processing an event or outside of an event.  When updated, the new CDC will
 * only affect subsequent event processing.
 */
public class CDCAwareChannelPipeline extends DefaultChannelPipeline
{
    private volatile CDC _cdc;

    public CDCAwareChannelPipeline()
    {
        try (CDC ignored = CDC.reset(CDC.getCellName(), CDC.getDomainName())) {
            _cdc = new CDC();
        }
    }

    @Override
    public void sendUpstream(ChannelEvent e)
    {
        try (CDC ignored = _cdc.restore()) {
            super.sendUpstream(e);
        }
    }

    @Override
    public void sendDownstream(ChannelEvent e)
    {
        try (CDC ignored = _cdc.restore()) {
            super.sendDownstream(e);
        }
    }

    public void setCDC(CDC cdc)
    {
        _cdc = cdc;
    }
}
