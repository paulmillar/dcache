package org.dcache.ftp.data;

/* Interface for reporting errors.
 */
public interface ErrorListener
{
    /** Log status messsages. */
    void say(String msg);

    /** Log error messsages. */
    void esay(String msg);

    /** Log error messsages. */
    void esay(Throwable t);
}
