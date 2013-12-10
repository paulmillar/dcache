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

package org.dcache.srm.util;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import org.springframework.transaction.PlatformTransactionManager;
import org.w3c.dom.Comment;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.Text;
import org.xml.sax.SAXException;

import javax.sql.DataSource;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import java.io.FileWriter;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.dcache.srm.AbstractStorageElement;
import org.dcache.srm.SRMAuthorization;
import org.dcache.srm.SRMUserPersistenceManager;
import org.dcache.srm.client.Transport;

import static org.dcache.srm.util.Configuration.Operation.*;

/**
 *
 * @author  timur
 */
public class Configuration
{

    /**
     * User requests that may take time, so could require scheduling and
     * database activity, are grouped together into one of six possible
     * operations.
     */
    public enum Operation
    {
        LS("ls"),
        PUT("put"),
        GET("get"),
        COPY("copy"),
        BRING_ONLINE("bring online"),
        RESERVE_SPACE("reserve space");

        final String displayName;

        Operation(String name)
        {
            this.displayName = name;
        }
    };

    private static final String XML_LABEL_TRANSPORT_CLIENT = "client_transport";

    private static final String INFINITY = "infinity";

    private boolean debug = false;

    private String urlcopy="../scripts/urlcopy.sh";

    private String gsiftpclinet = "globus-url-copy";

    private boolean gsissl = true;


    private int buffer_size=2048;
    private int tcp_buffer_size;
    private int parallel_streams=10;

    private int port=8443;
    private long authzCacheLifetime = 180;
    private String srm_root="/";
    private String proxies_directory = "../proxies";
    private int timeout=60*60; //one hour
    private String timeout_script="../scripts/timeout.sh";
    /**
     * Host to use in the surl (srm url) of the
     * local file, when giving the info (metadata) to srm clients
     */
    private String srmHost;
    /**
     * A host part of the srm url (surl) is used to determine if the surl
     * references file in this storage system.
     * In case of the copy operation, srm needs to be able to tell the
     * local surl from the remote one.
     * Also SRM needs to  refuse to perform operations on non local srm urls
     * This collection cosists of hosts that are cosidered local by this srm server.
     * This parameter has to be a collection because in case of the multihomed
     * or distributed server it may have more than one network name.
     *
     */
    private final Set<String> localSrmHosts=new HashSet<>();
    private AbstractStorageElement storage;
    private SRMAuthorization authorization;

    private long defaultSpaceLifetime = 24*60*60*1000;

    private boolean useUrlcopyScript=false;
    private boolean useDcapForSrmCopy=false;
    private boolean useGsiftpForSrmCopy=true;
    private boolean useHttpForSrmCopy=true;
    private boolean useFtpForSrmCopy=true;
    private boolean recursiveDirectoryCreation=false;
    private boolean advisoryDelete=false;
    private String nextRequestIdStorageTable = "srmnextrequestid";
    private boolean reserve_space_implicitely;
    private boolean space_reservation_strict;
    private long storage_info_update_period = TimeUnit.SECONDS.toMillis(30);
    private String qosPluginClass = null;
    private String qosConfigFile = null;
    private Integer maxQueuedJdbcTasksNum ; //null by default
    private Integer jdbcExecutionThreadNum;//null by default
    private String credentialsDirectory="/opt/d-cache/credentials";
    private boolean overwrite = false;
    private boolean overwrite_by_default = false;
    private int sizeOfSingleRemoveBatch = 100;
    private SRMUserPersistenceManager srmUserPersistenceManager;
    private int maxNumberOfLsEntries = 1000;
    private int maxNumberOfLsLevels = 100;
    private boolean clientDNSLookup=false;
    private String counterRrdDirectory = null;
    private String gaugeRrdDirectory = null;
    private String clientTransport = Transport.GSI.name();
    private DataSource dataSource;
    private PlatformTransactionManager transactionManager;

    private final ImmutableMap<Operation,OperationParameters> operations;

    public Configuration()
    {
        operations = ImmutableMap.<Operation,OperationParameters>builder()
                    .put(BRING_ONLINE, new DeferrableOperationParameters())
                    .put(PUT, new DeferrableOperationParameters())
                    .put(GET, new DeferrableOperationParameters())
                    .put(LS, new DeferrableOperationParameters())
                    .put(RESERVE_SPACE, new OperationParameters())
                    .put(COPY, new OperationParameters())
                    .build();
    }

    public Configuration(Map<Operation,OperationParameters> operations)
    {
        this.operations = ImmutableMap.<Operation,OperationParameters>builder()
                .putAll(operations)
                .build();
    }

    public Configuration(String configuration_file)
            throws ParserConfigurationException, SAXException, IOException
    {
        this();
        if (configuration_file != null && !configuration_file.isEmpty()) {
            read(configuration_file);
        }
    }


    public final void read(String file)
            throws ParserConfigurationException, SAXException, IOException
    {
        DocumentBuilderFactory factory =
                DocumentBuilderFactory.newInstance();
        DocumentBuilder builder = factory.newDocumentBuilder();
        Document document = builder.parse(file);
        Node root =document.getFirstChild();
        for(;root != null && !"srm-configuration".equals(root.getNodeName());
        root = document.getNextSibling()) {
        }
        if (root == null) {
            throw new IOException("root element \"srm-configuration\" not found");
        }


        if (root.getNodeName().equals("srm-configuration")) {

            Node node = root.getFirstChild();
            for(;node != null; node = node.getNextSibling()) {
                if(node.getNodeType()!= Node.ELEMENT_NODE) {
                    continue;
                }

                Node child = node.getFirstChild();
                for(;child != null; child = node.getNextSibling()) {
                    if(child.getNodeType() == Node.TEXT_NODE) {
                        break;
                    }
                }
                if(child == null) {
                    continue;
                }
                Text t  = (Text)child;
                String node_name = node.getNodeName();
                String text_value = t.getData().trim();
                if(text_value != null && text_value.equalsIgnoreCase("null")) {
                    text_value = null;
                }
                set(node_name.trim(), text_value);
            }
        }
        synchronized(localSrmHosts) {
            try {
                localSrmHosts.add(
                        InetAddress.getLocalHost().
                        getCanonicalHostName());
            } catch(IOException ioe) {
                localSrmHosts.add("localhost");
            }
        }

    }

    protected static void put(Document document, Node root, String name, int value, String comment)
    {
        put(document, root, name, Integer.toString(value), comment);
    }

    protected static void put(Document document, Node root, String name, long value, String comment)
    {
        put(document, root, name, Long.toString(value), comment);
    }

    protected static void put(Document document,Node root,String elem_name,String value, String comment_str) {
        //System.out.println("put elem_name="+elem_name+" value="+value+" comment="+comment_str);
        Text t = document.createTextNode("\n\n\t");
        root.appendChild(t);
        Comment comment = document.createComment(comment_str);
        root.appendChild(comment);
        t = document.createTextNode("\n\t");
        root.appendChild(t);
        Element element = document.createElement(elem_name);
        t = document.createTextNode(" "+value+" ");
        element.appendChild(t);
        root.appendChild(element);
    }

