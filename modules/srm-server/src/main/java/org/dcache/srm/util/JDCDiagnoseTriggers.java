package org.dcache.srm.util;

import org.dcache.util.DiagnoseTriggers;

/**
 * DiagnoseTriggers class that enables diagnose via JDC.
 */
public class JDCDiagnoseTriggers<T> extends DiagnoseTriggers<T>
{

    @Override
    public void enableDiagnose()
    {
        JDC.setDiagnoseEnabled(true);
    }
}
