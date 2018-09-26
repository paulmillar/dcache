package diskCacheV111.vehicles.transferManager;

import diskCacheV111.vehicles.IoJobInfo;
import diskCacheV111.vehicles.RemoteTransferJobInfo;

/**
 * Message to query the transfermanager service to discover the current
 * status of a transfer.
 */
public class TransferStatusQueryMessage extends TransferManagerMessage
{
    private static final long serialVersionUID = 1L;

    private int _state;
    private RemoteTransferJobInfo _info;

    public TransferStatusQueryMessage(long id)
    {
        setId(id);
    }

    public void setMoverInfo(RemoteTransferJobInfo info)
    {
        _info = info;
    }

    /**
     * Returns an IoJobInfo for the mover, or null if no mover is currently
     * active.
     */
    public RemoteTransferJobInfo getMoverInfo()
    {
        return _info;
    }

    public void setState(int state)
    {
        _state = state;
    }

    /**
     * Returns the current status of the request, as defined in the
     * TransferManangerHandler static field members.
     */
    public int getState()
    {
        return _state;
    }
}
