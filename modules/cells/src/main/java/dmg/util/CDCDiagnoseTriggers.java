package dmg.util;

import dmg.cells.nucleus.CDC;

import org.dcache.util.DiagnoseTriggers;

/**
 *  Specific DiagnosticTriggers class that enables diagnostic output in CDC.
 */
public class CDCDiagnoseTriggers<T> extends DiagnoseTriggers<T>
{
    @Override
    public void enableDiagnose()
    {
        CDC.setDiagnoseEnabled(true);
    }
}
