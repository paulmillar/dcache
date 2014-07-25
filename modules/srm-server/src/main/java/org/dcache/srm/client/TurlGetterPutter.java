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
 * TurlGetterPutter.java
 *
 * Created on May 1, 2003, 12:41 PM
 */

package org.dcache.srm.client;

import org.ietf.jgss.GSSCredential;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.net.MalformedURLException;

import org.dcache.srm.SRMException;
import org.dcache.srm.request.RequestCredential;
import org.dcache.srm.util.SrmUrl;

import static com.google.common.base.Preconditions.checkNotNull;
/**
 *
 * @author  timur
 */
public abstract class TurlGetterPutter implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(TurlGetterPutter.class);

    private final PropertyChangeSupport changeSupport = new PropertyChangeSupport(this);
    private final GSSCredential credential;
    private final String[] protocols;

    protected final String SURLs[];
    protected final long timeout;
    protected final int maxRetries;

    private volatile boolean stopped;

    public void notifyOfTURL(String SURL,String TURL,String requestId, String fileId,Long size) {
        logger.debug("notifyOfTURL( surl="+SURL+" , turl="+TURL+")");
        changeSupport.firePropertyChange(new TURLsArrivedEvent(this,SURL,TURL,requestId,fileId,size));
    }

    public void notifyOfFailure(String SURL,Object reason,String requestId, String fileId) {
        changeSupport.firePropertyChange(new TURLsGetFailedEvent(this,SURL,reason,requestId,fileId));
    }

    public void notifyOfFailure(Object reason) {
        changeSupport.firePropertyChange(new RequestFailedEvent(this,reason));
    }

    public void addListener(PropertyChangeListener listener) {
        changeSupport.addPropertyChangeListener(listener);
    }

    /** Creates a new instance of RemoteTurlGetter */
    public TurlGetterPutter(RequestCredential credential,
                            String SURLs[],
                            String[] protocols, long timeout, int maxRetries) {
        this.credential = credential.getDelegatedCredential();
        this.protocols = checkNotNull(protocols);
        this.SURLs = checkNotNull(SURLs);
        this.timeout = timeout;
        this.maxRetries = maxRetries;
    }

    public abstract void getInitialRequest() throws SRMException;

    /**
     * Getter for property stopped.
     * @return Value of property stopped.
     */
    public boolean isStopped() {
        return stopped;
    }

    /**
     * Setter for property stopped.
     * @param stopped New value of property stopped.
     */
    public void stop() {
        stopped = true;
    }

    protected GSSCredential getCredential()
    {
        return credential;
    }

    protected String[] getProtocols()
    {
        return this.protocols;
    }

    protected SrmUrl getSrmUrl() throws MalformedURLException
    {
        return new SrmUrl(SURLs[0]);
    }
}
