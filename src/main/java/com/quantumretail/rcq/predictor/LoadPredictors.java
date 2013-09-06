package com.quantumretail.rcq.predictor;

import com.quantumretail.resourcemon.ResourceMonitor;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * TODO: document me.
 *
 */
public class LoadPredictors {

    protected static final int NUM_CPUS = Runtime.getRuntime().availableProcessors();

    public static AdjustableLoadPredictor defaultLoadPredictor(Map<String,Double> defaultLoad, Map<String,Double> scalingFactors) {
        return new LoadAwareLoadPredictor(defaultLoad, scalingFactors);
    }

    public static AdjustableLoadPredictor defaultLoadPredictor() {
        return defaultLoadPredictor(defaultLoad(), defaultScalingFactors());
    }

    protected static Map<String, Double> defaultScalingFactors() {
        Map<String, Double> t = new HashMap<String, Double>();
        t.put(ResourceMonitor.CPU, 1.0);
        t.put(ResourceMonitor.HEAP_MEM, 1.0);
        return t;
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
        double n = 0.8 / (double) NUM_CPUS;
        double m = (640 * 1024) / (double) Runtime.getRuntime().maxMemory();
        Map<String, Double> t = new ConcurrentHashMap<String, Double>();
        t.put(ResourceMonitor.CPU, n);
        t.put(ResourceMonitor.HEAP_MEM, m);
        return t;
    }
}
