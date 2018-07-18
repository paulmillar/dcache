/* dCache - http://www.dcache.org/
 *
 * Copyright (C) 2018 Deutsches Elektronen-Synchrotron
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.dcache.auth;

import java.io.Serializable;
import java.security.Principal;
import static java.util.Objects.requireNonNull;

/**
 * A principal that describes a client-supplied identifier.  The client
 * identifier has two parts: the source and the id.  The source identifies
 * (as much as is possible) from where the ID came.  The identifier
 */
public class ClientIdPrincipal implements Principal, Serializable
{
    private static final long serialVersionUID = 1L;

    private final String source;
    private final String id;

    public ClientIdPrincipal(String source, String id)
    {
        this.source = requireNonNull(source);
        this.id = requireNonNull(id);
    }

    public String getSource()
    {
        return source;
    }

    public String getId()
    {
        return id;
    }

    @Override
    public String getName()
    {
        return source + ":" + id;
    }
}
