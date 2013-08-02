package com.quantumretail.rcq.predictor;

import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static junit.framework.Assert.assertEquals;

/**
 * TODO: document me.
 *
 */
public class ConstantLoadPredictorTest {

    @Test
    public void testScalingFactor_happyPath() throws Exception {
        Map<String, Double> load = new HashMap<String, Double>();
        load.put("CPU", 0.3);
        load.put("MEM", 0.2);

        Map<String, Double> scale = new HashMap<String, Double>();
        scale.put("CPU", 2.0);
        scale.put("MEM", 2.0);

        ConstantLoadPredictor loadPredictor = new ConstantLoadPredictor(load, scale);
        Map<String, Double> results = loadPredictor.predictLoad(null);

        assertEquals(2, results.size());
        assertEquals(.15, results.get("CPU"));
        assertEquals(.1, results.get("MEM"));

    }

    @Test
    public void testScalingFactor_reverse() throws Exception {
        Map<String, Double> load = new HashMap<String, Double>();
        load.put("CPU", 0.3);
        load.put("MEM", 0.2);

        Map<String, Double> scale = new HashMap<String, Double>();
        scale.put("CPU", 0.5);
        scale.put("MEM", 0.5);

        ConstantLoadPredictor loadPredictor = new ConstantLoadPredictor(load, scale);
        Map<String, Double> results = loadPredictor.predictLoad(null);

        assertEquals(2, results.size());
        assertEquals(.6, results.get("CPU"));
        assertEquals(.4, results.get("MEM"));

    }

    @Test
    public void testNonOverlapping() throws Exception {

        Map<String, Double> load = new HashMap<String, Double>();
        load.put("CPU", 0.3);
        load.put("MEM", 0.2);

        Map<String, Double> scale = new HashMap<String, Double>();
        scale.put("CPU", 2.0);
        scale.put("__FOO", 0.5);

        ConstantLoadPredictor loadPredictor = new ConstantLoadPredictor(load, scale);
        Map<String, Double> results = loadPredictor.predictLoad(null);

        assertEquals(2, results.size());
        assertEquals(.15, results.get("CPU"));
        assertEquals(.2, results.get("MEM"));
    }

    @Test
    public void test_null() throws Exception {

        Map<String, Double> load = new HashMap<String, Double>();
        load.put("CPU", 0.3);
        load.put("MEM", 0.2);

        ConstantLoadPredictor loadPredictor = new ConstantLoadPredictor(load, null);
        Map<String, Double> results = loadPredictor.predictLoad(null);

        assertEquals(2, results.size());
        assertEquals(.3, results.get("CPU"));
        assertEquals(.2, results.get("MEM"));
    }

    @Test
    public void test_blank() throws Exception {

        Map<String, Double> load = new HashMap<String, Double>();
        load.put("CPU", 0.3);
        load.put("MEM", 0.2);

        ConstantLoadPredictor loadPredictor = new ConstantLoadPredictor(load, Collections.<String, Double>emptyMap());
        Map<String, Double> results = loadPredictor.predictLoad(null);

        assertEquals(2, results.size());
        assertEquals(.3, results.get("CPU"));
        assertEquals(.2, results.get("MEM"));
    }


    @Test
    public void test_zeroScale() throws Exception {

        Map<String, Double> load = new HashMap<String, Double>();
        load.put("CPU", 0.3);
        load.put("MEM", 0.2);


        Map<String, Double> scale = new HashMap<String, Double>();
        scale.put("CPU", 0.0);
        scale.put("MEM", 2.0);

        ConstantLoadPredictor loadPredictor = new ConstantLoadPredictor(load, scale);

        Map<String, Double> results = loadPredictor.predictLoad(null);

        assertEquals(2, results.size());
        assertEquals(.3, results.get("CPU"));
        assertEquals(0.1, results.get("MEM"));
    }
//
//
//    public void meh() {
//        SimplePredictiveResourceMonitor monitor = new SimplePredictiveResourceMonitor(taskTracker, loadPredictor);
//
//        Map<String, Double> task2 = new HashMap<String, Double>();
//        task2.put("CPU", 0.2);
//        task2.put("MEM", 0.2);
//        task2.put("OTHER", 1.1);
//
//        Map<String, Double> task3 = new HashMap<String, Double>();
//        task3.put("CPU", 0.5);
//        task3.put("MEM", 0.5);
//
//        taskTracker.values.clear();
//        taskTracker.values.add(task1);
//        taskTracker.values.add(task2);
//        taskTracker.values.add(task3);
//
//        Map<String, Double> load = monitor.getLoad();
//        assertEquals(3, load.size());
//        assertEquals(2.0, load.get("CPU"));
//        assertEquals(1.8, load.get("MEM"));
//        assertEquals(2.2, load.get("OTHER"));
//    }
//
//
//    @Test
//    public void testScalingFactor_high() throws Exception {
//
//        SimplePredictiveResourceMonitor monitor = new SimplePredictiveResourceMonitor(taskTracker, loadPredictor);
//        // same values as before, but scaled to 0.5.
//        Map<String, Double> task1 = new HashMap<String, Double>();
//        task1.put("CPU", 0.3);
//        task1.put("MEM", 0.2);
//
//        Map<String, Double> task2 = new HashMap<String, Double>();
//        task2.put("CPU", 0.2);
//        task2.put("MEM", 0.2);
//        task2.put("OTHER", 1.1);
//
//        Map<String, Double> task3 = new HashMap<String, Double>();
//        task3.put("CPU", 0.5);
//        task3.put("MEM", 0.5);
//
//        taskTracker.values.clear();
//        taskTracker.values.add(task1);
//        taskTracker.values.add(task2);
//        taskTracker.values.add(task3);
//
//        Map<String, Double> load = monitor.getLoad();
//        assertEquals(3, load.size());
//        assertEquals(0.5, load.get("CPU"));
//        assertEquals(0.45, load.get("MEM"));
//        assertEquals(0.55, load.get("OTHER"));
//    }

}
