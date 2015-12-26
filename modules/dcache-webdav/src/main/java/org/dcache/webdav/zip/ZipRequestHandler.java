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

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Required;

import javax.security.auth.Subject;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;

import static javax.servlet.http.HttpServletResponse.*;

import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.attribute.FileTime;
import java.security.AccessControlContext;
import java.util.EnumSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import diskCacheV111.util.CacheException;
import diskCacheV111.util.FileNotFoundCacheException;
import diskCacheV111.util.FsPath;
import diskCacheV111.util.NotDirCacheException;

import dmg.cells.nucleus.CDC;
import dmg.cells.nucleus.CellEndpoint;
import dmg.cells.nucleus.CellInfo;
import dmg.cells.nucleus.CellMessageSender;

import org.dcache.auth.Origin;
import org.dcache.auth.Subjects;

import static org.dcache.namespace.FileAttribute.*;

import org.dcache.namespace.FileAttribute;
import org.dcache.util.RedirectedTransfer;
import org.dcache.util.Transfer;
import org.dcache.vehicles.FileAttributes;
import org.dcache.webdav.zip.TransferEngine.ZipInternalTransfer;

import static com.google.common.base.Preconditions.checkNotNull;




/**
 * This class provides the integration between dCache's support for
 * creating ZIP files with Jetty.
 */
public class ZipRequestHandler extends AbstractHandler implements CellMessageSender

{
    private static final Logger LOG = LoggerFactory.getLogger(ZipRequestHandler.class);
    private static final boolean DEFAULT_RECURSIVE = true;
    public static final Set<FileAttribute> REQUIRED_ATTRIBUTES =
            EnumSet.of(SIZE, CREATION_TIME, MODIFICATION_TIME, ACCESS_TIME);

    private FsPath _rootPath = new FsPath();
    private DirectoryListEngine _lister;
    private ZipRequestFactory _requestFactory;
    private TransferEngine _transfers;
    private String _cellName;
    private String _domainName;
    private boolean _isNameSiteUnique;

    @Required
    public void setDirectoryListEngine(DirectoryListEngine engine)
    {
        _lister = engine;
    }

    @Required
    public void setZipRequestFactory(ZipRequestFactory factory)
    {
        _requestFactory = checkNotNull(factory);
    }

    @Required
    public void setTransferEngine(TransferEngine engine)
    {
        _transfers = engine;
    }

    public void setRootPath(String path)
    {
        _rootPath = new FsPath(path);
    }

    public String getRootPath()
    {
        return _rootPath.toString();
    }

    @Override
    public void setCellEndpoint(CellEndpoint endpoint)
    {
        CellInfo info = endpoint.getCellInfo();
        _cellName = info.getCellName();
        _domainName = info.getDomainName();
        _isNameSiteUnique = endpoint.getArgs().getBooleanOption("export");
    }

    @Override
    public void handle(String target, Request request, HttpServletRequest servletRequest,
            HttpServletResponse response) throws IOException, ServletException
    {
        if (request.getMethod().equalsIgnoreCase("POST") &&
                request.getHeader("Accept").equalsIgnoreCase("application/zip")) {
            try (CDC ignored = CDC.reset(_cellName, _domainName)) {
                Transfer.initSession(_isNameSiteUnique, false);

                try {
                    // FIXME: subject = Subject.getSubject(null)
                    Subject subject = Subjects.of(0, 0, new int[0]);
                    subject.getPrincipals().add(new Origin(request.getRemoteInetSocketAddress().getAddress()));
                    handleRequest(subject, target, request, response);

                    response.getOutputStream().flush();
                    response.flushBuffer();
                } catch (RuntimeException e) {
                    LOG.error("Failed to create output: {}", e.toString(), e);
                    response.sendError(SC_INTERNAL_SERVER_ERROR, "Internal fault: " + e.toString());
                }

                request.setHandled(true);
            }
        }
    }

    private void handleRequest(Subject subject, String target, Request request,
            HttpServletResponse response) throws IOException
    {
        ZipRequest zipRequest = parseRequest(subject, target, request);
        try {
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType("application/zip");
            _lister.startListing(zipRequest);
            _transfers.startTransfers(zipRequest);
            deliverZip(zipRequest, response.getOutputStream());
        } catch (IOException e) {
            zipRequest.cancel();
            response.sendError(SC_BAD_REQUEST, "Problem sending data: " + e.toString());
            response.setContentType(null);
        } catch (InterruptedException e) {
            zipRequest.cancel();
            response.sendError(SC_INTERNAL_SERVER_ERROR, "Server shutting down");
            response.setContentType(null);
        } catch (NotDirCacheException e) {
            zipRequest.cancel();
            response.sendError(SC_METHOD_NOT_ALLOWED, "Not a directory");
            response.setContentType(null);
        } catch (FileNotFoundCacheException e) {
            zipRequest.cancel();
            response.sendError(SC_NOT_FOUND, "Not such directory");
            response.setContentType(null);
        } catch (CacheException e) {
            zipRequest.cancel();
            response.sendError(SC_INTERNAL_SERVER_ERROR, e.getMessage());
            response.setContentType(null);
        } catch (RuntimeException e) {
            zipRequest.cancel();
        }
    }

    private ZipRequest parseRequest(Subject subject, String target, Request request)
    {
        FsPath fullPath = new FsPath(_rootPath, new FsPath(target));
        String recursive = request.getParameter("r");
        boolean isRecursive = recursive == null ? DEFAULT_RECURSIVE : recursive.equals("t");
        return _requestFactory.createRequest(subject, isRecursive, fullPath);
    }

    private void deliverZip(ZipRequest request, ServletOutputStream rawOut)
    {
        ZipOutputStream out = new ZipOutputStream(rawOut);
        // NB. ZipOutputStream.STORED requires prior knowledge of CRC32
        out.setMethod(ZipOutputStream.DEFLATED);
        out.setLevel(1);

        RedirectedTransfer<String> data;
        try {
            while ((data = request.takeTransfer()) != null) {
                ZipInternalTransfer transfer = (ZipInternalTransfer) data; // FIXME

                String path = request.getPath().relativize(new FsPath(data.getTransferPath())).toString().substring(1);
                ZipEntry entry = new ZipEntry(path);
                FileAttributes attributes = data.getFileAttributes();
                entry.setSize(attributes.getSize());
                entry.setCreationTime(FileTime.fromMillis(attributes.getCreationTime()));
                entry.setLastAccessTime(FileTime.fromMillis(attributes.getAccessTime()));
                entry.setTime(attributes.getModificationTime());
                try {
                    out.putNextEntry(entry);
                    transfer.relayData(out);
                    out.closeEntry();
                } catch (IOException e) {
                    LOG.info("Failed to send file data: {}", e.toString());
                    request.cancel();
                    return;
                } catch (CacheException e) {
                    // The zip file format allows us to recover from this; however,
                    // the Java built-in implementation does not.
                    LOG.error("Internal error: {}", e.getMessage());
                    request.cancel();
                    break;
                }
            }

            // TODO: write out problems file.

            try {
                out.close();
            } catch (IOException e) {
                LOG.info("Failed to send final directory: {}", e.toString());
            }
        } catch (InterruptedException e) {
            // Need to be able to indicate that there is a problem.
            request.cancel();
        }
    }
}
