# Resource Constraining Queue #

## Why RCQ? ##

RCQ ("Resource Constraining Queue") is a blocking queue that attempts to only return an item if the system has available
resources. The definition of "available resources" is left to an implementation of a simple interface; it is easy to
extend RCQ to fit any definition you can imagine. There are, however, a number of good implementations available
out-of-the-box that use the JVM's built-in resource monitoring to measure system load.

RCQ is intended to be used as the queue given in the constructor to a java.util.concurrent.ThreadPoolExecutor, i.e:

    ResourceConstrainingQueue resourceConstrainingQueue = ResourceConstrainingQueues.defaultQueue();
    ExecutorService executor = new ThreadPoolExecutor(minThreads, maxThreads, 0L, TimeUnit.MILLISECONDS, resourceConstrainingQueue);

In this way, calling `executor.submit(foo)`  will only execute "foo" once the system has available resources. The
alternative is often creating an ThreadPoolExecutor with a number of threads equal to the number of available processors,
or something similar, but that will load the system only if the tasks never block for IO. And it doesn't take memory
or other resources into account at all.

RCQ is implemented as a wrapper around another queue (a "decorator pattern"), with the default delegate queue being a
java.util.concurrent.LinkedBlockingQueue. You may provide your own delegate if you prefer PriorityQueue semantics, or
a different BlockingQueue implementation. RCQ makes no presumptions on the behavior of its delegate beyond those
defined by the BlockingQueue interface.

## Some things that RCQ does *not* do ##

RCQ does not try to reorder items in the queue in order to fit available resources. If, for example, it determines that
it does not have enough resources for the next item in the queue, it does not attempt to see if it instead has enough
 resources for the second item in the queue. It adheres to the contract of Queue, preserving order. Since we don't know
 anything about the items in the queue, or what dependencies might exist between them, it isn't safe to take any
 liberties with the order in which they are returned.

That is not to say that RCQ must be a strictly FIFO queue. Since it merely wraps another BlockingQueue, it can wrap a
PriorityBlockingQueue or any other variant of `BlockingQueue` if you prefer.


## "Load" ##

Much of RCQ is concerned with measuring and constraining based on "resource load". At its core, it's concept of "resources"
are simply strings defined by a set of ResourceMonitor classes. It's easy to implement new ResourceMonitors and hook
them into the system, and they can return any named "resource" that they want. A ResourceMonitor returns a map of
arbitrary strings to doubles, where the double is expected to be a number between 0 and 1. 0 indicates "no load", while
1 indicates "fully loaded". There is nothing in the system constraining the number to be between 0 and 1; it's merely
convention.

Load is used by a `ConstraintStrategy` to constrain the queue when the load is over a certain threshold. Thresholds are
defined just like resources: a map of string to double.

For example:

    // Create the map of thresholds. In this sample, we have just one.
    Map<String,Double> thresholds = new HashMap<String,Double>();
    thresholds.add("MY_NEW_RESOURCE", 0.9);

    // Create the constraint strategy. This example will use just one resource monitor whose results aren't cached.
    ConstraintStrategy strategy = new SimpleReactiveConstraintStrategy<T>(new MyResourceMonitor(), thresholds);

    // Create the queue.
    BlockingQueue queue = new ResourceConstrainingQueue<T>(
                    new LinkedBlockingQueue<T>(),
                    strategy,
                    ResourceMonitors.DEFAULT_UPDATE_FREQ,
                    false);

Those classes are explained in more detail below.

The default ResourceMonitors measure "load" in terms of CPU, HEAP_MEM and LOAD_AVERAGE, which are hopefully
self-explanatory. To define your own resource, simply define a ResourceMonitor, and then in your ConstraintStrategy
set a threshold


## The Moving Parts ##

Here's an overview of the interfaces and classes involved:

#### com.quantumretail.collections.ResourceConstrainingQueue ####

The primary class; this is the queue implementation itself. It implements `BlockingQueue`, and most users should be able
to treat it simply as a `BlockingQueue`.