    public void write(String file) throws Exception {
        DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
        DocumentBuilder db = dbf.newDocumentBuilder();
        Document document = db.newDocument();
        //System.out.println("document is instanceof "+document.getClass().getName());
        Element root = document.createElement("srm-configuration");
        write(document, root);
        Text t = document.createTextNode("\n");
        root.appendChild(t);
        document.appendChild(root);

        TransformerFactory tFactory = TransformerFactory.newInstance();
        Transformer transformer = tFactory.newTransformer();
        DOMSource source = new DOMSource(document);
        StreamResult result = new StreamResult(new FileWriter(file));
        transformer.transform(source, result);
    }

    protected void write(Document document, Element root) {
        put(document,root,"debug",Boolean.toString(debug)," true or false");
        put(document,root,"urlcopy",urlcopy," path to the urlcopy script ");
        put(document,root,"gsiftpclient",gsiftpclinet," \"globus-url-copy\" or \"kftp\"");
        put(document,root,"gsissl",Boolean.toString(gsissl),"true if use http over gsi over ssl for SOAP invocations \n\t"+
                "or false to use plain http (no authentication or encryption)");
        put(document,root,"buffer_size",Integer.toString(buffer_size),
                "nonnegative integer, 2048 by default");
        put(document,root,"tcp_buffer_size",Integer.toString(tcp_buffer_size),
                "integer, 0 by default (which means do not set tcp_buffer_size at all)");
        put(document,root,"port",Integer.toString(port),
                "port on which to publish the srm service");
        put(document,root,"srmAuthzCacheLifetime", Long.toString(authzCacheLifetime),
                "time in seconds to cache authorizations ");
        put(document,root,"srm_root", srm_root,
                "root of the srm within the file system, nothing outside the root is accessible to the users");
        put(document,root,"proxies_directory", proxies_directory,
                "directory where deligated credentials will be temporarily stored, if external client is to be utilized");
        put(document,root,"timeout",Integer.toString(timeout),
                "timeout in seconds, how long to wait for the completeon of the transfer via external client, should the external client be used for the MSS to MSS transfers");
        put(document,root,"timeout_script",timeout_script ,
                "location of the timeout script");

        {
        DeferrableOperationParameters parameters =
                (DeferrableOperationParameters) operations.get(GET);
        put(document,root,"getReqTQueueSize", parameters.getReqTQueueSize(),
                "getReqTQueueSize");
        put(document,root,"getThreadPoolSize", parameters.getThreadPoolSize(),
                "getThreadPoolSize");
        put(document,root,"getMaxWaitingRequests", parameters.getMaxWaitingRequests(),
                "getMaxWaitingRequests");
        put(document,root,"getReadyQueueSize", parameters.getReadyQueueSize(),
                "getReadyQueueSize");
        put(document,root,"getMaxReadyJobs", parameters.getMaxReadyJobs(),
                "getMaxReadyJobs");
        put(document,root,"getMaxNumOfRetries", parameters.getMaxRetries(),
                "Maximum Number Of Retries for get file request");
        put(document,root,"getRetryTimeout", parameters.getRetryTimeout(),
                "get request Retry Timeout in milliseconds");
        put(document,root,"getLifetime", parameters.getLifetime(),
                "getLifetime");
        put(document,root,"getMaxRunningBySameOwner", parameters.getMaxRunningBySameOwner(),
                "getMaxRunningBySameOwner");
        }

        {
        DeferrableOperationParameters parameters =
                (DeferrableOperationParameters) operations.get(BRING_ONLINE);
        put(document,root,"bringOnlineReqTQueueSize", parameters.getReqTQueueSize(),
                "bringOnlineReqTQueueSize");
        put(document,root,"bringOnlineThreadPoolSize", parameters.getThreadPoolSize(),
                "bringOnlineThreadPoolSize");
        put(document,root,"bringOnlineMaxWaitingRequests", parameters.getMaxWaitingRequests(),
                "bringOnlineMaxWaitingRequests");
        put(document,root,"bringOnlineReadyQueueSize", parameters.getReadyQueueSize(),
                "bringOnlineReadyQueueSize");
        put(document,root,"bringOnlineMaxReadyJobs", parameters.getMaxReadyJobs(),
                "bringOnlineMaxReadyJobs");
        put(document,root,"bringOnlineMaxNumOfRetries", parameters.getMaxRetries(),
                "Maximum Number Of Retries for bringOnline file request");
        put(document,root,"bringOnlineRetryTimeout", parameters.getRetryTimeout(),
                "bringOnline request Retry Timeout in milliseconds");
        put(document,root,"bringOnlineMaxRunningBySameOwner",
                parameters.getMaxRunningBySameOwner(),
                "bringOnlineMaxRunningBySameOwner");
        put(document,root,"bringOnlineLifetime", parameters.getLifetime(),
                "bringOnlineLifetime");
        }

        {
        DeferrableOperationParameters tParameters =
                (DeferrableOperationParameters) operations.get(LS);
        put(document,root,"lsReqTQueueSize", tParameters.getReqTQueueSize(),
                "lsReqTQueueSize");
        put(document,root,"lsThreadPoolSize", tParameters.getThreadPoolSize(),
                "lsThreadPoolSize");
        put(document,root,"lsMaxWaitingRequests", tParameters.getMaxWaitingRequests(),
                "lsMaxWaitingRequests");
        put(document,root,"lsReadyQueueSize", tParameters.getReadyQueueSize(),
                "lsReadyQueueSize");
        put(document,root,"lsMaxReadyJobs", tParameters.getMaxReadyJobs(),
                "lsMaxReadyJobs");
        put(document,root,"lsMaxNumOfRetries", tParameters.getMaxRetries(),
                "Maximum Number Of Retries for ls file request");
        put(document,root,"lsRetryTimeout", tParameters.getRetryTimeout(),
                "ls request Retry Timeout in milliseconds");
        put(document,root,"lsMaxRunningBySameOwner", tParameters.getMaxRunningBySameOwner(),
                "lsMaxRunningBySameOwner");
        }

        {
        DeferrableOperationParameters parameters =
                (DeferrableOperationParameters) operations.get(PUT);
        put(document,root,"putReqTQueueSize", parameters.getReqTQueueSize(),
                "putReqTQueueSize");
        put(document,root,"putThreadPoolSize", parameters.getThreadPoolSize(),
                "putThreadPoolSize");
        put(document,root,"putMaxWaitingRequests", parameters.getMaxWaitingRequests(),
                "putMaxWaitingRequests");
        put(document,root,"putReadyQueueSize", parameters.getReadyQueueSize(),
                "putReadyQueueSize");
        put(document,root,"putMaxReadyJobs", parameters.getMaxReadyJobs(),
                "putMaxReadyJobs");
        put(document,root,"putMaxNumOfRetries", parameters.getMaxRetries(),
                "Maximum Number Of Retries for put file request");
        put(document,root,"putRetryTimeout", parameters.getRetryTimeout(),
                "put request Retry Timeout in milliseconds");
        put(document,root,"putLifetime", parameters.getLifetime(),
                "putLifetime");
        put(document,root,"putMaxRunningBySameOwner", parameters.getMaxRunningBySameOwner(),
                "putMaxRunningBySameOwner");
        }

        {
        DeferrableOperationParameters parameters =
                (DeferrableOperationParameters) operations.get(RESERVE_SPACE);
        put(document,root,"reserveSpaceReqTQueueSize", parameters.getReqTQueueSize(),
                "reserveSpaceReqTQueueSize");
        put(document,root,"reserveSpaceThreadPoolSize", parameters.getThreadPoolSize(),
                "reserveSpaceThreadPoolSize");
        put(document,root,"reserveSpaceMaxWaitingRequests", parameters.getMaxWaitingRequests(),
                "reserveSpaceMaxWaitingRequests");
        put(document,root,"reserveSpaceReadyQueueSize", parameters.getReadyQueueSize(),
                "reserveSpaceReadyQueueSize");
        put(document,root,"reserveSpaceMaxReadyJobs", parameters.getMaxReadyJobs(),
                "reserveSpaceMaxReadyJobs");
        put(document,root,"reserveSpaceMaxNumOfRetries", parameters.getMaxRetries(),
                "Maximum Number Of Retries for reserveSpace file request");
        put(document,root,"reserveSpaceRetryTimeout", parameters.getRetryTimeout(),
                "reserveSpace request Retry Timeout in milliseconds");
        put(document,root,"reserveSpaceLifetime", parameters.getLifetime(),
                "reserveSpaceLifetime");
        put(document,root,"reserveSpaceMaxRunningBySameOwner",
                parameters.getMaxRunningBySameOwner(),
                "reserveSpaceMaxRunningBySameOwner");
        }

        {
        OperationParameters parameters = operations.get(COPY);
        put(document,root,"copyReqTQueueSize", parameters.getReqTQueueSize(),
                "copyReqTQueueSize");
        put(document,root,"copyThreadPoolSize", parameters.getThreadPoolSize(),
                "copyThreadPoolSize");
        put(document,root,"copyMaxWaitingRequests", parameters.getMaxWaitingRequests(),
                "copyMaxWaitingRequests");
        put(document,root,"copyMaxNumOfRetries", parameters.getMaxRetries(),
                "Maximum Number Of Retries for copy file request");
        put(document,root,"copyRetryTimeout", parameters.getRetryTimeout(),
                "copy request Retry Timeout in milliseconds");

        put(document,root,"copyMaxRunningBySameOwner",
                parameters.getMaxRunningBySameOwner(),
                "copyMaxRunningBySameOwner");
        put(document,root,"copyLifetime", parameters.getLifetime(), "copyLifetime");
        }

        put(document,root,"defaultSpaceLifetime",Long.toString(defaultSpaceLifetime),
                "defaultSpaceLifetime");
        put(document,root,"useUrlcopyScript", Boolean.toString(useUrlcopyScript),
                "useUrlcopyScript");
        put(document,root,"useDcapForSrmCopy", Boolean.toString(useDcapForSrmCopy),
                "useDcapForSrmCopy");
        put(document,root,"useGsiftpForSrmCopy", Boolean.toString(useGsiftpForSrmCopy),
                "useGsiftpForSrmCopy");
        put(document,root,"useHttpForSrmCopy", Boolean.toString(useHttpForSrmCopy),
                "useHttpForSrmCopy");
        put(document,root,"useFtpForSrmCopy", Boolean.toString(useFtpForSrmCopy),
                "useFtpForSrmCopy");
        put(document,root,"recursiveDirectoryCreation", Boolean.toString(recursiveDirectoryCreation),
                "recursiveDirectoryCreation");
        put(document,root,"advisoryDelete", Boolean.toString(advisoryDelete),
                "advisoryDelete");
        put(document,root,"nextRequestIdStorageTable", nextRequestIdStorageTable,
                "nextRequestIdStorageTable");

        put(document,root,"reserve_space_implicitely",Boolean.toString(reserve_space_implicitely)," true or false");
        put(document,root,
                "space_reservation_strict",
                Boolean.toString(space_reservation_strict)," true or false");
        put(document,root,
                "storage_info_update_period",
                Long.toString(storage_info_update_period),
                "storage_info_update_period in milliseconds");
        put(document,root,
                XML_LABEL_TRANSPORT_CLIENT,
                clientTransport,
                "transport to use when connecting to other SRM instances");
    }


