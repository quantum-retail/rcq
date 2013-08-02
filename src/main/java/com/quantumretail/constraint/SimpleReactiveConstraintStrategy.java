package com.quantumretail.constraint;

import com.quantumretail.resourcemon.ResourceMonitor;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * A reactive constraint: it returns true if the *current* load is below a constant threshold.
 *
 */
public class SimpleReactiveConstraintStrategy<T> implements ConstraintStrategy<T> {

    private final ConcurrentMap<String, Double> thresholds;
    private final ResourceMonitor resourceMonitor;

    public SimpleReactiveConstraintStrategy(ResourceMonitor resourceMonitor, Map<String, Double> thresholds) {
        this.resourceMonitor = resourceMonitor;

        // we allow thresholds to be updated, so it should be a concurrent map.
        if (thresholds instanceof ConcurrentMap) {
            this.thresholds = (ConcurrentMap<String, Double>) thresholds;
        } else {
            this.thresholds = new ConcurrentHashMap<String, Double>(thresholds);
        }
    }

    @Override
    public boolean shouldReturn(T nextItem) {

        // get current load from resourceMonitor
        Map<String, Double> load = resourceMonitor.getLoad();

        return !isUnderThreshold(load);
    }

    protected boolean isUnderThreshold(Map<String, Double> load) {
        for (Map.Entry<String, Double> t : thresholds.entrySet()) {
            if (load.containsKey(t.getKey())) {
                if (load.get(t.getKey()) > t.getValue()) {
                    return false;
                }
            }
        }
        return true;
    }

    public ResourceMonitor getResourceMonitor() {
        return resourceMonitor;
    }

    public ConcurrentMap<String, Double> getThresholds() {
        return thresholds;
    }
}
