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

import com.google.common.base.Strings;
import com.google.common.collect.Maps;
import com.google.common.io.ByteStreams;
import com.google.common.net.InetAddresses;
import io.milton.servlet.ServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Required;

import javax.security.auth.Subject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Map;

import static java.util.concurrent.TimeUnit.*;

import java.util.concurrent.TimeUnit;

import diskCacheV111.util.CacheException;
import diskCacheV111.util.FsPath;
import diskCacheV111.util.PnfsHandler;
import diskCacheV111.util.TimeoutCacheException;
import diskCacheV111.vehicles.DoorTransferFinishedMessage;
import diskCacheV111.vehicles.HttpDoorUrlInfoMessage;
import diskCacheV111.vehicles.HttpProtocolInfo;
import diskCacheV111.vehicles.PoolIoFileMessage;
import diskCacheV111.vehicles.PoolMoverKillMessage;
import diskCacheV111.vehicles.ProtocolInfo;

import dmg.cells.nucleus.CellEndpoint;
import dmg.cells.nucleus.CellMessageReceiver;
import dmg.cells.nucleus.CellMessageSender;
import dmg.cells.nucleus.CellPath;

import org.dcache.auth.Subjects;
import org.dcache.cells.CellStub;
import org.dcache.util.RedirectedTransfer;
import org.dcache.util.Transfer;
import org.dcache.util.TransferRetryPolicies;
import org.dcache.util.TransferRetryPolicy;
import org.dcache.webdav.zip.ZipRequest.FileEntry;


/**
 * This class is responsible to initiating internal dCache HTTP transfers
 * to allow streaming of a ZIP file content.
 */
public class TransferEngine implements CellMessageSender, CellMessageReceiver
{
    private static final Logger LOG = LoggerFactory.getLogger(TransferEngine.class);
    private static final String PROTOCOL_INFO_NAME = "Http";
    private static final int PROTOCOL_INFO_MAJOR_VERSION = 1;
    private static final int PROTOCOL_INFO_MINOR_VERSION = 1;
    private static final int PROTOCOL_INFO_UNKNOWN_PORT = 0;
    public static final String TRANSACTION_ATTRIBUTE = "org.dcache.transaction";

    private PnfsHandler _pnfs;
    private String _ioQueue;
    private String _cell;
    private String _domain;
    private final TransferRetryPolicy _retryPolicy = TransferRetryPolicies.tryOncePolicy(3, MINUTES);

    private final Map<Integer,ZipInternalTransfer> _transfers = Maps.newConcurrentMap();

    private long _transferConfirmationTimeout = 60_000;
    private TimeUnit _transferConfirmationTimeoutUnit = MILLISECONDS;
    private long _killTimeout = 1_500;
    private TimeUnit _killTimeoutUnit = MILLISECONDS;
    private int _moverTimeout = 180_000;
    private TimeUnit _moverTimeoutUnit = MILLISECONDS;
    private CellStub _poolManagerStub;
    private CellStub _poolStub;
    private CellStub _billingStub;
    private InetAddress _internalAddress;

    public TransferEngine() throws UnknownHostException
    {
        _internalAddress = InetAddress.getLocalHost();
    }

    @Required
    public void setPnfsHandler(PnfsHandler handler)
    {
        _pnfs = handler;
    }

    @Required
    public void setPoolStub(CellStub stub)
    {
        _poolStub = stub;
    }

    @Required
    public void setPoolManagerStub(CellStub stub)
    {
        _poolManagerStub = stub;
    }

    @Required
    public void setBillingStub(CellStub stub)
    {
        _billingStub = stub;
    }

    public void setMoverTimeout(int value)
    {
        _moverTimeout = value;
    }

    public void setMoverTimeoutUnit(TimeUnit unit)
    {
        _moverTimeoutUnit = unit;
    }

    public void setKillTimeout(int value)
    {
        _killTimeout = value;
    }

    public void setKillTimeoutUnit(TimeUnit unit)
    {
        _killTimeoutUnit = unit;
    }

    public void setTransferConfirmationTimeout(int value)
    {
        _transferConfirmationTimeout = value;
    }

