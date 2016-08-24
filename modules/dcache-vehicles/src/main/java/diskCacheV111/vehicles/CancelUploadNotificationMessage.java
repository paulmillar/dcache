package diskCacheV111.vehicles;

import javax.annotation.Nonnull;
import javax.security.auth.Subject;

import diskCacheV111.util.PnfsId;

import static java.util.Objects.requireNonNull;

/**
 * Notify that an upload has been cancelled.
 */
public class CancelUploadNotificationMessage extends Message
{
    private final PnfsId _pnfsId;
    private final String _explanation;

    public CancelUploadNotificationMessage(Subject subject, PnfsId id, String explanation)
    {
        setSubject(subject);
        _pnfsId = requireNonNull(id);
        _explanation = requireNonNull(explanation);
    }

    @Nonnull
    public PnfsId getPnfsId()
    {
        return _pnfsId;
    }

    @Nonnull
    public String getExplanation()
    {
        return _explanation;
    }
}
