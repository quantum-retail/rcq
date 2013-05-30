package com.quantumretail.resourcemon;

import org.junit.Test;

public class HeapResourceMonitorTest {

    @Test
    public void testHappyPath() throws Exception {
        HeapResourceMonitor monitor = new HeapResourceMonitor();
        System.out.print(monitor.getLoad());
    }
}
