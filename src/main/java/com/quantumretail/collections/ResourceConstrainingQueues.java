package com.quantumretail.collections;

import com.quantumretail.constraint.ConstraintStrategies;
import com.quantumretail.rcq.predictor.TaskTracker;
import com.quantumretail.rcq.predictor.TaskTrackers;
import com.quantumretail.resourcemon.ResourceMonitors;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Class providing some factory methods of typical ResourceConstrainingQueue combinations
 *
 * There are a lot of moving parts here, so this is intended to make life a little simpler. Note that there is also
 * a builder, in case that helps.
 *
 * @see ResourceConstrainingQueue
 * @see ResourceConstrainingQueue#builder()
 */
public class ResourceConstrainingQueues {

    public static <T> ResourceConstrainingQueue<T> defaultQueue() {
        return new ResourceConstrainingQueue<T>();
    }


    public static <T> ResourceConstrainingQueue<T> defaultQueueWithFeedbackThread(Map<String, Double> thresholds, ScheduledExecutorService feedbackThread) {
        TaskTracker<T> taskTracker = TaskTrackers.defaultTaskTracker();
        return new ResourceConstrainingQueue<T>(new LinkedBlockingQueue<T>(),
                ConstraintStrategies.defaultCombinedConstraintStrategyWithFeedbackThread(thresholds, taskTracker, feedbackThread),
                ResourceMonitors.DEFAULT_UPDATE_FREQ,
                true,
                taskTracker);
    }

    public static <T> ResourceConstrainingQueue<T> defaultQueue(Map<String, Double> thresholds) {

        TaskTracker<T> taskTracker = TaskTrackers.defaultTaskTracker();
        return new ResourceConstrainingQueue<T>(
                new LinkedBlockingQueue<T>(),
                ConstraintStrategies.defaultConstraintStrategy(thresholds, taskTracker),
                ResourceMonitors.DEFAULT_UPDATE_FREQ,
                true,
                taskTracker);
    }

    public static <T> ResourceConstrainingQueue<T> defaultQueueWithFeedbackThread(Map<String, Double> thresholds) {
        ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor(new NameableDaemonThreadFactory("load-feedback-watcher-"));
        return defaultQueueWithFeedbackThread(thresholds, executorService);

    }


    /**
     * The default thread factory
     */
    static class NameableDaemonThreadFactory implements ThreadFactory {
        private static final AtomicInteger poolNumber = new AtomicInteger(1);
        private final ThreadGroup group;
        private final AtomicInteger threadNumber = new AtomicInteger(1);
        private final String namePrefix;

        NameableDaemonThreadFactory(String namePrefix) {
            SecurityManager s = System.getSecurityManager();
            group = (s != null) ? s.getThreadGroup() : Thread.currentThread().getThreadGroup();
            this.namePrefix = namePrefix;
        }

        public Thread newThread(Runnable r) {
            Thread t = new Thread(group, r, namePrefix + threadNumber.getAndIncrement(), 0);
            t.setDaemon(true);
            if (t.getPriority() != Thread.NORM_PRIORITY)
                t.setPriority(Thread.NORM_PRIORITY);
            return t;
        }
    }

}
