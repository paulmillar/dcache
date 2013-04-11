package org.dcache.missingfiles.plugins;

import java.util.Properties;


/**
 *  Factory class for the SEMsg plugin
 */
public class SEMsgPluginFactory implements PluginFactory
{
    @Override
    public String getName()
    {
        return "semsg";
    }

    @Override
    public Plugin createPlugin(Properties properties)
    {
        return new SEMsgPlugin(properties);
    }
}
