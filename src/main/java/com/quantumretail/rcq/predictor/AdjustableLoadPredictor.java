package com.quantumretail.rcq.predictor;

import java.util.Map;

/**
 * Marks a LoadPredictor whose scalingFactor can be changed at runtime.
 *
 *
 */
public interface AdjustableLoadPredictor extends LoadPredictor {

    /**
     * Get the current ScalingFactor. This map *should not* be changed; it may not be synchronized. It may be
     * unmodifiable, and should be treated as such.
     * To change the scaling factor, use {@link #setScalingFactor(java.util.Map)}.
     *
     * @return
     */
    public Map<String, Double> getScalingFactor();

    /**
     * Replace the current set of scaling factors with a new one.
     *
     * @param newScalingFactor
     */
    public void setScalingFactor(Map<String, Double> newScalingFactor);
}
