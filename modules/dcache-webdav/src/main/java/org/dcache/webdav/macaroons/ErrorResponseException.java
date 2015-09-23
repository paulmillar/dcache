package org.dcache.webdav.macaroons;

/**
 * An Exception thrown when the user makes a bad or invalid request.
 */
public class ErrorResponseException extends Exception
{
    private final int _status;

    public ErrorResponseException(int status, String message)
    {
        super(message);
        _status = status;
    }

    public int getStatus()
    {
        return _status;
    }
}
