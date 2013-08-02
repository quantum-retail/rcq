package com.quantumretail.rcq.predictor;

import com.quantumretail.resourcemon.ResourceMonitor;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TODO: document me.
 *
 */
public class LoadPredictors {

    protected static final int NUM_CPUS = Runtime.getRuntime().availableProcessors();

    public static AdjustableLoadPredictor defaultLoadPredictor() {
        return new LoadAwareLoadPredictor(defaultLoad(), Collections.<String, Double>emptyMap());
    }


    /**
     * By default, we'll assume that tasks are primarily CPU-bound (lots of CPU, not a lot of IO) and take up about 640K of RAM.
     * Why 640K?  Because that <a href="http://www.brainyquote.com/quotes/quotes/b/billgates379657.html">ought to be enough for anyone</a>
     *
     *
     * See also <a href="http://en.wikiquote.org/wiki/Talk:Bill_Gates#640K.2F1MB">the discussion on the veracity of the quote in question</a>,
     * but that's not as entertaining.
     */
    protected static Map<String, Double> defaultLoad() {
        double n = 1 / NUM_CPUS;
        double m = (640 * 1024) / Runtime.getRuntime().maxMemory();
        Map<String, Double> t = new ConcurrentHashMap<String, Double>();
        t.put(ResourceMonitor.CPU, n);
        t.put(ResourceMonitor.HEAP_MEM, n);
        return t;
    }
}
