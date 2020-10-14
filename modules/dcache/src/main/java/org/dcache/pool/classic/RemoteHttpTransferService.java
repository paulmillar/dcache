/* dCache - http://www.dcache.org/
 *
 * Copyright (C) 2015-2020 Deutsches Elektronen-Synchrotron
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
package org.dcache.pool.classic;

import org.dcache.util.RemoteTransferMatcher;

import eu.emi.security.authn.x509.OCSPParametes;
import eu.emi.security.authn.x509.ProxySupport;
import eu.emi.security.authn.x509.RevocationParameters;
import eu.emi.security.authn.x509.X509CertChainValidator;
import eu.emi.security.authn.x509.impl.OpensslCertChainValidator;
import eu.emi.security.authn.x509.impl.ValidatorParams;
import org.springframework.beans.factory.annotation.Required;

import java.net.URI;
import java.io.IOException;

import diskCacheV111.util.CacheException;
import diskCacheV111.vehicles.ProtocolInfo;
import diskCacheV111.vehicles.RemoteHttpDataTransferProtocolInfo;
import diskCacheV111.vehicles.RemoteHttpsDataTransferProtocolInfo;

import org.dcache.pool.movers.MoverProtocol;
import org.dcache.pool.movers.RemoteHttpDataTransferProtocol;
import org.dcache.pool.movers.RemoteHttpsDataTransferProtocol;

import static java.util.Objects.requireNonNull;
import static org.dcache.util.Files.checkDirectory;

public class RemoteHttpTransferService extends SecureRemoteTransferService

{
    private OpensslCertChainValidator validator;
    private RemoteTransferMatcher debuggedTransfers;

    @Required
    public void setDebuggedTransferMatcher(RemoteTransferMatcher matcher)
    {
        debuggedTransfers = requireNonNull(matcher);
    }

    @Override
    protected MoverProtocol createMoverProtocol(String localPath, ProtocolInfo rawInfo) throws Exception
    {
        RemoteHttpDataTransferProtocol moverProtocol;

        URI uri;
        boolean isDebugged;
        if (rawInfo instanceof RemoteHttpsDataTransferProtocolInfo) {
            RemoteHttpsDataTransferProtocolInfo info = (RemoteHttpsDataTransferProtocolInfo)rawInfo;
            uri = info.getUri();
            isDebugged = info.isDebugged();
            moverProtocol = new RemoteHttpsDataTransferProtocol(getCellEndpoint(), getValidator(), secureRandom);
        } else if (rawInfo instanceof RemoteHttpDataTransferProtocolInfo) {
            RemoteHttpsDataTransferProtocolInfo info = (RemoteHttpsDataTransferProtocolInfo)rawInfo;
            uri = info.getUri();
            isDebugged = info.isDebugged();
            moverProtocol = new RemoteHttpDataTransferProtocol(getCellEndpoint());
        } else {
            throw new CacheException(CacheException.CANNOT_CREATE_MOVER,
                    "Could not create third-party HTTP mover for " + rawInfo);
        }

        if (isDebugged || debuggedTransfers.matches(localPath, uri)) {
            moverProtocol.enableTransferLogging();
        }

        return moverProtocol;
    }

    @Override
    public void shutdown()
    {
        super.shutdown();
        synchronized (this) {
            if (validator != null) {
                validator.dispose();
            }
        }
    }

    private synchronized X509CertChainValidator getValidator() throws IOException
    {
        if (validator == null) {
            checkDirectory(getCertificateAuthorityPath());
            OCSPParametes ocspParameters = new OCSPParametes(getOcspCheckingMode());
            ValidatorParams validatorParams =
                    new ValidatorParams(new RevocationParameters(getCrlCheckingMode(), ocspParameters), ProxySupport.ALLOW);
            long updateInterval = getCertificateAuthorityUpdateIntervalUnit().toMillis(getCertificateAuthorityUpdateInterval());
            validator = new OpensslCertChainValidator(getCertificateAuthorityPath(), true, getNamespaceMode(), updateInterval, validatorParams,
                                                      false);
        }
        return validator;
    }
}