#### com.quantumretail.constraint.ConstraintStrategy ####

The strategy that the `ResourceConstrainingQueue` will use to decide whether it should return an item. In effect, it is
the thing that answers the question "do we have resources for this item?" where "this item" is the next item on the
list. It has one method: `shouldReturn(T nextItem)`, which returns a boolean.

The two primary implementations of `ConstraintStrategy` are the `SimpleReactiveConstraintStrategy` and the
`SimplePredictiveConstraintStrategy`.

The `SimpleReactiveConstraintStrategy` merely checks the current load on the system (via a `ResourceMonitor`) and returns
"false" when the load gets up beyond a certain threshold.

However, if the requests are particularly "bursty", you might find that the `ReactiveConstraintStrategy` hands out too
much before seeing what the effect on load will be -- for example, if the RCQ is used to feed a very large thread pool,
load is very low, and a lot of items are added at once, we'll get large bursts of threads, followed by a typically a
higher-than-ideal number of threads active.  That's because `ReactiveConstraintStrategy` will continue to return `true` until
those tasks being to be executed and cause an accompanying spike in resources; by the time that has happened, it has
handed out more items than it should.

One alternative is to try to predict what effect the next task we're considering handing out will have on the available
resources, and then determine whether that prediction + the current measured load will put the system beyond any
thresholds. `SimplePredictiveConstraintStrategy` attempts to do just that. To do this, it uses a `TaskTracker`, which
keeps track of which items are currently in-progress,
This is significantly more complex, but that is the price of a slightly more accurate prediction.

Some other potential options (not yet implemented) might be:

* a probabilistic strategy, with a decreasing probability that we hand something out based on available resources
* a strategy that makes some simple assumptions about things that have been handed out recently -- for example,
  assume that anything we've handed out in the last X ms will be adding Y% points, they just haven't yet.
  The ideal value of X and Y could even be learned over time, since we are tracking the real resource usage as well.


#### com.quantumretail.rcq.predictor.LoadPredictor ####

Given an item, what load do we expect it to have?  The default implementation checks to see if the item implements
the `LoadAware` interface, in which case it will use the value reported by the item itself; otherwise, it returns a
default value.

#### com.quantumretail.rcq.predictor.LoadAware ####

An interface that an item in the queue can implement that lets it communicate what its "expected load" is. It expresses
 that "load" as a map of resource type to

#### com.quantumretail.rcq.predictor.TaskTracker ####

This component tracks the number of tasks which are currently executing. It is used by the `SimplePredictiveConstraintStrategy`.

#### com.quantumretail.resourcemon.ResourceMonitor ####

This component actually measures some resource. It can measure one or more; it returns a map of arbitrary string
(representing the resource being measured) and a value, typically between 0 and 1, where 0 means "no load" and 1 means
"fully loaded".

#### com.quantumretail.resourcemon.AggregateResourceMonitor ####

Aggregates several resource monitors together. It just merges their maps, so later monitors trump earlier ones if they
happen to use the same keys.

#### com.quantumretail.resourcemon.CachingResourceMonitor ####

Wraps another resource manager, memoizing it for a configurable time period.

#### com.quantumretail.resourcemon.EWMAMonitor ####

Wraps another resource manager, smoothing its result using an exponentially weighted moving average.


## Factories and Builders ##

RCQ follows the convention of having a class with the plural form of the interface containing static factory methods
returning typical implementations of that interface. For example:

* **`com.quantumretail.collections.ResourceConstrainingQueues`**, containing builders for simple use-cases for complete queues.
* **`com.quantumretail.constraint.ConstraintStrategies`**, containing builders for a variety of ConstraintStrategies.
* **`com.quantumretail.rcq.predictor.LoadPredictors`** *idem*
* **`com.quantumretail.rcq.predictor.TaskTrackers`** *idem*
* **`com.quantumretail.resourcemon.ResourceMonitors`** *idem*


## TODO ##

* A comprehensive builder object that replaces the somewhat cumbersome static helper methods. Some of the static helpers
are getting long enough that they are hard to use.
