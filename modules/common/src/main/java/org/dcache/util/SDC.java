package org.dcache.util;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.google.common.base.Preconditions.checkState;

/**
 * The SharableDiagnosticContext provides a set of key-value pairs that is
 * thread-local by default but may be shared between threads.
 */
public class SDC
{
    private static ThreadLocal<ConcurrentHashMap<String,String>> _context =
            new ThreadLocal<ConcurrentHashMap<String,String>>() {
        @Override
        protected ConcurrentHashMap initialValue()
        {
            return new ConcurrentHashMap();
        }
    };

    public static void put(String key, String value)
    {
        if (value == null) {
            _context.get().remove(key);
        } else {
            _context.get().put(key, value);
        }
    }

    public static String get(String key)
    {
        return _context.get().get(key);
    }

    public static void remove(String key)
    {
        _context.get().remove(key);
    }

    public static void reset()
    {
        _context.set(new ConcurrentHashMap<String,String>());
    }

    private ConcurrentHashMap<String,String> _copy;
    private ConcurrentHashMap<String,String> _current;
    private AtomicBoolean _beenUsed = new AtomicBoolean();

    public SDC()
    {
        _current = _context.get();
        _copy = new ConcurrentHashMap<>(_current);
    }

    public void rollback()
    {
        checkState(!_beenUsed.getAndSet(true), "SDC rollback after being used");
        ConcurrentHashMap<String,String> storage = _context.get();
        storage.clear();
        storage.putAll(_copy);
    }

    public void enact()
    {
        checkState(!_beenUsed.getAndSet(true), "SDC enact after being used");
        _context.set(_current);
    }

    public void putInfo(String key, String value)
    {
        // update value
    }

    public String getFrom(String key)
    {
        return null;
    }

    public void removeFrom(String key)
    {
        // do remove
    }
}
