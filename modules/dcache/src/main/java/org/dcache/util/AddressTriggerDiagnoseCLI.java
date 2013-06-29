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
 * InetAddress that trigger enabling diagnose.  The class may be used
 * by several doors.  Each door cell should have its own instance of this
 * class.
 */
public class AddressTriggerDiagnoseCLI implements CellCommandListener
{
    private DiagnoseTriggers<InetAddress> _triggers;

    @Required
    public void setDiagnoseTriggers(DiagnoseTriggers<InetAddress> triggers)
    {
        _triggers = triggers;
    }

    @Command(name="diagnose add", hint="add an IP address",
            usage = "Add an IP address to the list.  The supplied address " +
                    "may be specified as an IPv4 address, an IPv6 address or " +
                    "as a hostname, which will be resolved to an address.\n\n" +
                    "All dCache components will log additional information " +
                    "when a client next connects from any of the " +
                    "listed address.  This information may be useful when " +
                    "diagnosing a client-specific problem.\n\n" +
                    "If a client triggers additional logging then its address " +
                    "is removed from the list so subsequent connections from " +
                    "the same client will not trigger the same detailed " +
                    "logging (i.e., this is a 'one-shot' diagnostic " +
                    "logging).  If subsequent additional logging is needed " +
                    "then this command must be called again.\n\n" +
                    "Additional logging may be triggered through gPlazma.  " +
                    "This has the advantage of being independent of which " +
                    "door the client connects to, but will miss some early " +
                    "interaction within the door.")
    public class DiagnoseAddCommand implements Callable<String>
    {
        @Argument(factoryMethod="getByName")
        InetAddress address;

        @Override
        public String call() throws UnknownHostException
        {
            _triggers.add(address);
            return "";
        }
    }

    @Command(name="diagnose rm", hint="remove a specific IP address",
            usage = "Manually remove an IP address so that a client " +
                    "connecting from that address will no longer trigger " +
                    "additional logging in all dCache components.\n\n" +
                    "Note that a client's address is removed automatically " +
                    "if it triggers additiona logging; therefore, in most " +
                    "cases, this command is not needed.")
    public class DiagnoseRmCommand implements Callable<String>
    {
        @Argument(factoryMethod="getByName")
        InetAddress address;

        @Override
        public String call() throws UnknownHostException
        {
            _triggers.remove(address);
            return "";
        }
    }

    @Command(name="diagnose clear", hint="remove all IP addresses",
            usage = "Removes all addresses that would trigger dCache " +
                    "components to logging additional, diagnostic " +
                    "information.  This is equivalent to calling the " +
                    "'diagnose rm' command for each of currently active " +
                    "address.\n\n" +
                    "Note that an address is removed automatically once a " +
                    "client connects; therefore, in many cases, this command " +
                    "is not be needed.")
    public class DiagnoseClearCommand implements Callable<String>
    {
        @Override
        public String call() throws UnknownHostException
        {
            _triggers.clear();
            return "";
        }
    }

    @Command(name="diagnose ls", hint="show all IP addresses",
            usage = "Print a list of addresses that will trigger additional " +
                    "logging.  When a client connects from any of the listed " +
                    "addresses all dCache components will log additional " +
                    "information.  The session-ID may be used to extract " +
                    "this information from log files.  Once a client has " +
                    "triggered this additional information, the address is " +
                    "removed automatically.")
    public class DiagnoseListCommand implements Callable<String>
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
