package org.dcache.srm.scheduler;

import org.dcache.srm.request.BringOnlineFileRequest;
import org.dcache.srm.request.GetFileRequest;
import org.dcache.srm.request.Job;
import org.dcache.srm.request.LsFileRequest;
import org.dcache.srm.request.PutFileRequest;
import org.dcache.srm.request.ReserveSpaceRequest;
import org.dcache.srm.util.Configuration;

import org.dcache.srm.util.Configuration.OperationParameters;
import org.dcache.srm.util.Configuration.DeferrableOperationParameters;
import static org.dcache.srm.util.Configuration.Operation.*;

/**
 *
 * @author timur
 */
public class SchedulerFactory
{
    private final Configuration config;
    private final String name;

    public SchedulerFactory(Configuration config, String name)
    {
        this.config = config;
        this.name = name;
    }

    public Scheduler buildLsScheduler()
    {
        Scheduler scheduler = new Scheduler("ls_" + name, LsFileRequest.class);

        DeferrableOperationParameters parameters =
                config.getDeferrableParametersFor(LS);

        scheduler.setMaxThreadQueueSize(parameters.getReqTQueueSize());
        scheduler.setThreadPoolSize(parameters.getThreadPoolSize());
        scheduler.setMaxWaitingJobNum(parameters.getMaxWaitingRequests());
        scheduler.setMaxReadyQueueSize(parameters.getReadyQueueSize());
        scheduler.setMaxReadyJobs(parameters.getMaxReadyJobs());
        scheduler.setMaxNumberOfRetries(parameters.getMaxRetries());
        scheduler.setRetryTimeout(parameters.getRetryTimeout());
        scheduler.setMaxRunningByOwner(parameters.getMaxRunningBySameOwner());
        scheduler.setPriorityPolicyPlugin(parameters.getPriorityPolicyPlugin());

        return scheduler;
    }

    public Scheduler buildGetScheduler()
    {
        Scheduler scheduler = new Scheduler("get_" + name, GetFileRequest.class);

        DeferrableOperationParameters parameters =
                config.getDeferrableParametersFor(GET);

        scheduler.setMaxThreadQueueSize(parameters.getReqTQueueSize());
        scheduler.setThreadPoolSize(parameters.getThreadPoolSize());
        scheduler.setMaxWaitingJobNum(parameters.getMaxWaitingRequests());
        scheduler.setMaxReadyQueueSize(parameters.getReadyQueueSize());
        scheduler.setMaxReadyJobs(parameters.getMaxReadyJobs());
        scheduler.setMaxNumberOfRetries(parameters.getMaxRetries());
        scheduler.setRetryTimeout(parameters.getRetryTimeout());
        scheduler.setMaxRunningByOwner(parameters.getMaxRunningBySameOwner());
        scheduler.setPriorityPolicyPlugin(parameters.getPriorityPolicyPlugin());

        return scheduler;
    }

    public Scheduler buildBringOnlineScheduler()
    {
        Scheduler scheduler = new Scheduler("bring_online_" + name, BringOnlineFileRequest.class);

        DeferrableOperationParameters parameters =
                config.getDeferrableParametersFor(BRING_ONLINE);

        scheduler.setMaxThreadQueueSize(parameters.getReqTQueueSize());
        scheduler.setThreadPoolSize(parameters.getThreadPoolSize());
        scheduler.setMaxWaitingJobNum(parameters.getMaxWaitingRequests());
        scheduler.setMaxReadyQueueSize(parameters.getReadyQueueSize());
        scheduler.setMaxReadyJobs(parameters.getMaxReadyJobs());
        scheduler.setMaxNumberOfRetries(parameters.getMaxRetries());
        scheduler.setRetryTimeout(parameters.getRetryTimeout());
        scheduler.setMaxRunningByOwner(parameters.getMaxRunningBySameOwner());
        scheduler.setPriorityPolicyPlugin(parameters.getPriorityPolicyPlugin());

        return scheduler;
    }

    public Scheduler buildPutScheduler()
    {
        Scheduler scheduler = new Scheduler("put_" + name, PutFileRequest.class);

        DeferrableOperationParameters parameters =
                config.getDeferrableParametersFor(PUT);

        scheduler.setMaxThreadQueueSize(parameters.getReqTQueueSize());
        scheduler.setThreadPoolSize(parameters.getThreadPoolSize());
        scheduler.setMaxWaitingJobNum(parameters.getMaxWaitingRequests());
        scheduler.setMaxReadyQueueSize(parameters.getReadyQueueSize());
        scheduler.setMaxReadyJobs(parameters.getMaxReadyJobs());
        scheduler.setMaxNumberOfRetries(parameters.getMaxRetries());
        scheduler.setRetryTimeout(parameters.getRetryTimeout());
        scheduler.setMaxRunningByOwner(parameters.getMaxRunningBySameOwner());
        scheduler.setPriorityPolicyPlugin(parameters.getPriorityPolicyPlugin());

        return scheduler;
    }

    public Scheduler buildCopyScheduler()
    {
        Scheduler scheduler = new Scheduler("copy_" + name, Job.class);

        OperationParameters parameters = config.getParametersFor(COPY);

        scheduler.setMaxThreadQueueSize(parameters.getReqTQueueSize());
        scheduler.setThreadPoolSize(parameters.getThreadPoolSize());
        scheduler.setMaxWaitingJobNum(parameters.getMaxWaitingRequests());
        scheduler.setMaxNumberOfRetries(parameters.getMaxRetries());
        scheduler.setRetryTimeout(parameters.getRetryTimeout());
        scheduler.setMaxRunningByOwner(parameters.getMaxRunningBySameOwner());
        scheduler.setPriorityPolicyPlugin(parameters.getPriorityPolicyPlugin());

        return scheduler;
    }

    public Scheduler buildReserveSpaceScheduler()
    {
        Scheduler scheduler = new Scheduler("reserve_space_" + name, ReserveSpaceRequest.class);

        DeferrableOperationParameters parameters =
                config.getDeferrableParametersFor(RESERVE_SPACE);

        scheduler.setMaxThreadQueueSize(parameters.getReqTQueueSize());
        scheduler.setThreadPoolSize(parameters.getThreadPoolSize());
        scheduler.setMaxWaitingJobNum(parameters.getMaxWaitingRequests());
        scheduler.setMaxReadyQueueSize(parameters.getReadyQueueSize());
        scheduler.setMaxReadyJobs(parameters.getMaxReadyJobs());
        scheduler.setMaxNumberOfRetries(parameters.getMaxRetries());
        scheduler.setRetryTimeout(parameters.getRetryTimeout());
        scheduler.setMaxRunningByOwner(parameters.getMaxRunningBySameOwner());
        scheduler.setPriorityPolicyPlugin(parameters.getPriorityPolicyPlugin());

        return scheduler;
    }
}
