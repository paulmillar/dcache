package org.dcache.gridsite;

import dmg.cells.nucleus.CellCommandListener;
import dmg.util.command.Command;
import dmg.util.command.DelayedCommand;
import java.util.EnumSet;
import java.util.NoSuchElementException;
import org.dcache.srm.request.RequestCredentialStorage;
import org.dcache.srm.request.RequestCredentialStorage.ListOption;
import org.dcache.util.ColumnWriter;
import org.springframework.beans.factory.annotation.Required;

/**
 * Some useful commands when dealing with delegated credentials.
 */
public class CredentialAdminInterface implements CellCommandListener
{
    private RequestCredentialStorage _store;

    @Required
    public void setCredentialStore(RequestCredentialStorage store)
    {
        _store = store;
    }

    @Command(name="credentials ls",
            description ="List all known delegated credentials.")
    public class CredentialsLsCommand extends DelayedCommand<String>
    {
        @Override
        public String execute() throws NoSuchElementException
        {
            ColumnWriter writer = new ColumnWriter()
                    .header("ID").left("id")
                    .space().header("Distinguished Name").left("dn")
                    .space().header("Role").left("role")
                    .space().header("Created").date("created")
                    .space().header("Expires").date("expires");
            _store.listRequestCredentials(EnumSet.noneOf(ListOption.class),
                    (id,name,role,created,expires,ignored) -> { writer.row().
                            value("id", id).
                            value("dn", name).
                            value("role", role).
                            value("created", created).
                            value("expires", expires);
                    });

            return writer.toString();
        }
    }
}
