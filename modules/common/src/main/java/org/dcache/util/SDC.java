package org.dcache.util;

import com.google.common.base.Joiner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.PhantomReference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicBoolean;


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
     * A class that implements Queue where all values are wrapped in a
     * WeakReference.  The methods never expose that weak-references are held;
     * the iterator will skip over any WeakReference that has been cleared as
     * will the Queue-related operations.
     * <p>
     * NB. This class differs from Queue as methods that operate on a specific
     * item are checked for being the same object, rather than checking with
     * the equals method.
     * <p>
     * The class wraps a ConcurrentLinkedDeque object, so inherits the
     * concurrency behaviour from that class.
     * <p>
     * The size method tries to make sure all cleared WeakReferences are not
     * included.  This makes the method slow; however, as it is only used when
     * debugging SDC, that trade-off seems reasonable.
     */
    private static class WeakConcurrentQueue<T> implements Queue<T>
    {
        private final Queue<WeakReference<T>> _inner =
                new ConcurrentLinkedDeque<>();

        @Override
        public boolean add(T e) throws IllegalStateException
        {
            return _inner.add(new WeakReference(e));
        }

        @Override
        public boolean offer(T e)
        {
            return _inner.offer(new WeakReference(e));
        }

        @Override
        public T remove() throws NoSuchElementException
        {
            T item;

            do {
                item = _inner.remove().get();
            } while (item == null);

            return item;
        }

        @Override
        public T poll()
        {
            T item;

            do {
                WeakReference<T> ref = _inner.poll();
                if (ref == null) {
                    return null;
                } else {
                    item = ref.get();
                }
            } while (item == null);

            return item;
        }

        @Override
        public T element() throws NoSuchElementException
        {
            T item = peek();
            if (item == null) {
                throw new NoSuchElementException("queue is empty");
            }
            return item;
        }

        @Override
        public T peek()
        {
            Iterator<WeakReference<T>> iterator = _inner.iterator();
            while (iterator.hasNext()) {
                WeakReference<T> ref = iterator.next();
                T item = ref.get();
                if (item == null) {
                    iterator.remove();
                } else {
                    return item;
                }
            }

            return null;
        }

        @Override
        public int size()
        {
            int count=0;

            // Since size is only called when debugging SDC, we can afford to
            // make this expensive but (hopefully) more accurate.
            Iterator<WeakReference<T>> iterator = _inner.iterator();
            while (iterator.hasNext()) {
                WeakReference<T> ref = iterator.next();
                T item = ref.get();
                if (item == null) {
                    iterator.remove();
                } else {
                    count++;
                }
            }

            return count;
        }

        @Override
        public boolean isEmpty()
        {
            return _inner.isEmpty();
        }

        @Override
        public boolean contains(Object o)
        {
            Iterator<WeakReference<T>> iterator = _inner.iterator();
            while (iterator.hasNext()) {
                WeakReference<T> ref = iterator.next();
                T item = ref.get();
                if (item == null) {
                    iterator.remove();
                } else if (item == o) {
                    return true;
                }
            }

            return false;
        }

        @Override
        public Iterator<T> iterator()
        {
            final Iterator<WeakReference<T>> iterator = _inner.iterator();

            return new Iterator<T>() {

                private T _next;
                private boolean _isNextValid;

                private T getNext() {
                    if (_isNextValid) {
                        return _next;
                    }

                    _next = null;

                    while (iterator.hasNext()) {
                        WeakReference<T> ref = iterator.next();
                        _next = ref.get();

                        if (_next == null) {
                            iterator.remove();
                        } else {
                            break;
                        }
                    }
                    _isNextValid = true;
                    return _next;
                }

                @Override
                public boolean hasNext()
                {
                    return getNext() != null;
                }

                @Override
                public T next()
                {
                    T next = getNext();

                    if (next == null) {
                        throw new NoSuchElementException("no more elements");
                    }

                    _isNextValid = false;
                    return next;
                }

                @Override
                public void remove()
                {
                    iterator.remove();
                }
            };
        }

        @Override
        public Object[] toArray()
        {
            ArrayList<T> items = new ArrayList<>(_inner.size());

            Iterator<WeakReference<T>> iterator = _inner.iterator();
            while (iterator.hasNext()) {
                WeakReference<T> ref = iterator.next();
                T item = ref.get();
                if (item == null) {
                    iterator.remove();
                } else {
                    items.add(item);
                }
            }

            return items.toArray();
        }

        @Override
        public <AT> AT[] toArray(AT[] a)
        {
            ArrayList<T> items = new ArrayList<>(_inner.size());

            Iterator<WeakReference<T>> iterator = _inner.iterator();
            while (iterator.hasNext()) {
                WeakReference<T> ref = iterator.next();
                T item = ref.get();
                if (item == null) {
                    iterator.remove();
                } else {
                    items.add(item);
                }
            }

            return items.toArray(a);
        }

        @Override
        public boolean remove(Object o)
        {
            boolean removed = false;

            Iterator<WeakReference<T>> iterator = _inner.iterator();
            while (iterator.hasNext()) {
                WeakReference<T> ref = iterator.next();
                T item = ref.get();
                if (item == null) {
                    iterator.remove();
                } else if (item == o) {
                    ref.clear();
                    iterator.remove();
                    removed = true;
                }
            }

            return removed;
        }

        @Override
        public boolean containsAll(Collection<?> c)
        {
            ArrayList itemsToCheck = new ArrayList(c);

            Iterator<WeakReference<T>> iterator = _inner.iterator();
            while (iterator.hasNext()) {
                WeakReference<T> ref = iterator.next();
                T item = ref.get();
                if (item == null) {
                    iterator.remove();
                } else {
                    while (itemsToCheck.remove(item)) {
                        // do nothing.
                    }
                }
            }

            return itemsToCheck.isEmpty();
        }

        @Override
        public boolean addAll(Collection<? extends T> c)
        {
            for (T item : c) {
                add(item);
            }

            return true;
        }

        @Override
        public boolean removeAll(Collection<?> c)
        {
            boolean changed = false;

            Iterator<WeakReference<T>> iterator = _inner.iterator();
            while (iterator.hasNext()) {
                WeakReference<T> ref = iterator.next();
                T item = ref.get();
                if (item == null) {
                    iterator.remove();
                } else if (c.contains(item)) {
                    ref.clear();
                    iterator.remove();
                    changed = true;
                }
            }

            return changed;
        }

        @Override
        public boolean retainAll(Collection<?> c)
        {
            boolean changed = false;

            Iterator<WeakReference<T>> iterator = _inner.iterator();
            while (iterator.hasNext()) {
                WeakReference<T> ref = iterator.next();
                T item = ref.get();
                if (item == null) {
                    iterator.remove();
                } else if (!c.contains(item)) {
                    ref.clear();
                    iterator.remove();
                    changed = true;
                }
            }

            return changed;
        }

        @Override
        public void clear()
        {
            _inner.clear();
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
         * for key.  Value may be null.
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
     * Other classes do not interact directly with this thread-local Context
     * object: a set of static methods mediate this interaction.
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
         * diagnosing problems, such as leaking Context objects.
         */
        public int getCaptureCount();

        /**
         * Provide a single-line textual description of the current SDC,
         * including all the captures and their current state.
         */
        public String describe();
    }


    /**
     * An implementation of SimpleMap that is thread-safe and has the same
     * concurrence behaviour as ConcurrentHashMap.  The class also implements
     * Iterable, allowing other code to iterate over all key-value pairs.
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
     * Class that wraps some other SimpleMap and honours the SimpleMap
     * interface, but no changes are propagated to the wrapped SimpleMap
     * until {@code #commit} is called.  This call results in all changes being
     * applied to the wrapped SimpleMap.
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

        /** Provide alternative description for wrapped SimpleMap. */
        protected void describeInnerAs(String description)
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

        /** Revert any pending changes. */
        public void reset()
        {
            _newValues.clear();
            _deletes.clear();
        }

        /** Include all pending changes from another DelayedSimpleMap. */
        protected void accept(DelayedSimpleMap<K,V> changes)
        {
            _deletes.addAll(changes._deletes);
            _newValues.putAll(changes._newValues);
        }

        /** Apply all pending changes to underlying SimpleMap. */
        public void commit()
        {
            commitTo(_inner);
        }

        /** Apply all pending changes to some other SimpleMap. */
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

        /** Remove any pending changes to the supplied key. */
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
         * Write all pending changes to maintaining-target. Pending changes
         * are not committed.
         */
        public void updateMaintained()
        {
            _maintained.accept(this);
        }
    }


    /**
     * The class is the context seen by a thread initially and some time after
     * calling SDC#adopt or SDC#callback.
     */
    private static class MainContext<K,V> implements Context<K,V>
    {
        /** Captures that have not yet been adopted. */
        private final Queue<CapturedContext<K,V>> _activeCaptures;

        /** All associations that are not at risk of being rolled back. */
        private final ConcurrentSimpleMap<K,V> _storage =
                new ConcurrentSimpleMap<>();

        /**
         * View that includes associations at risk of being rolled back.
         * Although this is a DelayedSimpleMap, the pending changes are
         * never committed.  Instead, this provides a convenient way of
         * defaulting to the not-at-risk set of associations if no
         * CapturedContext has updated the association
         */
        private final DelayedSimpleMap<K,V> _aggregatedChanges =
                new DelayedSimpleMap<>(_storage);

        /** A MainContext for a new Thread. */
        public MainContext()
        {
            _activeCaptures = new WeakConcurrentQueue();
        }

        /** A MainContext for an orphaned CapturedContext. */
        public MainContext(Queue<CapturedContext<K,V>> activeCaptures)
        {
            _activeCaptures = activeCaptures;

            for (CapturedContext<K,V> capture : activeCaptures) {
                capture.setMainContext(this, _storage, _aggregatedChanges);
            }
        }

        @Override
        public CapturedContext<K,V> capture()
        {
            CapturedContext<K,V> capture = new CapturedContext(this, _storage,
                    _aggregatedChanges);
            _activeCaptures.add(capture);
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
            for (CapturedContext<K,V> capture : _activeCaptures) {
                capture.forget(key);
            }
            _aggregatedChanges.forget(key);
            return _storage.put(key, value);
        }

        void rollback(CapturedContext<K,V> captureToRollback)
        {
            Queue<CapturedContext<K,V>> afterRollback =
                    new ConcurrentLinkedDeque<>();

            boolean isAfterRollback = false;

            Iterator<CapturedContext<K,V>> iterator = _activeCaptures.iterator();
            _aggregatedChanges.reset();
            while (iterator.hasNext()) {
                CapturedContext capture = iterator.next();
                if (capture == captureToRollback) {
                    iterator.remove();
                    isAfterRollback = true;
                } else if (isAfterRollback) {
                    iterator.remove();
                    afterRollback.add(capture);
                } else {
                    capture.updateAggregation();
                }
            }

            // Handle orphaned CapturedContext by giving them a new MainContext
            if (!afterRollback.isEmpty()) {
                MainContext<K,V> newMain = new MainContext<>(afterRollback);
                newMain._storage.putAll(_storage);
                captureToRollback.applyCaptureTo(newMain._storage);
                newMain.applyAdoptedCaptures();
            }
        }

        private void applyAdoptedCaptures()
        {
            Iterator<CapturedContext<K,V>> iterator = _activeCaptures.iterator();
            while (iterator.hasNext()) {
                CapturedContext capture = iterator.next();

                if (capture.applyCapture()) {
                    iterator.remove();
                } else {
                    break;
                }
            }
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
            sb.append("Main(").append(Integer.toHexString(hashCode())).
                    append("): aggregatedChanges=").append(_aggregatedChanges);
            for (CapturedContext<K,V> capture : _activeCaptures) {
                sb.append("; ");
                if (capture == currentThreadsContext) {
                    sb.append("*");
                }
                sb.append("(").append(Integer.toHexString(capture.hashCode()));
                sb.append(")=").append(capture);
            }
            return sb.toString();
        }

        private String describe(Context currentThreadsContext)
        {
            return toString(currentThreadsContext);
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
     * the underlying storage) but are live (for the aggregated view).
     * <p>
     * Adopting a context is a two-step operation: 'adopt' and 'apply'.  The
     * first step, adoption, makes all staged changes live.  The second step,
     * applying, is intended to allow the thread-local Context to go back to
     * being the MainContext.  The first step is triggered by
     * {@code #adopt} and makes this CapturedContext eligible for the
     * second step; the second step happens when all CapturedContext objects
     * earlier in the Queue are adopted or applied.
     * <p>
     * If an earlier CapturedContext is rolled-back then all subsequent
     * CapturedContext objects obtain a new MainContext.
     */
    private static class CapturedContext<K,V> implements Context<K,V>
    {
        private MainContext<K,V> _main;

        /** Main storage of associations. */
        private SimpleMap<K,V> _storage;

        /** Used by get and put to update state. */
        private MaintainingSimpleMap<K,V> _pending;

        /** Used by stagedPut for storing state. */
        private DelayedSimpleMap<K,V> _staged;

        /** Whether this capture has ever been adopted or rolledback. */
        private final AtomicBoolean _hasBeenUsed = new AtomicBoolean();

        /** Whether this captured has been adopted when it was used. */
        private volatile boolean _hasBeenAdopted;

        /** Whether this capture has been folded into main context. */
        private volatile boolean _hasBeenApplied;

        private final StackTraceElement[] _creationStacktrace;
        private StackTraceElement[] _usageStacktrace;

        public CapturedContext(MainContext<K,V> main, SimpleMap storage,
                DelayedSimpleMap aggregation)
        {
            setMainContext(main, storage, aggregation);
            _creationStacktrace = Thread.currentThread().getStackTrace();
        }

        public MainContext<K,V> getMainContext()
        {
            return _main;
        }

        public final void setMainContext(MainContext<K,V> main, SimpleMap storage,
                DelayedSimpleMap aggregation)
        {
            _main = main;

            _storage = storage;

            MaintainingSimpleMap<K,V> newPending = new MaintainingSimpleMap(storage, aggregation);
            if (_pending != null) {
                newPending.accept(_pending);
            }
            newPending.describeInnerAs("<MainContext>");

            DelayedSimpleMap<K,V> newStaged = new DelayedSimpleMap(newPending);
            if (_staged != null) {
                newStaged.accept(_staged);
            }

            _pending = newPending;
            _staged = newStaged;

            updateAggregation();
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
            if (_hasBeenApplied) {
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

        public boolean hasBeenApplied()
        {
            return _hasBeenApplied;
        }

        public void forget(K key)
        {
            _staged.forget(key);
            _pending.forget(key);
        }

        @Override
        public V get(K key)
        {
            if (_hasBeenApplied) {
                return _storage.get(key);
            } else {
                return _pending.get(key);
            }
        }

        public void rollback()
        {
            failUnless(!_hasBeenUsed.getAndSet(true));
            rememberUsage();
            _main.rollback(this);
        }

        public void adopt()
        {
            if (!_hasBeenUsed.getAndSet(true)) {
                rememberUsage();
                _staged.commit();
                _hasBeenAdopted = true;
                _main.applyAdoptedCaptures();
            }
        }

        public boolean applyCapture()
        {
            if (_hasBeenAdopted && !_hasBeenApplied) {
                _hasBeenApplied = true;
                _staged.commit();
                _pending.commit(); // Write pending into main storage
                return true;
            }

            return false;
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
            StringBuilder sb = new StringBuilder();
            if (_hasBeenUsed.get()) {
                sb.append(_hasBeenAdopted ? 'A' : 'R');
            }
            sb.append(_staged.toString());
            return sb.toString();
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

            // REVISIT: can we do this with PhantomReference and amortise the
            // cost against other operations?
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
                ((CapturedContext) context).hasBeenApplied()) {
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
     * and makes changes to the context then calling {@literal rollback}
     * against the first context then the changes made to the second context
     * are NOT affected by the rollback.
     * <p>
     * A captured context may be used once; either by calling {@literal adopt}
     * or {@literal rollback}.  Subsequent calls to {@literal rollback} will
     * fail; subsequent calls to {@literal adopt} are silently ignored.
     * <p>
     * Capture-local changes to the context may be made via the
     * {@code localPut} method.  Such changes only take effect once the
     * context is adopted.
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
        _captured.rollback();
        // REVIEW: should this be main or previous unapplied context?
        _currentContext.set(_captured.getMainContext());
    }

    /**
     * Update the current Thread so that subsequent interaction with the
     * context is shared.
     */
    public void adopt()
    {
        _captured.adopt();
        _currentContext.set(_captured);
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
     * finalized.  This is intended only for unit-tests.
     */
    PhantomReference<CapturedContext<String,String>>
            addPhantomToQueue(ReferenceQueue queue)
    {
        return new PhantomReference(_captured, queue);
    }
}
