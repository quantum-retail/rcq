package com.quantumretail.resourcemon;

import com.quantumretail.TaskTracker;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import static junit.framework.Assert.assertEquals;

/**
 * TODO: document me.
 *
 */
public class TaskDictatedResourceMonitorTest {

    TestTaskTracker taskTracker = new TestTaskTracker();


    @Test
    public void testHappyPath() throws Exception {
        TaskDictatedResourceMonitor monitor = new TaskDictatedResourceMonitor(taskTracker); // without an explicit scalingFactor, we should assume 1.0
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

        taskTracker.values.clear();
        taskTracker.values.add(task1);
        taskTracker.values.add(task2);
        taskTracker.values.add(task3);

        Map<String, Double> load = monitor.getLoad();
        assertEquals(3, load.size());
        assertEquals(1.0, load.get("CPU"));
        assertEquals(0.9, load.get("MEM"));
        assertEquals(1.1, load.get("OTHER"));

    }

    @Test
    public void testScalingFactor_low() throws Exception {

        TaskDictatedResourceMonitor monitor = new TaskDictatedResourceMonitor(taskTracker, 0.5);
        // same values as before, but scaled to 0.5.
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

        taskTracker.values.clear();
        taskTracker.values.add(task1);
        taskTracker.values.add(task2);
        taskTracker.values.add(task3);

        Map<String, Double> load = monitor.getLoad();
        assertEquals(3, load.size());
        assertEquals(2.0, load.get("CPU"));
        assertEquals(1.8, load.get("MEM"));
        assertEquals(2.2, load.get("OTHER"));
    }


    @Test
    public void testScalingFactor_high() throws Exception {

        TaskDictatedResourceMonitor monitor = new TaskDictatedResourceMonitor(taskTracker, 2.0);
        // same values as before, but scaled to 0.5.
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

        taskTracker.values.clear();
        taskTracker.values.add(task1);
        taskTracker.values.add(task2);
        taskTracker.values.add(task3);

        Map<String, Double> load = monitor.getLoad();
        assertEquals(3, load.size());
        assertEquals(0.5, load.get("CPU"));
        assertEquals(0.45, load.get("MEM"));
        assertEquals(0.55, load.get("OTHER"));
    }

    @Before
    public void setUp() throws Exception {
        taskTracker.values.clear();
    }

    private class TestTaskTracker implements TaskTracker {
        Collection<Map<String, Double>> values = new ArrayList<Map<String, Double>>();

        @Override
        public Collection<Map<String, Double>> getCurrentlyExecutingTasks() {
            return values;
        }
    }
}
