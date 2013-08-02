package com.quantumretail.resourcemon;

import com.quantumretail.EWMA;
import org.junit.Test;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static junit.framework.Assert.assertEquals;

/**
 * This class tests EWMAMonitor and EWMA together because the classes used to be together.
 * Since this test does cover both classes fairly well, it wasn't worth splitting them out.
 */
public class EWMAMonitorTest {

    private static final double DELTA = 0.000001;

    @Test
    public void test_happy_path() throws Exception {

        final ConstantResourceMonitor rm = ConstantResourceMonitor.build("CPU", 1.0, "MEM", 0.5);
        TestClock clock = new TestClock();
        EWMAMonitor monitor = new EWMAMonitor(rm, new EWMA(10, TimeUnit.MILLISECONDS, clock));

        Map<String, Double> load = monitor.getLoad();
        assertEquals(1.0, load.get("CPU"));
        assertEquals(0.5, load.get("MEM"));

        // 1 ms later, give an update.
        rm.map.put("CPU", 2.0);
        rm.map.put("MEM", 1.0);
        clock.value += TimeUnit.MILLISECONDS.toNanos(1);

        load = monitor.getLoad();
        assertEquals(1.066967, load.get("CPU"), DELTA);
        assertEquals(0.533483, load.get("MEM"), DELTA);

        clock.value += TimeUnit.MILLISECONDS.toNanos(1);
        load = monitor.getLoad();
        assertEquals(1.129449, load.get("CPU"), DELTA);
        assertEquals(0.564724, load.get("MEM"), DELTA);

        // 30 seconds later, give an update. By this point, our old values have expired.
        clock.value += TimeUnit.SECONDS.toNanos(30);
        rm.map.put("CPU", 3.0);
        rm.map.put("MEM", 2.0);
        load = monitor.getLoad();

        System.out.println("Load is: " + load);
        assertEquals(3.0, load.get("CPU"), DELTA);
        assertEquals(2.0, load.get("MEM"), DELTA);
    }

    @Test
    public void test_inc_update() throws Exception {

        final ConstantResourceMonitor rm = ConstantResourceMonitor.build("CPU", 1.0, "MEM", 0.5);
        TestClock clock = new TestClock();
        EWMAMonitor monitor1 = new EWMAMonitor(rm, new EWMA(10, TimeUnit.MILLISECONDS, clock));
        EWMAMonitor monitor2 = new EWMAMonitor(rm, new EWMA(10, TimeUnit.MILLISECONDS, clock));

        Map<String, Double> load = monitor1.getLoad();
        assertEquals(1.0, load.get("CPU"));
        assertEquals(0.5, load.get("MEM"));

        // monitor2 has the same inputs, so of course it should be the same:
        load = monitor2.getLoad();
        assertEquals(1.0, load.get("CPU"));
        assertEquals(0.5, load.get("MEM"));

        // things have spiked. Or something...
        rm.map.put("CPU", 2.0);
        rm.map.put("MEM", 1.0);

        // 1 ms later, get an update from monitor 1.
        clock.value += TimeUnit.MILLISECONDS.toNanos(1);
        load = monitor1.getLoad();
        assertEquals(1.066967, load.get("CPU"), DELTA);
        assertEquals(0.533483, load.get("MEM"), DELTA);

        // and again
        clock.value += TimeUnit.MILLISECONDS.toNanos(1);
        load = monitor1.getLoad();
        assertEquals(1.129449, load.get("CPU"), DELTA);
        assertEquals(0.564724, load.get("MEM"), DELTA);

        // take monitor 2, and just update it at 2ms. It should get the same results, even though it was only called once.
        load = monitor2.getLoad();
        assertEquals(1.129449, load.get("CPU"), DELTA);
        assertEquals(0.564724, load.get("MEM"), DELTA);

    }


