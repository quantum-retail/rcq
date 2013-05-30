package com.quantumretail.resourcemon;

import java.util.Map;

/**
 * Get the current load.
 * Load will be a map of string category name to a load value, where load value is a normalized number between 0 and 1
 * (where 0 is "no load" and 1 is "fully loaded").
 *
 * Note that all metrics aren't available on all systems.
 *
 */
public interface ResourceMonitor {

    public static final String CPU = "CPU";
    public static final String LOAD_AVERAGE = "LOAD_AVERAGE";
    public static final String HEAP_MEM = "HEAP_MEM";

    /**
     * Get a map of system resource usage. This can contain arbitrary key -> value pairs, where the keys are strings and
     * the values are numbers between 0.0 and 1.0.
     * @return
     */
    Map<String, Double> getLoad();

}
