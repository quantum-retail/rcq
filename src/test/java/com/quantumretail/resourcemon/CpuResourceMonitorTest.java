package com.quantumretail.resourcemon;

import org.junit.Test;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertNotNull;

/**
 * CpuResourceMonitor's behavior is JVM-dependant, so it's tough to test. We'll do our best to do some cursory tests for bad logic here.
 *
 */
public class CpuResourceMonitorTest {


    @Test
    public void testGetSunMethod() throws Exception {
        // this only works on Sun or OpenJDK version 1.7+
        CpuResourceMonitor monitor = new CpuResourceMonitor(true, false);
        Double cpu = monitor.getCPU();
        System.out.println("CPU is "+ cpu);
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
        System.out.println("CPU is "+ cpu+", while our random number is "+ random);
        assertNotNull(cpu);
    }
}
