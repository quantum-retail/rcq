package com.quantumretail.resourcemon;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.util.HashMap;
import java.util.Map;

/**
 * Returns the system load average / # processors, resulting in a number between 0 (no load) and x (fully loaded),
 * where x can be greater than 1. This is different than most ResourceMonitor implementations, which restrict values to
 * between 0 and 1. However, there is no hard max to load averages, so it's hard to scale them appropriately.
 *
 * Note that load average is not implemented on all platforms (Windows, in particular), so this may not return anything
 * useful.
 *
 */
public class LoadAverageResourceMonitor implements ResourceMonitor {

    final OperatingSystemMXBean operatingSystemMXBean;

    public LoadAverageResourceMonitor() {
        operatingSystemMXBean = ManagementFactory.getOperatingSystemMXBean();
    }

    @Override
    public Map<String, Double> getLoad() {
        Map<String,Double> m = new HashMap<String,Double>();
        m.put(LOAD_AVERAGE, getLoadAverage(getRawLoadAverage(), getAvailableProcessors()));
        m.put("LOAD_AVERAGE.raw", getRawLoadAverage());
        return m;
    }

    protected double getRawLoadAverage() {
        return operatingSystemMXBean.getSystemLoadAverage();
    }

    protected int getAvailableProcessors() {
        return operatingSystemMXBean.getAvailableProcessors();
    }

    protected double getLoadAverage(double rawLoadAverage, int availableProcessors) {
        return Math.max(0.0, rawLoadAverage / availableProcessors);
    }

}
