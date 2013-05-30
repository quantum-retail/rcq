package com.quantumretail.collections;

import com.quantumretail.resourcemon.AggregateResourceMonitor;
import com.quantumretail.resourcemon.CachingResourceMonitor;
import com.quantumretail.resourcemon.ResourceMonitor;
import com.quantumretail.TaskTracker;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.*;

/**
 * Note that this resource-constraining behavior ONLY occurs on {@link #poll()} and {@link #remove()}. Other access methods
 * like {@link #peek()}, {@link #iterator()}, {@link #toArray()}, and so on will bypass the resource-constraining behavior.
 *
 * In some ways, this implementation is naive. At the moment when the resource usage drops below the threshold, we hand out items
 * to all who ask, until the moment when resource usage rises above the threshold again. That means that if we have a lot
 * of askers (for example, if it's the queue feeding a very large thread pool) we'll get large bursts of threads, and
 * typically a higher-than-ideal # of threads active.
 *
 * There are some smarter ways around this:
 *  - we could make it probabilistic, with a decreasing probability that we hand something out based on available resources
 *  - we could make assumptions about what things we've handed out recently will do to the resource usage -- say, assume
 *    that anything we've handed out in the last X ms will be adding Y% points, they just haven't yet.
 *  - we could actually track the tasks that are currently active (via a separate TaskTracker), and come up with a
 *    weighted moving average of task-to-resource-utilization.
 *  - ???
 *
 */
public class ResourceConstrainingQueue<T> implements BlockingQueue<T> {
    public static <T> ResourceConstrainingQueueBuilder<T> builder() {
        return new ResourceConstrainingQueueBuilder<T>();
    }

    protected static final long DEFAULT_POLL_FREQ = 100L;

    final BlockingQueue<T> delegate;
    private final ConcurrentMap<String, Double> thresholds;
    private final ResourceMonitor resourceMonitor;
    private long resourcePollFrequencyMS = DEFAULT_POLL_FREQ;

    /**
     * Build a ResourceConstrainingQueue using all default options.
     * If you want to override some defaults, but not all, use the ResourceConstrainingQueueBuilder; it's much easier.
     */
    public ResourceConstrainingQueue() {
        this(new LinkedBlockingQueue<T>(), new CachingResourceMonitor(new AggregateResourceMonitor(), DEFAULT_POLL_FREQ), defaultThresholds(), DEFAULT_POLL_FREQ);
    }

    public ResourceConstrainingQueue(BlockingQueue<T> delegate, ResourceMonitor resourceMonitor, Map<String, Double> thresholds, long resourcePollFrequencyMS) {
        this.delegate = delegate;
        this.resourcePollFrequencyMS = resourcePollFrequencyMS;
        this.resourceMonitor = resourceMonitor;

        // we allow thresholds to be updated, so it should be a concurrent map.
        if (thresholds instanceof ConcurrentMap) {
            this.thresholds = (ConcurrentMap<String,Double>)thresholds;
        } else {
            this.thresholds = new ConcurrentHashMap<String,Double>(thresholds);
        }


    }

    public boolean add(T t) {
        return delegate.add(t);
    }

    public boolean offer(T t) {
        return delegate.offer(t);
    }

    /**
     * Note that this is an approximation, and as such, we take some liberties in regards accuracy when called from
     * multiple threads.
     * In particular:
     *
     * This implementation will do a peek, see if we have resource for the task at the head of the queue, and if so,
     * call delegate.remove() and return the result. That means that if two threads call this at the very same time,
     * they'll both check to see if we have resources for the same task (the one at the front of the queue) but they will
     * then return the first and then second task in the queue -- but neither thread will have checked to see if we have
     * resources for that second task!
     * We could fix this by doing something more accurate here, but since we don't have an atomic "compareAndGet" type
     * of operation from the underlying queue, we may need to resort to blocking. Currently, we're preferring speed over
     * complete accuracy here. In the face of multiple concurrent calls, the checks we're doing aren't accurate anyway.
     *
     *
     * @return
     */
    @Override
    public T remove() {
        while (true) {
            T nextItem = delegate.peek();
            if (nextItem == null || hasResourcesFor(nextItem)) {
                // Note that we might be returning a *different item* than nextItem if we have multiple threads accessing this concurrently!
                // We're intentionally taking that risk to avoid locking.
                return delegate.remove();
            } else {
                return null; // sleep? block?
            }
        }
    }


    /**
     * Returns null if we cannot currently execute anything.
     * @return
     */
    @Override
    public T poll() {
        T nextItem = delegate.peek();
        if (nextItem == null || hasResourcesFor(nextItem)) {
            // Note that we might be returning a *different item* than nextItem if we have multiple threads accessing this concurrently!
            // We're intentionally taking that risk to avoid locking.
            return delegate.poll();
        } else {
            return null;  // sleep? block?
        }
    }

    /**
     * We check for enough resources at the beginning of this method,
     * @return
     * @throws InterruptedException
     */
    @Override
    public T take() throws InterruptedException {
        while (true) {
            T nextItem = delegate.peek();
            if (hasResourcesFor(nextItem)) {
                // Note that we might be returning a *different item* than nextItem if we have multiple threads accessing this concurrently!
                // We're intentionally taking that risk to avoid locking.
                return delegate.take();
            } else {
                sleep();
            }
        }
    }

