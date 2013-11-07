package org.dcache.util;

/**
 * User: Podstavkov
 * Date: Feb 13, 2008
 * Time: 6:39:03 PM
 */

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.SQLException;

import static org.dcache.util.Exceptions.Behaviour.RETURNS_RUNTIMEEXCEPTION;
import static org.dcache.util.Exceptions.Behaviour.THROWS_RUNTIMEEXCEPTION;
import static org.dcache.util.Exceptions.unwrapInvocationTargetException;

/*
* Here we define the proxy class which will work as a ConnectionFactory
* provided by DriverManagerConnectionFactory from DBCP package, but the
* behaviour of createConnection method will be modified
*/
public class DMCFRetryProxyHandler implements InvocationHandler {

    protected Object delegate;
    private	int timeout;

    public DMCFRetryProxyHandler(Object delegate) {
        this(delegate, -1);
    }

    public DMCFRetryProxyHandler(Object delegate, int timeout) {
        this.delegate = delegate;
        this.timeout  = timeout ;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Exception {
        //
        Object result;
        String methodName = method.getName();
        if (methodName.startsWith("createConnection")) {
            Exception cause;
            //System.out.println("Calling method " + method + " at " + System.currentTimeMillis());
            int ntry = timeout >= 0 ? timeout/3 : 1000000;
            do {
                try {
                    result = method.invoke(delegate, args);
                    return result;
                } catch (InvocationTargetException e) {
                    cause = unwrapInvocationTargetException(e, THROWS_RUNTIMEEXCEPTION);
                    if (cause instanceof SQLException) {
                        System.out.println("createConnection(): Got exception " +
                                cause.getClass().getName() + ", SQLState: " +
                                ((SQLException)cause).getSQLState());
                        if (ntry-- > 0) {
                            try { Thread.sleep(3000); } catch (InterruptedException ie) {}
                            System.out.println("Sleep 3 s, try to get connection ... tries left: "+ntry);
                        }
                    } else {
                        throw cause;
                    }
                }
            } while (ntry > 0);
            throw cause;
        } else {
            try {
                //System.out.println("Calling method " + method + " at " + System.currentTimeMillis());
                result = method.invoke(delegate, args);
                return result;
            } catch (InvocationTargetException e) {
                throw unwrapInvocationTargetException(e, RETURNS_RUNTIMEEXCEPTION);
            } finally {
                //System.out.println("Called  method " + method + " at " + System.currentTimeMillis());
            }
        }
    }
}

