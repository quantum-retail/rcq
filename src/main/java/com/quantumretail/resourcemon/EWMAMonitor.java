package com.quantumretail.resourcemon;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Wraps another ResourceMonitor and calculates an exponentially-weighted moving average.
 *
 */
public class EWMAMonitor implements ResourceMonitor {

    //    final double rawAlpha;
    long smoothingTime;
    TimeUnit smoothingTimeUnit;

    Map<String, Double> previousValues = new ConcurrentHashMap<String, Double>();
    final ResourceMonitor resourceMonitor;

    long lastTimestamp = System.nanoTime();

    public EWMAMonitor(ResourceMonitor monitor) {
        this(monitor, 30, TimeUnit.SECONDS);
    }

    public EWMAMonitor(ResourceMonitor monitor, long smoothingTime, TimeUnit smoothingTimeUnit) {
        this.resourceMonitor = monitor;
//        this.rawAlpha = alpha;
        this.smoothingTime = smoothingTime;
        this.smoothingTimeUnit = smoothingTimeUnit;
    }

    @Override
    public Map<String, Double> getLoad() {
        Map<String, Double> values = resourceMonitor.getLoad();
        Map<String, Double> ewma = new HashMap<String, Double>();
        double alpha = getAlpha();
        boolean updated = false;
        for (Map.Entry<String, Double> entry : values.entrySet()) {
            if (entry.getValue() != null) {
                updated = true;
                Double prevValue = previousValues.get(entry.getKey());
                if (prevValue == null) {
                    ewma.put(entry.getKey(), entry.getValue());
                    previousValues.put(entry.getKey(), entry.getValue());
                } else {
                    double v = prevValue + alpha * (entry.getValue() - prevValue);
                    if (Double.isNaN(v)) {
                        ewma.put(entry.getKey(), prevValue);
                    } else {
                        ewma.put(entry.getKey(), v);
                        ewma.put("alpha", alpha);
                        previousValues.put(entry.getKey(), v);
                    }
                }
            }
        }
        if (updated) {
            lastTimestamp = System.nanoTime();
        }
        return ewma;
    }

    private double getAlpha() {
        // adjust the alpha based on how long it's been since the last sample.
        long intervalNanos = System.nanoTime() - lastTimestamp;
        return 1 - Math.exp(-intervalNanos / (double) (smoothingTimeUnit.toNanos(smoothingTime)));
    }
}
