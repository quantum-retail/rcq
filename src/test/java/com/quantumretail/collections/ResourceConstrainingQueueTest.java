package com.quantumretail.collections;

import com.quantumretail.constraint.ConstraintStrategy;
import com.quantumretail.constraint.SimplePredictiveConstraintStrategy;
import com.quantumretail.constraint.SimpleReactiveConstraintStrategy;
import com.quantumretail.rcq.predictor.*;
import com.quantumretail.resourcemon.HighestValueAggregateResourceMonitor;
import com.quantumretail.resourcemon.ResourceMonitor;
import com.quantumretail.resourcemon.ResourceMonitors;
import com.yammer.metrics.core.Meter;
import com.yammer.metrics.core.Metric;
import com.yammer.metrics.core.MetricName;
import com.yammer.metrics.core.MetricsRegistry;
import org.easymock.EasyMock;
import org.junit.Test;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static com.quantumretail.resourcemon.ResourceMonitors.defaultCachingResourceMonitor;
import static com.quantumretail.resourcemon.ResourceMonitors.defaultPredictiveResourceMonitor;
import static org.junit.Assert.*;

public class ResourceConstrainingQueueTest {

    final List<Future> futures = new ArrayList<Future>();

    /**
     * this test is not entirely deterministic, so it's disabled by default.
     * But it is a really interesting test to run now and again to see how well things operate.
     *
     * @throws Exception
     */
    @Test
    public void test_long_running() throws Exception {
        long start = System.currentTimeMillis();
        int numProcessors = ManagementFactory.getOperatingSystemMXBean().getAvailableProcessors();
        Map<String, Double> thresholds = new HashMap<String, Double>();
        final Double CPU_THRESHOLD = 0.95;
        thresholds.put(ResourceMonitor.CPU, CPU_THRESHOLD);
        // final ResourceMonitor monitor = new CachingResourceMonitor(new AggregateResourceMonitor(new SigarResourceMonitor(), new HeapResourceMonitor(), new LoadAverageResourceMonitor(), new CpuResourceMonitor(), new EWMAMonitor(new CpuResourceMonitor(), 100, TimeUnit.MILLISECONDS)), 100L);
        // final ResourceMonitor monitor = new CachingResourceMonitor(new AggregateResourceMonitor(), 100L);
        MetricsRegistry metricRegistry = new MetricsRegistry();

        ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(2, new ResourceConstrainingQueues.NameableDaemonThreadFactory("thread-monitor-"));
        // ResourceConstrainingQueue<Runnable> queue = new ResourceConstrainingQueue<Runnable>(new LinkedBlockingQueue<Runnable>(), ConstraintStrategies.<Runnable>defaultReactiveConstraintStrategy(thresholds, 100), 100);
        // ResourceConstrainingQueue<Runnable> queue = ResourceConstrainingQueues.defaultQueue(thresholds);

        TaskTracker<Runnable> taskTracker = TaskTrackers.defaultTaskTracker();
        ResourceConstrainingQueue<Runnable> queue = new ResourceConstrainingQueue<Runnable>(
                new LinkedBlockingQueue<Runnable>(),
                createConstraintStrategies(thresholds, taskTracker, scheduledExecutorService),
                ResourceMonitors.DEFAULT_UPDATE_FREQ,
                true,
                taskTracker);

        queue.registerMetrics(metricRegistry, "queue");

        ThreadPoolExecutor ex = new ThreadPoolExecutor(4 * numProcessors, 4 * numProcessors, 0L, TimeUnit.MILLISECONDS, queue);

        // send in the metricsRegistry if you want a super-verbose view of what's going on:
        ThreadMonitor threadMonitor = new ThreadMonitor(ex, null);
        // ThreadMonitor threadMonitor = new ThreadMonitor(ex, metricRegistry);

        ScheduledFuture future = scheduledExecutorService.scheduleAtFixedRate(threadMonitor, 100, 100, TimeUnit.MILLISECONDS);

        for (int i = 0; i < numProcessors * 500; i++) {
            futures.add(ex.submit(new BusyWaiter(100)));
        }

        for (Future f : futures) {
            f.get();
        }

        long runtime = System.currentTimeMillis() - start;
        System.out.println("Finished in " + runtime + " ms");

        printStatistics(CPU_THRESHOLD, threadMonitor);

        future.cancel(true);
        scheduledExecutorService.shutdown();
        ex.shutdown();
    }

