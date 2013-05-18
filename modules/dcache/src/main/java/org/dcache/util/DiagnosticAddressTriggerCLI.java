package org.dcache.util;

import org.springframework.beans.factory.annotation.Required;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.Callable;

import dmg.util.command.Argument;
import dmg.util.command.Command;

import org.dcache.cells.CellCommandListener;

/**
 * This class provides a common admin interface for managing the set of
 * InetAddress that trigger diagnostic.
 */
public class DiagnosticAddressTriggerCLI implements CellCommandListener
{
    private DiagnosticTriggers<InetAddress> _triggers;

    @Required
    public void setDiagnosticTriggers(DiagnosticTriggers<InetAddress> triggers)
    {
        _triggers = triggers;
    }

    @Command(name="diagnostic add", hint="add an address",
            usage = "Add an address that will trigger capturing diagnostic " +
                    "information when that host next connections.")
    public class DiagnosticAddCommand implements Callable<String>
    {
        @Argument(factory="getByName")
        InetAddress address;

        @Override
        public String call() throws UnknownHostException
        {
            _triggers.add(address);
            return "";
        }
    }

    @Command(name="diagnostic rm", hint="remove an address",
            usage = "Remove an address that will currently trigger capturing " +
                    "diagnostic information.")
    public class DiagnosticRmCommand implements Callable<String>
    {
        @Argument(factory="getByName")
        InetAddress address;

        @Override
        public String call() throws UnknownHostException
        {
            _triggers.remove(address);
            return "";
        }
    }

    @Command(name="diagnostic clear", hint="clear all hosts",
            usage = "Clears all hosts that would trigger capturing diagnostic " +
                    "information.")
    public class DiagnosticClearCommand implements Callable<String>
    {
        @Override
        public String call() throws UnknownHostException
        {
            _triggers.clear();
            return "";
        }
    }

    @Command(name="diagnostic list", hint="show all diagnostic triggers",
            usage = "Clean all hosts that would trigger capturing diagnostic " +
                    "information.")
    public class DiagnosticListCommand implements Callable<String>
    {
        @Override
        public String call() throws UnknownHostException
        {
            StringBuilder sb = new StringBuilder();

            for(InetAddress address : _triggers.getAll()) {
                sb.append(address).append('\n');
            }

            return sb.toString();
        }
    }
}
