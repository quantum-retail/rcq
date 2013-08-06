package com.quantumretail.constraint;

import com.quantumretail.rcq.predictor.*;
import com.quantumretail.resourcemon.HighestValueAggregateResourceMonitor;
import com.quantumretail.resourcemon.ResourceMonitor;
import com.quantumretail.resourcemon.ResourceMonitors;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static com.quantumretail.resourcemon.ResourceMonitors.*;

/**
 * Class providing some factory methods of typical constraint strategies.
 *
 * There are a lot of moving parts here, so this is intended to make life a little simpler.
 *
 */
public class ConstraintStrategies {

    public static <T> ConstraintStrategy<T> defaultReactiveConstraintStrategy() {
        return defaultReactiveConstraintStrategy(ResourceMonitors.DEFAULT_UPDATE_FREQ);
    }


    public static <T> ConstraintStrategy<T> defaultReactiveConstraintStrategy(Map<String, Double> thresholds) {
        return defaultReactiveConstraintStrategy(thresholds, DEFAULT_UPDATE_FREQ);
    }

    public static <T> ConstraintStrategy<T> defaultReactiveConstraintStrategy(Map<String, Double> thresholds, long updateFrequencyMS) {
        return new SimpleReactiveConstraintStrategy<T>(defaultCachingResourceMonitor(updateFrequencyMS), thresholds);
    }

    public static <T> ConstraintStrategy<T> defaultReactiveConstraintStrategy(long updateFrequencyMS) {
        return defaultReactiveConstraintStrategy(defaultThresholds(), updateFrequencyMS);
    }

    public static <T> ConstraintStrategy<T> defaultPredictiveConstraintStrategy(Map<String, Double> thresholds) {
        return new SimplePredictiveConstraintStrategy<T>(defaultPredictiveResourceMonitor(TaskTrackers.defaultTaskTracker()),
                thresholds,
                LoadPredictors.defaultLoadPredictor());
    }

    public static <T> ConstraintStrategy<T> defaultPredictiveConstraintStrategy() {
        return defaultPredictiveConstraintStrategy(defaultThresholds());
    }

    public static <T> ConstraintStrategy<T> defaultCombinedConstraintStrategy(Map<String, Double> thresholds, TaskTracker<T> taskTracker) {
        return defaultCombinedConstraintStrategyWithFeedbackThread(thresholds, taskTracker, null);
    }

    public static <T> ConstraintStrategy<T> defaultCombinedConstraintStrategyWithFeedbackThread(Map<String, Double> thresholds, TaskTracker<T> taskTracker, ScheduledExecutorService service) {
        AdjustableLoadPredictor loadPredictor = LoadPredictors.defaultLoadPredictor();
        ResourceMonitor predictive = defaultPredictiveResourceMonitor(taskTracker, loadPredictor);
        ResourceMonitor measured = defaultCachingResourceMonitor();

        if (service != null) {
            service.scheduleAtFixedRate(new ScalingFactorAdjuster(measured, predictive, loadPredictor), 10, 10, TimeUnit.SECONDS);
        }

        return new SimplePredictiveConstraintStrategy<T>(
                new HighestValueAggregateResourceMonitor(predictive, measured),
                thresholds,
                loadPredictor
        );
    }

    public static <T> ConstraintStrategy<T> defaultCombinedConstraintStrategy(TaskTracker<T> taskTracker) {
        return defaultCombinedConstraintStrategy(defaultThresholds(), taskTracker);
    }

    public static <T> ConstraintStrategy<T> defaultConstraintStrategy(TaskTracker<T> taskTracker) {
        return defaultConstraintStrategy(defaultThresholds(), taskTracker);
    }

    public static <T> ConstraintStrategy<T> defaultConstraintStrategy(Map<String, Double> thresholds, TaskTracker<T> taskTracker) {
        return defaultCombinedConstraintStrategy(thresholds, taskTracker);
    }


    protected static Map<String, Double> defaultThresholds() {
        Map<String, Double> t = new ConcurrentHashMap<String, Double>();
        t.put(ResourceMonitor.CPU, 0.95);
        t.put(ResourceMonitor.HEAP_MEM, 0.90);
        return t;
    }

}
