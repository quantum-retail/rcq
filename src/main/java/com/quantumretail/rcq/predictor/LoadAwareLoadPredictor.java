package com.quantumretail.rcq.predictor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Returns the load declared by tasks themselves if they implement LoadAware. Not really "predicting", so to speak...
 * just reporting what the tasks themselves say.
 * If the class doesn't implement LoadAware, we will return a default.
 */
public class LoadAwareLoadPredictor extends ConstantLoadPredictor {
    private static final Logger log = LoggerFactory.getLogger(LoadAwareLoadPredictor.class);

    public LoadAwareLoadPredictor(Map<String, Double> defaultLoad, Map<String, Double> scalingFactor) {
        super(defaultLoad, scalingFactor);
    }

    @Override
    public Map<String, Double> predictLoad(Object o) {
        Map<String, Double> m;
        if (o instanceof LoadAware) {
            m = applyScalingFactor(((LoadAware) o).load());
        } else {
            m = super.predictLoad(o); // fall back on the ConstantLoadPredictor.
        }
        if (log.isTraceEnabled()) {
            log.trace("Predicted load for " + o + ": " + m);
        }
        return m;
    }
}