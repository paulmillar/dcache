package org.dcache.xrootd.door;

import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;

import org.dcache.util.DiagnoseTriggers;
import org.dcache.xrootd.CDCEvent;

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
        SocketAddress sockAddress = e.getChannel().getRemoteAddress();
        if (sockAddress instanceof InetSocketAddress) {
            InetAddress address = ((InetSocketAddress)sockAddress).getAddress();

            if (_triggers.accept(address)) {
                ctx.sendUpstream(new CDCEvent(ctx.getChannel()));
            }
        }

        super.channelConnected(ctx, e);
    }
}