    public void setTransferConfirmationTimeoutUnit(TimeUnit unit)
    {
        _transferConfirmationTimeoutUnit = unit;
    }

    /**
     * Return the pool IO queue to use for WebDAV transfers.
     */
    public String getIoQueue()
    {
        return Strings.nullToEmpty(_ioQueue);
    }

    /**
     * Sets the pool IO queue to use for WebDAV transfers.
     */
    public void setIoQueue(String ioQueue)
    {
        _ioQueue = Strings.emptyToNull(ioQueue);
    }

    public void setInternalAddress(String ipString)
            throws IllegalArgumentException, UnknownHostException
    {
        if (!Strings.isNullOrEmpty(ipString)) {
            InetAddress address = InetAddresses.forString(ipString);
            if (address.isAnyLocalAddress()) {
                throw new IllegalArgumentException("Wildcard address is not a valid local address: " + address);
            }
            _internalAddress = address;
        } else {
            _internalAddress = InetAddress.getLocalHost();
        }
    }

    @Override
    public void setCellEndpoint(CellEndpoint endpoint)
    {
        _cell = endpoint.getCellInfo().getCellName();
        _domain = endpoint.getCellInfo().getDomainName();
    }

    public String getInternalAddress()
    {
        return _internalAddress.getHostAddress();
    }

    /**
     * Message handler for redirect messages from the pools.
     */
    public void messageArrived(HttpDoorUrlInfoMessage message)
    {
        RedirectedTransfer transfer = _transfers.get((int)message.getId());
        if (transfer != null) {
            transfer.redirect(message.getUrl());
        }
    }

    /**
     * Message handler for transfer completion messages from the
     * pools.
     */
    public void messageArrived(DoorTransferFinishedMessage message)
    {
        Transfer transfer = _transfers.get((int)message.getId());
        if (transfer != null) {
            transfer.finished(message);
        }
    }

    /**
     * Fall back message handler for mover creation replies. We
     * only receive these if the Transfer timed out before the
     * mover was created. Instead we kill the mover.
     */
    public void messageArrived(PoolIoFileMessage message)
    {
        if (message.getReturnCode() == 0) {
            String pool = message.getPoolName();
            _poolStub.notify(new CellPath(pool), new PoolMoverKillMessage(pool, message.getMoverId()));
        }
    }


    public void startTransfers(ZipRequest request) throws InterruptedException
    {
        request.scheduleNextStartTransfer(() -> startNextTransfer(request));
    }

    private void startNextTransfer(ZipRequest request)
    {
        try {
            FileEntry entry = request.pollFile(500, MILLISECONDS);

            if (entry == null) {
                LOG.info("Waiting for next file.");
                request.schedule(() -> startNextTransfer(request), 1, SECONDS);
                return;
            }

            if (entry == ZipRequest.FILE_SENTINEL) {
                request.noFurtherTransfers();
            } else {
                try {
                    RedirectedTransfer<String> transfer = startMover(request, entry);
                    tryOfferTransfer(request, transfer);
                } catch (CacheException e) {
                    request.addProblem("failed to start " + request.getPath()
                            + ": " + e.getMessage());
                    request.scheduleNextStartTransfer(() -> startNextTransfer(request));
                }
            }
        } catch (InterruptedException e) {
            request.cancel();
        }
    }

    private void tryOfferTransfer(ZipRequest request, RedirectedTransfer<String> transfer)
    {
        if (!request.offerTransfer(transfer)) {
            request.schedule(() -> tryOfferTransfer(request, transfer), 1, SECONDS);
            return;
        }
        request.scheduleNextStartTransfer(() -> startNextTransfer(request));
    }

