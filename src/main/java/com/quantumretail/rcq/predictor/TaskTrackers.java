package com.quantumretail.rcq.predictor;

/**
 * Helper factory methods for TaskTrackers.
 *
 * TaskTrackers aren't complicated, and there aren't a plethora to choose from, but we have factory methods for consistency.
 *
 */
public class TaskTrackers {

    @SuppressWarnings("unchecked")
    public static <T> TaskTracker<T> defaultTaskTracker() {
        return new CallableTaskTracker();
    }
}
