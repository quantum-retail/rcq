package com.quantumretail.rcq.predictor;

import java.util.Map;

/**
 * Implemented by a task if it knows what kind of load it will have.
 * Load should be a map of key (representing load type) to a double, which should be between 0 and 1.
 */
public interface LoadAware {

    Map<String, Double> load();

}
