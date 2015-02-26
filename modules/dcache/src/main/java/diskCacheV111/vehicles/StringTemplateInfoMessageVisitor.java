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
package diskCacheV111.vehicles;

import org.stringtemplate.v4.ST;

import java.util.Date;

import org.dcache.auth.SubjectWrapper;

public class StringTemplateInfoMessageVisitor implements InfoMessageVisitor
{
    private final ST template;

    public StringTemplateInfoMessageVisitor(ST template)
    {
        this.template = template;
    }

    protected void acceptInfoMessage(InfoMessage message)
    {
        template.add("date", new Date(message.getTimestamp()));
        template.add("queuingTime", message.getTimeQueued());
        template.add("message", message.getMessage());
        template.add("type", message.getMessageType());
        template.add("cellName", message.getCellName());
        template.add("cellType", message.getCellType());
        template.add("rc", message.getResultCode());
        template.add("subject", new SubjectWrapper(message.getSubject()));
        template.add("session", message.getTransaction());
    }

    protected void acceptFileInfoMessage(PnfsFileInfoMessage message)
    {
        acceptInfoMessage(message);
        template.add("pnfsid", message.getPnfsId());
        template.add("path", message.getPath());
        template.add("filesize", message.getFileSize());
        template.add("storage", message.getStorageInfo());
    }

    @Override
    public void visit(DoorRequestInfoMessage message)
    {
        acceptFileInfoMessage(message);
        template.add("transactionTime", message.getTransactionDuration());
        template.add("uid", message.getUid());
        template.add("gid", message.getGid());
        template.add("owner", message.getOwner());
        template.add("client", message.getClient());
    }

    @Override
    public void visit(MoverInfoMessage message)
    {
        acceptFileInfoMessage(message);
        template.add("transferred", message.getDataTransferred());
        template.add("connectionTime", message.getConnectionTime());
        template.add("waitingTime", message.getTimeWaiting());
        template.add("activeTime", message.getTimePending() + message.getTimeProcessing());
        template.add("pendingTime", message.getTimePending());
        template.add("processingTime", message.getTimeProcessing());

        template.add("netBandwidth", describeBandwidth(message.getDataTransferred(),
                message.getTimePending()));
        template.add("diskBandwidth", describeBandwidth(message.getDataTransferred(),
                message.getTimeProcessing()));
        template.add("closedTime", message.getTimeClosed());
        template.add("created", message.isFileCreated());
        template.add("protocol", message.getProtocolInfo());
        template.add("initiator", message.getInitiator());
        template.add("p2p", message.isP2P());
    }

    public String describeBandwidth(long size, long duration)
    {
        if (duration == 0L) {
            return "";
        }

        double bandwidth = 1000.0 * (double)size / duration;

        if (bandwidth < 2048.0) {
            return String.format("%.0fB/s", bandwidth);
        } else if (bandwidth < 2097152.0) {
            return String.format("%.0fkiB/s", bandwidth/1024.0);
        } else if (bandwidth < 2.1474836e9) {
            return String.format("%.0fMiB/s", bandwidth/1048576.0);
        } else if (bandwidth < 2.1990232e12) {
            return String.format("%.0fGiB/s", bandwidth/1.0737418e9);
        } else {
            return String.format("%.0fTiB/s", bandwidth/1.0995116e12);
        }
    }

    @Override
    public void visit(PoolHitInfoMessage message)
    {
        acceptFileInfoMessage(message);
        template.add("protocol", message.getProtocolInfo());
        template.add("cached", message.getFileCached());
    }

    @Override
    public void visit(RemoveFileInfoMessage message)
    {
        acceptFileInfoMessage(message);
    }

    @Override
    public void visit(StorageInfoMessage message)
    {
        acceptFileInfoMessage(message);
        template.add("transferTime", message.getTransferTime());
    }

    @Override
    public void visit(WarningPnfsFileInfoMessage message)
    {
        acceptFileInfoMessage(message);
    }
}
