package org.dcache.services.info.gathers.pnfsmanager;

import java.util.HashSet;
import java.util.Set;

import dmg.cells.nucleus.UOID;

import org.dcache.services.info.base.StateExhibitor;
import org.dcache.services.info.base.StateUpdateManager;
import org.dcache.services.info.gathers.DgaFactoryService;
import org.dcache.services.info.gathers.MessageMetadataRepository;
import org.dcache.services.info.gathers.MessageSender;
import org.dcache.services.info.gathers.Schedulable;

/**
 * Generate probes to query PnfsManager.
 */
public class PnfsManagerDgaFactoryService implements DgaFactoryService
{

    @Override
    public Set<Schedulable> createDgas(StateExhibitor exhibitor,
            MessageSender sender, StateUpdateManager sum,
            MessageMetadataRepository<UOID> msgMetaRepo)
    {
        Set<Schedulable> activity = new HashSet<>();

        activity.add(new AccountUsageByGidDga(sender, "PnfsManager",
                new AccountUsageByGidHandler(sum, msgMetaRepo), 120));

        return activity;
    }

}
