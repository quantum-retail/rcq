package com.quantumretail.rcq.predictor;

import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

/**
 * TODO: document me.
 *
 */
public class ScalingLoadPredictorTest {

    private static final double DELTA = 0.001;

    @Test
    public void testBound() throws Exception {
        ScalingLoadPredictor predictor = new ConstantLoadPredictor(Collections.<String, Double>emptyMap(), null);
        assertEquals(0.5, predictor.bound(0.5), DELTA);
        assertEquals(ScalingLoadPredictor.MIN_BOUND, predictor.bound(ScalingLoadPredictor.MIN_BOUND), DELTA);
        assertEquals(ScalingLoadPredictor.MIN_BOUND, predictor.bound(0.0), DELTA);
        assertEquals(ScalingLoadPredictor.MIN_BOUND, predictor.bound(-3.0), DELTA);
        assertEquals(ScalingLoadPredictor.MAX_BOUND, predictor.bound(ScalingLoadPredictor.MAX_BOUND), DELTA);
        assertEquals(ScalingLoadPredictor.MAX_BOUND, predictor.bound(3.0), DELTA);
        assertEquals(ScalingLoadPredictor.MAX_BOUND, predictor.bound(1.00), DELTA);
    }

    @Test
    public void testApplyScalingFactor() throws Exception {
        Map<String, Double> scalingFactor = new HashMap<String, Double>();
        scalingFactor.put("FOO", 0.1);
        scalingFactor.put("BAR", 3.0);
        ScalingLoadPredictor predictor = new ConstantLoadPredictor(Collections.<String, Double>emptyMap(), scalingFactor);


        Map<String, Double> loads = new HashMap<String, Double>();
        loads.put("FOO", 1.0);
        loads.put("BAR", 1.0);
        loads.put("another", 0.5);
        Map<String, Double> results = predictor.applyScalingFactor(loads);
        assertEquals(3, results.size());
        assertEquals(ScalingLoadPredictor.MAX_BOUND, results.get("FOO"), DELTA);
        assertEquals(0.3333, results.get("BAR"), DELTA);
        assertEquals(0.5, results.get("another"), DELTA);


    }
}
