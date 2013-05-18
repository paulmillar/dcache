package org.dcache.util;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;

import org.dcache.commons.util.NDC;

/**
 * This class holds a set of triggers for the NDC enableDiagnostic method.
 * One may add or remove a trigger, and the combined set of triggers may be
 * viewed or cleared.
 *
 * Either a single item or a combination of items may be processed.  If
 * the item or at least one item of the supplied collection matches one or more
 * of the triggers then NDC#enableDiagnostics is called and the matching items
 * are removed from the set of triggers.
 *
 * This class is generic and requires the specific type be immutable.
 */
public class DiagnosticTriggers<T>
{
    private final Set<T> _triggers = Collections.newSetFromMap(
        new ConcurrentHashMap<T, Boolean>());

    /**
     * Add an additional triggers to the existing set of triggers.
     * If an equal item is already registered then this method has no effect.
     */
    public void add(T item)
    {
        _triggers.add(item);
    }

    /**
     * Add zero or more additional triggers to the existing set of triggers.
     * If any item of the supplied collection is equal to an already registered
     * trigger then that item has no effect.
     */
    public void addAll(Collection<T> items)
    {
        _triggers.addAll(items);
    }

    /**
     * Remove item so that future calls to #accept with item will not trigger
     * diagnostics, or calls to #acceptAll that include item will not trigger
     * diagnostics as a result of item.
     * @param item the trigger item to remove
     * @return true if an item was removed.
     */
    public boolean remove(T item)
    {
        return _triggers.remove(item);
    }

    /**
     * Remove all the provided item so that future calls to #accept with
     * any member of items will not trigger diagnostics, or calls to #acceptAll
     * that include any subset of items will not trigger diagnostics due to
     * inclusion of that subset.
     * @return true if at least one trigger was removed.
     */
    public boolean removeAll(Collection<T> items)
    {
        return _triggers.removeAll(items);
    }

    /**
     * Remove all current trigger items from the set.
     */
    public void clear()
    {
        _triggers.clear();
    }

    /**
     * Returns an unmodifiable view of the set of triggers.
     */
    public Set<T> getAll()
    {
        return Collections.unmodifiableSet(_triggers);
    }

    /**
     * Process a potential trigger for NDC enableDiagnostic.  If the supplied
     * item equals a previously added trigger then NDC#enableDiagnostic is
     * called.  Subsequent calls with the same argument will not trigger
     * NDC#enableDiagnostic unless #add has called with this argument.
     * @return true if item triggered enabling diagnostic.
     */
    public boolean accept(T item)
    {
        boolean isTriggered = _triggers.remove(item);
        if (isTriggered) {
            NDC.enableDiagnostic();
        }
        return isTriggered;
    }

    /**
     * Process several potential triggers for NDC enableDiagnostic.  If any of
     * the supplied items equal any item previously added then
     * NDC#enableDiagnostic is called.  Subsequent calls with any subset of the
     * supplied argument will not trigger NDC#enableDiagnostic unless #add
     * has been called with an element from this subset.
     * @return true if any of the supplied items are triggers.
     */
    public boolean acceptAll(Collection<T> items)
    {
        boolean isTriggered = _triggers.removeAll(items);
        if (isTriggered) {
            NDC.enableDiagnostic();
        }
        return isTriggered;
    }
}
