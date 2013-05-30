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

    final OperatingSystemMXBean operatingSystemMXBean;
    final RuntimeMXBean runtimeMXBean;

    boolean sun17; // assume it's a Sun JVM to start with. If that proves false, we'll flip this to false.
    boolean procTime;
    boolean procStatFile;

    long prevProcessTimestamp = 0L;
    long prevProcessCpuTime = 0L;
    double prevCPU = 0.0;


    /**
     *
     */
    public CpuResourceMonitor() {
        this(true, false); // proctime is false by default -- not yet working
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
                this.prevProcessTimestamp = System.nanoTime();
                this.prevProcessCpuTime = getProcessCPUTime();
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
            m.put(CPU+".crm", d); // for debugging: sometimes I'm curious about what this one reports vs. another CPU monitoring method.
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
            //TODO: this code is broken! Needs to be fixed.
            try {
                long currentTimeNanos = System.nanoTime();
                Long processCpuTime = getProcessCPUTime();
                if (processCpuTime != null && processCpuTime >= 0.0) {
                    // note that this is getting process CPU time, not system CPU time.  Those are quite different things -- this is just CPU usage for this process, not the whole system. But that's all we have access to via this method.
                    Double cpu = getProcessTime(currentTimeNanos, processCpuTime, prevProcessTimestamp, prevProcessCpuTime, operatingSystemMXBean.getAvailableProcessors());
                    if (cpu != null) return cpu;
                }

            } catch (Throwable t) {
                // this method won't work, either.
                procTime = false;
            }
        }
        return null;
    }

    private Double getSunMethod() {
        Double d = ((com.sun.management.OperatingSystemMXBean) operatingSystemMXBean).getSystemCpuLoad();
        if (d == null || d.isNaN() || d <= 0.0) {
            d = ((com.sun.management.OperatingSystemMXBean) operatingSystemMXBean).getProcessCpuLoad();
        }
        if (d != null && !d.isNaN() && d >= 0.0) {
            if (d > 1) d = 1.0;
            return d;
        }
        else return null;
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

    private Double getProcessTime(long currentTimeNanos, long processCpuTime, long prevProcessTimestamp, long prevProcessCpuTime, int numProcessors) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {

        if (currentTimeNanos > prevProcessTimestamp) {
            if (prevProcessCpuTime > 0) {
                long elapsedCpuNanos = processCpuTime - prevProcessCpuTime;
                long elapsedTimeNanos = currentTimeNanos - prevProcessTimestamp;
                double cpu = elapsedCpuNanos / elapsedTimeNanos;

                this.prevCPU = cpu;
                this.prevProcessCpuTime = processCpuTime;
                this.prevProcessTimestamp = currentTimeNanos;
                return cpu;
            } else {
                // the first time we call this, processCpuTime won't be available
                this.prevProcessCpuTime = processCpuTime;
                this.prevProcessTimestamp = currentTimeNanos;
                return null;
            }
        } else if (prevCPU > 0) {
            return prevCPU;
        } else {
            return null;
        }
    }


}
