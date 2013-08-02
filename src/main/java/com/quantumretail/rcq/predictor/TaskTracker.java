package com.quantumretail.rcq.predictor;

import java.util.Collection;

/**
 * A placeholder for a resource-per-task enhancement, Coming Soon™
 */
public interface TaskTracker<T> {

    /**
     * Gets a collection representing the items that are currently in progress, where the definition of "in progress"
     * may be somewhat usage-dependent.
     * This collection isn't synchronized, so it may not be up-to-date at the point when you're looking at it.
     * The collection *may* be an unmodifiable collection, so callers should treat it as such.
     *
     * @return
     */
    Collection<T> currentTasks();

    /**
     * If we need to track tasks, we probably need a way of registering them for tracking.
     * @param nextItem
     */
    T register(T nextItem);
}
