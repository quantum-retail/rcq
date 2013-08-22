package com.quantumretail.rcq.predictor;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Assumes that the items we're tracking implement either {@link java.util.concurrent.Callable} or {@link Runnable},
 * and tracks their execution by wrapping them in a wrapper Callable or Runnable that notifies this class when they are
 * complete.
 *
 */
public class CallableTaskTracker implements TaskTracker {
    final ConcurrentMap<Object, Object> tasks = new ConcurrentHashMap<Object, Object>();

    final ConcurrentHashMap<Object, Integer> unableToExecuteTaskTries = new ConcurrentHashMap<Object, Integer>();

    @Override
    public int incrementConstrained(Object item) {
        Integer attempts= unableToExecuteTaskTries.get(item);
        if(attempts == null){
            attempts = 0;
        }
        attempts = attempts+1;
        unableToExecuteTaskTries.put(item, attempts);
        return attempts;
    }

    @Override
    public void resetConstrained(Object item) {
        if(unableToExecuteTaskTries.contains(item)){
        unableToExecuteTaskTries.put(item, 1);
        }
    }

    @Override
    public void removeConstrained(Object item) {
        unableToExecuteTaskTries.remove(item);
    }

    @Override
    public Collection<Object> currentTasks() {
        return Collections.unmodifiableCollection(tasks.values());
    }

    @Override
    public Object register(Object nextItem) {

        if (nextItem instanceof Runnable) {
            Object id = new Object(); // could use an AtomicLong here, but we just care about having a unique ID, which new Object() gives us w/o blocking.
            addTask(id, nextItem);
            return wrapRunnable((Runnable) nextItem, id);
        } else if (nextItem instanceof Callable) {
            Object id = new Object();
            addTask(id, nextItem);
            return wrapCallable((Callable) nextItem, id);
        } else {
            // we can't track this.
            return nextItem;
        }
    }

    protected void addTask(Object id, Object task) {
        tasks.put(id, task);
    }

    protected void removeTask(Object id) {
        tasks.remove(id);
    }

    protected Runnable wrapRunnable(final Runnable nextItem, final Object id) {
        return new Runnable() {

            @Override
            public void run() {
                try {
                    nextItem.run();
                } finally {
                    removeTask(id);
                }
            }
        };
    }


    protected Callable wrapCallable(final Callable c, final Object id) {
        return new Callable() {
            @Override
            public Object call() throws Exception {
                try {
                    return c.call();
                } finally {
                    removeTask(id);
                }
            }
        };
    }
}
