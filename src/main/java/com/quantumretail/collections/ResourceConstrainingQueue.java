package com.quantumretail.collections;

import com.quantumretail.resourcemon.AggregateResourceMonitor;
import com.quantumretail.resourcemon.CachingResourceMonitor;
import com.quantumretail.resourcemon.ResourceMonitor;

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
 * TODO: take in a parameter to indicate "strict vs. approximate" -- strict would use blocking in remove(), poll() and take(),
 * while approximate would keep the current non-blocking (but slightly less accurate) behavior.
 *
 */
public class ResourceConstrainingQueue<T> implements BlockingQueue<T> {
    public static <T> ResourceConstrainingQueueBuilder<T> builder() {
        return new ResourceConstrainingQueueBuilder<T>();
    }

    protected static final long DEFAULT_POLL_FREQ = 100L;

    final BlockingQueue<T> delegate;
    long retryFrequencyMS = DEFAULT_POLL_FREQ;

    final ConstraintStrategy<T> constraintStrategy;

    /**
     * Build a ResourceConstrainingQueue using all default options.
     * If you want to override some defaults, but not all, use the ResourceConstrainingQueueBuilder; it's much easier.
     */
    public ResourceConstrainingQueue() {
        this(new LinkedBlockingQueue<T>(), new SimpleResourceConstraintStrategy<T>(new CachingResourceMonitor(new AggregateResourceMonitor(), DEFAULT_POLL_FREQ), defaultThresholds()), DEFAULT_POLL_FREQ);
    }

