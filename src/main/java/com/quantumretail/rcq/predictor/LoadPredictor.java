package com.quantumretail.rcq.predictor;

import java.util.Map;

/**
 * TODO: document me.
 *
 */
public interface LoadPredictor {

    Map<String, Double> predictLoad(Object o);

}
