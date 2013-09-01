package org.dcache.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.google.common.base.Preconditions.checkState;

/**
 * The SharableDiagnosticContext provides a set of associations that, by
 * default, is thread-local, but allows for two or more threads to share the
 * same set of associations and for a thread to revert the set of associations
 * to a previous point.
 * <p>
 * The ability to both share associations and revert changes are initialised by
 * first capturing the current set of associations.  This is achieved by
 * creating a new SDC object.
 * <p>
 * To revert any changes to the set of associations, the capturing thread calls
 * the {@link #rollback} method.  It is a bug if any other thread calls this
 * method.
 * <p>
 * To share its set of associations with another thread, the capturing thread
 * passes the SDC object to the thread with which it wishes to share its set of
 * associations.  The thread receiving the object calls the {@link #adopt}
 * method.  Once two threads share the same set of associations, any changes
 * made by one thread will be observed by the other.
 * <p>
 * Once either {@literal rollback} or {@literal adopt} method is called then
 * neither method may be called subsequently.  It is a bug if neither method is
 * called before the object is garbage collected.
 * <p>
 * Additionally, a thread may modify the captured set of associations.  Such
 * changes will not have immediate effect, but will be seen by both threads
 * once {@literal adopt} is called.  Such changes have no effect if
 * {@literal rollback} is called.
 */
public class SDC
{
    private static final Logger LOG  = LoggerFactory.getLogger(SDC.class);

    /**
     * The limited subset of Map that we need.  However, unlike Map,
     * put(key, null) is allowed and used as a synonym for remove(key)
     */
    private interface SimpleMap<K,V>
    {
        /** Like {@link Map#put} but if value is null then treated like
         * {@link Map#remove}
         */
        public void put(K key, V value);

        /**
         * Return the current value of the entry, or null if there is no entry.
         */
        public V get(K key);
    }

    /**
     * Wrapper class around CurrentHashMap that exposes the SimpleMap interface.
     */
    private static class ConcurrentSimpleMap<K,V> implements SimpleMap<K,V>
    {
        private final ConcurrentHashMap<K,V> _storage =
                new ConcurrentHashMap<>();

        @Override
        public void put(K key, V value)
        {
            if (value == null) {
                _storage.remove(key);
            } else {
                _storage.put(key, value);
            }
        }

        @Override
        public V get(K key)
        {
            return _storage.get(key);
        }
    }

    /**
     * Provide a SimpleMap implementation that overlays some other SimpleMap and
     * that stages any changes.  If the commit method is called then all changes
     * made to a StagingSimpleMap will be written to the underlying SimpleMap
     * and any subsequent changes will pass through.
     */
    private static class StagingSimpleMap<K,V> implements SimpleMap<K,V>
    {
        private final ConcurrentSimpleMap<K,V> _storage =
                new ConcurrentSimpleMap<>();
        private final Set<K> _deleted =
                Collections.newSetFromMap(new ConcurrentHashMap<K,Boolean>());

        private final ConcurrentSimpleMap<K,V> _stagedStorage =
                new ConcurrentSimpleMap<>();
        private final Set<K> _stagedDeleted =
                Collections.newSetFromMap(new ConcurrentHashMap<K,Boolean>());

        private final SimpleMap<K,V> _inner;
        private final Semaphore _semaphore = new Semaphore(Integer.MAX_VALUE);
        private boolean _isCommitted;

        public StagingSimpleMap(SimpleMap inner)
        {
            _inner = inner;
        }

        @Override
        public void put(K key, V value)
        {
            try {
                _semaphore.acquire();

                if (_isCommitted) {
                    _inner.put(key, value);
                } else {
                    _storage.put(key, value);
                }
            } catch (InterruptedException e) {
                throw new RuntimeException("Interrupted while setting " + key);
            } finally {
                _semaphore.release();
            }
        }

        public void stagedPut(K key, V value)
        {
            try {
                _semaphore.acquire();

                checkState(!_isCommitted, "stagedPut not allowed after adopt or rollback");

                if (value == null) {
                    _stagedDeleted.add(key);
                } else {
                    _stagedStorage.put(key, value);
                }
            } catch (InterruptedException e) {
                throw new RuntimeException("Interrupted while setting " + key);
            } finally {
                _semaphore.release();
            }
        }

