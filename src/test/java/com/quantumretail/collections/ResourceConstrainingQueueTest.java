package com.quantumretail.collections;

import com.quantumretail.resourcemon.*;
import org.junit.Test;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static org.junit.Assert.assertTrue;

/**
 * TODO: document me.
 *
 */
public class ResourceConstrainingQueueTest {

    final List<Future> futures = new ArrayList<Future>();

    @Test
    public void testHappyPath() throws Exception {
        long start = System.currentTimeMillis();
        int numProcessors = ManagementFactory.getOperatingSystemMXBean().getAvailableProcessors();
        Map<String,Double> thresholds = new HashMap<String,Double>();
        final Double CPU_THRESHOLD = 0.9;
        thresholds.put(ResourceMonitor.CPU, CPU_THRESHOLD);
//        thresholds.put(ResourceMonitor.HEAP_MEM, 0.9);
//        thresholds.put(ResourceMonitor.LOAD_AVERAGE, 1.5);
//        final ResourceMonitor monitor = new CachingResourceMonitor(new AggregateResourceMonitor(new SigarResourceMonitor(), new HeapResourceMonitor(), new LoadAverageResourceMonitor(), new CpuResourceMonitor(), new EWMAMonitor(new CpuResourceMonitor(), 100, TimeUnit.MILLISECONDS)), 100L);
        final ResourceMonitor monitor = new CachingResourceMonitor(new AggregateResourceMonitor(), 100L);

        ThreadPoolExecutor ex = new ThreadPoolExecutor(4*numProcessors, 4*numProcessors, 0L, TimeUnit.MILLISECONDS, new ResourceConstrainingQueue<Runnable>(new LinkedBlockingQueue<Runnable>(), monitor, thresholds, 100));
        ScheduledExecutorService scheduledExecutorService = Executors.newScheduledThreadPool(1);
        ThreadMonitor threadMonitor = new ThreadMonitor(ex);
        ScheduledFuture future = scheduledExecutorService.scheduleAtFixedRate(threadMonitor, 100, 100, TimeUnit.MILLISECONDS);

        for (int i = 0; i < numProcessors * 100; i++) {
            futures.add(ex.submit(new BusyWaiter(100)));
        }

        for (Future f : futures) {
            f.get();
        }

        long runtime = System.currentTimeMillis() - start;
        System.out.println("Finished in "+ runtime + " ms");

        System.out.println("Average active threads: "+ threadMonitor.getAverageActiveThreads());
        System.out.println("Max active threads: "+ threadMonitor.getMaxActiveThreads());
        System.out.println("Average CPU: " + threadMonitor.getAverageCPU());

        assertTrue(threadMonitor.getAverageActiveThreads() < 200);
        double minThreshold = CPU_THRESHOLD - 0.2;
        double maxThreshold = CPU_THRESHOLD +0.03; // we can drift slightly over CPU_THRESHOLD, since we stop handing out tasks only after we cross it
        assertTrue("Expected average CPU usage to be between "+minThreshold+" and "+maxThreshold+" (with a threshold of "+CPU_THRESHOLD+") but it was "+ threadMonitor.getAverageCPU(), threadMonitor.getAverageCPU() <= maxThreshold);
        assertTrue("Expected average CPU usage to be between "+minThreshold+" and "+maxThreshold+" (with a threshold of "+CPU_THRESHOLD+") but it was "+ threadMonitor.getAverageCPU(), threadMonitor.getAverageCPU() >= minThreshold);

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

    private class ThreadMonitor implements Runnable {
        final ThreadPoolExecutor ex;
        ResourceMonitor resourceMonitor;
        int numSamples;
        int activeSum;
        int maxActive = 0;
        List<Map<String,Double>> loadSamples = new ArrayList<Map<String,Double>>();

        public ThreadMonitor(ThreadPoolExecutor ex) {
            this.ex = ex;
//            this.resourceMonitor = new AggregateResourceMonitor();
            this.resourceMonitor = ((ResourceConstrainingQueue)ex.getQueue()).getResourceMonitor();
        }

        @Override
        public void run() {
            int active = ex.getActiveCount();
            if (maxActive < active) maxActive = active;
            activeSum += active;
            numSamples++;
            loadSamples.add(resourceMonitor.getLoad());
            System.out.println(ex.getActiveCount() +" "+ ex.getCompletedTaskCount()+" "+ resourceMonitor.getLoad());
        }

        public double getAverageActiveThreads() {
            return (double)activeSum/(double)numSamples;
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
            while (System.currentTimeMillis() < (startMS + waitMS) ) {
                // busy wait!
                foo = (9000 * (foo + 1) + 3 + foo) % 3;
            }
            return foo;
        }
    }

}