    private ZipInternalTransfer startMover(ZipRequest request, FileEntry entry)
            throws CacheException, InterruptedException
    {
        String uri = null;
        ZipInternalTransfer transfer =
                new ZipInternalTransfer(_pnfs, request.getSubject(), entry.getPath());

        _transfers.put((int)transfer.getId(), transfer);

        try {
            transfer.acceptFileAttributes(entry.getAttributes(), false);
            try {
                transfer.selectPoolAndStartMover(_ioQueue, _retryPolicy);

                // REVISIT: this is a somewhat artificial problem.  In principle,
                // given a large number of source files, this could be a lot
                // more forgiving if this does not block other content from being
                // delivered.
                uri = transfer.waitForRedirect(_moverTimeout, _moverTimeoutUnit);
                if (uri == null) {
                    throw new TimeoutCacheException("internal timeout waiting for data to be ready");
                }
                transfer.setLocation(URI.create(uri));
            } finally {
                transfer.setStatus(null);
            }
            transfer.setStatus("Mover " + transfer.getPool() + "/" +
                    transfer.getMoverId() + ": ready to deliver content");
        } finally {
            if (uri == null) {
                LOG.warn("killing transfer");
                transfer.killMover(_killTimeout, _killTimeoutUnit);
                _transfers.remove((int)transfer.getId());
            }
        }

        return transfer;
    }

    /**
     * Specialisation of the Transfer class for HTTP transfers.
     */
    public class ZipInternalTransfer extends RedirectedTransfer<String>
    {
        private final InetSocketAddress _clientAddressForPool;

        private URI _location;

        public ZipInternalTransfer(PnfsHandler pnfs, Subject subject, FsPath path)
        {
            super(pnfs, subject, path);

            setCellName(_cell);
            setDomainName(_domain);
            setPoolManagerStub(_poolManagerStub);
            setPoolStub(_poolStub);
            setBillingStub(_billingStub);
            setClientAddress(new InetSocketAddress(Subjects.getOrigin(subject).getAddress(),
                    PROTOCOL_INFO_UNKNOWN_PORT));

            _clientAddressForPool = new InetSocketAddress(_internalAddress, 0);
        }



        protected ProtocolInfo createProtocolInfo(InetSocketAddress address)
        {
            HttpProtocolInfo protocolInfo =
                new HttpProtocolInfo(
                        PROTOCOL_INFO_NAME,
                        PROTOCOL_INFO_MAJOR_VERSION,
                        PROTOCOL_INFO_MINOR_VERSION,
                        address,
                        _cell, _domain,
                        _path.toString(),
                        _location,
                        HttpProtocolInfo.Disposition.INLINE);
            protocolInfo.setSessionId((int)getId());
            return protocolInfo;
        }

        @Override
        protected ProtocolInfo getProtocolInfoForPoolManager()
        {
            return createProtocolInfo(getClientAddress());
        }

        @Override
        protected ProtocolInfo getProtocolInfoForPool()
        {
            return createProtocolInfo(_clientAddressForPool);
        }

        public void setLocation(URI location)
        {
            _location = location;
        }

        public void relayData(OutputStream outputStream)
            throws IOException, CacheException, InterruptedException
        {
            setStatus("Mover " + getPool() + "/" + getMoverId() +
                      ": Opening data connection");
            try {
                HttpURLConnection connection;
                try {
                    URL url = new URL(getRedirect());
                    connection = (HttpURLConnection) url.openConnection();
                } catch (IOException e) {
                    throw new CacheException("Failed to connect: " + e.toString(), e);
                }

                try {
                    connection.setRequestProperty("Connection", "Close");
                    try {
                        connection.connect();
                    } catch (SocketTimeoutException e) {
                        throw new TimeoutCacheException("Server is busy (internal timeout)");
                    } catch (IOException e) {
                        throw new CacheException("Failed to connect: " + e.toString(), e);
                    }
                    try (InputStream inputStream = connection
                            .getInputStream()) {
                        setStatus("Mover " + getPool() + "/" + getMoverId() +
                                ": Sending data");
                        ByteStreams.copy(inputStream, outputStream);
                        outputStream.flush();
                    }

                } finally {
                    connection.disconnect();
                }

                if (!waitForMover(_transferConfirmationTimeout, _transferConfirmationTimeoutUnit)) {
                    throw new CacheException("Missing transfer confirmation from pool");
                }
            } catch (SocketTimeoutException e) {
                throw new TimeoutCacheException("Server is busy (internal timeout)");
            } finally {
                setStatus(null);
            }
        }
    }
}