    protected void set(String name, String value) {
        switch (name) {
        case "debug":
            debug = Boolean.valueOf(value);
            break;
        case "gsissl":
            gsissl = Boolean.valueOf(value);
            break;
        case "gsiftpclient":
            gsiftpclinet = value;
            break;
        case "urlcopy":
            urlcopy = value;
            break;
        case "buffer_size":
            buffer_size = Integer.parseInt(value);
            break;
        case "tcp_buffer_size":
            tcp_buffer_size = Integer.parseInt(value);
            break;
        case "port":
            port = Integer.parseInt(value);
            break;
        case "srmAuthzCacheLifetime":
            authzCacheLifetime = Long.parseLong(value);
            break;
        case "srm_root":
            srm_root = value;
            break;
        case "proxies_directory":
            proxies_directory = value;
            break;
        case "timeout":
            timeout = Integer.parseInt(value);
            break;
        case "timeout_script":
            timeout_script = value;
            break;
        case "getReqTQueueSize":
            operations.get(GET).setReqTQueueSize(Integer.parseInt(value));
            break;
        case "getThreadPoolSize":
            operations.get(GET).setThreadPoolSize(Integer.parseInt(value));
            break;
        case "getMaxWaitingRequests":
            operations.get(GET).setMaxWaitingRequests(Integer.parseInt(value));
            break;
        case "getReadyQueueSize":
            getDeferrableParametersFor(GET)
                    .setReadyQueueSize(Integer.parseInt(value));
            break;
        case "getMaxReadyJobs":
            getDeferrableParametersFor(GET).setMaxReadyJobs(Integer.parseInt(value));
            break;
        case "getMaxNumOfRetries":
            operations.get(GET).setMaxRetries(Integer.parseInt(value));
            break;
        case "getRetryTimeout":
            operations.get(GET).setRetryTimeout(Long.parseLong(value));
            break;
        case "getMaxRunningBySameOwner":
            operations.get(GET).setMaxRunningBySameOwner(Integer.parseInt(value));
            break;
        case "bringOnlineReqTQueueSize":
            operations.get(BRING_ONLINE).setReqTQueueSize(Integer.parseInt(value));
            break;
        case "bringOnlineThreadPoolSize":
            operations.get(BRING_ONLINE).setThreadPoolSize(Integer.parseInt(value));
            break;
        case "bringOnlineMaxWaitingRequests":
            operations.get(BRING_ONLINE).setMaxWaitingRequests(Integer.parseInt(value));
            break;
        case "bringOnlineReadyQueueSize":
            getDeferrableParametersFor(BRING_ONLINE)
                    .setReadyQueueSize(Integer.parseInt(value));
            break;
        case "bringOnlineMaxReadyJobs":
            getDeferrableParametersFor(BRING_ONLINE)
                    .setMaxReadyJobs(Integer.parseInt(value));
            break;
        case "bringOnlineMaxNumOfRetries":
            operations.get(BRING_ONLINE).setMaxRetries(Integer.parseInt(value));
            break;
        case "bringOnlineRetryTimeout":
            operations.get(BRING_ONLINE).setRetryTimeout(Long.parseLong(value));
            break;
        case "bringOnlineMaxRunningBySameOwner":
            operations.get(BRING_ONLINE).setMaxRunningBySameOwner(Integer.parseInt(value));
            break;
        case "lsReqTQueueSize":
            operations.get(LS).setReqTQueueSize(Integer.parseInt(value));
            break;
        case "lsThreadPoolSize":
            operations.get(LS).setThreadPoolSize(Integer.parseInt(value));
            break;
        case "lsMaxWaitingRequests":
            operations.get(LS).setMaxWaitingRequests(Integer.parseInt(value));
            break;
        case "lsReadyQueueSize":
            getDeferrableParametersFor(LS)
                    .setReadyQueueSize(Integer.parseInt(value));
            break;
        case "lsMaxReadyJobs":
            getDeferrableParametersFor(LS)
                    .setMaxReadyJobs(Integer.parseInt(value));
            break;
        case "lsMaxNumOfRetries":
            operations.get(LS).setMaxRetries(Integer.parseInt(value));
            break;
        case "lsRetryTimeout":
            operations.get(LS).setRetryTimeout(Long.parseLong(value));
            break;
        case "lsMaxRunningBySameOwner":
            operations.get(LS).setMaxRunningBySameOwner(Integer.parseInt(value));
            break;
        case "putReqTQueueSize":
            operations.get(PUT).setReqTQueueSize(Integer.parseInt(value));
            break;
        case "putThreadPoolSize":
            operations.get(PUT).setThreadPoolSize(Integer.parseInt(value));
            break;
        case "putMaxWaitingRequests":
            operations.get(PUT).setMaxWaitingRequests(Integer.parseInt(value));
            break;
        case "putReadyQueueSize":
            getDeferrableParametersFor(PUT)
                    .setReadyQueueSize(Integer.parseInt(value));
            break;
        case "putMaxReadyJobs":
            getDeferrableParametersFor(PUT)
                    .setMaxReadyJobs(Integer.parseInt(value));
            break;
        case "putMaxNumOfRetries":
            operations.get(PUT).setMaxRetries(Integer.parseInt(value));
            break;
        case "putRetryTimeout":
            operations.get(PUT).setRetryTimeout(Long.parseLong(value));
            break;
        case "putMaxRunningBySameOwner":
            operations.get(PUT).setMaxRunningBySameOwner(Integer.parseInt(value));
            break;
        case "copyReqTQueueSize":
            operations.get(COPY).setReqTQueueSize(Integer.parseInt(value));
            break;
        case "copyThreadPoolSize":
            operations.get(COPY).setThreadPoolSize(Integer.parseInt(value));
            break;
        case "copyMaxWaitingRequests":
            operations.get(COPY).setMaxWaitingRequests(Integer.parseInt(value));
            break;
        case "copyMaxNumOfRetries":
            operations.get(COPY).setMaxRetries(Integer.parseInt(value));
            break;
        case "copyRetryTimeout":
            operations.get(COPY).setRetryTimeout(Long.parseLong(value));
            break;
        case "copyMaxRunningBySameOwner":
            operations.get(COPY).setMaxRunningBySameOwner(Integer.parseInt(value));
            break;
        case "getLifetime":
            operations.get(GET).setLifetime(Long.parseLong(value));
            break;
        case "bringOnlineLifetime":
            operations.get(BRING_ONLINE).setLifetime(Long.parseLong(value));
            break;
        case "putLifetime":
            operations.get(PUT).setLifetime(Long.parseLong(value));
            break;
        case "copyLifetime":
            operations.get(COPY).setLifetime(Long.parseLong(value));
            break;
        case "defaultSpaceLifetime":
            defaultSpaceLifetime = Long.parseLong(value);
            break;
        case "useUrlcopyScript":
            useUrlcopyScript = Boolean.valueOf(value);
            break;
        case "useDcapForSrmCopy":
            useDcapForSrmCopy = Boolean.valueOf(value);
            break;
        case "useGsiftpForSrmCopy":
            useGsiftpForSrmCopy = Boolean.valueOf(value);
            break;
        case "useHttpForSrmCopy":
            useHttpForSrmCopy = Boolean.valueOf(value);
            break;
        case "useFtpForSrmCopy":
            useFtpForSrmCopy = Boolean.valueOf(value);
            break;
        case "recursiveDirectoryCreation":
            recursiveDirectoryCreation = Boolean.valueOf(value);
            break;
        case "advisoryDelete":
            advisoryDelete = Boolean.valueOf(value);
            break;
        case "nextRequestIdStorageTable":
            nextRequestIdStorageTable = value;
            break;
        case "reserve_space_implicitely":
            reserve_space_implicitely = Boolean.valueOf(value);
            break;
        case "space_reservation_strict":
            space_reservation_strict = Boolean.valueOf(value);
            break;
        case "storage_info_update_period":
            storage_info_update_period = Long.parseLong(value);
            break;
        case "qosPluginClass":
            qosPluginClass = value;
            break;
        case "qosConfigFile":
            qosConfigFile = value;
            break;
        case XML_LABEL_TRANSPORT_CLIENT:
            clientTransport = Transport.transportFor(value).name();
            break;
        case "reserveSpaceReqTQueueSize":
            operations.get(RESERVE_SPACE).setReqTQueueSize(Integer.parseInt(value));
            break;
        case "reserveSpaceThreadPoolSize":
            operations.get(RESERVE_SPACE).setThreadPoolSize(Integer.parseInt(value));
            break;
        case "reserveSpaceMaxWaitingRequests":
            operations.get(RESERVE_SPACE).setMaxWaitingRequests(Integer.parseInt(value));
            break;
        case "reserveSpaceReadyQueueSize":
            getDeferrableParametersFor(RESERVE_SPACE)
                    .setReadyQueueSize(Integer.parseInt(value));
            break;
        case "reserveSpaceMaxReadyJobs":
            getDeferrableParametersFor(RESERVE_SPACE)
                    .setMaxReadyJobs(Integer.parseInt(value));
            break;
        case "reserveSpaceMaxNumOfRetries":
            operations.get(RESERVE_SPACE).setMaxRetries(Integer.parseInt(value));
            break;
        case "reserveSpaceRetryTimeout":
            operations.get(RESERVE_SPACE).setRetryTimeout(Long.parseLong(value));
            break;
        case "reserveSpaceMaxRunningBySameOwner":
            operations.get(RESERVE_SPACE).setMaxRunningBySameOwner(Integer.parseInt(value));
            break;
        }
    }



