package com.quantumretail.resourcemon;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.OperatingSystemMXBean;
import java.util.*;

/**
 * Aggregates several resource monitors together. It just merges their maps, so later monitors trump earlier ones if
 * they happen to use the same keys.
 *
 */
public class AggregateResourceMonitor implements ResourceMonitor {

    List<ResourceMonitor> monitors = new ArrayList<ResourceMonitor>();

    /**
     * @param monitors later monitors trump earlier ones if they happen to use the same keys.
     */
    public AggregateResourceMonitor(ResourceMonitor... monitors) {
        this.monitors.addAll(Arrays.asList(monitors));
    }

    public AggregateResourceMonitor() {
        // as a default, we put Sigar first because, if it's available, it has perhaps the widest compatibility. However in my experience so far, it is less accurate than the CPU time available within
        // the Oracle JVM for 1.7x JVMs. That could well be platform-dependent, though.
        //
        monitors.addAll(Arrays.asList( new SigarResourceMonitor(), new HeapResourceMonitor(), new LoadAverageResourceMonitor(), new CpuResourceMonitor()));
    }

    @Override
    public Map<String, Double> getLoad() {
        Map<String,Double> m = new HashMap<String,Double>();

        for (ResourceMonitor monitor : monitors) {
            m.putAll(monitor.getLoad());
        }
        return m;
    }

}
