package org.dcache.commons.util;

import org.slf4j.MDC;

/**
 * The class emulates the Nested Diagnostic Context of Log4j.
 *
 * Besides providing static methods for working with the NDC, the
 * class can be instantiated to capture the state of the NDC.
 */
public class NDC
{
    /* Internally the class uses the MDC.  Two MDC keys are used: One
     * to hold the NDC in string form, and another to hold a comma
     * separated list of positions in the NDC string indicating the
     * boundaries.
     */
    static public final String KEY_NDC = "org.dcache.ndc";
    static public final String KEY_POSITIONS = "org.dcache.ndc.positions";
    static public final String KEY_DIAGNOSE_OFFSET = "org.dcache.ndc.diagnostic.offset";

    private final String _ndc;
    private final String _positions;
    private String _diagnose;

    public NDC(String ndc, String positions, String diagnose)
    {
        _ndc = ndc;
        _positions = positions;
        _diagnose = diagnose;
    }

    public String getNdc()
    {
        return _ndc;
    }

    public String getPositions()
    {
        return _positions;
    }

    private String getDiagnose()
    {
        return _diagnose;
    }

    public void enableStoredDiagnose()
    {
        if (_diagnose == null) {
            _diagnose = lastCommaOffset(_positions);
        }
    }

    /**
     * Wrapper around <code>MDC.put</code> and
     * <code>MDC.remove</code>. <code>value</code> is allowed to be
     * null.
     */
    static private void setMdc(String key, String value)
    {
        if (value != null) {
            MDC.put(key, value);
        } else {
            MDC.remove(key);
        }
    }

    /**
     * Clear any nested diagnostic information if any.
     */
    static public void clear()
    {
        MDC.remove(KEY_NDC);
        MDC.remove(KEY_POSITIONS);
        MDC.remove(KEY_DIAGNOSE_OFFSET);
    }

    /**
     * Returns the nested diagnostic context for the current thread.
     */
    static public NDC cloneNdc()
    {
        return new NDC(MDC.get(KEY_NDC), MDC.get(KEY_POSITIONS),
                MDC.get(KEY_DIAGNOSE_OFFSET));
    }

    /**
     * Replace the nested diagnostic context.
     */
    static public void set(NDC ndc)
    {
        setMdc(KEY_NDC, ndc.getNdc());
        setMdc(KEY_POSITIONS, ndc.getPositions());
        setMdc(KEY_DIAGNOSE_OFFSET, ndc.getDiagnose());
    }

    /**
     * Push new diagnostic context information for the current
     * thread.
     */
    static public void push(String message)
    {
        String ndc = MDC.get(KEY_NDC);
        if (ndc == null) {
            MDC.put(KEY_NDC, message);
            MDC.put(KEY_POSITIONS, "0");
        } else {
            MDC.put(KEY_NDC, ndc + " " + message);
            MDC.put(KEY_POSITIONS, MDC.get(KEY_POSITIONS) + "," + ndc.length());
        }
    }

    /**
     * Removes the diagnostic context information pushed the last.
     * Clients should call this method before leaving a diagnostic
     * context.
     */
    static public void pop()
    {
        String ndc = MDC.get(KEY_NDC);
        if (ndc != null) {
            String positions = MDC.get(KEY_POSITIONS);
            int pos = positions.lastIndexOf(',');
            if (pos == -1) {
                MDC.remove(KEY_NDC);
                MDC.remove(KEY_POSITIONS);
                MDC.remove(KEY_DIAGNOSE_OFFSET);
            } else {
                int offset = Integer.parseInt(positions.substring(pos + 1));
                MDC.put(KEY_NDC, ndc.substring(0, offset));
                String newPositions = positions.substring(0, pos);
                MDC.put(KEY_POSITIONS, newPositions);
                String diagnose = MDC.get(KEY_DIAGNOSE_OFFSET);
                if(diagnose != null) {
                    int newPos = newPositions.lastIndexOf(',');
                    if(newPos < Integer.valueOf(diagnose)) {
                        MDC.remove(KEY_DIAGNOSE_OFFSET);
                    }
                }
            }
        }
    }

    static public boolean isDiagnoseEnabled()
    {
        return MDC.get(KEY_DIAGNOSE_OFFSET) != null;
    }

    static public void enableDiagnose()
    {
        if (!isDiagnoseEnabled()) {
            MDC.put(KEY_DIAGNOSE_OFFSET, lastCommaOffset(MDC.get(KEY_POSITIONS)));
        }
    }

    static private String lastCommaOffset(String value)
    {
        return value == null ? "-1" : String.valueOf(value.lastIndexOf(','));
    }
}
