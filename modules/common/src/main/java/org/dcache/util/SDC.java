package org.dcache.util;

import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.PhantomReference;
import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.google.common.base.Preconditions.checkState;

/**
 * The SharableDiagnosticContext provides a set of key-value associations that,
 * by default, is thread-local, but allows two or more threads to share the
 * same set of associations and for a thread to revert the set of associations
 * to a previous point.
 * <p>
 * The ability to both share context and revert changes are initialised by first
 * capturing the context.  This is achieved by creating a new SDC object.
 * <p>
 * To share its context with another thread, the capturing thread passes the SDC
 * object to the other thread.  The receiving thread then calls {@link #adopt}.
 * Once two threads share the same context, changes made by one thread will be
 * observed by the other.
 * <p>
 * To revert changes made since capturing the context, the capturing thread
 * calls the {@link #rollback} method.  The behaviour is unspecified if another
 * thread calls this method.  If the capturing thread is already sharing its
 * context with another thread, reverting changes will only affect changes made
 * by the capturing thread.
 * <p>
 * Once either {@literal rollback} or {@literal adopt} method is called then
 * neither method may be called subsequently.
 * <p>
 * One common usage pattern is for a thread to capture the context before
 * adopting a new context, make changes to the context, then rollback to the
 * captured context.  Changes made to the shared context will not be affected
 * by the rollback and after the thread will revert to its original context.
 * <p>
 * The SDC object allows a thread to modify the captured context without
 * adopting it.  Such changes will not have immediate effect, but will be seen
 * by all threads with the same context once {@literal adopt} is called.
 * Such changes have no effect if {@literal rollback} is called.
 */
public class SDC
{
    private static final Logger LOG  = LoggerFactory.getLogger(SDC.class);

    private static final Joiner WITH_NEWLINES = Joiner.on("\n        ");

    /**
     * The WeakReference supplied by Java does not have an equals method.  This
     * class simply adds an equals and hashCode based on the wrapped object.
     */
    private static class WeakReferenceWithEquals<T> extends WeakReference<T>
    {
        private final int _hashCode;

        WeakReferenceWithEquals(T object)
        {
            super(object);
            _hashCode = object.hashCode();
        }

        @Override
        public boolean equals(Object other)
        {
            if (other instanceof Reference) {
                return Objects.equal(get(), ((Reference)other).get());
            } else {
                return false;
            }
        }

        @Override
        public int hashCode()
        {
            return _hashCode;
        }
    }

    /**
     * A simple Map-like interface.  This Interface is the basis for
     * interacting with context values.
     */
    private interface SimpleMap<K,V>
    {
        /**
         * Update the context so that the next call to {@code #get} with the
         * same key will return the supplied value.  Returns the previous value
         * for key.
         */
        public V put(K key, V value);

        /**
         * Return the value previously stored by {@code #put}.  The null value
         * is returned if put has never been called with this key.
         */
        public V get(K key);
    }


    /**
     * The interface on which public SDC operations are based.  Each thread
     * has a thread-local instance of a class that implements this interface.
     * Other classes do not interact directly with this thread-local class: a
     * set of static methods mediate this interaction.
     */
    private interface Context<K,V> extends SimpleMap<K,V>
    {
        /**
         * Obtain a new captured context.
         */
        public CapturedContext<K,V> capture();

        /**
         * Returns the current number of captures.  The method provides no
         * strict guarantee that a capture is not included after
         * {@code SDC#adopt} or {@code SDC#rollback}. This is intended for
         * diagnosing problems rather than for
         */
        public int getCaptureCount();

        /**
         * Provide a single-line textual description of the current SDC,
         * including all the captures and their current state.
         */
        public String describe();
    }


    /**
     * An implementation of ExtendedMap that is thread-safe and has the same
     * concurrence behaviour as ConcurrentHashMap.
     */
    private static class ConcurrentSimpleMap<K,V> implements SimpleMap<K,V>,
            Iterable<Map.Entry<K,V>>
    {
        private final Map<K,V> _storage = new ConcurrentHashMap<>();

        @Override
        public V put(K key, V value)
        {
            if (value == null) {
                return _storage.remove(key);
            } else {
                return _storage.put(key, value);
            }
        }

        @Override
        public V get(K key)
        {
            return _storage.get(key);
        }

        public void clear()
        {
            _storage.clear();
        }

        @Override
        public String toString()
        {
            return _storage.toString();
        }

        @Override
        public Iterator<Entry<K, V>> iterator()
        {
            return _storage.entrySet().iterator();
        }

        public void putAll(ConcurrentSimpleMap<K, V> newValues)
        {
            _storage.putAll(newValues._storage);
        }

        public boolean isEmpty()
        {
            return _storage.isEmpty();
        }
    }

