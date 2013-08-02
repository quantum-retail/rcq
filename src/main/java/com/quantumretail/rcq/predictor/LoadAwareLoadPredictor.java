package com.quantumretail.rcq.predictor;

import java.util.Map;

/**
 * Returns the load declared by tasks themselves if they implement LoadAware. Not really "predicting", so to speak...
 * just reporting what the tasks themselves say.
 * If the class doesn't implement LoadAware, we will return a default.
 */
public class LoadAwareLoadPredictor extends ConstantLoadPredictor {

    public LoadAwareLoadPredictor(Map<String, Double> defaultLoad, Map<String, Double> scalingFactor) {
        super(defaultLoad, scalingFactor);
    }

    @Override
    public Map<String, Double> predictLoad(Object o) {
        if (o instanceof LoadAware) {
            return applyScalingFactor(((LoadAware) o).load());
        } else {
            return super.predictLoad(o); // fall back on the ConstantLoadPredictor.
        }
    }
}