package com.quantumretail.rcq.predictor;

import java.util.Map;

/**
 * Braindead load "predictor" that doesn't predict anything. But might be helpful in some simple cases.
 *
 */
public class ConstantLoadPredictor extends ScalingLoadPredictor {

    Map<String, Double> load;

    public ConstantLoadPredictor(Map<String, Double> load, Map<String, Double> scalingFactor) {
        super(scalingFactor);
        this.load = load;
    }

    @Override
    public Map<String, Double> predictLoad(Object o) {
        return applyScalingFactor(load);
    }


}
