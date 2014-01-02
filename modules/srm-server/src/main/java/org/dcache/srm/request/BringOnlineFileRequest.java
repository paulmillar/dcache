/*
COPYRIGHT STATUS:
  Dec 1st 2001, Fermi National Accelerator Laboratory (FNAL) documents and
  software are sponsored by the U.S. Department of Energy under Contract No.
  DE-AC02-76CH03000. Therefore, the U.S. Government retains a  world-wide
  non-exclusive, royalty-free license to publish or reproduce these documents
  and software for U.S. Government purposes.  All documents and software
  available from this server are protected under the U.S. and Foreign
  Copyright Laws, and FNAL reserves all rights.


 Distribution of the software available from this server is free of
 charge subject to the user following the terms of the Fermitools
 Software Legal Information.

 Redistribution and/or modification of the software shall be accompanied
 by the Fermitools Software Legal Information  (including the copyright
 notice).

 The user is asked to feed back problems, benefits, and/or suggestions
 about the software to the Fermilab Software Providers.


 Neither the name of Fermilab, the  URA, nor the names of the contributors
 may be used to endorse or promote products derived from this software
 without specific prior written permission.



  DISCLAIMER OF LIABILITY (BSD):

  THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
  "AS IS" AND ANY EXPRESS OR IMPLIED  WARRANTIES, INCLUDING, BUT NOT
  LIMITED TO, THE IMPLIED  WARRANTIES OF MERCHANTABILITY AND FITNESS
  FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL FERMILAB,
  OR THE URA, OR THE U.S. DEPARTMENT of ENERGY, OR CONTRIBUTORS BE LIABLE
  FOR  ANY  DIRECT, INDIRECT,  INCIDENTAL, SPECIAL, EXEMPLARY, OR
  CONSEQUENTIAL DAMAGES  (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT
  OF SUBSTITUTE  GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
  BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY  OF
  LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
  NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT  OF THE USE OF THIS
  SOFTWARE, EVEN IF ADVISED OF THE  POSSIBILITY OF SUCH DAMAGE.


  Liabilities of the Government:

  This software is provided by URA, independent from its Prime Contract
  with the U.S. Department of Energy. URA is acting independently from
  the Government and in its own private capacity and is not acting on
  behalf of the U.S. Government, nor as its contractor nor its agent.
  Correspondingly, it is understood and agreed that the U.S. Government
  has no connection to this software and in no manner whatsoever shall
  be liable for nor assume any responsibility or obligation for any claim,
  cost, or damages arising out of or resulting from the use of the software
  available from this server.


  Export Control:

  All documents and software available from this server are subject to U.S.
  export control laws.  Anyone downloading information from this server is
  obligated to secure any necessary Government licenses before exporting
  documents or software obtained from this server.
 */

/*
 * FileRequest.java
 *
 * Created on July 5, 2002, 12:04 PM
 */

package org.dcache.srm.request;

import com.google.common.base.Joiner;
import org.apache.axis.types.UnsignedLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;

import java.net.URI;

import diskCacheV111.srm.RequestFileStatus;

import org.dcache.srm.AbstractStorageElement;
import org.dcache.srm.FileMetaData;
import org.dcache.srm.PinCallbacks;
import org.dcache.srm.SRM;
import org.dcache.srm.SRMAuthorizationException;
import org.dcache.srm.SRMException;
import org.dcache.srm.SRMInternalErrorException;
import org.dcache.srm.SRMInvalidPathException;
import org.dcache.srm.SRMInvalidRequestException;
import org.dcache.srm.SRMUser;
import org.dcache.srm.UnpinCallbacks;
import org.dcache.srm.scheduler.FatalJobFailure;
import org.dcache.srm.scheduler.IllegalStateTransition;
import org.dcache.srm.scheduler.NonFatalJobFailure;
import org.dcache.srm.scheduler.Scheduler;
import org.dcache.srm.scheduler.State;
import org.dcache.srm.v2_2.TBringOnlineRequestFileStatus;
import org.dcache.srm.v2_2.TReturnStatus;
import org.dcache.srm.v2_2.TSURLReturnStatus;
import org.dcache.srm.v2_2.TStatusCode;

