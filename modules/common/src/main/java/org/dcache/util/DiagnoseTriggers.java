package org.dcache.util;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static java.util.Collections.newSetFromMap;

/**
 * This class holds a set of triggers for somehow enabling diagnostic, the
 * exact way this is achieved is specified in the concrete subclass.
 * One may add or remove one or multiple triggers, and the combined set of
 * triggers may be viewed or cleared.
 *
 * Either a single item or multiple items may be processed in one go, via the
 * #accept and #acceptAll method respectively.  Processing involves checking
 * whether the supplied item or any member of the multiple items matches a
 * trigger (via the equals method).  If any match then the NDC#enableDiagnose
 * method is called.  All triggers that match the supplied item or items are
 * removed, so that a subsequent call to the #accept or #acceptAll method with
 * the same input will not call NDC#enableDiagnose, unless #add or #addAll has
 * been called.
 *
 * This class is generic and requires the specified type be immutable.
 */
abstract public class DiagnoseTriggers<T>
{
    private final Set<T> _triggers =
            newSetFromMap(new ConcurrentHashMap<T, Boolean>());

    /**
     * Add an additional trigger to the existing set of triggers.
     * If an equal trigger is already registered then this method will have no
     * effect.
     */
    public void add(T item)
    {
        _triggers.add(item);
    }

    /**
     * Add zero or more additional triggers to the existing set of triggers.
     * If any trigger of the supplied collection is equal to an already
     * registered trigger then adding that trigger will have no effect.
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
     * Returns the set of triggers.
     */
    public Set<T> getAll()
    {
        return new HashSet(_triggers);
    }

    /**
     * Process a potential trigger for NDC enableDiagnose.  If the supplied
     * item equals a previously added trigger then NDC#enableDiagnose is
     * called.  The trigger is removed so that subsequent calls with the same
     * argument will not trigger NDC#enableDiagnostic unless #add has called
     * with this argument.
     * @return true if item triggered enabling diagnostic.
     */
    public boolean accept(T item)
    {
        boolean isTriggered = _triggers.remove(item);
        if (isTriggered) {
            enableDiagnose();
        }
        return isTriggered;
    }

    /**
     * Process several potential triggers for NDC enableDiagnostic.  If any of
     * the supplied items equal any item previously added then
     * NDC#enableDiagnostic is called.  Subsequent calls with any subset of the
     * supplied argument will not trigger NDC#enableDiagnostic unless #add or
     * #addAll  has been called with elements from this subset.
     * @return true if any of the supplied items match a trigger.
     */
    public boolean acceptAll(Collection<T> items)
    {
        boolean isTriggered = _triggers.removeAll(items);
        if (isTriggered) {
            enableDiagnose();
        }
        return isTriggered;
    }

    public abstract void enableDiagnose();
}
