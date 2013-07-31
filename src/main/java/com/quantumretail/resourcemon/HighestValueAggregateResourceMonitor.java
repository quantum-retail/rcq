package com.quantumretail.resourcemon;

import java.util.*;

/**
 * Given a set of ResourceMonitors, this will return a map that contains the highest value for each key returned by each ResourceMonitor.
 * In other words, if this class has 2 inner ResourceMonitors where one returns:
 * <pre>
 *    KEY_1 : 0.5
 *    KEY_2 : 0.2
 *    KEY_3 : 0.9
 * </pre>
 * and the other returns:
 * <pre>
 *    KEY_1 : 0.1
 *    KEY_2 : 0.9
 *    KEY_4 : 0.5
 * </pre>
 *
 * Then this class will return a map containing:
 *
 * <pre>
 *     KEY_1 : 0.5
 *     KEY_2 : 0.9
 *     KEY_3 : 0.9
 *     KEY_4 : 0.5
 * </pre>
 *
 * That is, if the same key is returned by more than one ResourceMonitor, this class will pick the highest value.
 * That is, a map containing the superset of keys returned by all of the ResourceMonitors it is aggregating, and the highest
 * value for each key returned by any of the ResourceMonitors.
 *
 *
 */
public class HighestValueAggregateResourceMonitor implements ResourceMonitor {

    List<ResourceMonitor> monitors = new ArrayList<ResourceMonitor>();

    /**
     * @param monitors later monitors trump earlier ones if they happen to use the same keys.
     */
    public HighestValueAggregateResourceMonitor(ResourceMonitor... monitors) {
        if (monitors == null || monitors.length == 0) {
            throw new IllegalArgumentException("Need at least one ResourceMonitor as input");
        }
        this.monitors.addAll(Arrays.asList(monitors));
    }

    @Override
    public Map<String, Double> getLoad() {
        return aggregate(monitors);
    }

    private Map<String, Double> aggregate(List<ResourceMonitor> monitors) {
        Map<String, Double> load = new HashMap<String, Double>();
        for (ResourceMonitor monitor : monitors) {
            Map<String, Double> l = monitor.getLoad();
            for (Map.Entry<String, Double> entry : l.entrySet()) {
                if (entry.getValue() != null) {
                    if (load.containsKey(entry.getKey())) {
                        load.put(entry.getKey(), Math.max(load.get(entry.getKey()), entry.getValue()));
                    } else {
                        load.put(entry.getKey(), entry.getValue());
                    }
                }
            }
        }
        return load;
    }

}