    /**
     * Class that implements SimpleMap and wraps some other SimpleMap.  The
     * class honours the SimpleMap interface, but no changes are propagated
     * to the wrapped SimpleMap until @code #commit} is called.  This call
     * results in all changes being applied to the wrapped SimpleMap.
     * <p>
     * After {@code reset}, all changes are lost and subsequent calls to
     * {@code #get} reflect the value returned by the wrapped SimpleMap.
     */
    private static class DelayedSimpleMap<K,V> implements SimpleMap<K,V>
    {
        protected final SimpleMap<K,V> _inner;
        private String _innerDescription;

        private final ConcurrentSimpleMap<K,V> _newValues =
                new ConcurrentSimpleMap<>();
        private final Set<K> _deletes =
                Collections.newSetFromMap(new ConcurrentHashMap<K,Boolean>());

        public DelayedSimpleMap(SimpleMap<K,V> inner)
        {
            _inner = inner;
        }

        void describeInnerAs(String description)
        {
            _innerDescription = description;
        }

        @Override
        public V put(K key, V value)
        {
            V previous = get(key);
            if (value == null) {
                _deletes.add(key);
            } else {
                _deletes.remove(key);
            }
            _newValues.put(key, value);
            return previous;
        }

        @Override
        public V get(K key)
        {
            if (_deletes.contains(key)) {
                return null;
            } else {
                V current = _newValues.get(key);
                return current != null ? current : _inner.get(key);
            }
        }

        public void reset()
        {
            _newValues.clear();
            _deletes.clear();
        }

        /**
         * Allow a DelayedSimpleMap to act as the aggregation of several
         * DelayedSimpleMap instances.
         */
        protected void accept(DelayedSimpleMap<K,V> changes)
        {
            _deletes.addAll(changes._deletes);
            _newValues.putAll(changes._newValues);
        }

        public void commit()
        {
            commitTo(_inner);
        }

        public void commitTo(SimpleMap<K,V> map)
        {
            for (Map.Entry<K,V> entry : _newValues) {
                map.put(entry.getKey(), entry.getValue());
            }

            for (K key : _deletes) {
                map.put(key, null);
            }

            _newValues.clear();
            _deletes.clear();
        }

        public void forget(K key)
        {
            _newValues.put(key, null);
            _deletes.remove(key);
        }

        @Override
        public String toString()
        {
            String inner;
            if (_innerDescription != null) {
                inner = _innerDescription;
            } else {
                inner = _inner.toString();
            }

            StringBuilder sb = new StringBuilder();


            sb.append("[inner=").append(inner);

            if (!_newValues.isEmpty()) {
                sb.append(", updates=").append(_newValues);
            }

            if (!_deletes.isEmpty()) {
                sb.append(", deletes=").append(_deletes);
            }

            sb.append("]");
            return sb.toString();
        }
    }

    /**
     * A DelayedSimpleMap that is created with two SimpleMap instances: a
     * delay-target and a maintaining-target.  This class wraps the delay-target
     * and provides the same behaviour as the DelayedSimpleMap class.  All calls
     * to {@code #put} immediately update the maintaining-target.  In addition,
     * calls to {@code #updateMaintained} will propagate all pending changes
     * to the maintaining-target.
     */
    private static class MaintainingSimpleMap<K,V> extends DelayedSimpleMap<K,V>
    {
        private final DelayedSimpleMap<K,V> _maintained;

        public MaintainingSimpleMap(SimpleMap<K,V> delayed,
                    DelayedSimpleMap<K,V> maintained)
        {
            super(delayed);
            _maintained = maintained;
        }

        @Override
        public V put(K key, V value)
        {
            V old = super.put(key, value);
            _maintained.put(key, value);
            return old;
        }

        @Override
        public V get(K key)
        {
            return _maintained.get(key);
        }

        /**
         * Write all pending changes to maintaining-target.
         */
        public void updateMaintained()
        {
            _maintained.accept(this);
        }
    }


    /**
     * The class is the context seen by a thread initially and after calling
     * {@code SDC#adopt} or {@code SDC#rollback}.
     */
    private static class MainContext<K,V> implements Context<K,V>
    {
        private final Set<WeakReferenceWithEquals<CapturedContext<K,V>>> _activeCaptures =
                Collections.newSetFromMap(new ConcurrentHashMap<WeakReferenceWithEquals<CapturedContext<K,V>>,Boolean>());

        /** All associations that are not at risk of being rolled back. */
        private final ConcurrentSimpleMap<K,V> _storage =
                new ConcurrentSimpleMap<>();