    /** Getter for property urlcopy.
     * @return Value of property urlcopy.
     */
    public String getUrlcopy() {
        return urlcopy;
    }

    /** Setter for property urlcopy.
     * @param urlcopy New value of property urlcopy.
     */
    public void setUrlcopy(String urlcopy) {
        this.urlcopy = urlcopy;
    }

    /** Getter for property gsiftpclinet.
     * @return Value of property gsiftpclinet.
     */
    public String getGsiftpclinet() {
        return gsiftpclinet;
    }

    /** Setter for property gsiftpclinet.
     * @param gsiftpclinet New value of property gsiftpclinet.
     */
    public void setGsiftpclinet(String gsiftpclinet) {
        this.gsiftpclinet = gsiftpclinet;
    }

    /** Getter for property gsissl.
     * @return Value of property gsissl.
     */
    public boolean isGsissl() {
        return gsissl;
    }

    /** Setter for property gsissl.
     * @param gsissl New value of property gsissl.
     */
    public void setGsissl(boolean gsissl) {
        this.gsissl = gsissl;
    }

    /** Getter for property debug.
     * @return Value of property debug.
     */
    public boolean isDebug() {
        return debug;
    }

    /** Setter for property debug.
     * @param debug New value of property debug.
     */
    public void setDebug(boolean debug) {
        this.debug = debug;
    }


