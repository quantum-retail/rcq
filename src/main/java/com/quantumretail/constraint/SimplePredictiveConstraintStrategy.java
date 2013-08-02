package com.quantumretail.constraint;

import com.quantumretail.rcq.predictor.LoadPredictor;
import com.quantumretail.resourcemon.ResourceMonitor;

import java.util.HashMap;
import java.util.Map;

/**
 * A simple predictive constraint: it returns "true" if the current load + the predicted load of the new item is below
 * a hardcoded threshold.
 *
 */
public class SimplePredictiveConstraintStrategy<T> extends SimpleReactiveConstraintStrategy<T> {

    private final LoadPredictor loadPredictor;

    public SimplePredictiveConstraintStrategy(ResourceMonitor resourceMonitor, Map<String, Double> thresholds, LoadPredictor loadPredictor) {
        super(resourceMonitor, thresholds);
        this.loadPredictor = loadPredictor;

    }


    @Override
    public boolean shouldReturn(T nextItem) {

        // get current load from resourceMonitor
        Map<String, Double> load = getResourceMonitor().getLoad();

        Map<String, Double> itemLoad = loadPredictor.predictLoad(nextItem);

        // add this task's points. Does that put us past the threshold?
        Map<String, Double> newLoad = sum(load, itemLoad);

        //  is current load past the threshold?
        return isUnderThreshold(newLoad);
    }

    private Map<String, Double> sum(Map<String, Double> load, Map<String, Double> itemLoad) {
        Map<String, Double> newLoad = new HashMap<String, Double>(load);
        for (Map.Entry<String, Double> entry : itemLoad.entrySet()) {
            if (entry.getValue() != null) {
                if (newLoad.containsKey(entry.getKey())) {
                    newLoad.put(entry.getKey(), (newLoad.get(entry.getKey()) + entry.getValue()));
                } else {
                    newLoad.put(entry.getKey(), entry.getValue());
                }
            }
        }
        return newLoad;
    }
}
