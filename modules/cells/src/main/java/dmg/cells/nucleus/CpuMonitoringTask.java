package dmg.cells.nucleus;

import static com.google.common.base.Preconditions.checkArgument;
import com.google.common.primitives.Longs;
import dmg.util.CpuUsage;
import dmg.util.FractionalCpuUsage;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *  Provides the engine for calculating the CPU activity per cell.
 */
public class CpuMonitoringTask implements Runnable
{
    private static final Logger _log =
            LoggerFactory.getLogger(CpuMonitoringTask.class);
    private static final long DEFAULT_DELAY_BETWEEN_UPDATES = 2;
    private static final long MINIMUM_DELAY_BETWEEN_UPDATES = 1;
    private static final long MAXIMUM_DELAY_BETWEEN_UPDATES = 60;

    private final ThreadMXBean _threads =
            ManagementFactory.getThreadMXBean();
    private final Map<Long,ThreadInfo> _threadInfos = new HashMap<>();
    private final Map<String,CpuUsage> _cellCpuUsage = new HashMap<>();
    private final CellGlue _glue;

    private ScheduledFuture _task;
    private List<Thread> _allThreads;
    private boolean _isFirstRun;
    private long _lastUpdate;
    private boolean _threadMonitoringEnabled;

    private long _delayBetweenUpdates = DEFAULT_DELAY_BETWEEN_UPDATES;

    CpuMonitoringTask(CellGlue glue)
    {
        _glue = glue;
    }

    public void start(ScheduledExecutorService executor)
    {
        if(!_threads.isThreadCpuTimeSupported()) {
            throw new UnsupportedOperationException("Per-thread CPU " +
                    "monitoring not available in this JVM");
        }

        if(!_threads.isThreadCpuTimeEnabled()) {
            _log.debug("Per-thread CPU monitoring not enabled; enabling it...");
            _threads.setThreadCpuTimeEnabled(true);
            _threadMonitoringEnabled = true;
        }

        if(_task == null) {
            _log.debug("scheduling for every {} seconds", _delayBetweenUpdates);
            _task = scheduleTask(executor);
        }
    }


    public long getUpdateDelay()
    {
        return _delayBetweenUpdates;
    }

    public void setUpdateDelay(ScheduledExecutorService executor, long value)
    {
        checkArgument(value >= MINIMUM_DELAY_BETWEEN_UPDATES,
                "value too small");
        checkArgument(value <= MAXIMUM_DELAY_BETWEEN_UPDATES,
                "value too large");

        _delayBetweenUpdates = value;

        if(_task != null) {
            _log.debug("rescheduling for every {} seconds",
                    _delayBetweenUpdates);

            if(_task != null) {
                _task.cancel(true);
            }

            _task = scheduleTask(executor);
        }
    }

    private ScheduledFuture scheduleTask(ScheduledExecutorService executor)
    {
        _isFirstRun = true;
        return executor.scheduleWithFixedDelay(this, _delayBetweenUpdates,
                _delayBetweenUpdates, SECONDS);
    }

    public void stop()
    {
        if(_task != null) {
            _log.debug("cancelling CPU profiling");
            _task.cancel(true);
            _task = null;
            _glue.setAccumulatedCellCpuUsage(Collections.<String,CpuUsage>emptyMap());
            _glue.setCurrentCellCpuUsage(Collections.<String,FractionalCpuUsage>emptyMap());
        }

        if(_threadMonitoringEnabled) {
            _threads.setThreadCpuTimeEnabled(false);
            _threadMonitoringEnabled = false;
        }
    }

    @Override
    public void run()
    {
        long thisUpdate = System.currentTimeMillis();

        try {
            resetForQuantum();

            long[] threads = _threads.getAllThreadIds();

            for(long id : threads) {
                updateCellFromThread(id);
            }

            long duration =
                MILLISECONDS.toNanos(System.currentTimeMillis() - _lastUpdate);

            _cellCpuUsage.keySet().retainAll(aliveCellsAndNull());
            _threadInfos.keySet().retainAll(Longs.asList(threads));

            _glue.setAccumulatedCellCpuUsage(accumulatedCellCpuUsage());

            if(!_isFirstRun) {
                _glue.setCurrentCellCpuUsage(fractionalCellCpuUsage(duration));
            }

        } catch(RuntimeException e) {
            _log.warn("Failed:", e);
        }

        _isFirstRun = false;
        _lastUpdate = thisUpdate;
    }


    private List<String> aliveCellsAndNull()
    {
        List<String> cells = _glue.getCellNames();
        cells.add(null);
        return cells;
    }


    private Map<String,CpuUsage> accumulatedCellCpuUsage()
    {
        Map<String,CpuUsage> result = new HashMap<>();
        for(Map.Entry<String,CpuUsage> e : _cellCpuUsage.entrySet()) {
            result.put(e.getKey(), e.getValue().clone());
        }
        return result;
    }