    /** Getter for property buffer_size.
     * @return Value of property buffer_size.
     */
    public int getBuffer_size() {
        return buffer_size;
    }

    /** Setter for property buffer_size.
     * @param buffer_size New value of property buffer_size.
     */
    public void setBuffer_size(int buffer_size) {
        this.buffer_size = buffer_size;
    }

    /** Getter for property tcp_buffer_size.
     * @return Value of property tcp_buffer_size.
     */
    public int getTcp_buffer_size() {
        return tcp_buffer_size;
    }

    /** Setter for property tcp_buffer_size.
     * @param tcp_buffer_size New value of property tcp_buffer_size.
     */
    public void setTcp_buffer_size(int tcp_buffer_size) {
        this.tcp_buffer_size = tcp_buffer_size;
    }

    /** Getter for property port.
     * @return Value of property port.
     */
    public int getPort() {
        return port;
    }

    /** Setter for property port.
     * @param port New value of property port.
     */
    public void setPort(int port) {
        this.port = port;
    }

    /**
     * Getter for property authzCacheLifetime.
     * @return Value of property authzCacheLifetime.
     */
    public long getAuthzCacheLifetime() {
        return authzCacheLifetime;
    }

    /** Setter for property authzCacheLifetime.
     * @param authzCacheLifetime New value of property authzCacheLifetime.
     */
    public void setAuthzCacheLifetime(long authzCacheLifetime) {
        this.authzCacheLifetime = authzCacheLifetime;
    }

    /** Setter for property srm_root.
     * @param srm_root New value of property srm_root.
     */
    public void setSrm_root(String srm_root) {
        this.srm_root = srm_root;
    }

    /** Getter for property srm_root.
     * @return Value of property srm_root.
     */
    public String getSrm_root() {
        return srm_root;
    }

    /** Getter for property proxies_directory.
     * @return Value of property proxies_directory.
     */
    public String getProxies_directory() {
        return proxies_directory;
    }

    /** Setter for property proxies_directory.
     * @param proxies_directory New value of property proxies_directory.
     */
    public void setProxies_directory(String proxies_directory) {
        this.proxies_directory = proxies_directory;
    }

    /** Getter for property timeout.
     * @return Value of property timeout.
     */
    public int getTimeout() {
        return timeout;
    }

    /** Setter for property timeout.
     * @param timeout New value of property timeout.
     */
    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    /** Getter for property timeout_script.
     * @return Value of property timeout_script.
     */
    public String getTimeout_script() {
        return timeout_script;
    }

    /** Setter for property timeout_script.
     * @param timeout_script New value of property timeout_script.
     */
    public void setTimeout_script(String timeout_script) {
        this.timeout_script = timeout_script;
    }

    /**
     * this method returns collection of the local srm hosts.
     * A host part of the srm url (surl) is used to determine if the surl
     * references file in this storage system.
     * In case of the copy operation, srm needs to be able to tell the
     * local surl from the remote one.
     * Also SRM needs to  refuse to perform operations on non local srm urls
     * This collection cosists of hosts that are cosidered local by this srm server.
     * This parameter has to be a collection because in case of the multihomed
     * or distributed server it may have more than one network name.
     *
     * @return set of local srmhosts.
     */
    public Set<String> getSrmHosts() {
        synchronized(localSrmHosts) {
            Set<String> srmhostsCopy = new HashSet<>(localSrmHosts);
            return srmhostsCopy;
        }
    }

    /**
     * This method adds values to the collection of the local srm hosts.
     * A host part of the srm url (surl) is used to determine if the surl
     * references file in this storage system.
     * In case of the copy operation, srm needs to be able to tell the
     * local surl from the remote one.
     * Also SRM needs to  refuse to perform operations on non local srm urls
     * This collection cosists of hosts that are cosidered local by this srm server.
     * This parameter has to be a collection because in case of the multihomed
     * or distributed server it may have more than one network name.
     *
     * @param srmhost additional value of srmhost.
     */
    public void addSrmHost(String srmhost) {
        synchronized(localSrmHosts) {
            localSrmHosts.add(srmhost);
        }
    }

    /**
     * Sets the set of local srm hosts. See addSrmHost for details.
     */
    public void setSrmHostsAsArray(String[] hosts) {
        synchronized(localSrmHosts) {
            localSrmHosts.clear();
            localSrmHosts.addAll(Arrays.asList(hosts));
        }
    }

    /** Getter for property storage.
     * @return Value of property storage.
     */
    public AbstractStorageElement getStorage() {
        return storage;
    }

    /** Setter for property storage.
     * @param storage New value of property storage.
     */
    public void setStorage(AbstractStorageElement storage) {
        this.storage = storage;
    }

    /** Getter for property authorization.
     * @return Value of property authorization.
     */
    public SRMAuthorization getAuthorization() {
        return authorization;
    }

    /** Setter for property authorization.
     * @param authorization New value of property authorization.
     */
    public void setAuthorization(SRMAuthorization authorization) {
        this.authorization = authorization;
    }

