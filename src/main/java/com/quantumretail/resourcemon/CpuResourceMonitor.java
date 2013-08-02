package com.quantumretail.resourcemon;

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
 * <li>If we're on a Sun or IBM JVM, we can use processCPUTime (NOT YET WORKING)</li>
 * <li>If we're on Linux, there's CPU information in /proc/, but I haven't gone that far yet</li>
 * </ul>
 *
 * Failing those, you might want to try Sigar.
 */
public class CpuResourceMonitor implements ResourceMonitor {

    private static final long MIN_UPDATE_TIME = TimeUnit.MILLISECONDS.toNanos(10);
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
        this(true, true); // proctime is false by default -- not yet working
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
        Map<String, Double> m = new HashMap<String, Double>();
        Double d = getCPU();
        if (d != null) {
            m.put(CPU, d);
            m.put(CPU + ".crm", d); // for debugging: sometimes I'm curious about what this one reports vs. another CPU monitoring method.
        }
        return m;
    }

    protected Double getCPU() {
        if (sun17) {
            try {
                Double cpu = getSunMethod();
                if (cpu != null) return cpu;
            } catch (Throwable t) {
                // we don't really care why it failed, just that it failed. We won't try that again.
                sun17 = false;
            }
        }
//        if (procStatFile) {
//            try {
//                //        if (new File("/proc/stat").exists()) {
//            } catch (Throwable t) {
//                procStatFile = false;
//            }
//        }
        if (procTime) {
            try {
                long currentTimeNanos = System.nanoTime();
                Long processCpuTime = getProcessCPUTime();
                if (processCpuTime != null && processCpuTime >= 0.0) {
                    // note that this is getting process CPU time, not system CPU time.  Those are quite different things -- this is just CPU usage for this process, not the whole system. But that's all we have access to via this method.
                    ProcessCpuTime pct = getProcessTime(currentTimeNanos, processCpuTime, lastProcessCpuReading, operatingSystemMXBean.getAvailableProcessors());
                    this.lastProcessCpuReading = pct;
                    if (pct != null && pct.cpuLoad >= 0) return pct.cpuLoad;
                }

            } catch (Throwable t) {
                // this method won't work, either.
                procTime = false;
            }
        }
        return null;
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
                if (d > 1) d = 1.0;
                return d;
            } else {
                return null;
            }
        } else {
            Double d = (Double) cpuLoadMethod.invoke(operatingSystemMXBean);
            if (d != null && !d.isNaN() && d >= 0.0) {
                if (d > 1) d = 1.0;
                return d;
            } else {
                return null;
            }
        }
    }

    private Long getProcessCPUTime() throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {

        // both Sun and IBM JDK implementations of OperatingSystemMXBean expose this method
        Method m = operatingSystemMXBean.getClass().getMethod("getProcessCpuTime", null);
        if (m != null) {
            m.setAccessible(true);
            return (Long) m.invoke(operatingSystemMXBean);
        } else {
            return null;
        }
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

        if (currentTimeNanos > (previousReading.timestampNanos + MIN_UPDATE_TIME)) {  // if it's too close, there's no point in getting another reading.
            if (previousReading.processCpuTimeNanos > 0) {
                long elapsedCpuNanos = processCpuTime - previousReading.processCpuTimeNanos;
                long elapsedTimeNanos = currentTimeNanos - previousReading.timestampNanos;
                double cpu = elapsedCpuNanos / (elapsedTimeNanos * numProcessors);

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

