package com.quantumretail.collections;

import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.quantumretail.constraint.ConstraintStrategy;
import com.quantumretail.constraint.SimpleReactiveConstraintStrategy;
import com.quantumretail.resourcemon.ResourceMonitor;
import org.junit.Test;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static org.junit.Assert.*;

public class ResourceConstrainingQueueTest {

    final List<Future> futures = new ArrayList<Future>();

    /**
     * this test is not entirely deterministic, so it's disabled by default.
     * But it is a really interesting test to run now and again to see how well things operate.
     * @throws Exception
     */
//    @Test
    public void testHappyPath() throws Exception {
        long start = System.currentTimeMillis();
        int numProcessors = ManagementFactory.getOperatingSystemMXBean().getAvailableProcessors();
        Map<String, Double> thresholds = new HashMap<String, Double>();
        final Double CPU_THRESHOLD = 0.9;
        thresholds.put(ResourceMonitor.CPU, CPU_THRESHOLD);
//        final ResourceMonitor monitor = new CachingResourceMonitor(new AggregateResourceMonitor(new SigarResourceMonitor(), new HeapResourceMonitor(), new LoadAverageResourceMonitor(), new CpuResourceMonitor(), new EWMAMonitor(new CpuResourceMonitor(), 100, TimeUnit.MILLISECONDS)), 100L);
//        final ResourceMonitor monitor = new CachingResourceMonitor(new AggregateResourceMonitor(), 100L);
        MetricRegistry metricRegistry = new MetricRegistry();

        ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(1, new ResourceConstrainingQueues.NameableDaemonThreadFactory("thread-monitor-"));
//        ResourceConstrainingQueue<Runnable> queue = new ResourceConstrainingQueue<Runnable>(new LinkedBlockingQueue<Runnable>(), ConstraintStrategies.<Runnable>defaultReactiveConstraintStrategy(thresholds, 100), 100);
        ResourceConstrainingQueue<Runnable> queue = ResourceConstrainingQueues.<Runnable>defaultQueue(thresholds);

        queue.registerMetrics(metricRegistry, "queue");

        ThreadPoolExecutor ex = new ThreadPoolExecutor(4 * numProcessors, 4 * numProcessors, 0L, TimeUnit.MILLISECONDS, queue);

        // send in the metricsRegistry if you want a super-verbose view of what's going on:
        ThreadMonitor threadMonitor = new ThreadMonitor(ex, null);
//        ThreadMonitor threadMonitor = new ThreadMonitor(ex, metricRegistry);

        ScheduledFuture future = scheduledExecutorService.scheduleAtFixedRate(threadMonitor, 100, 100, TimeUnit.MILLISECONDS);

        for (int i = 0; i < numProcessors * 500; i++) {
            futures.add(ex.submit(new BusyWaiter(100)));
        }

        for (Future f : futures) {
            f.get();
        }

        long runtime = System.currentTimeMillis() - start;
        System.out.println("Finished in " + runtime + " ms");

        System.out.println("Average active threads: " + threadMonitor.getAverageActiveThreads());
        System.out.println("Max active threads: " + threadMonitor.getMaxActiveThreads());
        System.out.println("Average CPU: " + threadMonitor.getAverageCPU());

        assertTrue(threadMonitor.getAverageActiveThreads() < 200);
        double minThreshold = CPU_THRESHOLD - 0.2;
        double maxThreshold = CPU_THRESHOLD + 0.03; // we can drift slightly over CPU_THRESHOLD, since we stop handing out tasks only after we cross it
        assertTrue("Expected average CPU usage to be between " + minThreshold + " and " + maxThreshold + " (with a threshold of " + CPU_THRESHOLD + ") but it was " + threadMonitor.getAverageCPU(), threadMonitor.getAverageCPU() <= maxThreshold);
        assertTrue("Expected average CPU usage to be between " + minThreshold + " and " + maxThreshold + " (with a threshold of " + CPU_THRESHOLD + ") but it was " + threadMonitor.getAverageCPU(), threadMonitor.getAverageCPU() >= minThreshold);

        future.cancel(true);
        scheduledExecutorService.shutdown();
        ex.shutdown();
    }

//    This was here as a comparison for CPU % for non-constrained queues.
//    @Test
//    public void test_notConstrained() throws Exception {
//        long start = System.currentTimeMillis();
//        int numProcessors = ManagementFactory.getOperatingSystemMXBean().getAvailableProcessors();
//
//        ThreadPoolExecutor ex = new ThreadPoolExecutor(200, 200, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<Runnable>());
//        ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(1);
//        ThreadMonitor threadMonitor = new ThreadMonitor(ex);
//        ScheduledFuture future = scheduledExecutorService.scheduleAtFixedRate(threadMonitor, 100, 100, TimeUnit.MILLISECONDS);
//
//        for (int i = 0; i < numProcessors * 1000; i++) {
//            futures.add(ex.submit(new BusyWaiter(100)));
//        }
//
//        for (Future f : futures) {
//            f.get();
//        }
//
//        long runtime = System.currentTimeMillis() - start;
//        System.out.println("Finished in "+ runtime + " ms");
//
//        System.out.println("Average active threads: "+ threadMonitor.getAverageActiveThreads());
//        System.out.println("Max active threads: "+ threadMonitor.getMaxActiveThreads());
//        System.out.println("Average CPU: " + threadMonitor.getAverageCPU());
//
//        assertTrue(threadMonitor.getAverageActiveThreads() < 200);
//        assertTrue("Expected average CPU usage to be between 0.5 and 0.9 (with a threshold of 0.9) but it was "+ threadMonitor.getAverageCPU(), threadMonitor.getAverageCPU() <= 0.9);
//        assertTrue("Expected average CPU usage to be between 0.5 and 0.9 (with a threshold of 0.9) but it was "+ threadMonitor.getAverageCPU(), threadMonitor.getAverageCPU() > 0.5);
//
//        future.cancel(true);
//        scheduledExecutorService.shutdown();
//        ex.shutdown();
//    }


