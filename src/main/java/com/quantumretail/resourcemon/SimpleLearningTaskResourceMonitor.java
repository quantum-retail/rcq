package com.quantumretail.resourcemon;

import com.quantumretail.TaskTracker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Returns a resourceMonitor where "load" is defined as the sum of the predicted loads of each task currently running,
 * as reported by the TaskTracker provided in the constructor.
 *
 * It might be helpful to combine this ResourceMonitor with one that measures actual resource usage
 * (AggregateResourceMonitor, CpuResourceMonitor, LoadAverageResourceMonitor, etc.) within a
 * HighestValueAggregateResourceMonitor; that way, you'll be able to react to the predicted load OR real load,
 * whichever is higher.
 */
public class SimpleLearningTaskResourceMonitor extends TaskDictatedResourceMonitor {
    private static final Logger log = LoggerFactory.getLogger(SimpleLearningTaskResourceMonitor.class);
    final ResourceMonitor measuredResourceMonitor;
    final EWMA ewma;
    final static String SF = "SF";
    double scalingFactor;

    public SimpleLearningTaskResourceMonitor(TaskTracker taskTracker, double scalingFactor, ResourceMonitor measuredResourceMonitor) {
        super(taskTracker, scalingFactor);
        this.scalingFactor = scalingFactor;
        this.measuredResourceMonitor = measuredResourceMonitor;
        this.ewma = new EWMA(1, TimeUnit.HOURS); // long halflife; we want to make adjustments very slowly.
        ewma.calculate(Collections.singletonMap(SF, scalingFactor));
    }

    /**
     * Ok, here's the tricky part of this class.  We're going to see how different our scaled map is from the measured
     * results from our measuredResourceMonitor. We're going to figure out what the scaling factor *should* have been
     * for the two to be equal, and we're going to send that into the fairly long-halflife EWMA function. So over time
     * the scaling factor should trend toward ideal, based on measured values.
     *
     * @param inputMap MAY BE MUTATED
     * @param scalingFactor
     * @return
     */
    @Override
    protected Map<String, Double> applyScalingFactor(Map<String, Double> inputMap, double scalingFactor) {
        Map<String, Double> predictedLoad = super.applyScalingFactor(inputMap, scalingFactor);
        Map<String, Double> currentLoad = measuredResourceMonitor.getLoad();
        // See how different our scaled map is from the measured results from our measuredResourceMonitor.
        // of course, the predicted load could be entirely correct, and we just haven't gotten there yet (perhaps the tasks take a while to ramp up).
        // That's why we use a very long learning rate, so that we don't do anything rash.
        double sumOfDifferences = 0.0;
        int terms = 0;
        for (Map.Entry<String, Double> entry : currentLoad.entrySet()) {
            Double v = predictedLoad.get(entry.getKey());
            if (entry.getValue() != null && v != null) {
                terms++;
                sumOfDifferences += (v - entry.getValue());
            }
        }
        if (terms == 0) {
            log.warn("The measured resource monitor (" + measuredResourceMonitor.getClass() + ") and predicted resource monitor (" + super.getClass() + ") don't share any metrics in common. We won't be able to apply any learning.");
            return predictedLoad;
        } else {
            double avgDifference = sumOfDifferences / terms;

            // Figure out what the scaling factor *should* have been for the two to be equal,
            double hypotheticalScalingFactor = scalingFactor + avgDifference;

            // Send that into the EWMA function.

            Map<String, Double> newSFMap = ewma.calculate(Collections.singletonMap(SF, hypotheticalScalingFactor));
            double newSF = newSFMap.get(SF);

            if (log.isTraceEnabled()) {
                log.trace("Predicted task load is " + predictedLoad + ", and current scaling factor is " + scalingFactor + "; calculated load is " + currentLoad + ", so new adjusted scaling factor is " + newSF);
            }

            this.scalingFactor = newSF;
            return predictedLoad;
        }
    }


    @Override
    public double getScalingFactor() {
        return this.scalingFactor;
    }
}