/**
 *
 * @author  timur
 * @version
 */
public final class BringOnlineFileRequest extends FileRequest<BringOnlineRequest> {
    private final static Logger logger =
            LoggerFactory.getLogger(BringOnlineFileRequest.class);

    // the globus url class created from surl_string
    private URI surl;
    private String pinId;
    private String fileId;
    private transient FileMetaData fileMetaData;

    /** Creates new FileRequest */
    public BringOnlineFileRequest(long requestId,
                                  Long requestCredentalId,
                                  URI surl,
                                  long lifetime,
                                  int maxNumberOfRetries)
    {
        super(requestId,
                requestCredentalId,
                lifetime,
                maxNumberOfRetries);
        logger.debug("BringOnlineFileRequest, requestId="+requestId+" fileRequestId = "+getId());
        this.surl = surl;
    }

    /**
     * restore constructore, used for restoring the existing
     * file request from the database
     */

    public BringOnlineFileRequest(
    long id,
    Long nextJobId,
    long creationTime,
    long lifetime,
    int stateId,
    String errorMessage,
    String scheduelerId,
    long schedulerTimeStamp,
    int numberOfRetries,
    int maxNumberOfRetries,
    long lastStateTransitionTime,
    JobHistory[] jobHistoryArray,
    long requestId,
    Long  requestCredentalId,
    String statusCodeString,
    String SURL,
    String fileId,
    String pinId
    ) {
        super(id,
        nextJobId,
        creationTime,
        lifetime,
        stateId,
        errorMessage,
        scheduelerId,
        schedulerTimeStamp,
        numberOfRetries,
        maxNumberOfRetries,
        lastStateTransitionTime,
        jobHistoryArray,
        requestId,
        requestCredentalId,
        statusCodeString);

        this.surl = URI.create(SURL);

        if(fileId != null && (!fileId.equalsIgnoreCase("null"))) {
            this.fileId = fileId;
        }

        if(pinId != null && (!pinId.equalsIgnoreCase("null"))) {
            this.pinId = pinId;
        }
    }

    public void setPinId(String pinId) {
        wlock();
        try {
            this.pinId = pinId;
        } finally {
            wunlock();
        }
    }

    public String getPinId() {
        rlock();
        try {
            return pinId;
        } finally {
            runlock();
        }
    }

    public boolean isPinned() {
        rlock();
        try {
            return getPinId() != null;
        } finally {
            runlock();
        }

    }

    public URI getSurl() {
        rlock();
        try {
            return surl;
        } finally {
            runlock();
        }
    }


    public String getSurlString() {
        return getSurl().toASCIIString();
    }

    public String getFileId() {
        rlock();
        try {
            return fileId;
        } finally {
            runlock();
        }
    }


    @Override
    public RequestFileStatus getRequestFileStatus(){
        RequestFileStatus rfs;
        if(fileMetaData != null) {
            rfs = new RequestFileStatus(fileMetaData);
        }
        else {
            rfs = new RequestFileStatus();
        }

        rfs.fileId = (int) getId();
        rfs.SURL = getSurlString();


        if(this.isPinned()) {
            rfs.isPinned = true;
            rfs.isCached = true;
        }

        State state = getState();
        if(state == State.DONE) {
            rfs.state = "Done";
        }
        else if(state == State.READY) {
            rfs.state = "Ready";
        }
        else if(state == State.TRANSFERRING) {
            rfs.state = "Running";
        }
        else if(state == State.FAILED
        || state == State.CANCELED ) {
            rfs.state = "Failed";
        }
        else {
            rfs.state = "Pending";
        }

        //logger.debug(" returning requestFileStatus for "+rfs.toString());
        return rfs;
    }

