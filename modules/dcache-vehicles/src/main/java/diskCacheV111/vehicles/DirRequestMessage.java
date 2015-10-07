package diskCacheV111.vehicles;

import diskCacheV111.util.PnfsId;

import org.dcache.auth.attributes.Restriction;

/**
 * A request that the {@literal dir} cell list information about the specified
 * path.
 */
public class DirRequestMessage extends PoolIoFileMessage
{
    private final Restriction _restriction;

    public DirRequestMessage(String pool, PnfsId pnfsId, ProtocolInfo info,
            Restriction restriction) {
        super(pool, pnfsId, info);
        _restriction = restriction;
    }

    public Restriction getRestriction()
    {
        return _restriction;
    }
}
