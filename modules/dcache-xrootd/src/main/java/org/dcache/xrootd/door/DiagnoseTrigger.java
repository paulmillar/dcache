package org.dcache.xrootd.door;

import org.jboss.netty.channel.Channel;
import org.jboss.netty.channel.ChannelEvent;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelStateEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.logging.LoggingHandler;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import org.dcache.util.DiagnoseTriggers;
import dmg.cells.nucleus.CDC;

/**
 * Capture a channel connected event and check if we should enable diagnose
 * for this connection.
 */
public class DiagnoseTrigger extends SimpleChannelUpstreamHandler
{
    private final DiagnoseTriggers<InetAddress> _triggers;
    private boolean _isDiagnoseEnabled;

    public DiagnoseTrigger(DiagnoseTriggers<InetAddress> triggers)
    {
        _triggers = triggers;
    }

    @Override
    public void handleUpstream(ChannelHandlerContext ctx, ChannelEvent e)
            throws Exception
    {
        super.handleUpstream(ctx, e);
        CDC.setDiagnoseEnabled(_isDiagnoseEnabled);
    }


    @Override
    public void channelConnected(ChannelHandlerContext ctx,
                                 ChannelStateEvent e)
            throws Exception
    {
        super.channelConnected(ctx, e);

        SocketAddress address = e.getChannel().getRemoteAddress();
        if (address instanceof InetSocketAddress) {
            _isDiagnoseEnabled = _triggers.accept(((InetSocketAddress)address).getAddress());

            if (_isDiagnoseEnabled) {
                ChannelPipeline pipeline = ctx.getPipeline();

                if (!pipeline.getNames().contains("logger")) {
                    pipeline.addAfter("decoder", "logger",
                            new LoggingHandler(NettyXrootdServer.class));
                }
            }
        }
    }
}
