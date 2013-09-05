package com.quantumretail.resourcemon;

/**
 * Represents a thing that contains a ResourceMonitor. Or is aware of ResourceMonitors. Or otherwise can return a
 * ResourceMonitor if coerced.
 *
 */
public interface ResourceMonitorAware {

    ResourceMonitor getResourceMonitor();
}