    /**
     * If we decide we want pluggable behavior here, take a look at LMAX Disruptor's WaitStrategy classes
     */
    private void sleep() throws InterruptedException {
        Thread.sleep(resourcePollFrequencyMS);
    }


    @Override
    public T poll(long timeout, TimeUnit unit) throws InterruptedException {
        // we have to do a little extra work here because we may have to wait for some time before we have enough resources.
        // We make a reasonable effort to ensure that the combined wait-until-resources-are-available and poll time don't
        // exceed the desired timeout.
        long totalSleepNanos = 0;
        long startNanos = System.nanoTime();
        long timeoutNanos = unit.toNanos(timeout);
        while (totalSleepNanos > timeoutNanos) {
            T nextItem = delegate.peek();
            if (hasResourcesFor(nextItem)) {
                // Note that we might be returning a *different item* than nextItem if we have multiple threads accessing this concurrently!
                // We're intentionally taking that risk to avoid locking.
                return delegate.poll( timeoutNanos - totalSleepNanos, TimeUnit.NANOSECONDS);
            } else {
                sleep();
                totalSleepNanos = System.nanoTime() - startNanos;
            }
        }
        // if we got here, we timed out.
        return null;
    }


    public boolean hasResourcesFor(T nextItem) {

        // get current load from resourceMonitor
        Map<String,Double> load = resourceMonitor.getLoad();

        /*
        if taskTracker != null
            get current # of tasks from taskTracker
            calculate load-task-per-point
            add this task's points. Does that put us past the threshold?
        else
            is current load past the threshold?

         */
        for (Map.Entry<String, Double> t : thresholds.entrySet()) {
            if (load.containsKey(t.getKey())) {
                if (load.get(t.getKey()) > t.getValue()) {
                    return false;
                }
            }
        }

        return true;
    }


    @Override
    public T element() {
        return delegate.element();
    }

    @Override
    public T peek() {
        return delegate.peek();
    }

    @Override
    public int size() {
        return delegate.size();
    }

    @Override
    public boolean isEmpty() {
        return delegate.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
        return delegate.contains(o);
    }

    public int drainTo(Collection<? super T> c) {
        return delegate.drainTo(c);
    }

    public int drainTo(Collection<? super T> c, int maxElements) {
        return delegate.drainTo(c, maxElements);
    }

    @Override
    public Iterator<T> iterator() {
        return delegate.iterator();
    }

    @Override
    public Object[] toArray() {
        return delegate.toArray();
    }

    @Override
    public <T1> T1[] toArray(T1[] a) {
        return delegate.toArray(a);
    }

    @Override
    public boolean remove(Object o) {
        return delegate.remove(o);
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return delegate.containsAll(c);
    }

    public boolean addAll(Collection<? extends T> c) {
        return delegate.addAll(c);
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        return delegate.removeAll(c);
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        return delegate.retainAll(c);
    }

    @Override
    public void clear() {
        delegate.clear();
    }

    @Override
    public boolean equals(Object o) {
        return delegate.equals(o);
    }

    @Override
    public int hashCode() {
        return delegate.hashCode();
    }

    public void put(T t) throws InterruptedException {
        delegate.put(t);
    }

    public boolean offer(T t, long timeout, TimeUnit unit) throws InterruptedException {
        return delegate.offer(t, timeout, unit);
    }

    @Override
    public int remainingCapacity() {
        return delegate.remainingCapacity();
    }

    public ResourceMonitor getResourceMonitor() {
        return resourceMonitor;
    }

    protected static Map<String, Double> defaultThresholds() {
        Map<String, Double> t;
        t = new ConcurrentHashMap<String,Double>();
        t.put(ResourceMonitor.CPU, 0.95);
        t.put(ResourceMonitor.HEAP_MEM, 0.90);
        return t;
    }


    public static class ResourceConstrainingQueueBuilder<T> {
        ResourceMonitor builderresourceMonitor = null;
        BlockingQueue<T> builderdelegate = null;
        private long builderresourcePollFrequencyMS = DEFAULT_POLL_FREQ;
        private Map<String, Double> builderthresholds = null;

        public ResourceConstrainingQueueBuilder<T> withResourceMonitor(ResourceMonitor rm) {
            this.builderresourceMonitor = rm;
            return this;
        }

        public ResourceConstrainingQueueBuilder<T> withBlockingQueue(BlockingQueue<T> q) {
            this.builderdelegate = q;
            return this;
        }

        public ResourceConstrainingQueueBuilder<T> withResourcePollFrequency(long pollFrequencyInMS) {
            this.builderresourcePollFrequencyMS = pollFrequencyInMS;
            return this;
        }

        public ResourceConstrainingQueueBuilder<T> withThresholds(Map<String,Double> t) {
            this.builderthresholds = t;
            return this;
        }

        public ResourceConstrainingQueue<T> build() {
            ResourceMonitor rm = builderresourceMonitor;
            BlockingQueue<T> d= builderdelegate;
            long pollfreq = builderresourcePollFrequencyMS;
            Map<String, Double> t = builderthresholds;
            if (rm == null) {
                rm = new CachingResourceMonitor(new AggregateResourceMonitor(), DEFAULT_POLL_FREQ);
            }
            if (d == null) {
                d = new LinkedBlockingQueue<T>();
            }
            if (t == null) {
                t = defaultThresholds();
            }
            return new ResourceConstrainingQueue<T>(d, rm, t, pollfreq);
        }

    }
}
