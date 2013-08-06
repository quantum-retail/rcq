package com.quantumretail.resourcemon;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * There's a lot of ugly code here, because CPU usage isn't available in a standard way.
 *
 * There are a few options I know of:
 *
 * <ul>
 * <li>If we're on a Sun JVM that is 1.7 or higher, the Sun version of OperatingSystemMXBean has CPU information. I believe OpenJDK has this available as well.</li>
 * <li>If we're on a Sun or IBM JVM, we can use OperatingSystemMXBean.processCPUTime</li>
 * <li>If we're on Linux, there's CPU information in /proc/, but I haven't gone that far yet</li>
 * </ul>
 *
 * Failing those, you might want to try Sigar.
 */
public class CpuResourceMonitor implements ResourceMonitor {
    private static final Logger log = LoggerFactory.getLogger(CpuResourceMonitor.class);
    protected static final long MIN_UPDATE_TIME = TimeUnit.MILLISECONDS.toNanos(10);
    final OperatingSystemMXBean operatingSystemMXBean;
    final RuntimeMXBean runtimeMXBean;

    boolean sun17; // assume it's a Sun JVM to start with. If that proves false, we'll flip this to false.
    boolean procTime;
    boolean procStatFile;

    ProcessCpuTime lastProcessCpuReading;


    /**
     *
     */
    public CpuResourceMonitor() {
        this(true, true);
    }

    /**
     * This constructor is mainly for testing. Use the default constructor instead.
     * @param trySunMethod
     * @param tryProcTimeMethod
     */
    public CpuResourceMonitor(boolean trySunMethod, boolean tryProcTimeMethod) {
        operatingSystemMXBean = ManagementFactory.getOperatingSystemMXBean();
        runtimeMXBean = ManagementFactory.getRuntimeMXBean();

        this.sun17 = trySunMethod;
        this.procTime = tryProcTimeMethod;
        if (tryProcTimeMethod) {
            try {
                // "prime the pump" for the procTime method.
                this.lastProcessCpuReading = new ProcessCpuTime(getProcessCPUTime(), System.nanoTime(), -1.0);
            } catch (Throwable t) {
                // ignore; we'll set this flag back to false the first time we run getCPU();
            }
        }
    }


    @Override
    public Map<String, Double> getLoad() {
        return getCPU();
    }

    protected Map<String, Double> getCPU() {
        Double systemCPU = null;
        Double procCPU = null;
        systemCPU = tryOrDisableSunMethod(systemCPU);
//        if (procStatFile) {
//            try {
//               if (new File("/proc/stat").exists()) {
//                   ....
//               }
//            } catch (Throwable t) {
//                procStatFile = false;
//            }
//        }
        procCPU = tryOrDisableProctimeMethod(procCPU);

        return assembleReturnMap(systemCPU, procCPU);
    }

    private Map<String, Double> assembleReturnMap(Double systemCPU, Double procCPU) {
        Map<String, Double> retval = new HashMap<String, Double>();
        if (systemCPU != null) {
            setCPUMetric(systemCPU, retval);
            retval.put(CPU + ".system", systemCPU);
        }
        if (procCPU != null) {
            retval.put(CPU + ".proc", procCPU);
        }
        if (procCPU != null && systemCPU == null) {
            setCPUMetric(procCPU, retval);
        }
        return retval;
    }

    private void setCPUMetric(Double cpu, Map<String, Double> m) {
        m.put(CPU, cpu);
        m.put(CPU + ".measured", cpu);
        m.put(CPU + ".crm", cpu); // for debugging: sometimes I'm curious about what this one reports vs. another CPU monitoring method.
    }

    private Double tryOrDisableProctimeMethod(Double procCPU) {
        if (procTime) {
            try {
                long currentTimeNanos = System.nanoTime();
                Long processCpuTime = getProcessCPUTime();
                if (processCpuTime != null && processCpuTime >= 0.0) {
                    // note that this is getting process CPU time, not system CPU time.  Those are quite different things -- this is just CPU usage for this process, not the whole system. But that's all we have access to via this method.
                    ProcessCpuTime pct = getProcessTime(currentTimeNanos, processCpuTime, lastProcessCpuReading, operatingSystemMXBean.getAvailableProcessors());
                    this.lastProcessCpuReading = pct;
                    if (pct != null && pct.cpuLoad >= 0) {
                        procCPU = pct.cpuLoad;
                    }
                }

            } catch (Throwable t) {
                // this method won't work, either.
                procTime = false;
            }
        }
        return procCPU;
    }

