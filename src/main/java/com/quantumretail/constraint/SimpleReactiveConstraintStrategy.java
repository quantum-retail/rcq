package com.quantumretail.constraint;

import com.quantumretail.MetricsAware;
import com.quantumretail.resourcemon.ResourceMonitor;
import com.quantumretail.resourcemon.ResourceMonitorAware;
import com.yammer.metrics.core.Meter;
import com.yammer.metrics.core.MetricName;
import com.yammer.metrics.core.MetricsRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

/**
 * A reactive constraint: it returns true if the *current* load is below a constant threshold.
 *
 */
public class SimpleReactiveConstraintStrategy<T> implements ConstraintStrategy<T>, MetricsAware, ResourceMonitorAware {
    private final Logger log = LoggerFactory.getLogger(getClass());

    private final ConcurrentMap<String, Double> thresholds;
    private final ResourceMonitor resourceMonitor;
    private final ConcurrentMap<String, Meter> metrics = new ConcurrentHashMap<String, Meter>();
    private MetricsRegistry metricRegistry = null;
    private Meter allowed = null;
    private Meter denied = null;
    private String metricName = null;

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

        return isUnderThreshold(load);
    }

    protected boolean isUnderThreshold(Map<String, Double> load) {
        for (Map.Entry<String, Double> t : thresholds.entrySet()) {
            if (load.containsKey(t.getKey())) {
                if (load.get(t.getKey()) > t.getValue()) {
                    if (log.isTraceEnabled()) {
                        log.trace("Disqualifying because of " + t.getKey() + " (" + load.get(t.getKey()) + " > " + t.getValue() + ")");
                    }
                    if (metricRegistry != null) {
                        Meter denialMeter = getOrCreateDenialMeter(t.getKey());
                        denialMeter.mark();
                    }
                    if (denied != null) {
                        denied.mark();
                    }
                    return false;
                }
            }
        }
        if (allowed != null) {
            allowed.mark();
        }
        return true;
    }

    private Meter getOrCreateDenialMeter(String key) {
        Meter m = metrics.get(key);
        if (m == null) {
            m = metricRegistry.newMeter(new MetricName(SimpleReactiveConstraintStrategy.class, metricName, "denied " + key), "item", TimeUnit.SECONDS);
            metrics.putIfAbsent(key, m);
        }
        return m;
    }

    public ResourceMonitor getResourceMonitor() {
        return resourceMonitor;
    }

    public ConcurrentMap<String, Double> getThresholds() {
        return thresholds;
    }

    /**
     * Note that we expect this to only be called once. If it's called more than once, we'll overwrite our existing
     * metrics and replace them with new ones registered in the new metrics registry. Last caller wins.
     *
     * @param metricRegistry
     * @param name
     */
    @Override
    public void registerMetrics(MetricsRegistry metricRegistry, String name) {
        allowed = metricRegistry.newMeter(new MetricName(SimpleReactiveConstraintStrategy.class, name, "allowed"), "item", TimeUnit.SECONDS);
        denied = metricRegistry.newMeter(new MetricName(SimpleReactiveConstraintStrategy.class, name, "denied"), "item", TimeUnit.SECONDS);
        this.metricRegistry = metricRegistry;
        this.metricName = name;
    }


}