    public TBringOnlineRequestFileStatus getTGetRequestFileStatus()
            throws SRMInvalidRequestException
    {
        TBringOnlineRequestFileStatus fileStatus = new TBringOnlineRequestFileStatus();
        if(fileMetaData != null) {
            fileStatus.setFileSize(new UnsignedLong(fileMetaData.size));
        }

        try {
             fileStatus.setSourceSURL(new org.apache.axis.types.URI(getSurlString()));
        } catch (org.apache.axis.types.URI.MalformedURIException e) {
            logger.error(e.toString());
            throw new SRMInvalidRequestException("wrong surl format");
        }

        if(this.isPinned()) {

            fileStatus.setRemainingPinTime((int)(getRemainingLifetime()/1000));
        }
        fileStatus.setEstimatedWaitTime(getContainerRequest().getRetryDeltaTime());
        fileStatus.setStatus(getReturnStatus());

        return fileStatus;
    }

    public TSURLReturnStatus  getTSURLReturnStatus() throws SRMInvalidRequestException
    {
        try {
            return new TSURLReturnStatus(new org.apache.axis.types.URI(getSurlString()), getReturnStatus());
        } catch (org.apache.axis.types.URI.MalformedURIException e) {
            logger.error(e.toString());
            throw new SRMInvalidRequestException("wrong surl format");
        }
    }

    @Override
    public void toString(StringBuilder sb, boolean longformat) {
        sb.append(" BringOnlineFileRequest ");
        sb.append(" id:").append(getId());
        sb.append(" priority:").append(getPriority());
        sb.append(" creator priority:");
        try {
            sb.append(getUser().getPriority());
        } catch (SRMInvalidRequestException ire) {
            sb.append("Unknown");
        }
        sb.append(" state:").append(getState());
        if(longformat) {
            sb.append('\n').append("   SURL: ").append(getSurl());
            sb.append('\n').append("   pinned: ").append(isPinned());
            String thePinId = getPinId();
            if(thePinId != null) {
                sb.append('\n').append("   pinid: ").append(thePinId);
            }
            sb.append('\n').append("   status code:").append(getStatusCode());
            sb.append('\n').append("   error message:").append(getErrorMessage());
            sb.append('\n').append("   History of State Transitions: \n");
            sb.append(getHistory());
        }
    }

    @Override
    public final void run() throws NonFatalJobFailure, FatalJobFailure {
        logger.debug("run()");
        try {
            if(getPinId() == null) {
                // [ SRM 2.2, 5.4.3] SRM_FILE_BUSY: client requests for a file which there is an
                // active srmPrepareToPut (no srmPutDone is yet called) request for.
                if (SRM.getSRM().isFileBusy(surl)) {
                    setStateAndStatusCode(State.FAILED, "The requested SURL is locked by an upload.",
                            TStatusCode.SRM_FILE_BUSY);
                }

                // do not check explicitely if we can read the file
                // this is done by pnfs manager when we call askFileId()

                logger.debug("pinId is null, asking to pin ");
                pinFile();
                if(getPinId() == null) {
                    setState(State.ASYNCWAIT, "Pinning file.");
                    logger.debug("BringOnlineFileRequest: waiting async notification about pinId...");
                    return;
                }
            }
        } catch(SRMException | DataAccessException | IllegalStateTransition e) {
            // FIXME some SRMExceptions are permanent failures while others
            // are temporary.  Code currently doesn't distinguish, so will
            // always retry internally even if problem isn't transitory.
            throw new NonFatalJobFailure(e.getMessage());
        }
        logger.info("PinId is "+getPinId()+" returning, scheduler should change" +
            " state to \"Ready\"");
    }

    public void askFileId() throws NonFatalJobFailure, FatalJobFailure {
        try {
        }
        catch(Exception e) {
            logger.error(e.toString());
            throw new NonFatalJobFailure(e.toString());
        }
    }

    public void pinFile()
        throws NonFatalJobFailure, FatalJobFailure, SRMException
    {
        BringOnlineRequest request = getContainerRequest();
        String[] protocols = request.getProtocols();
        if (protocols != null && !isProtocolSupported(protocols)) {
            throw new FatalJobFailure("Transfer protocols not supported: " +
                                      Joiner.on(", ").join(protocols));
        }

        long desiredPinLifetime = request.getDesiredOnlineLifetimeInSeconds();
        if (desiredPinLifetime != -1) {
            desiredPinLifetime *= 1000;  // convert to millis
        }

        URI surl = getSurl();
        logger.info("Pinning {}", surl);
        getStorage().pinFile(getUser(),
                             surl,
                             getContainerRequest().getClient_host(),
                             desiredPinLifetime,
                             String.valueOf(getRequestId()),
                             new ThePinCallbacks(getId()));
    }

