package com.quantumretail;

import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static junit.framework.Assert.assertEquals;

/**
 * The EWMAMonitorTest already covers most of EWMA quite well. This test mainly covers some implementation details.
 *
 */
public class EWMATest {

    private static final double DELTA = 0.000001;

    @Test
    public void test_calc_alpha() throws Exception {
        double val = EWMA.alpha(6584875, TimeUnit.SECONDS.toNanos(1));
        assertEquals(1.0, val, DELTA);
    }

    @Test
    public void test_calc_alpha_short_halflife() throws Exception {
        double val = EWMA.alpha(1, TimeUnit.SECONDS.toMillis(3));
        assertEquals(1.0, val, DELTA);
    }

    @Test
    public void test_calc_alpha_long_halflife() throws Exception {
        double val = EWMA.alpha(TimeUnit.DAYS.toMillis(1), TimeUnit.SECONDS.toMillis(3));
        assertEquals(0.000024, val, DELTA);
    }

    @Test
    public void test_calc_alpha_overflow_test() throws Exception {
        double val = EWMA.alpha(TimeUnit.DAYS.toMillis(1), TimeUnit.SECONDS.toMillis(3));
        assertEquals(0.000024, val, DELTA);
    }

}
