package org.dcache.xrootd.door;

import javax.security.auth.Subject;

import java.net.InetSocketAddress;
import java.util.UUID;

import diskCacheV111.util.FsPath;
import diskCacheV111.util.PnfsHandler;
import diskCacheV111.vehicles.ProtocolInfo;

import dmg.cells.nucleus.CellPath;

import org.dcache.util.RedirectedTransfer;
import org.dcache.vehicles.XrootdProtocolInfo;

public class XrootdTransfer extends RedirectedTransfer<InetSocketAddress>
{
    private UUID _uuid;
    private InetSocketAddress _doorAddress;
    private int _fileHandle;

    public XrootdTransfer(PnfsHandler pnfs, Subject subject, FsPath path) {
        super(pnfs, subject, path);
    }

    public synchronized void setFileHandle(int fileHandle) {
        _fileHandle = fileHandle;
    }

    public synchronized int getFileHandle() {
        return _fileHandle;
    }

    public synchronized void setUUID(UUID uuid) {
        _uuid = uuid;
    }

    public synchronized void setDoorAddress(InetSocketAddress doorAddress) {
        _doorAddress = doorAddress;
    }

    protected synchronized ProtocolInfo createProtocolInfo() {
        InetSocketAddress client = getClientAddress();
        XrootdProtocolInfo protocolInfo =
            new XrootdProtocolInfo(XrootdDoor.XROOTD_PROTOCOL_STRING,
                                   XrootdDoor.XROOTD_PROTOCOL_MAJOR_VERSION,
                                   XrootdDoor.XROOTD_PROTOCOL_MINOR_VERSION,
                                   client,
                                   new CellPath(getCellName(), getDomainName()),
                                   getPnfsId(),
                                   _fileHandle,
                                   _uuid,
                                   _doorAddress);
        protocolInfo.setPath(_path.toString());
        return protocolInfo;
    }

    @Override
    protected ProtocolInfo getProtocolInfoForPoolManager() {
        return createProtocolInfo();
    }

    @Override
    protected ProtocolInfo getProtocolInfoForPool() {
        return createProtocolInfo();
    }
}
