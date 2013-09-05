package com.quantumretail;

import com.yammer.metrics.core.MetricsRegistry;

/**
 * Indicates that a class can expose things via the Coda Hale's Metrics framework (<a href="http://metrics.codahale.com">http://metrics.codahale.com</a>)
 *
 */
public interface MetricsAware {

    /**
     * Note that most implementors expect this to only be called once. If you call this more than once, and especially
     * if you call this more than once with different metrics registries, the behavior is undefined.
     * @param metrics the metrics registry in which to register our metrics
     * @param name the name to append to all of our metrics. Probably relates to the use of this component: "job-queue", "foo-request-queue", etc.
     */
    public void registerMetrics(MetricsRegistry metrics, String name);

}
