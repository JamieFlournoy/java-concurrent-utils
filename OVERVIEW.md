#  Code Overview

If you prefer Javadocs, they are available on `javadoc.io`:

[![Javadocs](https://www.javadoc.io/badge/com.pervasivecode/concurrent-utils.svg)](https://www.javadoc.io/doc/com.pervasivecode/concurrent-utils)

## Interfaces


### In package com.pervasivecode.utils.concurrent.timing:

#### [ListenableTimer](src/main/java/com/pervasivecode/utils/concurrent/timing/ListenableTimer.java)

A timer that can be observed by a `StateChangeListener`, which can be invoked when the timer starts or stops (or both).

#### [ListenableTimer.StateChangeListener](src/main/java/com/pervasivecode/utils/concurrent/timing/ListenableTimer.java)

A listener that will be notified when the state of a `ListenableTimer` changes.

#### [MultistageStopwatch](src/main/java/com/pervasivecode/utils/concurrent/timing/MultistageStopwatch.java)

This stopwatch manages multiple concurrent timers tracking each stage of a user-defined multi-stage operation, aggregating the resulting duration values.

#### [MultistageStopwatch.TimingSummary](src/main/java/com/pervasivecode/utils/concurrent/timing/MultistageStopwatch.java)

A summary of the timer activity for one stage of multistage operation.

#### [QueryableTimer](src/main/java/com/pervasivecode/utils/concurrent/timing/QueryableTimer.java)

A timer that can be asked about its current state.

#### [StoppableTimer](src/main/java/com/pervasivecode/utils/concurrent/timing/StoppableTimer.java)

A timer that can be stopped.

## Enums

### In package com.pervasivecode.utils.concurrent.executors:

#### [BlockingExecutorService.Operation](src/main/java/com/pervasivecode/utils/concurrent/executors/BlockingExecutorService.java)

The kind of time-consuming activity that the `BlockingExecutorService` is engaged in, for use as a timer-type value in an MultistageStopwatch that is measuring how much time is being spent in each activity.

## Implementation Classes

### In package com.pervasivecode.utils.concurrent.timing:

#### [HistogramBasedStopwatch](src/main/java/com/pervasivecode/utils/concurrent/timing/HistogramBasedStopwatch.java)

This type of `MultistageStopwatch` populates a timing Histogram for each type of operation with counts representing how long those operations took.

#### [SimpleActiveTimer	](src/main/java/com/pervasivecode/utils/concurrent/timing/SimpleActiveTimer.java)

A timer implementation that is [stoppable](src/main/java/com/pervasivecode/utils/concurrent/timing/StoppableTimer.java), [queryable](src/main/java/com/pervasivecode/utils/concurrent/timing/QueryableTimer.java), and [listenable](src/main/java/com/pervasivecode/utils/concurrent/timing/ListenableTimer.java).

#### [SimpleMultistageStopwatch](src/main/java/com/pervasivecode/utils/concurrent/timing/SimpleMultistageStopwatch.java)

This type of `MultistageStopwatch` keeps a total of elapsed time used by all timers for each type of operation, along with counts representing how long those operations took.

#### [SimpleTimingSummary](src/main/java/com/pervasivecode/utils/concurrent/timing/SimpleTimingSummary.java)

A basic TimingSummary implememtation, with validation.

#### [SimpleTimingSummary.Builder](src/main/java/com/pervasivecode/utils/concurrent/timing/SimpleTimingSummary.java)

This object will build a SimpleTimingSummary instance.

### In package com.pervasivecode.utils.concurrent.executors:

#### [BlockingExecutorService](src/main/java/com/pervasivecode/utils/concurrent/executors/BlockingExecutorService.java)

Wrap a `ThreadPoolExecutor` with an `ExecutorService` implementation that blocks the thread attempting to submit a task when the task queue is full, rather than rejecting the submitted task (as `ThreadPoolExecutor` does).

#### [BlockingExecutorServiceConfig](src/main/java/com/pervasivecode/utils/concurrent/executors/BlockingExecutorServiceConfig.java)

This object holds configuration information for a `BlockingExecutorService` instance.

#### [BlockingExecutorServiceConfig.Builder](src/main/java/com/pervasivecode/utils/concurrent/executors/BlockingExecutorServiceConfig.java)

This object will build a `BlockingExecutorServiceConfig` instance.

## Example Classes

### [TriathlonTimingExample](src/examples/java/com/pervasivecode/utils/concurrent/example/TriathlonTimingExample.java)

Example of how to use `MultistageStopwatch`, `MultistageStopwatch.TimingSummary`, and `DurationFormatter` to time multiple stages of activity by multiple concurrent actors, and to represent the captured timing information in a human-readable form.
