package dmg.cells.nucleus;

import org.slf4j.MDC;

import dmg.util.TimebasedCounter;

import org.dcache.commons.util.NDC;
import org.dcache.util.SDC;

/**
 * The Cell Diagnostic Context, a utility class for working with the
 * Log4j NDC and MDC.
 *
 * Notice that the MDC is automatically inherited by child threads
 * upon creation. Thus the domain, cell name, and session identifier
 * is inherited. The same is not true for the NDC, which needs to be
 * explicitly copied. Special care must be taken when using shared
 * thread pools, as this can span several cells. For those the MDC
 * should be initialised per task, not per thread.
 *
 * The class serves two purposes:
 *
 * - It contains a number of static methods for manipulating the MDC
 *   and NDC.
 *
 * - CDC instances capture the Cells related MDC values and the NDC.
 *   These can be applied to other threads. This is useful when using
 *   worker threads that should inherit the context of the task
 *   creation point.
 *
 *   CDC implements AutoCloseable and will restore the captured values
 *   when closed(). This allows the following useful patterns:
 *
 *     try (CDC ignored = new CDC()) {
 *       // temporarily modify the CDC - it will auto restore
 *     }
 *
 *   and
 *
 *     try (CDC ignored = cdc.restore()) {
 *         // temporarily use a previously captured cdc
 *     }
 */
public class CDC implements AutoCloseable
{
    public final static String MDC_DOMAIN = "cells.domain";
    public final static String MDC_CELL = "cells.cell";
    public final static String MDC_SESSION = "cells.session";
    public final static String SDC_DIAGNOSE = "cells.diagnose";

    private final static TimebasedCounter _sessionCounter =
        new TimebasedCounter();

    private final NDC _ndc;
    private final SDC _sdc;
    private final String _session;
    private final String _cell;
    private final String _domain;


    /**
     * Captures the cells diagnostic context of the calling thread.
     *
     * For the diagnose command there are two use-cases to consider:
     * isolation/roll-back and sharing.
     *
     *   1. ability to roll-back enabling of diagnose messages; for example:
     *
     *       try (CDC ignored = new CDC()) {
     *           // commands here
     *           CDC.setDiagnoseEnabled(true);
     *           // more commands with diagnose enabled
     *       }
     *
     *       // commands isolated from effect of enabling diagnose
     *
     *   2. the ability for two threads to share the same diagnose context; for
     *      example, thread-1 does:
     *
     *       final CDC captured = new CDC();
     *
     *      and, when thread-2 does:
     *
     *       try (CDC ignored = captured.restore()) {
     *           CDC.setDiagnoseEnabled(true);
     *       }
     *
     *      then diagnose should be enabled for thread-1, too.
     */
    public CDC()
    {
        _session = MDC.get(MDC_SESSION);
        _cell = MDC.get(MDC_CELL);
        _domain = MDC.get(MDC_DOMAIN);
        _sdc = new SDC();
        _ndc = NDC.cloneNdc();
    }

    /**
     * Wrapper around <code>MDC.put</code> and
     * <code>MDC.remove</code>. <code>value</code> is allowed to e
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

    private void apply()
    {
        setMdc(MDC_DOMAIN, _domain);
        setMdc(MDC_CELL, _cell);
        setMdc(MDC_SESSION, _session);
        if (_ndc == null) {
            NDC.clear();
        } else {
            NDC.set(_ndc);
        }
    }

    @Override
    public void close()
    {
        apply();
        _sdc.rollback();
    }

    /**
     * Restore the cells diagnostic context to the calling thread. The old
     * diagnostic context is captured and returned.
     */
    public CDC restore()
    {
        CDC cdc = new CDC();
        apply();
        _sdc.adopt();
        return cdc;
    }

    /**
     * Returns the cell name stored in the MDC of the calling
     * thread.
     */
    static public String getCellName()
    {
        return MDC.get(MDC_CELL);
    }

    /**
     * Returns the domain name stored in the MDC of the calling
     * thread.
     */
    static public String getDomainName()
    {
        return MDC.get(MDC_DOMAIN);
    }

    /**
     * Returns the session identifier stored in the MDC of the calling
     * thread.
     */
    static public String getSession()
    {
        return MDC.get(MDC_SESSION);
    }

