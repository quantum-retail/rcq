package com.quantumretail.resourcemon;

import com.quantumretail.rcq.predictor.LoadPredictor;
import com.quantumretail.rcq.predictor.LoadPredictors;
import com.quantumretail.rcq.predictor.TaskTracker;

/**
 *Helper factory methods for ResourceMonitors.
 *
 */
public class ResourceMonitors {

    public static final long DEFAULT_UPDATE_FREQ = 100L;

    public static ResourceMonitor defaultCachingResourceMonitor() {
        return defaultCachingResourceMonitor(DEFAULT_UPDATE_FREQ);
    }

    public static ResourceMonitor defaultCachingResourceMonitor(long updateFrequencyMS) {
        return new CachingResourceMonitor(new AggregateResourceMonitor(), updateFrequencyMS);
    }

    public static ResourceMonitor defaultPredictiveResourceMonitor(TaskTracker taskTracker) {
        return defaultPredictiveResourceMonitor(taskTracker, LoadPredictors.defaultLoadPredictor());
    }

    public static ResourceMonitor defaultPredictiveResourceMonitor(TaskTracker taskTracker, LoadPredictor loadPredictor) {
        return new SimplePredictiveResourceMonitor(taskTracker, loadPredictor);
    }

    public static ResourceMonitor defaultResourceMonitor() {
        return defaultCachingResourceMonitor(DEFAULT_UPDATE_FREQ);
    }

}