    @Override
    protected void stateChanged(State oldState) {
        State state = getState();
        logger.debug("State changed from "+oldState+" to "+getState());
        if(state == State.READY) {
            try {
                getContainerRequest().resetRetryDeltaTime();
            }
            catch (SRMInvalidRequestException ire) {
                logger.error(ire.toString());
            }
        }
        if(state == State.CANCELED || state == State.FAILED ) {
            if(getFileId() != null && getPinId() != null) {
                UnpinCallbacks callbacks = new TheUnpinCallbacks(this.getId());
                logger.info("state changed to final state, unpinning fileId= "+ getFileId()+" pinId = "+getPinId());
                try {
                    getStorage().unPinFile(getUser(),getFileId(),callbacks, getPinId());
                }
                catch (SRMInvalidRequestException ire) {
                    logger.error(ire.toString());
                    return;
                }
            }
        }
        super.stateChanged(oldState);
    }

    @Override
    public boolean isTouchingSurl(URI surl)
    {
        return surl.equals(getSurl());
    }

    public TReturnStatus release(SRMUser user) throws SRMInternalErrorException
    {
        String fileId;
        String pinId;

        wlock();
        try {
            State state = getState();
            switch (state) {
            case DONE:
                fileId = getFileId();
                pinId = getPinId();
                if (fileId == null || pinId == null) {
                    return new TReturnStatus(TStatusCode.SRM_FAILURE, "SURL is not pinned");
                }
                // Unpinning is done below, outside the lock
                break;
            case CANCELED:
                return new TReturnStatus(TStatusCode.SRM_ABORTED, "SURL has been aborted and cannot be released");
            case FAILED:
                return new TReturnStatus(TStatusCode.SRM_FAILURE, "Pinning failed");
            default:
                logger.warn("Canceled by the srmReleaseFiles");
                setState(State.CANCELED, "Aborted by srmReleaseFile request.");
                return new TReturnStatus(TStatusCode.SRM_ABORTED, "SURL is not yet pinned, pinning aborted");
            }
        } catch (IllegalStateTransition e) {
            return new TReturnStatus(TStatusCode.SRM_FAILURE, e.getMessage());
        } finally {
            wunlock();
        }

        TheUnpinCallbacks callbacks = new TheUnpinCallbacks(getId());
        logger.debug("srmReleaseFile, unpinning fileId={} pinId={}", fileId, pinId);
        getStorage().unPinFile(user, fileId, callbacks, pinId);
        try {
            callbacks.waitCompletion(60000);
            if (!callbacks.isDone()) {
                throw new SRMInternalErrorException("Operation timed out");
            }
            if (!callbacks.success) {
                return new TReturnStatus(TStatusCode.SRM_FAILURE, "Failed to unpin SURL: " + callbacks.getError());
            }
            setPinId(null);
            this.saveJob();
            return new TReturnStatus(TStatusCode.SRM_SUCCESS, null);
        } catch (InterruptedException e) {
            throw new SRMInternalErrorException("Operation interrupted");
        }
    }

    @Override
    public TReturnStatus getReturnStatus()
    {
        String description = getLastJobChange().getDescription();
        TStatusCode statusCode = getStatusCode();
        if (statusCode != null) {
            if (statusCode == TStatusCode.SRM_FILE_PINNED ||
                    statusCode == TStatusCode.SRM_SUCCESS ||
                    statusCode == TStatusCode.SRM_RELEASED) {
                description = null;
            }
            return new TReturnStatus(statusCode, description);
        }

        switch (getState()) {
        case PENDING:
        case RESTORED:
        case RQUEUED:
            return new TReturnStatus(TStatusCode.SRM_REQUEST_QUEUED, description);
        case READY:
        case TRANSFERRING:
            return new TReturnStatus(TStatusCode.SRM_FILE_PINNED, null);
        case DONE:
            if (getPinId() != null) {
                return new TReturnStatus(TStatusCode.SRM_SUCCESS, null);
            }  else {
                return new TReturnStatus(TStatusCode.SRM_RELEASED, null);
            }
        case CANCELED:
            return new TReturnStatus(TStatusCode.SRM_ABORTED, description);
        case FAILED:
            return new TReturnStatus(TStatusCode.SRM_FAILURE, description);
        default:
            return new TReturnStatus(TStatusCode.SRM_REQUEST_INPROGRESS, description);
        }
    }