        @Override
        public V get(K key)
        {
            try {
                _semaphore.acquire();

                if (_isCommitted) {
                    return _inner.get(key);
                }

                V value = _storage.get(key);
                return value != null ? value : _inner.get(key);
            } catch (InterruptedException e) {
                throw new RuntimeException("Interrupted while getting " + key);
            } finally {
                _semaphore.release();
            }
        }

        public void commit() throws InterruptedException
        {
            _semaphore.acquire(Integer.MAX_VALUE);

            for (Map.Entry<K,V> entry : _storage._storage.entrySet()) {
                _inner.put(entry.getKey(), entry.getValue());
            }

            for (Map.Entry<K,V> entry : _stagedStorage._storage.entrySet()) {
                _inner.put(entry.getKey(), entry.getValue());
            }

            for (K key : _deleted) {
                _inner.put(key, null);
            }

            for (K key : _stagedDeleted) {
                _inner.put(key, null);
            }

            _isCommitted = true;

            _semaphore.release(Integer.MAX_VALUE);
        }
    }

    private static ThreadLocal<SimpleMap<String,String>> _context =
            new ThreadLocal<SimpleMap<String,String>>() {
        @Override
        protected SimpleMap initialValue()
        {
            return new ConcurrentSimpleMap();
        }
    };

    /**
     * Create or update the entry key with the supplied value.   If value is
     * null then then entry is removed.
     */
    public static void put(String key, String value)
    {
        _context.get().put(key, value);
    }

    /**
     * Obtain the current value associated with key or null if there is
     * no value associated with this key.
     */
    public static String get(String key)
    {
        return _context.get().get(key);
    }

    /**
     * Remove the value associated with the supplied key.
     */
    public static void remove(String key)
    {
        _context.get().put(key, null);
    }

    /**
     * Create a new set of associations for the current thread that is
     * independent of any other thread's set of associations.  This new set of
     * associations is initially empty.
     */
    public static void reset()
    {
        _context.set(new ConcurrentSimpleMap());
    }


    private final StagingSimpleMap<String,String> _shared;
    private final AtomicBoolean _hasBeenUsed = new AtomicBoolean();
    private final StackTraceElement[] _constructed;

    /**
     * Capture the current thread's set of associations.  There are two uses
     * for this captured: rolling back to the capture point and enabling two
     * Threads to share the same context.
     *
     * When rolling back, any changes made to the SDC between being captured
     * and when the rollback method is called by this thread are lost.  If the
     * SDC is shared with some other thread then changes made by that other
     * thread are NOT affected and will not be lost.
     *
     * When sharing an SDC between threads, one thread captures its current
     * state and passes this object to the thread with which it is to share
     * state.  When the other thread calls the share method then its context is
     * lost and it adopts the context of the sharing thread.
     *
     * The thread creating a new SDC object is responsible for calling
     * precisely one method of rollback or share.  It is a bug if neither is
     * called before the object is garbage collected.
     */
    public SDC()
    {
        SimpleMap base = _context.get();
        if (base instanceof StagingSimpleMap) {
            base = ((StagingSimpleMap)base)._inner;
        }
        _shared = new StagingSimpleMap(base);
        _context.set(_shared);

        // We remove the call to getStackTrace as it isn't interesting.
        StackTraceElement[] elements = Thread.currentThread().getStackTrace();
        _constructed = Arrays.copyOfRange(elements, 1, elements.length);
    }

    public void rollback()
    {
        checkState(!_hasBeenUsed.getAndSet(true), "cannot rollback after " +
                "capture has been used");
        _context.set(_shared._inner);
    }

    public void adopt() throws InterruptedException
    {
        checkState(!_hasBeenUsed.getAndSet(true), "cannot adopt after " +
                "capture has been used");
        _shared.commit();
        _context.set(_shared._inner);
    }

    @Override
    protected void finalize() throws Throwable
    {
        if (!_hasBeenUsed.get()) {
            StringBuilder sb =
                    new StringBuilder("BUG: captured not used; stack-trace from constructor:\n");
            for (StackTraceElement element : _constructed) {
                sb.append("    ").append(element).append('\n');
            }
            LOG.warn("{}", sb);
        }

        super.finalize();
    }

    /**
     * Update a captured SDC by setting the association for key to the
     * supplied value.  If value is null then the association is removed.
     * This will have no effect until adopt is called.
     */
    public void localPut(String key, String value)
    {
        _shared.stagedPut(key, value);
    }

    /**
     * Remove the association from within a captured SDC.  This has no effect
     * until adopt is called.
     */
    public void localRemove(String key)
    {
        _shared.stagedPut(key, null);
    }
}
