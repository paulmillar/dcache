package org.dcache.pool.movers;

import org.jboss.netty.channel.ChannelPipeline;
import org.jboss.netty.channel.ChannelPipelineFactory;

/**
 * A ChannelPipelineFactory that allows dynamic enabling of logging.
 */
public interface DynamicLoggingChannelPipelineFactory extends ChannelPipelineFactory
{
    /**
     * Add additional logging to the pipeline.  This method should be idempotent
     * so that multiple calls have the same effect as a single call.
     */
    public void addLogging(ChannelPipeline pipeline);
}