    /**
     *
     * @param newLifetime  new lifetime in millis
     *  -1 stands for infinite lifetime
     * @return int lifetime left in millis
     *    -1 stands for infinite lifetime
     */
    @Override
    public long extendLifetime(long newLifetime) throws SRMException {
        long remainingLifetime = getRemainingLifetime();
        if(remainingLifetime >= newLifetime) {
            return remainingLifetime;
        }
        long requestLifetime = getContainerRequest().extendLifetimeMillis(newLifetime);
        if(requestLifetime <newLifetime) {
            newLifetime = requestLifetime;
        }
        if(remainingLifetime >= newLifetime) {
            return remainingLifetime;
        }

        newLifetime = extendLifetimeMillis(newLifetime);
        if(remainingLifetime >= newLifetime) {
            return remainingLifetime;
        }
        if(getPinId() == null) {
            return newLifetime;
        }
        SRMUser user = getUser();
        getStorage().extendPinLifetime(user,getFileId(), getPinId(),newLifetime);
        return newLifetime;
    }

    /**
     * @param fileId the fileId to set
     */
    private void setFileId(String fileId) {
        wlock();
        try {
            this.fileId = fileId;
        } finally {
            wunlock();
        }
    }

    private  static class ThePinCallbacks implements PinCallbacks {

        final long fileRequestJobId;

        public ThePinCallbacks(long fileRequestJobId) {
            this.fileRequestJobId = fileRequestJobId;
        }

        public BringOnlineFileRequest getBringOnlineFileRequest()
            throws SRMInvalidRequestException
        {
            return Job.getJob(fileRequestJobId, BringOnlineFileRequest.class);
        }

        @Override
        public void FileNotFound(String reason) {
            try {
                BringOnlineFileRequest fr = getBringOnlineFileRequest();
                try {
                    fr.setStateAndStatusCode(
                            State.FAILED,
                            reason,
                            TStatusCode.SRM_INVALID_PATH);
                } catch(IllegalStateTransition ist) {
                    if (!ist.getFromState().isFinal()) {
                        logger.error(ist.getMessage());
                    }
                }
            } catch (SRMInvalidRequestException e) {
                logger.warn(e.getMessage());
            }
        }

        @Override
        public void Unavailable(String reason)
        {
            try {
                BringOnlineFileRequest fr = getBringOnlineFileRequest();
                try {
                    fr.setStateAndStatusCode(
                            State.FAILED,
                            reason,
                            TStatusCode.SRM_FILE_UNAVAILABLE);
                }
                catch(IllegalStateTransition ist) {
                    if (!ist.getFromState().isFinal()) {
                        logger.error(ist.getMessage());
                    }
                }
            } catch (SRMInvalidRequestException e) {
                logger.warn(e.getMessage());
            }
        }

        @Override
        public void AuthorizationError( String error) {
            try {
                BringOnlineFileRequest fr = getBringOnlineFileRequest();
                try {
                    fr.setStateAndStatusCode(State.FAILED, error, TStatusCode.SRM_AUTHORIZATION_FAILURE);
                } catch(IllegalStateTransition ist) {
                    if (!ist.getFromState().isFinal()) {
                        logger.error(ist.getMessage());
                    }
                }
            } catch (SRMInvalidRequestException e) {
                logger.warn(e.getMessage());
            }
        }

