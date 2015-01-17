package org.dcache.services.info.gathers.pnfsmanager;

import diskCacheV111.vehicles.Message;

import dmg.cells.nucleus.CellMessage;
import dmg.cells.nucleus.CellMessageAnswerable;
import dmg.cells.nucleus.CellPath;

import org.dcache.services.info.gathers.MessageSender;
import org.dcache.services.info.gathers.SkelPeriodicActivity;
import org.dcache.vehicles.PnfsAccountUsageByGidMessage;

/**
 *  Send a message to PnfsManager to query the current usage by gid.
 */
public class AccountUsageByGidDga extends SkelPeriodicActivity
{
    private final CellPath _path;
    private final MessageSender _sender;
    private final CellMessageAnswerable _handler;

    public AccountUsageByGidDga(MessageSender sender, String cellName, CellMessageAnswerable handler, long interval)
    {
        super(interval);

        _path = new CellPath(cellName);
        _handler = handler;
        _sender = sender;
    }

    @Override
    public void trigger()
    {
        super.trigger();
        Message message = new PnfsAccountUsageByGidMessage();
        message.setReplyRequired(true);
        _sender.sendMessage(getPeriod(), _handler, new CellMessage(_path, message));
    }

    @Override
    public String toString()
    {
        return getClass().getSimpleName();
    }
}