    private Map<String,FractionalCpuUsage> fractionalCellCpuUsage(long duration)
    {
        Map<String,FractionalCpuUsage> result = new HashMap<>();
        for(Map.Entry<String,CpuUsage> e : _cellCpuUsage.entrySet()) {
            String cell = e.getKey();
            CpuUsage usage = e.getValue();

            try {
                result.put(cell, new FractionalCpuUsage(usage, duration));
            } catch(RuntimeException re) {
                _log.warn("Failed for {}: {}", cell, re.getMessage());
            }
        }
        return result;
    }


    private void updateCellFromThread(long id)
    {
        ThreadInfo info = getOrCreateThreadInfo(id);

        if(info == null) {
            // Give up: we couldn't identify this thread.
            return;
        }

        long total = _threads.getThreadCpuTime(id);
        long user = _threads.getThreadUserTime(id);

        if(total == -1 || user == -1) {
            // thread died between getOrCreateThreadInfo and getThread* methods
            return;
        }

        if(user > total) {
            // This shouldn't happen, but some JVM implementations have different
            // resolutions for different types and seem to round-up.  We limit
            // the 'user' value at 'total' to compensate.
            user = total;
        }

        long diffTotal = info.setTotal(total);
        long diffUser = info.setUser(user);
        info.assertValues();


        if(diffUser > diffTotal) {
            // Again, this shouldn't happen, but due to resolution and
            // rounding problems, it does.  Use the same compensation strategy
            // of limiting diffUser to diffTotal.
            diffUser = diffTotal;
        }

        CpuUsage cellUsage =
                getOrCreateCpuUsageForCell(info.getCellName());

        cellUsage.addCombined(diffTotal);
        cellUsage.addUser(diffUser);
        cellUsage.assertValues();
    }

    private void resetForQuantum()
    {
        for(CpuUsage usage : _cellCpuUsage.values()) {
            usage.reset();
        }

        _allThreads = null;
    }

    private CpuUsage getOrCreateCpuUsageForCell(String cellName)
    {
        CpuUsage usage;

        if(_cellCpuUsage.containsKey(cellName)) {
            usage = _cellCpuUsage.get(cellName);
        } else {
            usage = new CpuUsage();
            _cellCpuUsage.put(cellName, usage);
        }

        return usage;
    }

    private ThreadInfo getOrCreateThreadInfo(long id)
    {
        ThreadInfo info = _threadInfos.get(id);

        if(info == null || !info.isAlive()) {
            info = addNewThread(id);
        }

        return info;
    }

    private Thread getThreadFromId(long id)
    {
        if(_allThreads == null) {
            // This is an expensive operation, so the result is cached.
            _allThreads = discoverAllThreadsFromStackTraces();
        }

        for(Thread thread : _allThreads) {
            if(thread.getId() == id) {
                return thread;
            }
        }

        /* Can't find the thread: perhaps it is a new thread that died
         * very quickly?
         */
        return null;
    }

    private List<Thread> discoverAllThreadsFromStackTraces()
    {
        Set<Thread> threads = Thread.getAllStackTraces().keySet();

        List<Thread> allThreads  = new ArrayList<>(threads.size());
        allThreads.addAll(threads);
        return allThreads;
    }


    private List<Thread> discoverAllThreadsFromThreadGroup()
    {
        ThreadGroup tg = Thread.currentThread().getThreadGroup();
        while(tg.getParent() != null) {
            tg = tg.getParent();
        }

        Thread[] list;
        do {
            list = new Thread[tg.activeCount()+20];
        } while(tg.enumerate(list) == list.length);

        return Arrays.asList(list);
    }


    private ThreadInfo addNewThread(long id)
    {
        Thread thread = getThreadFromId(id);
        if(thread == null) {
            return null;
        }

        String cellName = _glue.cellNameFor(thread);
        ThreadInfo stats = new ThreadInfo(thread, cellName);
        _threadInfos.put(id, stats);

        return stats;
    }


    /**
     * Holds information about a thread: caching information and allowing
     * calculation the amount of CPU used since last time.
     */
    private static class ThreadInfo
    {
        private final WeakReference<Thread> _thread;
        private final String _cell;
        private final CpuUsage _cpuUsage = new CpuUsage();

        ThreadInfo(Thread thread, String cell)
        {
            _thread = new WeakReference<>(thread);
            _cell = cell;
        }

        public String getCellName()
        {
            return _cell;
        }

        public boolean isAlive()
        {
            Thread thread = _thread.get();
            return thread != null && thread.isAlive();
        }

        public long getTotal()
        {
            return _cpuUsage.getCombined();
        }

        public long getUser()
        {
            return _cpuUsage.getUser();
        }

        public long setTotal(long newValue)
        {
            return _cpuUsage.setCombined(newValue);
        }

        public long setUser(long newValue)
        {
            return _cpuUsage.setUser(newValue);
        }

        public void assertValues()
        {
            _cpuUsage.assertValues();
        }
    }
}
