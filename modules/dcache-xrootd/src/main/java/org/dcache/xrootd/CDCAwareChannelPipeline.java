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

    @Override
    public void sendUpstream(ChannelEvent e)
    {
        try (CDC ignored = establishCDC()) {
            super.sendUpstream(e);
        }
    }

    @Override
    public void sendDownstream(ChannelEvent e)
    {
        try (CDC ignored = establishCDC()) {
            super.sendDownstream(e);
        }
    }

    public void setCDC(CDC cdc)
    {
        _cdc = cdc;
    }

    private CDC establishCDC()
    {
        if (_cdc == null) {
            return new CDC();
        } else {
            return _cdc.restore();
        }
    }
}
