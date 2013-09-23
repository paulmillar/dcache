package org.dcache.xrootd.door;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.SimpleChannelHandler;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import org.dcache.cells.CellCommandListener;
import org.dcache.util.DiagnoseTriggers;

/**
 * Capture a channel connected event and check if we should enable diagnose
 * for this connection.
 */
public class DiagnoseTrigger extends SimpleChannelHandler
        implements CellCommandListener
{
    private final DiagnoseTriggers<InetAddress> _triggers;

    public DiagnoseTrigger(DiagnoseTriggers<InetAddress> triggers)
    {
        _triggers = triggers;
    }

    @Override
    public void channelConnected(ChannelHandlerContext ctx,
                                 ChannelStateEvent e)
            throws Exception
    {
        super.channelConnected(ctx, e);
        SocketAddress address = e.getChannel().getRemoteAddress();
        if (address instanceof InetSocketAddress) {
            _triggers.accept(((InetSocketAddress)address).getAddress());
        }
    }
}
