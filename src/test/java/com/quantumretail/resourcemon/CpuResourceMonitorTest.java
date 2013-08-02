package com.quantumretail.resourcemon;

import org.junit.Test;

import java.lang.management.ManagementFactory;

import static junit.framework.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * CpuResourceMonitor's behavior is JVM-dependant, so it's tough to test. We'll do our best to do some cursory tests for bad logic here.
 *
 */
public class CpuResourceMonitorTest {


    private static final double DELTA = 0.0000001;

    @Test
    public void testGetSunMethod() throws Exception {
        // this only works on Sun or OpenJDK version 1.7+
        CpuResourceMonitor monitor = new CpuResourceMonitor(true, false);
        Double cpu = monitor.getCPU();
        System.out.println("CPU is " + cpu);
        if (ManagementFactory.getRuntimeMXBean().getVmVendor().contains("Oracle")) {
            assertNotNull(cpu);
        }
    }

    @Test
    public void testGetProcessTimeMethod() throws Exception {
        CpuResourceMonitor monitor = new CpuResourceMonitor(false, true);
        long start = System.currentTimeMillis(); // we need to wait a bit before we get an interesting result. ANd no better way than to busy-wait...maybe we'll get a useful CPU metric, too.
        double random = 0.0;
        while (System.currentTimeMillis() < (start + 500)) {
            random = Math.random() * 100 % 9;
        }
        Double cpu = monitor.getCPU();
        System.out.println("CPU is " + cpu + ", while our random number is " + random);
        assertNotNull(cpu);
        // uncomment when this is working:
//        assertTrue("CPU should have been something > 0.0", cpu > 0.0);
    }

    @Test
    public void test_getProcessTime_edges() throws Exception {
        CpuResourceMonitor monitor = new CpuResourceMonitor(false, true);
        CpuResourceMonitor.ProcessCpuTime prev = new CpuResourceMonitor.ProcessCpuTime(0, 0, -1);
        CpuResourceMonitor.ProcessCpuTime value = monitor.getProcessTime(1, 1, prev, 1);
        assertEquals(prev, value);

        prev = new CpuResourceMonitor.ProcessCpuTime(1, 1, -1);
        value = monitor.getProcessTime(1, 1, prev, 1);
        assertEquals(prev, value); // we still don't have enough to go by to return a valid value -- we don't have 2 unique measurements.

        value = monitor.getProcessTime(2, 2, prev, 1);
        assertEquals(prev, value); // we still don't have enough to go by to return a valid value -- measurements are within 10 milliseconds

        long later = 20000001;
        value = monitor.getProcessTime(later, later, prev, 1);
        assertEquals(later, value.processCpuTimeNanos);
        assertEquals(later, value.timestampNanos);
        assertEquals(1.0, value.cpuLoad, DELTA);

        // now calling it again where current = prev should return the previous reading.
        CpuResourceMonitor.ProcessCpuTime value2 = monitor.getProcessTime(2, 5, value, 1);
        assertEquals(value, value2);
    }
}
