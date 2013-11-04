package org.dcache.xrootd.door;

import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

import org.dcache.util.DiagnoseTriggers;

/**
 * Capture a channel connected event and check if we should enable diagnose
 * for this connection.
 */
public class DiagnoseTrigger extends SimpleChannelUpstreamHandler
{
    private final DiagnoseTriggers<InetAddress> _triggers;

    public DiagnoseTrigger(DiagnoseTriggers<InetAddress> triggers)
    {
        _triggers = triggers;
    }

    @Override
    public void channelConnected(final ChannelHandlerContext ctx,
                                 ChannelStateEvent e)
            throws Exception
    {
        SocketAddress genericAddress = e.getChannel().getRemoteAddress();
        if (genericAddress instanceof InetSocketAddress) {
            InetSocketAddress inetAddress = (InetSocketAddress)genericAddress;
            _triggers.accept(inetAddress.getAddress());
        }

        super.channelConnected(ctx, e);
    }
}
