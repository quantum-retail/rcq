package com.quantumretail.resourcemon;

import java.util.HashMap;
import java.util.Map;

/**
 * Wraps an existing ResourceMonitor in a friendly memoizing shell. Keeps you from pounding on the wrapped ResourceMonitor so hard.
 *
 * This class avoids locking, so it doesn't *guarantee* that we won't call the ResourceMonitor more often than updateFrequencyMs.
 * But in general use, it will.
 *
 */
public class CachingResourceMonitor implements ResourceMonitor {

    long lastUpdatedMs = 0;
    final long updateFrequencyMs;
    private Map<String, Double> currentMetrics = null;

    final ResourceMonitor delegate;

    /**
     * Note that we don't <strong>guarantee</strong> that we won't call the delegate ResourceMonitor more often than
     * updateFrequencyMs; in the case of concurrent accesses, we could. For most cases, this is worth the tradeoff to
     * avoid locking on every access, which would be a scalability problem that may well keep you from using all those
     * resources to begin with.
     *
     * @param delegate
     * @param updateFrequencyMs
     */
    public CachingResourceMonitor(ResourceMonitor delegate, long updateFrequencyMs) {
        this.delegate = delegate;
        this.updateFrequencyMs = updateFrequencyMs;
    }

    @Override
    public Map<String, Double> getLoad() {
        if (lastUpdatedMs + updateFrequencyMs < System.currentTimeMillis()) {
            reloadMetrics();
        }
        return currentMetrics;
    }

    private void reloadMetrics() {
        // replace current metrics
        currentMetrics = delegate.getLoad();
    }
}