        @Override
        public void Timeout() {
            try {
                BringOnlineFileRequest fr = getBringOnlineFileRequest();
                if (!fr.getState().isFinal()) {
                    fr.pinFile();
                }
            } catch (SRMInvalidRequestException e) {
                logger.warn(e.getMessage());
            } catch (NonFatalJobFailure | FatalJobFailure | SRMException e) {
                logger.error(e.getMessage());
            }
        }

        @Override
        public void Pinned(FileMetaData fileMetaData, String pinId) {
            try {
                logger.debug("File pinned (pinId={}).", pinId);
                BringOnlineFileRequest fr = getBringOnlineFileRequest();
                fr.wlock();
                try {
                    State state = fr.getState();
                    if (state == State.ASYNCWAIT || state == State.RUNNING) {
                        fr.setFileId(fileMetaData.fileId);
                        fr.fileMetaData = fileMetaData;
                        fr.setPinId(pinId);
                        fr.setState(State.DONE, "File is pinned.");
                    }
                } finally {
                    fr.wunlock();
                }
            } catch (SRMInvalidRequestException e) {
                logger.warn(e.getMessage());
            } catch (IllegalStateTransition e) {
                logger.error(e.getMessage());
            }
        }

        @Override
        public void PinningFailed(String reason) {
            try {
                BringOnlineFileRequest fr = getBringOnlineFileRequest();
                try {
                    fr.setState(State.FAILED,reason);
                } catch(IllegalStateTransition ist) {
                    if (!ist.getFromState().isFinal()) {
                        logger.error(ist.getMessage());
                    }
                }
            } catch (SRMInvalidRequestException e) {
                logger.warn(e.getMessage());
            }
        }

    }

    private static class TheUnpinCallbacks implements UnpinCallbacks {

        Long fileRequestJobId;

        public TheUnpinCallbacks(Long fileRequestJobId) {
            this.fileRequestJobId = fileRequestJobId;
        }

        public BringOnlineFileRequest getBringOnlineFileRequest()
                throws SRMInvalidRequestException {
            if(fileRequestJobId != null) {
                return Job.getJob(fileRequestJobId, BringOnlineFileRequest.class);
            }
            return null;
        }

        @Override
        public void Error( String error) {
            try {
                BringOnlineFileRequest fr = getBringOnlineFileRequest();
                this.error = "Unpinning failed: "+ error;
                logger.warn(this.error);
                success = false;
            } catch (SRMInvalidRequestException e) {
                logger.warn(e.getMessage());
            } finally {
                done();
            }
        }

        @Override
        public void Exception( Exception e) {
            try {
                BringOnlineFileRequest fr = getBringOnlineFileRequest();
                logger.error("Unpinning failed.", e);
                this.error = "Unpinning failed: " + e.toString();
                success = false;
            } catch (SRMInvalidRequestException e1) {
                logger.warn(e1.getMessage());
            } finally {
                done();
            }
        }




        @Override
        public void Timeout() {
            try {
                BringOnlineFileRequest fr = getBringOnlineFileRequest();
                this.error = "Unpinning timed out.";
                logger.error(this.error);
                success = false;
            } catch (SRMInvalidRequestException e) {
                logger.error(e.getMessage());
            } finally {
                done();
            }
        }

        @Override
        public void Unpinned(String pinId) {
            try {
                BringOnlineFileRequest fr = getBringOnlineFileRequest();
                if(fr != null) {
                    logger.debug("File unpinned (pinId={}).", pinId);
                    State state = fr.getState();
                   if(state == State.ASYNCWAIT) {
                        fr.setPinId(pinId);
                        Scheduler scheduler = Scheduler.getScheduler(fr.getSchedulerId());
                       scheduler.schedule(fr);
                    }
                }
                success = true;
            } catch (SRMInvalidRequestException e) {
                logger.warn(e.getMessage());
            } catch (InterruptedException e) {
                logger.error(e.toString());
            } catch (IllegalStateTransition e) {
                logger.error(e.getMessage());
            } finally {
                done();
            }
        }

        @Override
        public void UnpinningFailed(String reason) {
            try {
                BringOnlineFileRequest fr = getBringOnlineFileRequest();
                this.error = "Unpinning failed: "+ reason;
                logger.error(this.error);
                success = false;
            } catch (SRMInvalidRequestException e) {
                logger.error(e.getMessage());
            } finally {
                done();
            }
        }

