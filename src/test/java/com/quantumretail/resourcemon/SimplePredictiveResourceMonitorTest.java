package com.quantumretail.resourcemon;

import com.quantumretail.rcq.predictor.LoadPredictor;
import com.quantumretail.rcq.predictor.TaskTracker;
import org.junit.Before;
import org.junit.Test;

import java.util.*;

import static junit.framework.Assert.assertEquals;

/**
 * TODO: document me.
 *
 */
public class SimplePredictiveResourceMonitorTest {

    TestTaskTracker taskTracker = new TestTaskTracker();
    TestLoadPredictor loadPredictor = new TestLoadPredictor();


    @Test
    public void testHappyPath() throws Exception {
        SimplePredictiveResourceMonitor monitor = new SimplePredictiveResourceMonitor(taskTracker, loadPredictor); // without an explicit scalingFactor, we should assume 1.0
        Map<String, Double> task1 = new HashMap<String, Double>();
        task1.put("CPU", 0.3);
        task1.put("MEM", 0.2);

        Map<String, Double> task2 = new HashMap<String, Double>();
        task2.put("CPU", 0.2);
        task2.put("MEM", 0.2);
        task2.put("OTHER", 1.1);

        Map<String, Double> task3 = new HashMap<String, Double>();
        task3.put("CPU", 0.5);
        task3.put("MEM", 0.5);

        registerTask(task1);
        registerTask(task2);
        registerTask(task3);

        Map<String, Double> load = monitor.getLoad();
        assertEquals(3, load.size());
        assertEquals(1.0, load.get("CPU"));
        assertEquals(0.9, load.get("MEM"));
        assertEquals(1.1, load.get("OTHER"));

    }

    @Before
    public void setUp() throws Exception {
        taskTracker.values.clear();
        loadPredictor.values.clear();
    }

    private void registerTask(Map<String, Double> task) {
        String id = UUID.randomUUID().toString();
        taskTracker.values.add(id);
        loadPredictor.values.put(id, task);
    }

    private class TestLoadPredictor implements LoadPredictor {
        Map<Object, Map<String, Double>> values = new HashMap<Object, Map<String, Double>>();

        @Override
        public Map<String, Double> predictLoad(Object o) {
            return values.get(o);
        }
    }

    private class TestTaskTracker implements TaskTracker {
        Collection<Object> values = new ArrayList<Object>();

        @Override
        public Collection<Object> currentTasks() {
            return values;
        }

        @Override
        public Object register(Object nextItem) {
            return values.add(nextItem);
        }
    }
}
