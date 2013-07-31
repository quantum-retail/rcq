package com.quantumretail;

import java.util.Collection;
import java.util.Map;

/**
 * A placeholder for a resource-per-task enhancement, Coming Soon™
 */
public interface TaskTracker {

    /**
     * Gets a collection representing the declared resources of the tasks that are currently executing.
     * This collection isn't synchronized, so it may not be up-to-date at the point when you're looking at it.
     * The collection *may* be an unmodifiable collection, so callers should treat it as such.
     *
     * @return
     */
    Collection<Map<String, Double>> getCurrentlyExecutingTasks();

}