    private Double tryOrDisableSunMethod(Double systemCPU) {
        if (sun17) {
            try {
                systemCPU = getSunMethod();
            } catch (Throwable t) {
                // we don't really care why it failed, just that it failed. We won't try that again.
                sun17 = false;
            }
        }
        return systemCPU;
    }

    private Method cpuLoadMethod = null;

    protected Double getSunMethod() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        if (cpuLoadMethod == null) {
            Method m = operatingSystemMXBean.getClass().getMethod("getSystemCpuLoad");
            m.setAccessible(true);
            Double d = (Double) m.invoke(operatingSystemMXBean);
            if (d == null || d.isNaN() || d <= 0.0) {
                m = operatingSystemMXBean.getClass().getMethod("getProcessCpuLoad");
                m.setAccessible(true);
                d = (Double) m.invoke(operatingSystemMXBean);
            }
            if (d != null && !d.isNaN() && d >= 0.0) {
                cpuLoadMethod = m;
            }
        }
        if (cpuLoadMethod != null) {
            Double d = (Double) cpuLoadMethod.invoke(operatingSystemMXBean);
            if (d != null && !d.isNaN() && d >= 0.0) {
                if (d > 1) d = 1.0;
                return d;
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    private Method processCpuTimeMethod = null;

    protected Long getProcessCPUTime() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {

        // both Sun and IBM JDK implementations of OperatingSystemMXBean expose this method
        if (processCpuTimeMethod == null) {
            processCpuTimeMethod = operatingSystemMXBean.getClass().getMethod("getProcessCpuTime", null);
            processCpuTimeMethod.setAccessible(true);
        }
        return (Long) processCpuTimeMethod.invoke(operatingSystemMXBean);
    }

    /**
     * @param currentTimeNanos the current time
     * @param processCpuTime the amount of CPU time, in nanos, that this process has consumed.
     * @param previousReading the result of the last call
     * @param numProcessors the number of processors in this system.
     * @return
     * @throws NoSuchMethodException
     * @throws InvocationTargetException
     * @throws IllegalAccessException
     */
    protected ProcessCpuTime getProcessTime(long currentTimeNanos, long processCpuTime, ProcessCpuTime previousReading, int numProcessors) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        // what we're measuring is the process CPU time that has elapsed since our last measurement. We'll compare the
        // process time that has elapsed vs. the wall-clock time that has elapsed, and use that to generate a percentage.
        // That being the case, it only really makes sense to take the measurement if a bit of time has elapsed.
        if (currentTimeNanos > (previousReading.timestampNanos + MIN_UPDATE_TIME)) {  // if it's too close, there's no point in getting another reading.
            if (previousReading.processCpuTimeNanos > 0) {
                long elapsedCpuNanos = processCpuTime - previousReading.processCpuTimeNanos;
                long elapsedTimeNanos = currentTimeNanos - previousReading.timestampNanos;
                double cpu = (double) elapsedCpuNanos / (double) (elapsedTimeNanos * numProcessors);
                return new ProcessCpuTime(processCpuTime, currentTimeNanos, cpu);

            } else {
                // the first time we call this, processCpuTime won't be available
                return new ProcessCpuTime(processCpuTime, currentTimeNanos, -1);
            }
        } else {
            return previousReading;
        }
    }

    protected static class ProcessCpuTime {

        final long processCpuTimeNanos;
        final long timestampNanos;
        final double cpuLoad;

        protected ProcessCpuTime(long processCpuTimeNanos, long timestampNanos, double cpuLoad) {
            this.processCpuTimeNanos = processCpuTimeNanos;
            this.timestampNanos = timestampNanos;
            this.cpuLoad = cpuLoad;
        }
    }

}

