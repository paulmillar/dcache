package org.dcache.util;

import com.google.common.base.Throwables;

import java.lang.reflect.InvocationTargetException;

/**
 *  Utility class to help when handling exceptions.
 */
public class Exceptions
{
    private Exceptions()
    {
    }

    public enum Behaviour {
        /** Returns all Exceptions */
        RETURNS_RUNTIMEEXCEPTION,

        /** Throws RTE, returns all other Exceptions */
        THROWS_RUNTIMEEXCEPTION
    }

    /**
     * Convenience method to handle exceptions from a reflection method call.
     * If the exception is not an instance of InvocationTargetException then
     * the argument is returned.  Otherwise, the Throwable that caused the ITE
     * is examined.  If the cause is an Error then this is re-thrown.  If it is
     * an instance of RuntimeException then the method either throws or returns
     * it.  For all other Exceptions, the cause is returned.
     */
    public static Exception unwrapInvocationTargetException(Exception e,
            Behaviour behaviour)
    {
        Exception unwrapped = e;

        if (e instanceof InvocationTargetException) {
            Throwable cause = e.getCause();

            if (cause instanceof Error) {
                throw (Error)cause;
            }

            if (behaviour == Behaviour.THROWS_RUNTIMEEXCEPTION &&
                    cause instanceof RuntimeException) {
                throw (RuntimeException)cause;
            }

            if (cause != null) {
                unwrapped = (Exception)cause;
            }
        }

        return unwrapped;
    }
}
