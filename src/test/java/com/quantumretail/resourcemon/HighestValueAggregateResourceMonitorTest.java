package com.quantumretail.resourcemon;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static junit.framework.Assert.assertEquals;

/**
 * TODO: document me.
 *
 */
public class HighestValueAggregateResourceMonitorTest {


    @Test(expected = IllegalArgumentException.class)
    public void test_fail_with_no_inputs() throws Exception {
        new HighestValueAggregateResourceMonitor();
    }

    @Test
    public void test_happy_path1() throws Exception {

        final Map<String, Double> m1 = new HashMap<String, Double>();
        m1.put("CPU", 0.3);
        m1.put("MEM", 0.2);
        final ResourceMonitor rm1 = new TestResourceMonitor(m1);

        final Map<String, Double> m2 = new HashMap<String, Double>();
        m2.put("CPU", 0.2);
        m2.put("MEM", 0.2);
        m2.put("OTHER", 1.1);
        final ResourceMonitor rm2 = new TestResourceMonitor(m2);

        final Map<String, Double> m3 = new HashMap<String, Double>();
        m3.put("CPU", 0.5);
        m3.put("MEM", 0.1);
        final ResourceMonitor rm3 = new TestResourceMonitor(m3);

        HighestValueAggregateResourceMonitor monitor = new HighestValueAggregateResourceMonitor(rm1, rm2, rm3);

        Map<String, Double> load = monitor.getLoad();
        assertEquals(3, load.size());
        assertEquals(0.5, load.get("CPU"));
        assertEquals(0.2, load.get("MEM"));
        assertEquals(1.1, load.get("OTHER"));

    }


    @Test
    public void test_happy_path2() throws Exception {
        // like case 1, but where the highest values are all either in rm1 or rm2
        final Map<String, Double> m1 = new HashMap<String, Double>();
        m1.put("CPU", 0.9);
        m1.put("MEM", 0.2);
        final ResourceMonitor rm1 = new TestResourceMonitor(m1);

        final Map<String, Double> m2 = new HashMap<String, Double>();
        m2.put("CPU", 0.2);
        m2.put("MEM", 0.4);
        m2.put("OTHER", 1.1);
        final ResourceMonitor rm2 = new TestResourceMonitor(m2);

        final Map<String, Double> m3 = new HashMap<String, Double>();
        m3.put("CPU", 0.5);
        m3.put("MEM", 0.1);
        final ResourceMonitor rm3 = new TestResourceMonitor(m3);

        HighestValueAggregateResourceMonitor monitor = new HighestValueAggregateResourceMonitor(rm1, rm2, rm3);

        Map<String, Double> load = monitor.getLoad();
        assertEquals(3, load.size());
        assertEquals(0.9, load.get("CPU"));
        assertEquals(0.4, load.get("MEM"));
        assertEquals(1.1, load.get("OTHER"));

    }

    public static class TestResourceMonitor implements ResourceMonitor {
        public final Map<String, Double> map;

        public TestResourceMonitor(Map<String, Double> map) {
            this.map = map;
        }

        @Override
        public Map<String, Double> getLoad() {
            return map;
        }
    }
}
