package com.quantumretail.rcq.predictor;

import java.util.Map;

/**
 * A load predictor that takes in a "scaling factor" that will be applied to all of its predictions.
 * The "scaling factor" is intended to represent the difference in capability of a given server, although it could
 * represent any other type of modification that should be applied to one or more resource type. If you use a load
 * predictor that relies on prior, server-agnostic knowledge of the task (for example, the {@link LoadAwareLoadPredictor} that
 * relies on tasks implementing the {@link LoadAware} interface and declaring their resource usage), then you will be
 * able to predict that Task A requires 100% of the resources of a particular server, but what happens if you then run
 * that task on a different server? You can set a server-specific "scaling factor" for that new server, indicating that
 * it has, for example, 2x more memory and 3x more CPU. So something that took 100% of the memory on the smaller server
 * should take 50% of this one.
 *
 * As indicated in that example, scaling factors are applied using the formula:
 * {pre} load / scaling_factor {pre}
 *
 * So if load = <pre>{ MEM : 1.0 }</pre> and scaling factor = <pre>{MEM : 2.0}</pre>, then the resulting load is <pre>{MEM : 0.5}</pre>.
 *
 *
 */
public abstract class ScalingLoadPredictor implements AdjustableLoadPredictor {
    private Map<String, Double> scalingFactor;

    public ScalingLoadPredictor(Map<String, Double> scalingFactor) {
        this.scalingFactor = scalingFactor;
    }

    /**
     * Note that, for performance reasons, we may mutate the input map in-place rather than making a copy.
     * We call this method a lot, and it's an internal method, so it's worth it.
     * @param inputMap MAY BE MUTATED
     * @return
     */
    protected Map<String, Double> applyScalingFactor(Map<String, Double> inputMap) {
        if (scalingFactor != null && !scalingFactor.isEmpty()) {
            for (Map.Entry<String, Double> loadEntry : inputMap.entrySet()) {
                Double sf = scalingFactor.get(loadEntry.getKey());
                if (sf != null && sf != 0 && sf != 1) {  // ignore null because it means "no adjustment", zero because it doesn't make sense, and 1 because it wouldn't change anything.
                    inputMap.put(loadEntry.getKey(), (loadEntry.getValue() / scalingFactor.get(loadEntry.getKey())));
                }
            }
        }
        return inputMap;
    }

    public Map<String, Double> getScalingFactor() {
        return scalingFactor;
    }

    public void setScalingFactor(Map<String, Double> scalingFactor) {
        this.scalingFactor = scalingFactor;
    }
}