    /**
     * Sets the session in the MDC for the calling thread.
     *
     * @param session Session identifier.
     */
    static public void setSession(String session)
    {
        setMdc(MDC_SESSION, session);
    }

    /**
     * Creates and sets a new session identifier. The session
     * identifier is based on static TimedbasedCounter and is thus
     * with a high probability unique in this JVM.
     *
     * As long as each JVM uses its own prefix, the identifier will be
     * unique over multible JVMs.
     *
     * @see dmg.util.TimedbasedCounter
     */
    static public void createSession(String prefix)
    {
        setSession(prefix + _sessionCounter.next());
    }

    /**
     * Creates and sets a new session identifier. Uses the domain name
     * stored in he MDC of the calling thread as a prefix. This
     * guarantees uniqueness among a cells system (but see
     * documentation of TimebasedCounter for the limits).
     *
     * @throws IllegalStateException if domain name is not set in MDC
     * @see dmg.util.TimedbasedCounter
     */
    static public void createSession()
    {
        Object domain = MDC.get(MDC_DOMAIN);
        if (domain == null) {
            throw new IllegalStateException("Missing domain name in MDC");
        }

        createSession(domain.toString() + "-");
    }

    /**
     * Setup the cell diagnostic context of the calling
     * thread. Threads created from the calling thread automatically
     * inherit this information. The old diagnostic context is
     * captured and returned.
     */
    static public CDC reset(CellNucleus cell)
    {
        return reset(cell.getCellName(), cell.getCellDomainName());
    }

    /**
     * Setup the cell diagnostic context of the calling
     * thread. Threads created from the calling thread automatically
     * inherit this information. The old diagnostic context is
     * captured and returned.
     */
    static public CDC reset(String cellName, String domainName)
    {
        CDC cdc = new CDC();
        setMdc(MDC_CELL, cellName);
        setMdc(MDC_DOMAIN, domainName);
        MDC.remove(MDC_SESSION);
        SDC.reset();
        NDC.clear();
        return cdc;
    }

    /**
     * Returns the message description added to the NDC as part of the
     * message related diagnostic context.
     */
    static protected String getMessageContext(CellMessage envelope)
    {
        StringBuilder s = new StringBuilder();

        Object session = envelope.getSession();
        if (session != null) {
            s.append(session).append(' ');
        }

        s.append(envelope.getSourcePath().getCellName());

        Object msg = envelope.getMessageObject();
        if (msg instanceof HasDiagnosticContext) {
            String context =
                ((HasDiagnosticContext) msg).getDiagnosticContext();
            s.append(' ').append(context);
        }

        return s.toString();
    }

    /**
     * Setup message related diagnostic context for the calling
     * thread. Adds information about a message to the MDC and NDC.
     * The CDC is captured before any changes are made and returned.  This
     * allows the caller to revert to the previous CDC state
     */
    static public CDC setMessageContext(CellMessage envelope)
    {
        CDC cdc = new CDC();
        Object session = envelope.getSession();
        NDC.push(getMessageContext(envelope));
        setMdc(MDC_SESSION, (session == null) ? null : session.toString());
        if (envelope.isDiagnoseEnabled()) {
            setDiagnoseEnabled(true);
        }
        return cdc;
    }

    /**
     * Clears all cells related MDC entries and the NDC.
     */
    static public void clear()
    {
        MDC.remove(MDC_DOMAIN);
        MDC.remove(MDC_CELL);
        MDC.remove(MDC_SESSION);
        NDC.clear();
    }

    public static void updateDiagnose(CellMessage envelope)
    {
        if (envelope.isDiagnoseEnabled()) {
            setDiagnoseEnabled(true);
        }
    }

    public void updateStoredDiagnose(CellMessage envelope)
    {
        if (envelope.isDiagnoseEnabled()) {
            _sdc.localPut(SDC_DIAGNOSE, "enabled");
        }
    }

    public static boolean isDiagnoseEnabled()
    {
        return SDC.get(SDC_DIAGNOSE) != null;
    }

    public static void setDiagnoseEnabled(boolean isEnabled)
    {
        SDC.put(SDC_DIAGNOSE, isEnabled ? "enabled" : null);
    }

    static void resetDiagnose()
    {
        SDC.put(SDC_DIAGNOSE, null);
    }
}