    @Test
    public void test_same_values() throws Exception {
        final ConstantResourceMonitor rm = ConstantResourceMonitor.build("CPU", 1.0, "MEM", 0.5);
        TestClock clock = new TestClock();
        EWMAMonitor monitor = new EWMAMonitor(rm, new EWMA(10, TimeUnit.MILLISECONDS, clock));

        Map<String, Double> load = monitor.getLoad();
        assertEquals(3, load.size()); // it's 3 because of the "alpha" value.
        assertEquals(1.0, load.get("CPU"));
        assertEquals(0.5, load.get("MEM"));

        load = monitor.getLoad();
        assertEquals(1.0, load.get("CPU"));
        assertEquals(0.5, load.get("MEM"));

        load = monitor.getLoad();
        assertEquals(1.0, load.get("CPU"));
        assertEquals(0.5, load.get("MEM"));

        load = monitor.getLoad();
        assertEquals(3, load.size()); // it's 3 because of the "alpha" value.
        assertEquals(1.0, load.get("CPU"));
        assertEquals(0.5, load.get("MEM"));
    }

    @Test
    public void test_advance_by_halflife() throws Exception {
        final ConstantResourceMonitor rm = ConstantResourceMonitor.build("CPU", 1000.0, "MEM", 500.0);
        TestClock clock = new TestClock();
        EWMAMonitor monitor = new EWMAMonitor(rm, new EWMA(2, TimeUnit.MILLISECONDS, clock));

        Map<String, Double> load;

        load = monitor.getLoad();
        assertEquals(1000.0, load.get("CPU"), DELTA);
        assertEquals(500, load.get("MEM"), DELTA);

        rm.map.put("CPU", 10.0);
        rm.map.put("MEM", 10.0);

        // advance the clock by the halflife value
        clock.value += TimeUnit.MILLISECONDS.toNanos(2);
        load = monitor.getLoad();
        assertEquals(505.0, load.get("CPU"), DELTA);
        assertEquals(255.0, load.get("MEM"), DELTA);

        // advance the clock by the halflife value
        clock.value += TimeUnit.MILLISECONDS.toNanos(2);
        load = monitor.getLoad();
        assertEquals(257.5, load.get("CPU"), DELTA);
        assertEquals(132.5, load.get("MEM"), DELTA);

        // advance the clock by the halflife value
        clock.value += TimeUnit.MILLISECONDS.toNanos(2);
        load = monitor.getLoad();
        assertEquals(133.75, load.get("CPU"), DELTA);
        assertEquals(71.25, load.get("MEM"), DELTA);

        // it should eventually trend to 10.0, if we wait long enough.
        clock.value += TimeUnit.MINUTES.toNanos(1);
        load = monitor.getLoad();
        assertEquals(10.0, load.get("CPU"), DELTA);
        assertEquals(10.0, load.get("MEM"), DELTA);
    }

    @Test
    public void test_high_alpha() throws Exception {
        final ConstantResourceMonitor rm = ConstantResourceMonitor.build("CPU", 1000.0, "MEM", 100.0);
        TestClock clock = new TestClock();
        EWMAMonitor monitor = new EWMAMonitor(rm, new EWMA(1, TimeUnit.NANOSECONDS, clock));

        Map<String, Double> load;

        load = monitor.getLoad();
        assertEquals(1000.0, load.get("CPU"), DELTA);
        assertEquals(100, load.get("MEM"), DELTA);

        rm.map.put("CPU", 570.0);
        rm.map.put("MEM", 32.0);
        // advance the clock
        clock.value += TimeUnit.MILLISECONDS.toNanos(2);
        load = monitor.getLoad();
        assertEquals(570.0, load.get("CPU"), DELTA);
        assertEquals(32.0, load.get("MEM"), DELTA);

        rm.map.put("CPU", 10.0);
        rm.map.put("MEM", 3.0);
        // advance the clock
        clock.value += TimeUnit.MILLISECONDS.toNanos(2);
        load = monitor.getLoad();
        assertEquals(10.0, load.get("CPU"), DELTA);
        assertEquals(3, load.get("MEM"), DELTA);
    }

    public static class TestClock implements EWMA.Clock {
        long value = 0;

        @Override
        public long nanoTime() {
            return value;
        }
    }
}
