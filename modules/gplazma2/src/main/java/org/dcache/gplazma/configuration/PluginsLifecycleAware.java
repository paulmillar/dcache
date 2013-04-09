package org.dcache.gplazma.configuration;

/**
 *  Allow for discovery that gPlazma configuration has been reloaded
 */
public interface PluginsLifecycleAware
{
    /**
     * Indicates that the gPlazma configuration has changed, triggering
     * re
     */
    public void pluginsReloaded();
}