        /** View that includes associations at risk of being rolled back. */
        private final DelayedSimpleMap<K,V> _aggregatedChanges =
                new DelayedSimpleMap<>(_storage);

        @Override
        public CapturedContext<K,V> capture()
        {
            CapturedContext<K,V> capture = new CapturedContext(this, _storage,
                    _aggregatedChanges);

            Set<WeakReferenceWithEquals<CapturedContext<K,V>>> captures =
                Collections.newSetFromMap(new ConcurrentHashMap<WeakReferenceWithEquals<CapturedContext<K,V>>,Boolean>());
            captures.addAll(_activeCaptures);
            capture.setEarlierCaptures(captures);

            _activeCaptures.add(new WeakReferenceWithEquals(capture));
            return capture;
        }

        @Override
        public int getCaptureCount()
        {
            return _activeCaptures.size();
        }

        @Override
        public V put(K key, V value)
        {
            // Remove any potential change from other threads.
            for (WeakReferenceWithEquals<CapturedContext<K,V>> captureRef : _activeCaptures) {
                CapturedContext capture = captureRef.get();
                if (capture != null) {
                    capture.forget(key);
                }
            }
            _aggregatedChanges.forget(key);
            return _storage.put(key, value);
        }

        void remove(CapturedContext<K,V> captureToRemove)
        {
            removeContext(captureToRemove);
            rebuildAggregatedView();
        }

        void rollback(CapturedContext<K,V> captureToRollback)
        {
            Collection<CapturedContext> after = capturedAfter(captureToRollback);

            if (!after.isEmpty()) {
                MainContext<K,V> newMain = new MainContext<>();
                newMain._storage.putAll(this._storage);

                captureToRollback.applyCaptureTo(newMain._storage);

                for (CapturedContext context : after) {
                    context._main = newMain;
                    newMain._activeCaptures.add(new WeakReferenceWithEquals<CapturedContext<K,V>>(context));
                    removeContext(context);
                    context.retainEarlierCaptures(after);
                }

                newMain.rebuildAggregatedView();
            }

            remove(captureToRollback);
        }

        private void removeContext(CapturedContext<K,V> capture)
        {
            WeakReferenceWithEquals<CapturedContext<K,V>> removeRef =
                    new WeakReferenceWithEquals<>(capture);
            _activeCaptures.remove(removeRef);
            removeRef.clear();
        }

        private void rebuildAggregatedView()
        {
            _aggregatedChanges.reset();
            Iterator<WeakReferenceWithEquals<CapturedContext<K,V>>> iterator =
                    _activeCaptures.iterator();
            while (iterator.hasNext()) {
                WeakReferenceWithEquals<CapturedContext<K,V>> captureRef = iterator.next();

                CapturedContext capture = captureRef.get();
                if (capture != null) {
                    capture.updateAggregation();
                } else {
                    iterator.remove();
                }
            }
        }

        private Collection<CapturedContext> capturedAfter(CapturedContext rollback)
        {
            Set<CapturedContext> after = new HashSet<>();

            WeakReferenceWithEquals<CapturedContext> rollbackRef =
                    new WeakReferenceWithEquals<>(rollback);

            for (WeakReferenceWithEquals<CapturedContext<K,V>> captureRef :
                    _activeCaptures) {
                CapturedContext capture = captureRef.get();
                if (capture != null && capture.hasEarlierCapture(rollbackRef)) {
                    after.add(capture);
                }
            }

            return after;
        }

        @Override
        public String toString()
        {
            return toString(null);
        }

        public String toString(Context currentThreadsContext)
        {
            StringBuilder sb = new StringBuilder();
            if (currentThreadsContext == this) {
                sb.append("*");
            }
            sb.append("MainContext(").append(Integer.toHexString(hashCode())).
                    append("): changes=").append(_aggregatedChanges);
            for (WeakReferenceWithEquals<CapturedContext<K,V>> captureRef : _activeCaptures) {
                sb.append("; ");
                CapturedContext capture = captureRef.get();
                if (capture != null) {
                    if (capture == currentThreadsContext) {
                        sb.append("*");
                    }
                    sb.append("Capture=").append(capture);
                } else {
                    sb.append("ZombieCapture");
                }
            }
            return sb.toString();
        }

        private String describe(Context current)
        {
            return toString(current);
        }

        @Override
        public String describe()
        {
            return describe(this);
        }

        @Override
        public V get(K key)
        {
            return _aggregatedChanges.get(key);
        }
    }


