package com.quantumretail.resourcemon;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Wraps another ResourceMonitor and calculates an exponentially-weighted moving average.
 *
 * This smooths some of the volatility in values, at the expense of quick response to changes.
 * It works by decreasing the weighting for each older data points exponentially, therefore favoring newer entries.
 * By picking the "halflife" of old entries, you can decide how much to favor new entries .
 *
 * One advantage of the EWMA approach is that we only need to keep the most recent value, instead of all previous entries.
 *
 */
public class EWMAMonitor implements ResourceMonitor {
    private static final Logger log = LoggerFactory.getLogger(EWMAMonitor.class);
    protected static final double logOf2 = Math.log(2.0); // 0.69315... no need to keep calculating it.
    private static final String ALPHA_KEY = EWMAMonitor.class.getSimpleName() + ".alpha";

    Map<String, Double> previousValues = new ConcurrentHashMap<String, Double>();

    final long halfLifeNanos;
    long previousTimestampNanos;

    final Clock clock;

    final ResourceMonitor resourceMonitor;

    /**
     * Creates an EWMAMonitor with a half-life of 30 seconds; that is, a value is considered half as significant as a new value after 30 seconds.
     * @param monitor the monitor to whose results we will apply the average. If the monitor returns multiple values, we will apply the average to all of them.
     */
    public EWMAMonitor(ResourceMonitor monitor) {
        this(monitor, 30, TimeUnit.SECONDS);
    }

    /**
     * Creates an EWMAMonitor with the specified half-life.
     *
     * @param monitor the monitor to whose results we will apply the average. If the monitor returns multiple values, we will apply the average to all of them.
     * @param halfLifeTime the time at which older entries are considered half as significant as a new entry. The smaller this value, the more volatile the results will be, but the faster you will be able to react to changes.
     * @param halfLifeTimeUnit the time unit of halfLifeTime
     */
    public EWMAMonitor(ResourceMonitor monitor, long halfLifeTime, TimeUnit halfLifeTimeUnit) {
        this(monitor, halfLifeTime, halfLifeTimeUnit, new SystemClock());
    }

    /**
     * This value that takes an explicit Clock is for testing purposes only; the default implementation of Clock uses
     * System.nanoTime() and should be fine for general use.
     *
     * @param monitor
     * @param halfLifeTime
     * @param halfLifeTimeUnit
     * @param clock
     */
    public EWMAMonitor(ResourceMonitor monitor, long halfLifeTime, TimeUnit halfLifeTimeUnit, Clock clock) {
        this.resourceMonitor = monitor;
//        this.rawAlpha = alpha;
        this.halfLifeNanos = halfLifeTimeUnit.toNanos(halfLifeTime);
        this.clock = clock;
        this.previousTimestampNanos = clock.nanoTime();
    }

    @Override
    public Map<String, Double> getLoad() {
        Map<String, Double> values = resourceMonitor.getLoad();
        Map<String, Double> ewma = new HashMap<String, Double>();

        long timestampNanos = clock.nanoTime();
        double alpha = alpha(halfLifeNanos, (timestampNanos - previousTimestampNanos));

        boolean updated = false;
        for (Map.Entry<String, Double> entry : values.entrySet()) {
            if (entry.getValue() != null) {
                updated = true;
                Double prevValue = previousValues.get(entry.getKey());
                if (prevValue == null) {
                    ewma.put(entry.getKey(), entry.getValue());
                    previousValues.put(entry.getKey(), entry.getValue());
                } else {
                    double v = calc(alpha, prevValue, entry.getValue());
                    if (Double.isNaN(v)) {
                        ewma.put(entry.getKey(), prevValue);
                    } else {
                        ewma.put(entry.getKey(), v);
                        previousValues.put(entry.getKey(), v);
                    }
                }
            }
        }
        if (updated) {
            previousTimestampNanos = clock.nanoTime();
            ewma.put(ALPHA_KEY, alpha);
        }
        return ewma;
    }

    protected double calc(double alpha, double prevValue, double newValue) {
        return (alpha * newValue) + (1 - alpha) * prevValue;
    }

    /**
     * Calculate the alpha (decay factor) from specified half-life and interval between data points.
     *
     * Note that we don't really care if halfLife and interval are specified in nanos, as long as they're both specified
     * in the same units. We'll end up dividing one against the other.
     */
    protected static Double alpha(long halfLifeNanos, long intervalNanos) {
        if (intervalNanos <= 0) return 0.5; // if two samples are taken at the same time, we'll give them equal weight.
        if (halfLifeNanos <= 0) {
            throw new IllegalArgumentException("halfLife must be > 0");
        }

        double decayRate = logOf2 / halfLifeNanos;
        return 1 - Math.exp(-decayRate * intervalNanos);
    }

    /**
     * Exists for testing, so that we can override the system clock for deterministic tests.
     */
    public static interface Clock {
        public long nanoTime();
    }

    public static class SystemClock implements Clock {

        @Override
        public long nanoTime() {
            return System.nanoTime();
        }
    }

}