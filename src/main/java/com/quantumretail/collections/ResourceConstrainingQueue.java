package com.quantumretail.collections;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Meter;
import com.codahale.metrics.MetricRegistry;
import com.quantumretail.MetricsAware;
import com.quantumretail.constraint.ConstraintStrategies;
import com.quantumretail.constraint.ConstraintStrategy;
import com.quantumretail.rcq.predictor.TaskTracker;
import com.quantumretail.rcq.predictor.TaskTrackers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

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
public class ResourceConstrainingQueue<T> implements BlockingQueue<T>, MetricsAware {
    private static final Logger log = LoggerFactory.getLogger(ResourceConstrainingQueue.class);

    public static <T> ResourceConstrainingQueueBuilder<T> builder() {
        return new ResourceConstrainingQueueBuilder<T>();
    }

    protected static final long DEFAULT_POLL_FREQ = 100L;

    final BlockingQueue<T> delegate;
    long retryFrequencyMS = DEFAULT_POLL_FREQ;

    final ConstraintStrategy<T> constraintStrategy;

    final TaskTracker<T> taskTracker;

    private Meter trackedRemovals = null;
    private Meter additions = null;
    private Counter pendingItems = null;
    private Meter sleeps = null;

    /**
     * Build a ResourceConstrainingQueue using all default options.
     * If you want to override some defaults, but not all, use the ResourceConstrainingQueueBuilder; it's much easier.
     */
    public ResourceConstrainingQueue() {
        this(new LinkedBlockingQueue<T>(), TaskTrackers.<T>defaultTaskTracker(), DEFAULT_POLL_FREQ);
    }

    public ResourceConstrainingQueue(LinkedBlockingQueue<T> delegate, TaskTracker<T> taskTracker, long defaultPollFreq) {
        this(delegate, ConstraintStrategies.defaultConstraintStrategy(taskTracker), defaultPollFreq, taskTracker);
    }


    public ResourceConstrainingQueue(BlockingQueue<T> delegate, ConstraintStrategy<T> constraintStrategy, long retryFrequencyMS) {
        this(delegate, constraintStrategy, retryFrequencyMS, null);
    }

    public ResourceConstrainingQueue(BlockingQueue<T> delegate, ConstraintStrategy<T> constraintStrategy, long retryFrequencyMS, TaskTracker<T> taskTracker) {
        this.delegate = delegate;
        this.retryFrequencyMS = retryFrequencyMS;
        this.constraintStrategy = constraintStrategy;
        this.taskTracker = taskTracker;
    }

    protected T trackIfNecessary(T item) {
        if (trackedRemovals != null) {
            trackedRemovals.mark();
        }
        if (pendingItems != null) {
            pendingItems.dec();
        }

        if (taskTracker != null) {
            return taskTracker.register(item);
        } else {
            return item;
        }
    }

    public boolean add(T t) {
        if (pendingItems != null) {
            pendingItems.inc();
        }
        if (additions != null) {
            additions.mark();
        }
        return delegate.add(t);
    }