    public ResourceConstrainingQueue(BlockingQueue<T> delegate, ConstraintStrategy<T> constraintStrategy, long retryFrequencyMS) {
        this.delegate = delegate;
        this.retryFrequencyMS = retryFrequencyMS;
        this.constraintStrategy = constraintStrategy;

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
            if (nextItem == null || shouldReturn(nextItem)) {
                // Note that we might be returning a *different item* than nextItem if we have multiple threads accessing this concurrently!
                // We're intentionally taking that risk to avoid locking.
                return delegate.remove();
            } else {
                return null; // sleep? block?
            }
        }
    }

    protected boolean shouldReturn(T nextItem) {
        return constraintStrategy.shouldReturn(nextItem);
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
     * @return the next value in the queue or null if we cannot currently execute anything.
     */
    @Override
    public T poll() {
        T nextItem = delegate.peek();
        if (nextItem == null || shouldReturn(nextItem)) {
            // Note that we might be returning a *different item* than nextItem if we have multiple threads accessing this concurrently!
            // We're intentionally taking that risk to avoid locking.
            return delegate.poll();
        } else {
            return null;  // sleep? block?
        }
    }

    /**
     * See poll() for a description of the potential inaccuracy in this method.
     *
     * @see #poll() for an explanation of the potential inaccuracy in this method
     * @return
     * @throws InterruptedException
     */
    @Override
    public T take() throws InterruptedException {
        while (true) {
            T nextItem = delegate.peek();
            if (shouldReturn(nextItem)) {
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
        Thread.sleep(retryFrequencyMS);
    }

    /**
     * See poll() for a description of the potential inaccuracy in this method.
     *
     * @see #poll() for an explanation of the potential inaccuracy in this method
     */
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
            if (shouldReturn(nextItem)) {
                // Note that we might be returning a *different item* than nextItem if we have multiple threads accessing this concurrently!
                // We're intentionally taking that risk to avoid locking.
                return delegate.poll(timeoutNanos - totalSleepNanos, TimeUnit.NANOSECONDS);
            } else {
                sleep();
                totalSleepNanos = System.nanoTime() - startNanos;
            }
        }
        // if we got here, we timed out.
        return null;
    }

    /**
     * This method does NOT make any resource constraint checks. It just delegates to the underlying queue.
     * @see java.util.Queue#element()
     */
    @Override
    public T element() {
        return delegate.element();
    }

    /**
     * This method does NOT make any resource constraint checks. It just delegates to the underlying queue.
     * @see java.util.Queue#peek()
     */
    @Override
    public T peek() {
        return delegate.peek();
    }

    /**
     * This method does not make any resource constraint checks. It just delegates to the underlying queue. So it is
     * literally "how many elements in the queue?" not "how many elements do I have resources to execute?"
     * @see java.util.Queue#size()
     */
    @Override
    public int size() {
        return delegate.size();
    }

    /**
     * This method does not make any resource constraint checks. It just delegates to the underlying queue. So it is
     * literally "are there any elements in the queue?" not "do I have resources to execute any elements in the queue?"
     * @see java.util.Queue#isEmpty()
     */
    @Override
    public boolean isEmpty() {
        return delegate.isEmpty();
    }

    /**
     * This method does not make any resource constraint checks. It just delegates to the underlying queue.
     * @see java.util.Queue#contains(Object)
     */
    @Override
    public boolean contains(Object o) {
        return delegate.contains(o);
    }

    /**
     * This method does not make any resource constraint checks. It just delegates to the underlying queue.
     * @see java.util.concurrent.BlockingQueue#drainTo(java.util.Collection)
     */
    public int drainTo(Collection<? super T> c) {
        return delegate.drainTo(c);
    }

    /**
     * This method does not make any resource constraint checks. It just delegates to the underlying queue.
     * @see java.util.concurrent.BlockingQueue#drainTo(java.util.Collection, int)
     */
    public int drainTo(Collection<? super T> c, int maxElements) {
        return delegate.drainTo(c, maxElements);
    }

    /**
     * This method just delegates to the underlying queue; the returned iterator will NOT honor any resource constraints.
     * This may change in the future.
     *
     * @see java.util.concurrent.BlockingQueue#iterator()
     */
    @Override
    public Iterator<T> iterator() {
        return delegate.iterator();
    }

    /**
     * This method does NOT make any resource constraint checks. It just delegates to the underlying queue.
     * @see java.util.Queue#toArray()
     */
    @Override
    public Object[] toArray() {
        return delegate.toArray();
    }

    /**
     * This method does NOT make any resource constraint checks. It just delegates to the underlying queue.
     * @see java.util.Queue#toArray(Object[])
     */
    @Override
    public <T1> T1[] toArray(T1[] a) {
        return delegate.toArray(a);
    }

    /**
     * This method does not make any resource constraint checks. It just delegates to the underlying queue.
     * @see java.util.Queue#remove(Object)
     */
    @Override
    public boolean remove(Object o) {
        return delegate.remove(o);
    }

    /**
     * This method does not make any resource constraint checks. It just delegates to the underlying queue.
     * @see java.util.Queue#containsAll(java.util.Collection)
     */
    @Override
    public boolean containsAll(Collection<?> c) {
        return delegate.containsAll(c);
    }

    /**
     * This method does not make any resource constraint checks. It just delegates to the underlying queue.
     * @see java.util.concurrent.BlockingQueue#addAll(java.util.Collection)
     */
    public boolean addAll(Collection<? extends T> c) {
        return delegate.addAll(c);
    }

    /**
     * This method does not make any resource constraint checks. It just delegates to the underlying queue.
     * @see java.util.concurrent.BlockingQueue#removeAll(java.util.Collection)
     */
    @Override
    public boolean removeAll(Collection<?> c) {
        return delegate.removeAll(c);
    }

    /**
     * This method does not make any resource constraint checks. It just delegates to the underlying queue.
     * @see java.util.concurrent.BlockingQueue#retainAll(java.util.Collection)
     */
    @Override
    public boolean retainAll(Collection<?> c) {
        return delegate.retainAll(c);
    }

    /**
     * This method does not make any resource constraint checks. It just delegates to the underlying queue.
     * @see java.util.concurrent.BlockingQueue#clear()
     */
    @Override
    public void clear() {
        delegate.clear();
    }

    /**
     * returns true if o is an instance of ResourceConstrainingQueues and their underlying queues are equal.
     * Most of the definition of "equality", then, is delegated to the underlying queues.
     *
     * @see java.util.Collection#equals(Object)
     */
    @Override
    public boolean equals(Object o) {
        return o instanceof ResourceConstrainingQueue && delegate.equals(o);
    }

    @Override
    public int hashCode() {
        return delegate.hashCode();
    }


    /**
     * Add t to the back of the queue. Note that ResourceConstrainingQueue doesn't make any resource constraint checks
     * on insertions into the queue, only on removals.
     *
     * @see java.util.concurrent.BlockingQueue#put(Object)
     * @param t
     * @throws InterruptedException
     */
    public void put(T t) throws InterruptedException {
        delegate.put(t);
    }

    /**
     * Note that ResourceConstrainingQueue doesn't make any resource constraint checks
     * on insertions into the queue, only on removals.
     *
     * @see java.util.concurrent.BlockingQueue#offer(Object, long, java.util.concurrent.TimeUnit)
     * @param t
     * @throws InterruptedException
     */
    public boolean offer(T t, long timeout, TimeUnit unit) throws InterruptedException {
        return delegate.offer(t, timeout, unit);
    }

    @Override
    public int remainingCapacity() {
        return delegate.remainingCapacity();
    }

    /**
     * Get the constraint strategy that we're using to decide whether to hand out items.
     * @return
     */
    public ConstraintStrategy<T> getConstraintStrategy() {
        return constraintStrategy;
    }

    protected static Map<String, Double> defaultThresholds() {
        Map<String, Double> t;
        t = new ConcurrentHashMap<String, Double>();
        t.put(ResourceMonitor.CPU, 0.95);
        t.put(ResourceMonitor.HEAP_MEM, 0.90);
        return t;
    }


    public static class ResourceConstrainingQueueBuilder<T> {
        BlockingQueue<T> builderdelegate = null;
        private long builderresourcePollFrequencyMS = DEFAULT_POLL_FREQ;
        ConstraintStrategy<T> builderConstraintStrategy;

        public ResourceConstrainingQueueBuilder<T> withConstraintStrategy(ConstraintStrategy<T> cs) {
            this.builderConstraintStrategy = cs;
            return this;
        }

        public ResourceConstrainingQueueBuilder<T> withBlockingQueue(BlockingQueue<T> q) {
            this.builderdelegate = q;
            return this;
        }

        public ResourceConstrainingQueueBuilder<T> withRetryFrequency(long pollFrequencyInMS) {
            this.builderresourcePollFrequencyMS = pollFrequencyInMS;
            return this;
        }

        public ResourceConstrainingQueue<T> build() {
            BlockingQueue<T> d = builderdelegate;
            long pollfreq = builderresourcePollFrequencyMS;
            ConstraintStrategy<T> cs = builderConstraintStrategy;
            if (cs == null) {
                cs = new SimpleResourceConstraintStrategy<T>(new CachingResourceMonitor(new AggregateResourceMonitor(), DEFAULT_POLL_FREQ), defaultThresholds());
            }
            if (d == null) {
                d = new LinkedBlockingQueue<T>();
            }
            return new ResourceConstrainingQueue<T>(d, cs, pollfreq);
        }

    }

    public static interface ConstraintStrategy<T> {

        /**
         * Returns true if we should return this item -- implicitly, "do we have resources for this item?"
         * The implementation may choose to ignore the parameter entirely.
         *
         * @param nextItem
         * @return
         */
        public boolean shouldReturn(T nextItem);
    }

    public static class SimpleResourceConstraintStrategy<T> implements ConstraintStrategy<T> {
        private final ConcurrentMap<String, Double> thresholds;
        private final ResourceMonitor resourceMonitor;

        public SimpleResourceConstraintStrategy(ResourceMonitor resourceMonitor, Map<String, Double> thresholds) {
            this.resourceMonitor = resourceMonitor;

            // we allow thresholds to be updated, so it should be a concurrent map.
            if (thresholds instanceof ConcurrentMap) {
                this.thresholds = (ConcurrentMap<String, Double>) thresholds;
            } else {
                this.thresholds = new ConcurrentHashMap<String, Double>(thresholds);
            }
        }

        @Override
        public boolean shouldReturn(T nextItem) {

            // get current load from resourceMonitor
            Map<String, Double> load = resourceMonitor.getLoad();

            /*
            if taskTracker != null
                get current # of tasks from taskTracker
                calculate load-per-task-point
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

        public ResourceMonitor getResourceMonitor() {
            return resourceMonitor;
        }

        public ConcurrentMap<String, Double> getThresholds() {
            return thresholds;
        }
    }
}
