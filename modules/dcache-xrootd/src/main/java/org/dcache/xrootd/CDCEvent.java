package org.dcache.xrootd;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelFuture;

import dmg.cells.nucleus.CDC;

import static org.jboss.netty.channel.Channels.succeededFuture;

/**
 * A ChannelEvent that transports the CDC that should be used for this
 * ChannelPipeline.
 */
public class CDCEvent implements ChannelEvent
{
    private final Channel _channel;
    private final CDC _cdc = new CDC();

    public CDCEvent(Channel channel)
    {
        _channel = channel;
    }

    public CDC getCDC()
    {
        return _cdc;
    }

    @Override
    public Channel getChannel()
    {
        return _channel;
    }

    @Override
    public ChannelFuture getFuture()
    {
        return succeededFuture(getChannel());
    }
}
