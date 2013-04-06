package dmg.util;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;

/**
 * Store the accumulated CPU usage in nanoseconds.  Models CPU usage as
 * combined = user + system.
 *
 * Allows time advancement in two ways: by specifying a new value (which
 * return the increment) or by specifying an increment (which returns the new
 * value).
 *
 * Since user and combined values may be updated independently, there is a
 * separate assert method that may be called once both values have been
 * updated.
 */
public class CpuUsage implements Cloneable
{
    private long _combined;
    private long _user;

    public void reset()
    {
        _combined = 0;
        _user = 0;
    }

    @Override
    public CpuUsage clone()
    {
        CpuUsage cloned = new CpuUsage();
        cloned._combined = _combined;
        cloned._user = _user;
        return cloned;
    }

    public long addCombined(long delta)
    {
        checkArgument(delta >= 0, "negative delta not allowed");
        _combined += delta;
        return _combined;
    }

    public long addUser(long delta)
    {
        checkArgument(delta >= 0, "negative delta not allowed");
        _user += delta;
        return _user;
    }

    public long setCombined(long newValue)
    {
        long oldValue = _combined;
        checkArgument(newValue >= oldValue, "retrograde clock detected");
        _combined = newValue;
        return newValue - oldValue;
    }

    public long setUser(long newValue)
    {
        long oldValue = _user;
        checkArgument(newValue >= oldValue, "retrograde clock detected");
        _user = newValue;
        return newValue - oldValue;
    }

    public long getCombined()
    {
        return _combined;
    }

    public long getUser()
    {
        return _user;
    }

    public long getSystem()
    {
        return _combined - _user;
    }

    public void assertValues()
    {
        checkState(_combined >= _user, "user value greater than combined");
    }
}
