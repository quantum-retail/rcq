package com.quantumretail.resourcemon;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.HashMap;
import java.util.Map;

/**
 * Returns heap memory usage between 0 (no memory used) and 1 (all memory used).
 *
 */
public class HeapResourceMonitor implements ResourceMonitor {
//    public static final String HEAP_MEM = "HEAP_MEM";
//    public static final String HEAP_MAX = "HEAP_MAX";
//    public static final String HEAP_COMMITTED = "HEAP_COMMITTED";
//    public static final String HEAP_FREE = "HEAP_FREE";

    final MemoryMXBean memoryMXBean;

    public HeapResourceMonitor() {
        memoryMXBean = ManagementFactory.getMemoryMXBean();
    }

    @Override
    public Map<String, Double> getLoad() {
        MemoryUsage memoryUsage = memoryMXBean.getHeapMemoryUsage();
        long max = memoryUsage.getMax();
        long committed = memoryUsage.getCommitted();
        long used = memoryUsage.getUsed();

        Map<String, Double> map = new HashMap<String, Double>();
        // if we have max, then max - used / max
        // otherwise, committed - used / committed
        if (max > 0) {
            map.put(HEAP_MEM, 1.0 - ( (max - used) / (double) max) );
        } else {
            map.put(HEAP_MEM, 1.0 - ( (committed - used) / (double) committed) );
        }

        return map;
    }

}
