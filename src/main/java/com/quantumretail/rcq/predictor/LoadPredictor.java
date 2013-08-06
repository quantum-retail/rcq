package com.quantumretail.rcq.predictor;

import java.util.Map;

/**
 * Predict the load for this particular object
 */
public interface LoadPredictor {

    Map<String, Double> predictLoad(Object o);

}
