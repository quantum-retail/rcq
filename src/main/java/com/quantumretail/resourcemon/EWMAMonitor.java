package com.quantumretail.resourcemon;

import com.quantumretail.EWMA;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
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

    final ResourceMonitor resourceMonitor;

    final EWMA ewma;

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
        this(monitor, new EWMA(halfLifeTime, halfLifeTimeUnit));
    }

    /**
     * This value that takes an explicit EWMA is for testing purposes only; the default implementation is probably fine.
     *
     * @param monitor
     * @param ewma an explicit EWMA instance.
     */
    public EWMAMonitor(ResourceMonitor monitor, EWMA ewma) {
        this.resourceMonitor = monitor;
        this.ewma = ewma;

    }

    @Override
    public Map<String, Double> getLoad() {
        Map<String, Double> values = resourceMonitor.getLoad();
        return ewma.calculate(values);
    }


}