        private boolean done;
        private boolean success  = true;
        private String error;

        public synchronized boolean  isSuccess() {
            return done && success;
        }
        public  boolean waitCompletion(long timeout) throws InterruptedException {
           long starttime = System.currentTimeMillis();
            while(true) {
                synchronized(this) {
                    wait(1000);
                    if(done) {
                        return success;
                    }
                    else
                    {
                        if((System.currentTimeMillis() - starttime)>timeout) {
                            error = "Unpinning timed out.";
                            return false;
                        }
                    }
                }
            }
        }

        public  synchronized void done() {
            done = true;
            notifyAll();
        }

        public String getError() {
            return error;
        }

        public synchronized  boolean  isDone() {
            return done;
        }
    }

    public static TReturnStatus unpinBySURLandRequestToken(
            AbstractStorageElement storage, SRMUser user, String requestToken, URI surl)
            throws SRMInternalErrorException
    {
        String fileId;
        try {
            fileId = storage.getFileMetaData(user, surl, true).fileId;
        } catch (SRMInternalErrorException e) {
            throw e;
        } catch (SRMAuthorizationException e) {
            return new TReturnStatus(TStatusCode.SRM_AUTHORIZATION_FAILURE, e.getMessage());
        } catch (SRMInvalidPathException e) {
            return new TReturnStatus(TStatusCode.SRM_INVALID_PATH, e.getMessage());
        } catch (SRMException e) {
            return new TReturnStatus(TStatusCode.SRM_FAILURE, e.getMessage());
        }

        BringOnlineFileRequest.TheUnpinCallbacks unpinCallbacks =
                new BringOnlineFileRequest.TheUnpinCallbacks(null);
        storage.unPinFileBySrmRequestId(user, fileId, unpinCallbacks, requestToken);
        try {
            unpinCallbacks.waitCompletion(60000);
            if (!unpinCallbacks.isDone()) {
                throw new SRMInternalErrorException("Operation timed out");
            }
            if (!unpinCallbacks.isSuccess()) {
                return new TReturnStatus(TStatusCode.SRM_FAILURE, "Failed to unpin: " + unpinCallbacks.getError());
            }
        } catch (InterruptedException ie) {
            logger.error(ie.toString());
            throw new SRMInternalErrorException("Operation interrupted");
        }
        return new TReturnStatus(TStatusCode.SRM_SUCCESS, null);
    }

    public static TReturnStatus unpinBySURL(AbstractStorageElement storage, SRMUser user, URI surl)
            throws SRMInternalErrorException
    {
        String fileId;
        try {
            fileId = storage.getFileMetaData(user, surl, true).fileId;
        } catch (SRMInternalErrorException e) {
            throw e;
        } catch (SRMAuthorizationException e) {
            return new TReturnStatus(TStatusCode.SRM_AUTHORIZATION_FAILURE, e.getMessage());
        } catch (SRMInvalidPathException e) {
            return new TReturnStatus(TStatusCode.SRM_INVALID_PATH, e.getMessage());
        } catch (SRMException e) {
            return new TReturnStatus(TStatusCode.SRM_FAILURE, e.getMessage());
        }

        BringOnlineFileRequest.TheUnpinCallbacks unpinCallbacks =
                new BringOnlineFileRequest.TheUnpinCallbacks(null);
        storage.unPinFile(user, fileId, unpinCallbacks);
        try {
            unpinCallbacks.waitCompletion(60000); //one minute
            if (!unpinCallbacks.isDone()) {
                throw new SRMInternalErrorException("Operation timed out");
            }
            if (!unpinCallbacks.isSuccess()) {
                return new TReturnStatus(TStatusCode.SRM_FAILURE, "Failed to unpin: " + unpinCallbacks.getError());
            }
        } catch (InterruptedException ie) {
            logger.error(ie.toString());
            throw new SRMInternalErrorException("Operation interrupted");
        }
        return new TReturnStatus(TStatusCode.SRM_SUCCESS, null);
    }
}