    private static String timeToString(long value)
    {
        return (value == Long.MAX_VALUE) ? INFINITY : String.valueOf(value);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("SRM Configuration:");
        sb.append("\n\t\"defaultSpaceLifetime\"  request lifetime: ").append(this.defaultSpaceLifetime );
        sb.append("\n\tdebug=").append(this.debug);
        sb.append("\n\tgsissl=").append(this.gsissl);
        sb.append("\n\tgridftp buffer_size=").append(this.buffer_size);
        sb.append("\n\tgridftp tcp_buffer_size=").append(this.tcp_buffer_size);
        sb.append("\n\tgridftp parallel_streams=").append(this.parallel_streams);
        sb.append("\n\tgsiftpclinet=").append(this.gsiftpclinet);
        sb.append("\n\turlcopy=").append(this.urlcopy);
        sb.append("\n\tsrm_root=").append(this.srm_root);
        sb.append("\n\ttimeout_script=").append(this.timeout_script);
        sb.append("\n\turlcopy timeout in seconds=").append(this.timeout);
        sb.append("\n\tproxies directory=").append(this.proxies_directory);
        sb.append("\n\tport=").append(this.port);
        sb.append("\n\tsrmHost=").append(getSrmHost());
        sb.append("\n\tlocalSrmHosts=");
        for(String host:this.getSrmHosts()) {
            sb.append(host).append(", ");
        }
        sb.append("\n\tuseUrlcopyScript=").append(this.useUrlcopyScript);
        sb.append("\n\tuseGsiftpForSrmCopy=").append(this.useGsiftpForSrmCopy);
        sb.append("\n\tuseHttpForSrmCopy=").append(this.useHttpForSrmCopy);
        sb.append("\n\tuseDcapForSrmCopy=").append(this.useDcapForSrmCopy);
        sb.append("\n\tuseFtpForSrmCopy=").append(this.useFtpForSrmCopy);

        for (Map.Entry<Operation,OperationParameters> entry :
                operations.entrySet()) {
            OperationParameters parameters = entry.getValue();
            Operation operation = entry.getKey();
            sb.append("\n\t\t *** ").append(operation.displayName).append(" Parameters **");
            sb.append(parameters.toString());
        }

        sb.append("\n\treserve_space_implicitely=").append(this.reserve_space_implicitely);
        sb.append("\n\tspace_reservation_strict=").append(this.space_reservation_strict);
        sb.append("\n\tstorage_info_update_period=").append(this.storage_info_update_period);
        sb.append("\n\tqosPluginClass=").append(this.qosPluginClass);
        sb.append("\n\tqosConfigFile=").append(this.qosConfigFile);
        sb.append("\n\tclientDNSLookup=").append(this.clientDNSLookup);
        sb.append( "\n\tclientTransport=").append(clientTransport);
        return sb.toString();
    }

    /** Getter for property parallel_streams.
     * @return Value of property parallel_streams.
     */
    public int getParallel_streams() {
        return parallel_streams;
    }

    /** Setter for property parallel_streams.
     * @param parallel_streams New value of property parallel_streams.
     */
    public void setParallel_streams(int parallel_streams) {
        this.parallel_streams = parallel_streams;
    }


    /** Getter for property useUrlcopyScript.
     * @return Value of property useUrlcopyScript.
     *
     */
    public boolean isUseUrlcopyScript() {
        return useUrlcopyScript;
    }

    /** Setter for property useUrlcopyScript.
     * @param useUrlcopyScript New value of property useUrlcopyScript.
     *
     */
    public void setUseUrlcopyScript(boolean useUrlcopyScript) {
        this.useUrlcopyScript = useUrlcopyScript;
    }

    /** Getter for property useDcapForSrmCopy.
     * @return Value of property useDcapForSrmCopy.
     *
     */
    public boolean isUseDcapForSrmCopy() {
        return useDcapForSrmCopy;
    }

    /** Setter for property useDcapForSrmCopy.
     * @param useDcapForSrmCopy New value of property useDcapForSrmCopy.
     *
     */
    public void setUseDcapForSrmCopy(boolean useDcapForSrmCopy) {
        this.useDcapForSrmCopy = useDcapForSrmCopy;
    }

    /** Getter for property useGsiftpForSrmCopy.
     * @return Value of property useGsiftpForSrmCopy.
     *
     */
    public boolean isUseGsiftpForSrmCopy() {
        return useGsiftpForSrmCopy;
    }

    /** Setter for property useGsiftpForSrmCopy.
     * @param useGsiftpForSrmCopy New value of property useGsiftpForSrmCopy.
     *
     */
    public void setUseGsiftpForSrmCopy(boolean useGsiftpForSrmCopy) {
        this.useGsiftpForSrmCopy = useGsiftpForSrmCopy;
    }

    /** Getter for property useHttpForSrmCopy.
     * @return Value of property useHttpForSrmCopy.
     *
     */
    public boolean isUseHttpForSrmCopy() {
        return useHttpForSrmCopy;
    }

    /** Setter for property useHttpForSrmCopy.
     * @param useHttpForSrmCopy New value of property useHttpForSrmCopy.
     *
     */
    public void setUseHttpForSrmCopy(boolean useHttpForSrmCopy) {
        this.useHttpForSrmCopy = useHttpForSrmCopy;
    }

    /** Getter for property useFtpForSrmCopy.
     * @return Value of property useFtpForSrmCopy.
     *
     */
    public boolean isUseFtpForSrmCopy() {
        return useFtpForSrmCopy;
    }

    /** Setter for property useFtpForSrmCopy.
     * @param useFtpForSrmCopy New value of property useFtpForSrmCopy.
     *
     */
    public void setUseFtpForSrmCopy(boolean useFtpForSrmCopy) {
        this.useFtpForSrmCopy = useFtpForSrmCopy;
    }

    /** Getter for property recursiveDirectoryCreation.
     * @return Value of property recursiveDirectoryCreation.
     *
     */
    public boolean isRecursiveDirectoryCreation() {
        return recursiveDirectoryCreation;
    }

    /** Setter for property recursiveDirectoryCreation.
     * @param recursiveDirectoryCreation New value of property recursiveDirectoryCreation.
     *
     */
    public void setRecursiveDirectoryCreation(boolean recursiveDirectoryCreation) {
        this.recursiveDirectoryCreation = recursiveDirectoryCreation;
    }

    /** Getter for property advisoryDelete.
     * @return Value of property advisoryDelete.
     *
     */
    public boolean isAdvisoryDelete() {
        return advisoryDelete;
    }

    /** Setter for property advisoryDelete.
     * @param advisoryDelete New value of property advisoryDelete.
     *
     */
    public void setAdvisoryDelete(boolean advisoryDelete) {
        this.advisoryDelete = advisoryDelete;
    }

    public void setDataSource(DataSource ds) {
        this.dataSource = ds;
    }

    public DataSource getDataSource()
    {
        return dataSource;
    }

    public void setTransactionManager(PlatformTransactionManager transactionManager)
    {
        this.transactionManager = transactionManager;
    }

    public PlatformTransactionManager getTransactionManager()
    {
        return transactionManager;
    }

    /**
     * Getter for property nextRequestIdStorageTable.
     * @return Value of property nextRequestIdStorageTable.
     */
    public String getNextRequestIdStorageTable() {
        return nextRequestIdStorageTable;
    }

