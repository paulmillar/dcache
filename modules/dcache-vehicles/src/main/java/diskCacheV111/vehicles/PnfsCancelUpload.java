/* dCache - http://www.dcache.org/
 *
 * Copyright (C) 2014 Deutsches Elektronen-Synchrotron
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
package diskCacheV111.vehicles;

import javax.annotation.Nonnull;
import javax.security.auth.Subject;

import diskCacheV111.util.FsPath;

import org.dcache.auth.attributes.Restriction;

import static java.util.Objects.requireNonNull;

/**
 * Revoke a temporary upload path.
 *
 * This message operates on a temporary upload path generated with
 * PnfsCreateUploadPath. The temporary upload path will be deleted
 * and the path will no longer be available for writing.
 */
public class PnfsCancelUpload extends PnfsMessage
{
    private static final long serialVersionUID = 1198546600602532976L;

    private final String uploadPath;
    private String explanation = "SRM cancelled upload";

    public PnfsCancelUpload(Subject subject, Restriction restriction, FsPath uploadPath, FsPath path)
    {
        setSubject(subject);
        setRestriction(restriction);
        setPnfsPath(path.toString());
        setReplyRequired(true);
        this.uploadPath = uploadPath.toString();
    }

    public FsPath getPath()
    {
        return FsPath.create(getPnfsPath());
    }

    public FsPath getUploadPath()
    {
        return FsPath.create(uploadPath);
    }

    public void setExplanation(@Nonnull String explanation)
    {
        this.explanation = requireNonNull(explanation);
    }

    @Nonnull
    public String getExplanation()
    {
        return explanation;
    }
}