    @Test
    public void test_remove() throws Exception {
        ConstantConstraintStrategy<Integer> strategy = new ConstantConstraintStrategy<Integer>(true);
        ResourceConstrainingQueue<Integer> q = ResourceConstrainingQueue.<Integer>builder()
                .withConstraintStrategy(strategy)
                .build();

        // basic test:
        q.add(5);
        assertEquals(1, q.size());
        Integer val = q.remove();
        assertEquals((Integer) 5, val);
        assertEquals(0, q.size());
    }


    /**
     * I'll admit, this test is here more for test coverage numbers than the real possibility that the builder is broken.
     * Although i suppose the builder *could* be broken....
     * @throws Exception
     */
    @Test
    public void testBuilder() throws Exception {
        LinkedBlockingDeque<Object> deque = new LinkedBlockingDeque<Object>();
        ConstantConstraintStrategy strategy = new ConstantConstraintStrategy(false);
        ResourceConstrainingQueue<Object> q = ResourceConstrainingQueue.builder()
                .withConstraintStrategy(strategy)
                .withRetryFrequency(10000)
                .withBlockingQueue(deque)
                .build();

        assertSame(strategy, q.constraintStrategy);
        assertSame(deque, q.delegate);
        assertEquals(10000, q.retryFrequencyMS);

    }

    private class ThreadMonitor implements Runnable {
        final ThreadPoolExecutor ex;
        ResourceMonitor resourceMonitor;
        MetricRegistry registry;
        int numSamples;
        int activeSum;
        int maxActive = 0;
        List<Map<String, Double>> loadSamples = new ArrayList<Map<String, Double>>();

        public ThreadMonitor(ThreadPoolExecutor ex, MetricRegistry registry) {
            this.ex = ex;
//            this.resourceMonitor = new AggregateResourceMonitor();
            this.resourceMonitor = ((SimpleReactiveConstraintStrategy) ((ResourceConstrainingQueue) ex.getQueue()).getConstraintStrategy()).getResourceMonitor();
            this.registry = registry;
        }

        @Override
        public void run() {
            int active = ex.getActiveCount();
            if (maxActive < active) maxActive = active;
            activeSum += active;
            numSamples++;
            loadSamples.add(resourceMonitor.getLoad());
            System.out.println(ex.getActiveCount() + " " + ex.getCompletedTaskCount() + " " + resourceMonitor.getLoad());
            if (registry != null) {
                for (Map.Entry<String, Meter> m : registry.getMeters().entrySet()) {
                    System.out.println(" -- " + m.getKey() + " : " + m.getValue().getMeanRate() + ", " + m.getValue().getOneMinuteRate() + " (" + m.getValue().getCount() + ")");
                }
            }
        }

        public double getAverageActiveThreads() {
            return (double) activeSum / (double) numSamples;
        }

        public int getMaxActiveThreads() {
            return maxActive;
        }

        public double getAverageCPU() {
            int samples = 0;
            double sum = 0;
            for (Map<String, Double> loadSample : loadSamples) {
                if (loadSample.containsKey(ResourceMonitor.CPU)) {
                    sum += loadSample.get(ResourceMonitor.CPU);
                    samples++;
                }
            }
            return sum / samples;
        }


    }


    private static class BusyWaiter implements Callable<Long> {
        long waitMS = 100L;

        private BusyWaiter(long waitMS) {
            this.waitMS = waitMS;
        }

        @Override
        public Long call() throws Exception {
            long startMS = System.currentTimeMillis();
            long foo = 0;
            while (System.currentTimeMillis() < (startMS + waitMS)) {
                // busy wait!
                foo = (9000 * (foo + 1) + 3 + foo) % 3;
            }
            return foo;
        }
    }

    private class ConstantConstraintStrategy<T> implements ConstraintStrategy<T> {
        boolean value;

        private ConstantConstraintStrategy(boolean value) {
            this.value = value;
        }

        @Override
        public boolean shouldReturn(Object nextItem) {
            return value;
        }
    }
}