    /**
     * An instance of this class that is the Context seen by a Thread after
     * capturing the context.  Changes to the context are delayed (against
     * the underlying storage) but are live (for the aggregated view).  This
     * allows such changes to be rolled back if {@code #removeCapture} is
     * called.
     * <p>
     * If {@code #applyCapture} is called then all changes are propagated to
     * the underlying storage and the capture is removed from the list of
     * captures maintained by MainContext.
     */
    private static class CapturedContext<K,V> implements Context<K,V>
    {
        private MainContext<K,V> _main;

        /** Main storage of associations. */
        private final SimpleMap<K,V> _storage;

        /** Used by get and put to update state. */
        private final MaintainingSimpleMap<K,V> _pending;

        /** Used by stagedPut for storing state. */
        private final DelayedSimpleMap<K,V> _staged;

        /** Whether this capture has ever been adopted or rolledback. */
        private final AtomicBoolean _hasBeenUsed = new AtomicBoolean();


        /** Set of captures that existed when this capture was created. */
        private Set<WeakReferenceWithEquals<CapturedContext<K,V>>> _earlierCaptures;

        private final StackTraceElement[] _creationStacktrace;
        private StackTraceElement[] _usageStacktrace;

        public CapturedContext(MainContext<K,V> main, SimpleMap storage,
                DelayedSimpleMap aggregation)
        {
            _main = main;
            _storage = storage;
            _pending = new MaintainingSimpleMap(storage, aggregation);
            _pending.describeInnerAs("<MainContext>");
            _staged = new DelayedSimpleMap(_pending);
            _creationStacktrace = Thread.currentThread().getStackTrace();
        }

        public void setEarlierCaptures(Set<WeakReferenceWithEquals<CapturedContext<K,V>>> captures)
        {
            _earlierCaptures = captures;
        }

        public boolean hasEarlierCapture(WeakReferenceWithEquals<CapturedContext<K,V>> captureRef)
        {
            return _earlierCaptures.contains(captureRef);
        }

        public void retainEarlierCaptures(Iterable<CapturedContext<K,V>> captures)
        {
            Collection<WeakReferenceWithEquals<CapturedContext<K,V>>> refs =
                    new HashSet<>();
            for (CapturedContext<K,V> context : captures) {
                refs.add(new WeakReferenceWithEquals<>(context));
            }
            _earlierCaptures.retainAll(refs);
        }

        public MainContext<K,V> getMainContext()
        {
            return _main;
        }

        @Override
        public int getCaptureCount()
        {
            return _main.getCaptureCount();
        }

        @Override
        public String describe()
        {
            return _main.describe(this);
        }

        @Override
        public V put(K key, V value)
        {
            if (_hasBeenUsed.get()) {
                return _storage.put(key, value);
            } else {
                return _pending.put(key, value);
            }
        }

        public void stagedPut(K key, V value)
        {
            failUnless(!_hasBeenUsed.get());
            _staged.put(key, value);
        }

        public boolean hasBeenUsed()
        {
            return _hasBeenUsed.get();
        }

        public void forget(K key)
        {
            _staged.forget(key);
            _pending.forget(key);
        }

        @Override
        public V get(K key)
        {
            if (_hasBeenUsed.get()) {
                return _storage.get(key);
            } else {
                return _pending.get(key);
            }
        }

        public void removeCapture()
        {
            failUnless(!_hasBeenUsed.getAndSet(true));
            rememberUsage();
            _main.rollback(this);
        }

        public void applyCapture()
        {
            if (!_hasBeenUsed.getAndSet(true)) {
                _staged.commit();  // Update pending with localPut values
                _pending.commit(); // Write pending into main storage
                _main.remove(this);
            }
        }

        public void applyCaptureTo(ConcurrentSimpleMap<K,V> map)
        {
            _pending.commitTo(map);
            _staged.commitTo(map);
        }

        /**
         * Custom version of Guava's checkState.
         */
        private void failUnless(boolean isValidState)
        {
            if (!isValidState) {
                LOG.error("Cannot use context after adopt or rollback.  " +
                        "Context captured:\n        {}" +
                        "\nCaptured context used:\n        {}" +
                        "\nAttempted second use:\n        {}",
                        WITH_NEWLINES.join(_creationStacktrace),
                        WITH_NEWLINES.join(_usageStacktrace),
                        WITH_NEWLINES.join(Thread.currentThread().getStackTrace()));

                throw new IllegalStateException("SDC captured context " +
                        "cannot be used after adopt or rollback");
            }
        }

        private void rememberUsage()
        {
            _usageStacktrace = Thread.currentThread().getStackTrace();
        }

        public void updateAggregation()
        {
            if (!_hasBeenUsed.get()) {
                _pending.updateMaintained();
            }
        }


