package com.quantumretail.rcq.predictor;

import org.junit.Test;

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
        ScalingLoadPredictor predictor = new ConstantLoadPredictor(null, null);
        assertEquals(0.5, predictor.bound(0.5), DELTA);
        assertEquals(0.1, predictor.bound(0.1), DELTA);
        assertEquals(0.01, predictor.bound(0.0), DELTA);
        assertEquals(0.01, predictor.bound(-3.0), DELTA);
        assertEquals(0.99, predictor.bound(3.0), DELTA);
        assertEquals(0.99, predictor.bound(0.99), DELTA);
        assertEquals(0.99, predictor.bound(1.00), DELTA);
    }

    @Test
    public void testApplyScalingFactor() throws Exception {
        Map<String, Double> scalingFactor = new HashMap<String, Double>();
        scalingFactor.put("FOO", 0.1);
        scalingFactor.put("BAR", 3.0);
        ScalingLoadPredictor predictor = new ConstantLoadPredictor(null, scalingFactor);


        Map<String, Double> loads = new HashMap<String, Double>();
        loads.put("FOO", 1.0);
        loads.put("BAR", 1.0);
        loads.put("another", 0.5);
        Map<String, Double> results = predictor.applyScalingFactor(loads);
        assertEquals(3, results.size());
        assertEquals(0.99, results.get("FOO"), DELTA);
        assertEquals(0.3333, results.get("BAR"), DELTA);
        assertEquals(0.5, results.get("another"), DELTA);


    }
}
