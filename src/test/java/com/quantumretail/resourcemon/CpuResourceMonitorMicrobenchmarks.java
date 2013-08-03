package com.quantumretail.resourcemon;

import com.carrotsearch.junitbenchmarks.AbstractBenchmark;
import org.junit.Test;

import java.util.Map;

/**
 * A simple microbenchmark to test methods of getting CPU time.
 *
 */
public class CpuResourceMonitorMicrobenchmarks extends AbstractBenchmark {

    public static final int COUNT = 100000;

//    /**
//     * This only compiles on JDK7
//     * @throws Exception
//     */
//    @Test
//    public void testDirectMethod() throws Exception {
//        OperatingSystemMXBean operatingSystemMXBean = ManagementFactory.getOperatingSystemMXBean();
//        Double d = null;
//        for (int i = 0; i < COUNT; i++) {
//            d = ((com.sun.management.OperatingSystemMXBean) operatingSystemMXBean).getSystemCpuLoad();
//            if (d.isNaN() || d <= 0.0) {
//                d = ((com.sun.management.OperatingSystemMXBean) operatingSystemMXBean).getProcessCpuLoad();
//            }
//            if (!d.isNaN() && d >= 0.0) {
//                if (d > 1) d = 1.0;
//            }
//        }
////        System.out.println("D: "+d);
//    }


    @Test
    public void testSunMethod() throws Exception {
        // this only works w/ JDK7
        CpuResourceMonitor cpuResourceMonitor = new CpuResourceMonitor();
        Double d = null;
        for (int i = 0; i < COUNT; i++) {
            d = cpuResourceMonitor.getSunMethod();
        }
//        System.out.println("D: "+d);
    }

    @Test
    public void testProcMethod() throws Exception {
        CpuResourceMonitor cpuResourceMonitor = new CpuResourceMonitor(false, true);
        Map<String, Double> d = null;
        for (int i = 0; i < COUNT; i++) {
            d = cpuResourceMonitor.getCPU();
        }
//        System.out.println("D: "+d);
    }


}
