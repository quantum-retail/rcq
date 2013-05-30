package com.quantumretail.resourcemon;

import org.junit.Test;

/**
 * TODO: document me.
 *
 */
public class LoadAverageResourceMonitorTest {

    @Test
    public void testSanity() throws Exception {
        LoadAverageResourceMonitor monitor = new LoadAverageResourceMonitor();
        System.out.println(monitor.getLoad());

    }
}
