/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.dcache.services.info.gathers.pnfsmanager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

import diskCacheV111.namespace.usage.Record;
import diskCacheV111.namespace.usage.Type;
import diskCacheV111.namespace.usage.Usage;

import dmg.cells.nucleus.UOID;

import org.dcache.services.info.base.IntegerStateValue;
import org.dcache.services.info.base.StatePath;
import org.dcache.services.info.base.StateUpdate;
import org.dcache.services.info.base.StateUpdateManager;
import org.dcache.services.info.gathers.CellMessageHandlerSkel;
import org.dcache.services.info.gathers.MessageMetadataRepository;
import org.dcache.vehicles.PnfsAccountUsageByGidMessage;

/**
 *
 * @author paul
 */
public class AccountUsageByGidHandler extends CellMessageHandlerSkel
{
    private static final Logger LOG = LoggerFactory.getLogger(AccountUsageByGidHandler.class);
    private static final StatePath ROOT = StatePath.parsePath("summary.usage.by-group");

    public AccountUsageByGidHandler(StateUpdateManager sum, MessageMetadataRepository<UOID> msgHandlerChain)
    {
        super(sum, msgHandlerChain);
    }

    @Override
    public void process(Object payload, long lifetime)
    {
        LOG.error("Received payload");
        if (!(payload instanceof PnfsAccountUsageByGidMessage)) {
            LOG.warn("Unknown response payload: {}", payload.getClass().getCanonicalName());
            return;
        }

        Map<Long,Usage> report = ((PnfsAccountUsageByGidMessage)payload).getUsage();

        if (report == null) {
            LOG.warn("Received null usage record");
            return;
        }

        StateUpdate update = new StateUpdate();
        update.purgeUnder(ROOT);

        report.entrySet().stream().forEach(e -> appendUsage(update, ROOT.newChild(Long.toString(e.getKey())), e.getValue(), lifetime));
        applyUpdates(update);
    }

    private void appendUsage(StateUpdate update, StatePath path, Usage usage, long lifetime)
    {
        for (Type type : Type.values()) {
            if (usage.has(type)) {
                Record record = usage.get(type);
                StatePath parent = path.newChild(type.name().toLowerCase());

                update.appendUpdate(parent.newChild("file-count"),
                        new IntegerStateValue(record.getFileCount(), lifetime));
                update.appendUpdate(parent.newChild("logical"),
                        new IntegerStateValue(record.getLogicalUsage(), lifetime));

                Map<String,Long> physicalUsage = record.getPhysicalUsage();
                if (!physicalUsage.isEmpty()) {
                    StatePath physical = parent.newChild("physical");
                    physicalUsage.entrySet().stream().forEach(e -> {
                        update.appendUpdate(physical.newChild(e.getKey()),
                                new IntegerStateValue(e.getValue(), lifetime));
                    });
                }
            }
        }
    }
}