    public boolean offer(T t) {
        if (pendingItems != null) {
            pendingItems.inc();
        }
        if (additions != null) {
            additions.mark();
        }
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
                return trackIfNecessary(delegate.remove());
            } else {
                return null; // sleep? block?
            }
        }
    }

    protected boolean shouldReturn(T nextItem) {

        boolean shouldReturn = constraintStrategy.shouldReturn(nextItem);
        if (!shouldReturn && (taskTracker != null && taskTracker.currentTasks().isEmpty())) {
            if (log.isDebugEnabled()) {
                log.debug("Constraint strategy says we should not return an item, but task tracker says that there is nothing in progress. So returning it anyway.");
            }
            return true;
        } else {
            return shouldReturn;
        }
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
            return trackIfNecessary(delegate.poll());

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
                return trackIfNecessary(delegate.take());
            } else {
                sleep();
            }
        }
    }

    /**
     * If we decide we want pluggable behavior here, take a look at LMAX Disruptor's WaitStrategy classes
     */
    private void sleep() throws InterruptedException {
        if (sleeps != null) {
            sleeps.mark();
        }
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
                return trackIfNecessary(delegate.poll(timeoutNanos - totalSleepNanos, TimeUnit.NANOSECONDS));
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
        int count = delegate.drainTo(c);
        if (pendingItems != null) {
            pendingItems.dec(count);
        }
        return count;
    }

    /**
     * This method does not make any resource constraint checks. It just delegates to the underlying queue.
     * @see java.util.concurrent.BlockingQueue#drainTo(java.util.Collection, int)
     */
    public int drainTo(Collection<? super T> c, int maxElements) {
        int count = delegate.drainTo(c, maxElements);
        if (pendingItems != null) {
            pendingItems.dec(count);
        }
        return count;
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
        if (pendingItems != null) {
            pendingItems.dec();
        }
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
        if (pendingItems != null) {
            pendingItems.inc(c.size());
        }
        return delegate.addAll(c);
    }

    /**
     * This method does not make any resource constraint checks. It just delegates to the underlying queue.
     * @see java.util.concurrent.BlockingQueue#removeAll(java.util.Collection)
     */
    @Override
    public boolean removeAll(Collection<?> c) {
        if (pendingItems != null) {
            pendingItems.dec(c.size());
        }
        return delegate.removeAll(c);
    }

    /**
     * This method does not make any resource constraint checks. It just delegates to the underlying queue.
     * @see java.util.concurrent.BlockingQueue#retainAll(java.util.Collection)
     */
    @Override
    public boolean retainAll(Collection<?> c) {
        //TODO: pendingItems isn't tracking changes via this method yet.
        return delegate.retainAll(c);
    }

    /**
     * This method does not make any resource constraint checks. It just delegates to the underlying queue.
     * @see java.util.concurrent.BlockingQueue#clear()
     */
    @Override
    public void clear() {
        if (pendingItems != null) {
            int size = delegate.size();
            pendingItems.dec(size);
        }
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
        if (pendingItems != null) {
            pendingItems.inc();
        }
        if (additions != null) {
            additions.mark();
        }
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
        if (pendingItems != null) {
            pendingItems.inc();
        }
        if (additions != null) {
            additions.mark();
        }
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

    public void registerMetrics(MetricRegistry metrics, String name) {
        metrics.register(MetricRegistry.name(ResourceConstrainingQueue.class, name, "size"),
                new Gauge<Integer>() {
                    @Override
                    public Integer getValue() {
                        return size();
                    }
                });

        pendingItems = metrics.counter(MetricRegistry.name(ResourceConstrainingQueue.class, name, "pending-items"));
        trackedRemovals = metrics.meter(MetricRegistry.name(ResourceConstrainingQueue.class, name, "remove-poll-take"));
        additions = metrics.meter(MetricRegistry.name(ResourceConstrainingQueue.class, name, "add-offer-put"));
        sleeps = metrics.meter(MetricRegistry.name(ResourceConstrainingQueue.class, "sleeps"));

        if (this.constraintStrategy instanceof MetricsAware) {
            ((MetricsAware) constraintStrategy).registerMetrics(metrics, name);
        }
        if (this.delegate instanceof MetricsAware) {
            ((MetricsAware) delegate).registerMetrics(metrics, name);
        }
        if (this.taskTracker instanceof MetricsAware) {
            ((MetricsAware) taskTracker).registerMetrics(metrics, name);
        }

    }


    public static class ResourceConstrainingQueueBuilder<T> {
        BlockingQueue<T> builderdelegate = null;
        private long builderresourcePollFrequencyMS = DEFAULT_POLL_FREQ;
        ConstraintStrategy<T> builderConstraintStrategy;
        TaskTracker<T> builderTaskTracker;

        public ResourceConstrainingQueueBuilder<T> withConstraintStrategy(ConstraintStrategy<T> cs) {
            this.builderConstraintStrategy = cs;
            return this;
        }

        public ResourceConstrainingQueueBuilder<T> withTaskTracker(TaskTracker<T> tt) {
            this.builderTaskTracker = tt;
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
                cs = ConstraintStrategies.defaultReactiveConstraintStrategy(pollfreq);
            }
            if (d == null) {
                d = new LinkedBlockingQueue<T>();
            }
            return new ResourceConstrainingQueue<T>(d, cs, pollfreq);
        }

    }


}
