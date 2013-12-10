package org.dcache.srm.request.sql;

import com.google.common.base.Throwables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.dcache.srm.SRMUserPersistenceManager;
import org.dcache.srm.request.BringOnlineFileRequest;
import org.dcache.srm.request.BringOnlineRequest;
import org.dcache.srm.request.CopyFileRequest;
import org.dcache.srm.request.CopyRequest;
import org.dcache.srm.request.GetFileRequest;
import org.dcache.srm.request.GetRequest;
import org.dcache.srm.request.Job;
import org.dcache.srm.request.LsFileRequest;
import org.dcache.srm.request.LsRequest;
import org.dcache.srm.request.PutFileRequest;
import org.dcache.srm.request.PutRequest;
import org.dcache.srm.request.ReserveSpaceRequest;
import org.dcache.srm.scheduler.AsynchronousSaveJobStorage;
import org.dcache.srm.scheduler.CanonicalizingJobStorage;
import org.dcache.srm.scheduler.FinalStateOnlyJobStorageDecorator;
import org.dcache.srm.scheduler.IllegalStateTransition;
import org.dcache.srm.scheduler.JobStorage;
import org.dcache.srm.scheduler.JobStorageFactory;
import org.dcache.srm.scheduler.NoopJobStorage;
import org.dcache.srm.scheduler.SchedulerContainer;
import org.dcache.srm.scheduler.SharedMemoryCacheJobStorage;
import org.dcache.srm.scheduler.State;
import org.dcache.srm.util.Configuration;
import org.dcache.srm.util.Configuration.OperationParameters;

import static org.dcache.srm.util.Configuration.Operation.*;

/**
 *
 * @author timur
 */
public class DatabaseJobStorageFactory extends JobStorageFactory{
    private static final Logger logger =
            LoggerFactory.getLogger(DatabaseJobStorageFactory.class);
    private final Map<Class<? extends Job>, JobStorage<?>> jobStorageMap =
        new LinkedHashMap<>(); // JobStorage initialization order is significant to ensure file
                               // requests are cached before container requests are loaded
    private final Map<Class<? extends Job>, JobStorage<?>> unmodifiableJobStorageMap =
            Collections.unmodifiableMap(jobStorageMap);
    private final ExecutorService executor;
    private SchedulerContainer container;
    private final DataSource datasource;
    private final PlatformTransactionManager transactionManager;
    private final SRMUserPersistenceManager userPersistenceManager;

    private <J extends Job> void add(OperationParameters config,
                     Class<J> entityClass,
                     Class<? extends DatabaseJobStorage<J>> storageClass)
            throws InstantiationException,
                   IllegalAccessException,
                   IllegalArgumentException,
                   InvocationTargetException,
                   NoSuchMethodException,
                   SecurityException, DataAccessException
    {
        JobStorage<J> js;
        if (config.isDatabaseEnabled()) {
            js = buildDatabaseJobStorage(storageClass, config);
            js = new AsynchronousSaveJobStorage<>(js, executor);
            if (config.getStoreCompletedRequestsOnly()) {
                js = new FinalStateOnlyJobStorageDecorator<>(js);
            }
        } else {
            js = new NoopJobStorage<>();
        }
        jobStorageMap.put(entityClass, new CanonicalizingJobStorage<>(new SharedMemoryCacheJobStorage<>(js, entityClass), entityClass));
    }


    private <J extends Job> JobStorage<J> buildDatabaseJobStorage(Class<? extends DatabaseJobStorage<J>> storageClass,
            OperationParameters config) throws NoSuchMethodException,
            InstantiationException, IllegalAccessException,
            IllegalArgumentException, InvocationTargetException
    {
        if (DatabaseRequestStorage.class.isAssignableFrom(storageClass)) {
            return storageClass.getConstructor(OperationParameters.class,
                    DataSource.class, PlatformTransactionManager.class,
                    SRMUserPersistenceManager.class).newInstance(config,
                            datasource, transactionManager,
                            userPersistenceManager);
        } else {
            return storageClass.getConstructor(OperationParameters.class,
                    DataSource.class, PlatformTransactionManager.class)
                    .newInstance(config, datasource, transactionManager);
        }
    }


    public DatabaseJobStorageFactory(Configuration config) throws DataAccessException, IOException
    {
        datasource = config.getDataSource();
        transactionManager = config.getTransactionManager();
        userPersistenceManager = config.getSrmUserPersistenceManager();

        executor = new ThreadPoolExecutor(
                config.getJdbcExecutionThreadNum(), config.getJdbcExecutionThreadNum(),
                0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<Runnable>(config.getMaxQueuedJdbcTasksNum()));
        try {
            add(config.getParametersFor(BRING_ONLINE),
                BringOnlineFileRequest.class,
                BringOnlineFileRequestStorage.class);
            add(config.getParametersFor(BRING_ONLINE),
                BringOnlineRequest.class,
                BringOnlineRequestStorage.class);

            add(config.getParametersFor(COPY),
                CopyFileRequest.class,
                CopyFileRequestStorage.class);
            add(config.getParametersFor(COPY),
                CopyRequest.class,
                CopyRequestStorage.class);

            add(config.getParametersFor(PUT),
                PutFileRequest.class,
                PutFileRequestStorage.class);
            add(config.getParametersFor(PUT),
                PutRequest.class,
                PutRequestStorage.class);

            add(config.getParametersFor(GET),
                GetFileRequest.class,
                GetFileRequestStorage.class);
            add(config.getParametersFor(GET),
                GetRequest.class,
                GetRequestStorage.class);

            add(config.getParametersFor(LS),
                LsFileRequest.class,
                LsFileRequestStorage.class);
            add(config.getParametersFor(LS),
                LsRequest.class,
                LsRequestStorage.class);

            add(config.getParametersFor(RESERVE_SPACE),
                ReserveSpaceRequest.class,
                ReserveSpaceRequestStorage.class);
        } catch (InstantiationException e) {
            Throwables.propagateIfPossible(e.getCause(), IOException.class);
            throw new RuntimeException("Request persistence initialization failed: " + e.toString(), e);
        } catch (IllegalAccessException | InvocationTargetException | NoSuchMethodException e) {
            throw new RuntimeException("Request persistence initialization failed: " + e.toString(), e);
        }
    }

    public void setSchedulerContainer(SchedulerContainer container)
    {
        this.container = container;
    }

    public void init() throws IllegalStateTransition, InterruptedException, DataAccessException
    {
        for (JobStorage<?> jobStorage : jobStorageMap.values()) {
            jobStorage.init();
        }

        for (JobStorage<?> storage: jobStorageMap.values()) {
            Set<? extends Job> jobs = storage.getJobs(null, State.PENDING);
            container.schedule(jobs);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <J extends Job> JobStorage<J> getJobStorage(J job) {
        return getJobStorage((Class<J>) job.getClass());
    }

    @Override
    @SuppressWarnings("unchecked")
    public <J extends Job> JobStorage<J> getJobStorage(Class<? extends J> jobClass) {
        JobStorage<J> js = (JobStorage<J>) jobStorageMap.get(jobClass);
        if (js == null) {
            throw new UnsupportedOperationException(
                    "JobStorage for class " + jobClass + " is not supported");
        }
        return js;
    }

    @Override
    public Map<Class<? extends Job>, JobStorage<?>> getJobStorages()
    {
        return unmodifiableJobStorageMap;
    }

}
