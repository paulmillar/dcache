/* dCache - http://www.dcache.org/
 *
 * Copyright (C) 2015 Deutsches Elektronen-Synchrotron
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
package org.dcache.webdav.zip;

import org.springframework.beans.factory.annotation.Required;

import javax.security.auth.Subject;

import java.util.concurrent.ScheduledExecutorService;

import diskCacheV111.util.FsPath;


/**
 * A factory for creating ZipRequest objects, each of which encapsulates
 * a request to create a zip file.
 */
public class ZipRequestFactory
{
    private ScheduledExecutorService _executor;

    @Required
    public void setExecutor(ScheduledExecutorService executor)
    {
        _executor = executor;
    }

    public ZipRequest createRequest(Subject subject, boolean isRecursive, FsPath path)
    {
        return new ZipRequest(subject, isRecursive, path, _executor);
    }
}
