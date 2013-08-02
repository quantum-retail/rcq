package com.quantumretail.resourcemon;

import com.quantumretail.rcq.predictor.LoadPredictor;
import com.quantumretail.rcq.predictor.TaskTracker;

import java.util.ArrayList;
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
public class SimplePredictiveResourceMonitor implements ResourceMonitor {
    private final TaskTracker taskTracker;
    private final LoadPredictor loadPredictor;


    public SimplePredictiveResourceMonitor(TaskTracker taskTracker, LoadPredictor loadPredictor) {
        this.taskTracker = taskTracker;
        this.loadPredictor = loadPredictor;
    }


    @Override
    public Map<String, Double> getLoad() {
        // get a list of what is currently executing
        final Collection<Object> tasks = taskTracker.currentTasks();
        return predictLoadForTasks(tasks);
    }

    private Map<String, Double> predictLoadForTasks(Collection<Object> tasks) {
        Collection<Map<String, Double>> taskLoads = new ArrayList<Map<String, Double>>(tasks.size());
        for (Object task : tasks) {
            taskLoads.add(loadPredictor.predictLoad(task));
        }
        return sumTasks(taskLoads);
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

    public TaskTracker getTaskTracker() {
        return taskTracker;
    }
}
