package org.dcache.srm.handler;

import org.apache.axis.types.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.dcache.srm.AbstractStorageElement;
import org.dcache.srm.SRM;
import org.dcache.srm.SRMAbortedException;
import org.dcache.srm.SRMFileRequestNotFoundException;
import org.dcache.srm.SRMInternalErrorException;
import org.dcache.srm.SRMInvalidRequestException;
import org.dcache.srm.SRMRequestTimedOutException;
import org.dcache.srm.SRMUser;
import org.dcache.srm.request.PutFileRequest;
import org.dcache.srm.request.PutRequest;
import org.dcache.srm.request.Request;
import org.dcache.srm.request.RequestCredential;
import org.dcache.srm.util.JDC;
import org.dcache.srm.v2_2.ArrayOfAnyURI;
import org.dcache.srm.v2_2.ArrayOfTSURLReturnStatus;
import org.dcache.srm.v2_2.SrmPutDoneRequest;
import org.dcache.srm.v2_2.SrmPutDoneResponse;
import org.dcache.srm.v2_2.TReturnStatus;
import org.dcache.srm.v2_2.TSURLReturnStatus;
import org.dcache.srm.v2_2.TStatusCode;

import static com.google.common.base.Preconditions.checkNotNull;

public class SrmPutDone
{
    private static final Logger LOGGER =
            LoggerFactory.getLogger(SrmPutDone.class);

    private final SrmPutDoneRequest request;
    private final SRMUser user;
    private SrmPutDoneResponse response;

    public SrmPutDone(SRMUser user,
                      RequestCredential credential,
                      SrmPutDoneRequest request,
                      AbstractStorageElement storage,
                      SRM srm,
                      String clientHost)
    {
        this.request = checkNotNull(request);
        this.user = checkNotNull(user);
    }

    public SrmPutDoneResponse getResponse()
    {
        if (response == null) {
            try {
                response = srmPutDone();
            } catch (SRMInvalidRequestException e) {
                response = getFailedResponse(e.getMessage(), TStatusCode.SRM_INVALID_REQUEST);
            } catch (SRMRequestTimedOutException e) {
                response = getFailedResponse(e.getMessage(), TStatusCode.SRM_REQUEST_TIMED_OUT);
            } catch (SRMInternalErrorException e) {
                LOGGER.error(e.getMessage());
                response = getFailedResponse(e.getMessage(), TStatusCode.SRM_INTERNAL_ERROR);
            } catch (SRMAbortedException e) {
                response = getFailedResponse(e.getMessage(), TStatusCode.SRM_ABORTED);
            }
        }
        return response;
    }

    private SrmPutDoneResponse srmPutDone()
            throws SRMInvalidRequestException, SRMRequestTimedOutException, SRMAbortedException,
                   SRMInternalErrorException
    {
        URI[] surls = getSurls(request);
        PutRequest putRequest = Request.getRequest(request.getRequestToken(), PutRequest.class);
        try (JDC ignored = putRequest.applyJdc()) {
            putRequest.wlock();
            try {
                switch (putRequest.getState()) {
                case FAILED:
                    if (putRequest.getStatusCode() == TStatusCode.SRM_REQUEST_TIMED_OUT) {
                        throw new SRMRequestTimedOutException("Total request time exceeded");
                    }
                    break;
                case CANCELED:
                    throw new SRMAbortedException("Request has been aborted.");
                }

                TSURLReturnStatus[] returnStatuses = new TSURLReturnStatus[surls.length];
                for (int i = 0; i < surls.length; ++i) {
                    if (surls[i] == null) {
                        throw new SRMInvalidRequestException("SiteURLs[" + (i + 1) + "] is null.");
                    }
                    TReturnStatus returnStatus;
                    try {
                        PutFileRequest fileRequest = putRequest
                                .getFileRequestBySurl(java.net.URI.create(surls[i].toString()));
                        try (JDC ignore = fileRequest.applyJdc()) {
                            returnStatus = fileRequest.done(user);
                        }
                    } catch (SRMFileRequestNotFoundException e) {
                        returnStatus = new TReturnStatus(TStatusCode.SRM_INVALID_PATH, "File does not exist.");
                    }
                    returnStatuses[i] = new TSURLReturnStatus(surls[i], returnStatus);
                }

                // FIXME: we do this to make the srm update the status of the request if it changed
                putRequest.getRequestStatus();

                return new SrmPutDoneResponse(
                        ReturnStatuses.getSummaryReturnStatus(returnStatuses),
                        new ArrayOfTSURLReturnStatus(returnStatuses));
            } finally {
                putRequest.wunlock();
            }
        }
    }

    private static URI[] getSurls(SrmPutDoneRequest request) throws SRMInvalidRequestException
    {
        ArrayOfAnyURI arrayOfSURLs = request.getArrayOfSURLs();
        if (arrayOfSURLs == null || arrayOfSURLs.getUrlArray().length == 0) {
            throw new SRMInvalidRequestException("arrayOfSURLs is empty.");
        }
        return arrayOfSURLs.getUrlArray();
    }

    public static final SrmPutDoneResponse getFailedResponse(String error)
    {
        return getFailedResponse(error, TStatusCode.SRM_FAILURE);
    }

    public static final SrmPutDoneResponse getFailedResponse(String error, TStatusCode statusCode)
    {
        SrmPutDoneResponse srmPutDoneResponse = new SrmPutDoneResponse();
        srmPutDoneResponse.setReturnStatus(new TReturnStatus(statusCode, error));
        return srmPutDoneResponse;
    }
}