        @Override
        public CapturedContext<K, V> capture()
        {
            return _main.capture();
        }

        @Override
        public String toString()
        {
            return ((_hasBeenUsed.get()) ? "!" : "") +
                    _staged.toString();
        }

        @Override
        public void finalize() throws Throwable
        {
            /*
             * This allows for a thread to create a capture and never use it.
             *
             * After capturing the context, a thread's Context will be an
             * instance of CapturedContext.  Updates to the state will be
             * visable to other threads through the aggregated view; however,
             * if the CapturedContext is simply lost then the next rebuild of
             * the aggregated view will loose any changes here.
             *
             * To prevent this, we apply any changes made to this capture when
             * the object is garbage-collected.
             */
            if (!_hasBeenUsed.get()) {
                applyCapture();
            }
            super.finalize();
        }
    }

    private static ThreadLocal<Context<String,String>> _currentContext =
            new ThreadLocal<Context<String,String>>() {
        @Override
        protected Context<String,String> initialValue()
        {
            return new MainContext<>();
        }
    };

    private static Context<String,String> getContext()
    {
        Context context = _currentContext.get();

        if (context instanceof CapturedContext &&
                ((CapturedContext) context).hasBeenUsed()) {
            context = ((CapturedContext) context).getMainContext();
            _currentContext.set(context);
        }

        return context;
    }

    /**
     * Create or update the entry key with the supplied value.   If value is
     * null then then entry is removed.
     */
    public static void put(String key, String value)
    {
        getContext().put(key, value);
    }

    /**
     * Obtain the current value associated with key or null if there is
     * no value associated with this key.
     */
    public static String get(String key)
    {
        return getContext().get(key);
    }

    /**
     * A convenience method to remove the value associated with the supplied
     * key.
     */
    public static void remove(String key)
    {
        getContext().put(key, null);
    }

    public static boolean isSet(String key)
    {
        return get(key) != null;
    }

    /**
     * Create a new set of associations for the current thread that is
     * independent of any other thread's set of associations.  This new set of
     * associations is initially empty.
     */
    public static void reset()
    {
        _currentContext.set(new MainContext());
    }

    public static int countCaptures()
    {
        return getContext().getCaptureCount();
    }

    /**
     * Provide a single-line description of the current context, including
     * all captures.  Intended for diagnosing problems.
     */
    public static String describe()
    {
        return getContext().describe();
    }

    private final CapturedContext<String,String> _captured;

    /**
     * Capture the context of the current Thread.  There are two uses for the
     * captured context: rolling back changes made to the context and to allow
     * two Threads to share the same context.
     * <p>
     * Share a context between two Threads involves one Thread making its
     * context available and the second Thread adopting it.  To make its context
     * available, the first Thread captures the context and passes the SDC
     * object to the second Thread.  To adopt this context, the second Thread
     * calls {@code #adopt}.  Once shared, any changes to the context will
     * be observed by both Threads.
     * <p>
     * To rollback changes, a thread captures the context.  Later, the same
     * thread calls {@code #rollback} to undo any changes.  The behaviour is
     * unspecified if another thread calls {@literal rollback}.  If the context
     * is shared by with threads, only changes made by the thread between
     * capturing the context and calling {@literal rollback} are affected;
     * changes made by other threads are unaffected.
     * <p>
     * If, after capturing the context, this thread adopts some other context
     * and makes changes to that context then calling {@literal rollback} will
     * NOT affect those changes.
     * <p>
     * A captured context is for single use.  Once either {@literal adopt} or
     * {@literal rollback} has been called, neither method is allowed.
     */
    public SDC()
    {
        _captured = getContext().capture();
        _currentContext.set(_captured);
    }

    /**
     * Revert context to a previous state.  If the context is shared, only
     * Thread-local changes are affected.
     */
    public void rollback()
    {
        _captured.removeCapture();
        _currentContext.set(_captured.getMainContext());
    }

    /**
     * Update the current Thread so that subsequent interaction with the
     * context is shared.
     */
    public void adopt()
    {
        _captured.applyCapture();
        _currentContext.set(_captured.getMainContext());
    }

    /**
     * Update a captured SDC by setting the association for key to the
     * supplied value. This will have no effect until adopt is called.
     */
    public void localPut(String key, String value)
    {
        _captured.stagedPut(key, value);
    }


    /**
     * Provide mechanism to be notified when this CapturedContext has been
     * finalized.
     */
    PhantomReference<CapturedContext<String,String>>
            addPhantomToQueue(ReferenceQueue queue)
    {
        return new PhantomReference(_captured, queue);
    }
}
