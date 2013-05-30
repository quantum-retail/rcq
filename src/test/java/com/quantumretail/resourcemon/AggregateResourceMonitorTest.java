package com.quantumretail.resourcemon;

import org.junit.Test;

import java.util.Collections;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * There's not much functionality here to test, really.
 *
 */
public class AggregateResourceMonitorTest {

    private static final double DELTA = 0.00000001;

    @Test
    public void testSanity() throws Exception {
        // when we construct AggregateResourceMonitor with no arguments, it uses some default monitors.
        AggregateResourceMonitor monitor = new AggregateResourceMonitor();
        // we should currently have 2 fields by default.
        Map<String, Double> map = monitor.getLoad();

        assertTrue(map.containsKey(ResourceMonitor.HEAP_MEM));
        assertTrue(map.containsKey(ResourceMonitor.LOAD_AVERAGE));
    }


    @Test
    public void testMultipleMonitors() throws Exception {
        ResourceMonitor mon1 = new ResourceMonitor() {
            @Override
            public Map<String, Double> getLoad() {
                return Collections.singletonMap("FOO", 0.1);
            }
        };

        ResourceMonitor mon2 = new ResourceMonitor() {
            @Override
            public Map<String, Double> getLoad() {
                return Collections.singletonMap("BAR", 0.99);
            }
        };

        AggregateResourceMonitor monitor = new AggregateResourceMonitor(mon1, mon2);
        Map<String, Double> map = monitor.getLoad();
        assertEquals(0.1, map.get("FOO"), DELTA);
        assertEquals(0.99, map.get("BAR"), DELTA);
        assertEquals(2, map.size());
    }

    @Test
    public void testOverlappingMonitors() throws Exception {
        ResourceMonitor mon1 = new ResourceMonitor() {
            @Override
            public Map<String, Double> getLoad() {
                return Collections.singletonMap("FOO", 0.1);
            }
        };

        ResourceMonitor mon2 = new ResourceMonitor() {
            @Override
            public Map<String, Double> getLoad() {
                return Collections.singletonMap("FOO", 0.99);
            }
        };

        AggregateResourceMonitor monitor = new AggregateResourceMonitor(mon1, mon2);
        Map<String, Double> map = monitor.getLoad();
        assertEquals(0.99, map.get("FOO"), DELTA); // last one wins, currently.


    }
}
