package com.quantumretail.resourcemon;

import com.quantumretail.TaskTracker;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Returns a resourceMonitor where "load" is defined as the sum of the predicted loads of each task currently running,
 * as reported by the TaskTracker provided in the constructor.
 *
 * It might be helpful to combine this ResourceMonitor with one that measures actual resource usage
 * (AggregateResourceMonitor, CpuResourceMonitor, LoadAverageResourceMonitor, etc.) within a
 * HighestValueAggregateResourceMonitor; that way, you'll be able to react to the predicted load OR real load,
 * whichever is higher.
 */
public class TaskDictatedResourceMonitor implements ResourceMonitor {
    private final TaskTracker taskTracker;
    private final double scalingFactor;

    public TaskDictatedResourceMonitor(TaskTracker taskTracker) {
        this(taskTracker, 1.0);
    }

    public TaskDictatedResourceMonitor(TaskTracker taskTracker, double scalingFactor) {
        this.taskTracker = taskTracker;
        this.scalingFactor = scalingFactor;
    }


    @Override
    public Map<String, Double> getLoad() {
        // get a list of what is currently executing
        final Collection<Map<String, Double>> tasks = taskTracker.getCurrentlyExecutingTasks();
        // sum up what is currently executing and apply the scaling factor to it.
        return applyScalingFactor(sumTasks(tasks));
    }

    /**
     * Note that, for performance reasons, we'll mutate the input map in-place rather than making a copy.
     * We call this method a lot, and it's private, so it's worth it.
     * @param inputMap WILL BE MUTATED
     * @return
     */
    private Map<String, Double> applyScalingFactor(Map<String, Double> inputMap) {
        if (scalingFactor != 0 && scalingFactor != 1.0) {
            for (Map.Entry<String, Double> s : inputMap.entrySet()) {
                inputMap.put(s.getKey(), (s.getValue() / scalingFactor));
            }
        }
        return inputMap;
    }

    private Map<String, Double> sumTasks(Collection<Map<String, Double>> tasks) {
        Map<String, Double> load = new HashMap<String, Double>();
        for (Map<String, Double> task : tasks) {
            for (Map.Entry<String, Double> entry : task.entrySet()) {
                if (entry.getValue() != null) {
                    if (load.containsKey(entry.getKey())) {
                        load.put(entry.getKey(), (load.get(entry.getKey()) + entry.getValue()));
                    } else {
                        load.put(entry.getKey(), entry.getValue());
                    }
                }
            }
        }
        return load;
    }
}