    @Test(expected = ExecutionException.class)
    public void testNoResources_ThenFail() throws Exception {
        LinkedBlockingQueue delegate = new LinkedBlockingQueue();
        ConstraintStrategy constraintStrategy = EasyMock.createMock(ConstraintStrategy.class);
        //always return false for should return
        EasyMock.expect(constraintStrategy.shouldReturn(EasyMock.anyObject())).andReturn(false).anyTimes();
        EasyMock.replay(constraintStrategy);
        //create a constraining queue with retry of 100 ms and item threshold of only 1
        ResourceConstrainingQueue<Runnable> resourceConstrainingQueue = new NoResource_RCQ<Runnable>(delegate, constraintStrategy, 100, true, TaskTrackers.<Runnable>defaultTaskTracker(), 1);
        resourceConstrainingQueue.setFailAfterAttemptThresholdReached(true);
        //Using a custom thread pool executor to simulate the possibility of custom future tasks coming into the system
        CustomThreadPoolExecutor ex = new CustomThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, resourceConstrainingQueue, new ResourceConstrainingQueues.NameableDaemonThreadFactory("test"));
        ex.prestartAllCoreThreads();
        Future future = ex.submit(new SimpleCallable());
        Object result = future.get(2, TimeUnit.SECONDS);
    }

    class SimpleCallable implements Callable<String> {
        @Override
        public String call() throws Exception {
            return "Done";
        }
    }

    private class CustomThreadPoolExecutor extends ThreadPoolExecutor {
        public CustomThreadPoolExecutor(int corePoolSize, int maximumPoolSize, long keepAliveTime, TimeUnit timeUnit, BlockingQueue<Runnable> runnables, java.util.concurrent.ThreadFactory threadFactory) {
            super(corePoolSize, maximumPoolSize, keepAliveTime, timeUnit, runnables, threadFactory);
        }

        @Override
        protected <V> RunnableFuture<V> newTaskFor(Callable<V> c) {
            return new CustomFutureTask(c);
        }

        class CustomFutureTask extends FutureTask {
            public CustomFutureTask(Callable callable) {
                super(callable);
            }
        }
    }

    private class NoResource_RCQ<T> extends ResourceConstrainingQueue<T> {
        public NoResource_RCQ(BlockingQueue<T> delegate, ConstraintStrategy<T> constraintStrategy, long retryFrequencyMS, boolean strict, TaskTracker<T> taskTracker, long constrainedItemThreshold) {
            super(delegate, constraintStrategy, retryFrequencyMS, strict, taskTracker, constrainedItemThreshold);
        }

        @Override
        protected boolean shouldReturn(T nextItem) {
            return constraintStrategy.shouldReturn(nextItem);
        }
    }

    private void printStatistics(Double CPU_THRESHOLD, ThreadMonitor threadMonitor) {
        System.out.println("\n****");
        System.out.println("Average active threads: " + threadMonitor.getAverageActiveThreads());
        System.out.println("Max active threads: " + threadMonitor.getMaxActiveThreads());
        System.out.println("Average CPU: " + threadMonitor.getAverageCPU());
        System.out.println("Average Measured CPU: " + threadMonitor.getAverageForKey("CPU.measured"));
        System.out.println("Average Predicted CPU: " + threadMonitor.getAverageForKey("CPU.predicted"));
        System.out.println("Threshold was " + CPU_THRESHOLD);
        System.out.println("****\n");
        assertTrue(threadMonitor.getAverageActiveThreads() < 200);
        double minThreshold = CPU_THRESHOLD - 0.2;
        double maxThreshold = CPU_THRESHOLD + 0.03; // we can drift slightly over CPU_THRESHOLD, since we stop handing out tasks only after we cross it
        assertTrue("Expected average CPU usage to be between " + minThreshold + " and " + maxThreshold + " (with a threshold of " + CPU_THRESHOLD + ") but it was " + threadMonitor.getAverageCPU(), threadMonitor.getAverageCPU() <= maxThreshold);
        assertTrue("Expected average CPU usage to be between " + minThreshold + " and " + maxThreshold + " (with a threshold of " + CPU_THRESHOLD + ") but it was " + threadMonitor.getAverageCPU(), threadMonitor.getAverageCPU() >= minThreshold);
    }

    private ConstraintStrategy<Runnable> createConstraintStrategies(Map<String, Double> thresholds, TaskTracker<Runnable> taskTracker, ScheduledExecutorService service) {
        AdjustableLoadPredictor loadPredictor = LoadPredictors.defaultLoadPredictor();
        ResourceMonitor predictive = defaultPredictiveResourceMonitor(taskTracker, loadPredictor);
        ResourceMonitor measured = defaultCachingResourceMonitor();

        if (service != null) {
            service.scheduleAtFixedRate(new ScalingFactorAdjuster(measured, predictive, loadPredictor, 10, TimeUnit.SECONDS), 1, 10, TimeUnit.SECONDS);
        }

        return new SimplePredictiveConstraintStrategy<Runnable>(
                new HighestValueAggregateResourceMonitor(predictive, measured),
                thresholds,
                loadPredictor
        );
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
     *
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
        MetricsRegistry registry;
        int numSamples;
        int activeSum;
        int maxActive = 0;
        List<Map<String, Double>> loadSamples = new ArrayList<Map<String, Double>>();

        public ThreadMonitor(ThreadPoolExecutor ex, MetricsRegistry registry) {
            this.ex = ex;
//            this.resourceMonitor = new ResourceMonitor() {
//                @Override
//                public Map<String, Double> getLoad() {
//                    return Collections.emptyMap();
//                }
//            };
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
                for (Map.Entry<MetricName, Metric> m : registry.allMetrics().entrySet()) {
                    if (m.getValue() instanceof Meter) {
                        System.out.println(" -- " + m.getKey() + " : " + ((Meter) m.getValue()).meanRate() + ", " + ((Meter) m.getValue()).oneMinuteRate() + " (" + ((Meter) m.getValue()).count() + ")");
                    }
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
            return getAverageForKey(ResourceMonitor.CPU);
        }

        public double getAverageForKey(String key) {
            int samples = 0;
            double sum = 0;
            for (Map<String, Double> loadSample : loadSamples) {
                if (loadSample.containsKey(key)) {
                    sum += loadSample.get(key);
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
