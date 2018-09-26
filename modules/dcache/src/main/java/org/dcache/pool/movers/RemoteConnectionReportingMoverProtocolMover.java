/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.dcache.pool.movers;

import java.net.InetSocketAddress;
import java.util.Collection;
import java.util.function.Supplier;

import diskCacheV111.vehicles.PoolIoFileMessage;

import dmg.cells.nucleus.CellPath;

import org.dcache.pool.classic.ChecksumModule;
import org.dcache.pool.classic.TransferService;
import org.dcache.pool.repository.ReplicaDescriptor;

/**
 * A specialised version of MoverProtocolMover for a MoverProtocol that
 * implements RemoteConnectionReporting.
 */
public class RemoteConnectionReportingMoverProtocolMover extends MoverProtocolMover
        implements RemoteConnectionReporting
{
    private final Supplier<Collection<InetSocketAddress>> _connectionSupplier;

    public RemoteConnectionReportingMoverProtocolMover(ReplicaDescriptor handle, PoolIoFileMessage message, CellPath pathToDoor,
                    TransferService<MoverProtocolMover> transferService,
                    MoverProtocol moverProtocol, ChecksumModule checksumModule)
    {
        super(handle, message, pathToDoor, transferService, moverProtocol, checksumModule);
        _connectionSupplier = ((RemoteConnectionReporting)moverProtocol)::getConnections;
    }

    @Override
    public Collection<InetSocketAddress> getConnections()
    {
        return _connectionSupplier.get();
    }
}