    /**
     * Setter for property nextRequestIdStorageTable.
     * @param nextRequestIdStorageTable New value of property nextRequestIdStorageTable.
     */
    public void setNextRequestIdStorageTable(String nextRequestIdStorageTable) {
        this.nextRequestIdStorageTable = nextRequestIdStorageTable;
    }

    public static void main( String[] args) throws Exception {
        if(args == null || args.length !=2 ||
                args[0].equalsIgnoreCase("-h")  ||
                args[0].equalsIgnoreCase("-help")  ||
                args[0].equalsIgnoreCase("--h")  ||
                args[0].equalsIgnoreCase("--help")
                ) {
            System.err.println("Usage: Configuration load <file>\n or Configuration save <file>");
            return;
        }

        String command = args[0];
        String file = args[1];

        switch (command) {
        case "load": {
            System.out.println("reading configuration from file " + file);
            Configuration config = new Configuration(file);
            System.out.println("read configuration successfully:");
            System.out.print(config.toString());
            break;
        }
        case "save": {
            Configuration config = new Configuration();
            System.out.print(config.toString());
            System.out.println("writing configuration to a file " + file);
            config.write(file);
            System.out.println("done");
            break;
        }
        default:
            System.err
                    .println("Usage: Co<nfiguration load <file>\n or Configuration save <file>");

            break;
        }
    }

    /**
     * Getter for property reserve_space_implicitely.
     *
     * @return Value of property reserve_space_implicitely.
     */
    public boolean isReserve_space_implicitely() {
        return reserve_space_implicitely;
    }

    /**
     * Setter for property reserve_space_implicitely.
     *
     * @param reserve_space_implicitely New value of property reserve_space_implicitely.
     */
    public void setReserve_space_implicitely(boolean reserve_space_implicitely) {
        this.reserve_space_implicitely = reserve_space_implicitely;
    }

    /**
     * Getter for property space_reservation_strict.
     * @return Value of property space_reservation_strict.
     */
    public boolean isSpace_reservation_strict() {
        return space_reservation_strict;
    }

    /**
     * Setter for property space_reservation_strict.
     * @param space_reservation_strict New value of property space_reservation_strict.
     */
    public void setSpace_reservation_strict(boolean space_reservation_strict) {
        this.space_reservation_strict = space_reservation_strict;
    }

    /**
     * Getter for property storage_info_update_period.
     * @return Value of property storage_info_update_period.
     */
    public long getStorage_info_update_period() {
        return storage_info_update_period;
    }

    /**
     * Setter for property storage_info_update_period.
     * @param storage_info_update_period New value of property storage_info_update_period.
     */
    public void setStorage_info_update_period(long storage_info_update_period) {
        this.storage_info_update_period = storage_info_update_period;
    }


    public String getQosPluginClass() {
        return qosPluginClass;
    }
    public void setQosPluginClass(String qosPluginClass) {
        this.qosPluginClass = Strings.emptyToNull(qosPluginClass);
    }
    public String getQosConfigFile() {
        return qosConfigFile;
    }
    public void setQosConfigFile(String qosConfigFile) {
        this.qosConfigFile = Strings.emptyToNull(qosConfigFile);
    }

    public long getDefaultSpaceLifetime() {
        return defaultSpaceLifetime;
    }

    public void setDefaultSpaceLifetime(long defaultSpaceLifetime) {
        this.defaultSpaceLifetime = defaultSpaceLifetime;
    }

     public Integer getJdbcExecutionThreadNum() {
        return jdbcExecutionThreadNum;
    }

    public void setJdbcExecutionThreadNum(Integer jdbcExecutionThreadNum) {
        this.jdbcExecutionThreadNum = jdbcExecutionThreadNum;
    }

     public Integer getMaxQueuedJdbcTasksNum() {
        return maxQueuedJdbcTasksNum;
    }

    public void setMaxQueuedJdbcTasksNum(Integer maxQueuedJdbcTasksNum) {
        this.maxQueuedJdbcTasksNum = maxQueuedJdbcTasksNum;
    }

    public String getCredentialsDirectory() {
        return credentialsDirectory;
    }

    public void setCredentialsDirectory(String credentialsDirectory) {
        this.credentialsDirectory = credentialsDirectory;
    }

    public boolean isOverwrite() {
        return overwrite;
    }

    public void setOverwrite(boolean overwrite) {
        this.overwrite = overwrite;
    }

    public int getSizeOfSingleRemoveBatch() {
	    return sizeOfSingleRemoveBatch;
    }

    public void setSizeOfSingleRemoveBatch(int size) {
	    sizeOfSingleRemoveBatch=size;
    }

    public int getMaxNumberOfLsLevels() {
	    return maxNumberOfLsLevels;
    }

    public void setMaxNumberOfLsLevels(int max_ls_levels) {
	    maxNumberOfLsLevels=max_ls_levels;
    }

    public int getMaxNumberOfLsEntries() {
	    return maxNumberOfLsEntries;
    }

    public void setMaxNumberOfLsEntries(int max_ls_entries) {
	   maxNumberOfLsEntries=max_ls_entries;
    }

    public boolean isOverwrite_by_default() {
        return overwrite_by_default;
    }

    public void setOverwrite_by_default(boolean overwrite_by_default) {
        this.overwrite_by_default = overwrite_by_default;
    }


    public SRMUserPersistenceManager getSrmUserPersistenceManager() {
        return srmUserPersistenceManager;
    }

    public void setSrmUserPersistenceManager(SRMUserPersistenceManager srmUserPersistenceManager) {
        this.srmUserPersistenceManager = srmUserPersistenceManager;
    }

    /**
     * @return the clientDNSLookup
     */
    public boolean isClientDNSLookup() {
        return clientDNSLookup;
    }

    /**
     * @param clientDNSLookup the clientDNSLookup to set
     */
    public void setClientDNSLookup(boolean clientDNSLookup) {
        this.clientDNSLookup = clientDNSLookup;
    }

    /**
     * @return the rrdDirectory
     */
    public String getCounterRrdDirectory() {
        return counterRrdDirectory;
    }

    /**
     * @param rrdDirectory the rrdDirectory to set
     */
    public void setCounterRrdDirectory(String rrdDirectory) {
        this.counterRrdDirectory = rrdDirectory;
    }

    /**
     * @return the gaugeRrdDirectory
     */
    public String getGaugeRrdDirectory() {
        return gaugeRrdDirectory;
    }

    /**
     * @param gaugeRrdDirectory the gaugeRrdDirectory to set
     */
    public void setGaugeRrdDirectory(String gaugeRrdDirectory) {
        this.gaugeRrdDirectory = gaugeRrdDirectory;
    }

    /**
     * @return the srmHost
     */
    public String getSrmHost() {
        return srmHost;
    }

    /**
     * @param srmHost the srmHost to set
     */
    public void setSrmHost(String srmHost) {
        this.srmHost = srmHost;
    }

