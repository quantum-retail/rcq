package com.quantumretail.rcq.predictor;

import com.quantumretail.EWMA;
import com.quantumretail.resourcemon.ResourceMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * A Runnable, intended to run periodically in a separate thread (via a ScheduledExecutorService, most likely) which
 * will monitor the real load vs. the predicted load and adjust the "scaling factor" (the system-specific multiplier
 * on "predicted load") using an exponentially weighted moving average.
 * It optionally takes a measurementHalfLife parameter, which you can use to adjust how quickly it will move the
 * predicted load to meet the measured load. You should set this to something close to the expected runtime of one
 * of the tasks that we are measuring the effects of.
 *
 * Since we are tracking those tasks, perhaps in the future we could set that value ourself. But that would introduce
 * another interdependency between components (this time on TaskTracker) that might not be worth it.
 *
 * There's a convenient helper method to create these in {@link com.quantumretail.constraint.ConstraintStrategies}.
 */
public class ScalingFactorAdjuster implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(ScalingFactorAdjuster.class);

    private final ResourceMonitor predictiveResourceMonitor;
    private final ResourceMonitor measuredResourceMonitor;
    private final AdjustableLoadPredictor loadPredictor;

    final EWMA ewma;

    public ScalingFactorAdjuster(ResourceMonitor measuredResourceMonitor, ResourceMonitor predictiveResourceMonitor, AdjustableLoadPredictor loadPredictor) {
        this(measuredResourceMonitor, predictiveResourceMonitor, loadPredictor, 1, TimeUnit.HOURS); // the default is a very long half-life;  we want to make adjustments very slowly.
    }

    public ScalingFactorAdjuster(ResourceMonitor measuredResourceMonitor, ResourceMonitor predictiveResourceMonitor, AdjustableLoadPredictor loadPredictor, long measurementHalflife, TimeUnit measurementHalflifeTimeUnit) {
        this.measuredResourceMonitor = measuredResourceMonitor;
        this.predictiveResourceMonitor = predictiveResourceMonitor;
        this.loadPredictor = loadPredictor;

        this.ewma = new EWMA(measurementHalflife, measurementHalflifeTimeUnit);
        ewma.calculate(loadPredictor.getScalingFactor());

    }

    /**
     * Ok, here's the meaty part of this class.  We're going to see how different our scaled map is from the measured
     * results from our measuredResourceMonitor. We're going to figure out what the scaling factor *should* have been
     * for the two to be equal, and we're going to send that into the fairly long-halflife EWMA function. So over time
     * the scaling factor should trend toward ideal, based on measured values.
     *
     */
    @Override
    public void run() {

        Map<String, Double> predictedLoad = predictiveResourceMonitor.getLoad();
        Map<String, Double> currentLoad = measuredResourceMonitor.getLoad();

        Map<String, Double> startingScalingFactors = loadPredictor.getScalingFactor();

        boolean allZero = true;
        for (Double predictedValue : predictedLoad.values()) {
            if (predictedValue > 0) allZero = false;
        }
        if (allZero) {
            // we don't have any predictions. This is probably because there are no tasks running. We don't want to skew
            // our scaling factors in this case. Return without doing anything.
            return;
        }

        // See how different our scaled map is from the measured results from our measuredResourceMonitor.
        // of course, the predicted load could be entirely correct, and we just haven't gotten there yet (perhaps the tasks take a while to ramp up).
        // That's why we use a very long learning rate, so that we don't do anything rash.

        // A potential subclass of this could look at predicted load some time in the past -- perhaps, half the
        // length of time of an average task (which would require TaskTracker support to figure out, I think).
        Map<String, Double> idealScalingFactors = new HashMap<String, Double>(startingScalingFactors);
        boolean matches = false;
        for (Map.Entry<String, Double> prediction : predictedLoad.entrySet()) {
            Double currentValue = currentLoad.get(prediction.getKey());
            if (prediction.getValue() != null && currentValue != null) {
                double sf = getStartingScalingFactor(startingScalingFactors, prediction.getKey());

                // Figure out what the scaling factor *should* have been for the two to be equal,
                double idealSF = sf + (prediction.getValue() - currentValue);
                idealScalingFactors.put(prediction.getKey(), idealSF);
                matches = true;
            }
        }
        if (matches) {
            // Send the map of "ideal" scaling factors (those that would have made predictions exactly match real life) into the EWMA function.
            Map<String, Double> newSFMap = ewma.calculate(idealScalingFactors);

            if (log.isTraceEnabled()) {
                log.trace("Predicted task load is " + predictedLoad + ", and current scaling factors are " + startingScalingFactors + "; calculated load is " + currentLoad + ", so new adjusted scaling factors are " + newSFMap);
            }

            loadPredictor.setScalingFactor(newSFMap);
        }
    }

    /**
     * Gets the given key, or 1.0 if not found.
     */
    private double getStartingScalingFactor(Map<String, Double> startingScalingFactors, String key) {
        Double sf = startingScalingFactors.get(key);
        if (sf != null) return sf;
        else return 1.0;
    }

}
