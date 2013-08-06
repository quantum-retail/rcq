package com.quantumretail;


import com.quantumretail.resourcemon.EWMAMonitor;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Expontentially Weighted Moving Average.
 * This smooths some of the volatility in values, at the expense of quick response to changes.
 * It works by decreasing the weighting for each older data points exponentially, therefore favoring newer entries.
 * By picking the "halflife" of old entries, you can decide how much to favor new entries .
 */
public class EWMA {

    protected static final double logOf2 = Math.log(2.0); // 0.69315... no need to keep calculating it.
    private static final String ALPHA_KEY = EWMAMonitor.class.getSimpleName() + ".alpha";

    Map<String, Double> previousValues = new ConcurrentHashMap<String, Double>();

    final long halfLifeNanos;
    long previousTimestampNanos;

    final Clock clock;
    final static Clock systemClock = new SystemClock();

    /**
     * Creates an EWMA with a half-life of 30 seconds; that is, a value is considered half as significant as a new value after 30 seconds.
     */
    public EWMA() {
        this(30, TimeUnit.SECONDS);
    }

    /**
     * Creates an EWMAMonitor with the specified half-life.
     *
     * @param halfLifeTime the time at which older entries are considered half as significant as a new entry. The smaller this value, the more volatile the results will be, but the faster you will be able to react to changes.
     * @param halfLifeTimeUnit the time unit of halfLifeTime
     */
    public EWMA(long halfLifeTime, TimeUnit halfLifeTimeUnit) {
        this(halfLifeTime, halfLifeTimeUnit, systemClock);
    }

    /**
     * This version that takes an explicit Clock is for testing purposes only; the default implementation of Clock uses
     * System.nanoTime() and should be fine for general use.
     *
     */
    public EWMA(long halfLifeTime, TimeUnit halfLifeTimeUnit, Clock clock) {

        this.halfLifeNanos = halfLifeTimeUnit.toNanos(halfLifeTime);
        this.clock = clock;
        this.previousTimestampNanos = clock.nanoTime();
    }


    public Map<String, Double> calculate(Map<String, Double> values) {
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