    public Transport getClientTransport() {
        return Transport.transportFor(clientTransport);
    }

    public void setClientTransport(Transport transport) {
        clientTransport = transport.name();
    }

    public void setClientTransportByName(String name) {
        clientTransport = Transport.transportFor(name).name();
    }

    public DeferrableOperationParameters getDeferrableParametersFor(Operation operation)
    {
        return (DeferrableOperationParameters) operations.get(operation);
    }

    public OperationParameters getParametersFor(Operation operation)
    {
        return operations.get(operation);
    }


    /**
     * Set of parameters surrounding a particular operation group.  FIXME
     * lifetime value is not used for LS.
     */
    public static class OperationParameters
    {
        // Scheduler-related parameters
        private long lifetime = 24*60*60*1000;
        private int reqTQueueSize = 1000;
        private int threadPoolSize = 30;
        private int maxWaitingRequests = 1000;
        private int maxNumOfRetries = 10;
        private long retryTimeout = 60000;
        private int maxRunningBySameOwner = 10;
        private String priorityPolicyPlugin = "DefaultJobAppraiser";

        // DB-related parameters
        private boolean databaseEnabled = true;
        private boolean requestHistoryDatabaseEnabled = false;
        private boolean storeCompletedRequestsOnly = false;
        private int keepRequestHistoryPeriod = 30;
        private long expiredRequestRemovalPeriod = 3600;
        private boolean cleanPendingRequestsOnRestart = false;

        public boolean isDatabaseEnabled() {
            return databaseEnabled;
        }

        public void setDatabaseEnabled(boolean value) {
            databaseEnabled = value;
        }

        public boolean getStoreCompletedRequestsOnly() {
            return storeCompletedRequestsOnly;
        }

        public void setStoreCompletedRequestsOnly(boolean value) {
            storeCompletedRequestsOnly = value;
        }

        public boolean isRequestHistoryDatabaseEnabled() {
            return requestHistoryDatabaseEnabled;
        }

        public void setRequestHistoryDatabaseEnabled(boolean value) {
            requestHistoryDatabaseEnabled = value;
        }

        public int getKeepRequestHistoryPeriod() {
            return keepRequestHistoryPeriod;
        }

        public void setKeepRequestHistoryPeriod(int value) {
            keepRequestHistoryPeriod = value;
        }

        public long getExpiredRequestRemovalPeriod() {
            return expiredRequestRemovalPeriod;
        }

        public void setExpiredRequestRemovalPeriod(long value) {
            expiredRequestRemovalPeriod = value;
        }

        public boolean isCleanPendingRequestsOnRestart() {
            return cleanPendingRequestsOnRestart;
        }

        public void setCleanPendingRequestsOnRestart(boolean value) {
            cleanPendingRequestsOnRestart = value;
        }

        @Override
        public String toString()
        {
            StringBuilder sb = new StringBuilder();
            sb.append("databaseEnabled=").append(databaseEnabled).append('\n');
            sb.append("storeCompletedRequestsOnly=").append(storeCompletedRequestsOnly).append('\n');
            sb.append("requestHistoryDatabaseEnabled=").append(requestHistoryDatabaseEnabled).append('\n');
            sb.append("cleanPendingRequestsOnRestart=").append(cleanPendingRequestsOnRestart).append('\n');
            sb.append("keepRequestHistoryPeriod=").append(keepRequestHistoryPeriod).append(" days\n");
            sb.append("expiredRequestRemovalPeriod=").append(expiredRequestRemovalPeriod).append(" seconds\n");
            sb.append("request Lifetime in milliseconds =").append(lifetime).append('\n');
            sb.append("max thread queue size =").append(reqTQueueSize).append('\n');
            sb.append("max number of threads =").append(threadPoolSize).append('\n');
            sb.append("max number of waiting file requests =").append(maxWaitingRequests).append('\n');
            sb.append("maximum number of retries = ").append(maxNumOfRetries).append('\n');
            sb.append("retry timeout in miliseconds =").append(retryTimeout).append('\n');
            sb.append("maximum number of jobs running created").append('\n');
            sb.append("by the same owner if other jobs are queued =").append(maxRunningBySameOwner).append('\n');
            return sb.toString();
        }

        public long getLifetime()
        {
            return lifetime;
        }

        public void setLifetime(long lifetime)
        {
            this.lifetime = lifetime;
        }

        public int getReqTQueueSize()
        {
            return reqTQueueSize;
        }

        public void setReqTQueueSize(int size)
        {
            reqTQueueSize = size;
        }

        public int getThreadPoolSize()
        {
            return threadPoolSize;
        }

        public void setThreadPoolSize(int size)
        {
            threadPoolSize = size;
        }

        public int getMaxWaitingRequests()
        {
            return maxWaitingRequests;
        }

        public void setMaxWaitingRequests(int max)
        {
            maxWaitingRequests = max;
        }

        public int getMaxRetries()
        {
            return maxNumOfRetries;
        }

        public void setMaxRetries(int max)
        {
            maxNumOfRetries = max;
        }

        public long getRetryTimeout()
        {
            return retryTimeout;
        }

        public void setRetryTimeout(long timeout)
        {
            retryTimeout = timeout;
        }

        public int getMaxRunningBySameOwner()
        {
            return maxRunningBySameOwner;
        }

        public void setMaxRunningBySameOwner(int max)
        {
            maxRunningBySameOwner = max;
        }

        public String getPriorityPolicyPlugin()
        {
            return priorityPolicyPlugin;
        }

        public void setPriorityPolicyPlugin(String name)
        {
            priorityPolicyPlugin = name;
        }
    }

    /**
     * Configuration for operations that are throttled and support deferring
     * the result of an operation.
     */
    public static class DeferrableOperationParameters extends OperationParameters
    {
        private int readyQueueSize = 1000;
        private int maxReadyJobs = 60;
        private long switchToAsynchronousModeDelay = 0;

        public int getReadyQueueSize()
        {
            return readyQueueSize;
        }

        public void setReadyQueueSize(int value)
        {
            readyQueueSize = value;
        }

        public int getMaxReadyJobs()
        {
            return maxReadyJobs;
        }

        public void setMaxReadyJobs(int value)
        {
            maxReadyJobs = value;
        }

        public void setSwitchToAsynchronousModeDelay(long delay)
        {
            switchToAsynchronousModeDelay = delay;
        }

        public long getSwitchToAsynchronousModeDelay()
        {
            return switchToAsynchronousModeDelay;
        }

        @Override
        public String toString()
        {
            StringBuilder sb = new StringBuilder();

            sb.append(super.toString());
            sb.append("max ready queue size =").append(readyQueueSize).append('\n');
            sb.append("max number of ready file requests =").append(maxReadyJobs).append('\n');
            sb.append("switch to async mode delay=");
            sb.append(timeToString(switchToAsynchronousModeDelay)).append('\n');

            return sb.toString();
        }
    }
}
