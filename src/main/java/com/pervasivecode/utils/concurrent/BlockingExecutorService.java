package com.pervasivecode.utils.concurrent;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.pervasivecode.utils.concurrent.timing.MultistageStopwatch;
import com.pervasivecode.utils.concurrent.timing.StoppableTimer;
import com.pervasivecode.utils.time.api.CurrentNanosSource;

/**
 * Wrap a ThreadPoolExecutor with an ExecutorService implementation that blocks the thread
 * attempting to submit a task when the task queue is full, rather than rejecting the submitted task
 * (as {@link ThreadPoolExecutor} does).
 * <p>
 * This can be useful in applications where there is a finite but large number of tasks to be
 * processed by the ExecutorService, so the task queue of pending submitted tasks would have to be
 * unreasonably large to prevent the thread(s) generating {@link Runnable} or {@link Callable}
 * instances from experiencing task rejection (a {@link RejectedExecutionException}). With a
 * {@link ThreadPoolExecutor} this would result in rejected tasks. With this class, the
 * task-submitting thread can be throttled simply by the fact that {@link #submit} will block until
 * the work queue of the enclosed {@link ThreadPoolExecutor} has enough space to enqueue the task.
 * <p>
 * A similar effect can be produced by the {@link ThreadPoolExecutor}'s
 * {@link ThreadPoolExecutor.CallerRunsPolicy}, but in that case, the decision to run tasks on the
 * task-generating thread creates situations where a long-running task would keeping the
 * task-generating caller busy, resulting in threads in the {@link ThreadPoolExecutor} finishing
 * their own tasks and then being starved for new tasks. This drawback could be partly mitigated by
 * lengthening the work queue (to try and ensure that there are enough pending tasks in the queue to
 * keep the worker threads busy until the work-generating thread is finished and can fill the queue
 * with more tasks) but that means the caller has to estimate how big the queue will need to be
 * based on how long a task will take to finish.
 * <p>
 * This class embodies a simpler solution from the caller's perspective, which is to just block the
 * task-generating thread(s) while the queue is full, allowing it to run whenever the queue has
 * available space to fill.
 */
public class BlockingExecutorService implements ExecutorService {
  /**
   * The kind of time-consuming activity that the BlockingExecutorService is engaged in, for use as
   * a timer-type value in an {@link MultistageStopwatch} that is measuring how much time is being
   * spent in each activity.
   */
  public enum Operation {
  BLOCK, QUEUE, GRACEFUL_SHUTDOWN
  }

  private final int maxQueueCapacity;
  private final int maxUnblockedTaskCount;
  private final Semaphore queueSlotSemaphore;
  private final ExecutorService executor;
  private final MultistageStopwatch<Operation> stopwatch;
  private final AtomicBoolean isQueueShutdown = new AtomicBoolean(false);
  private final CurrentNanosSource nanoSource;

  // TODO try to make the taskSubmittingThread be a thread in the regular executor service, to
  // simplify the implementation of this class.
  private final ExecutorService taskSubmittingThread = Executors.newSingleThreadExecutor();

  /**
   * Create a BlockingExecutorService with the specified configuration.
   * 
   * @param config An object containing configuration information for the BlockingExecutorService
   *        instance being created.
   */
  public BlockingExecutorService(BlockingExecutorServiceConfig config) {
    // Note: validation of config values is done in BlockingExecutorServiceConfig.Builder#build(),
    // so we don't need to do it here.
    maxQueueCapacity = config.queueSize();
    stopwatch = config.stopwatch();
    nanoSource = config.currentNanosSource();


    // TODO update this comment.

    // Make a semaphore with enough permits to fill the queue. ThreadPoolExecutor will reject a
    // task if the queue is full and a new worker thread hasn't started up and grabbed an item,
    // so we can't rely on (maxQueueCapacity + config.maxThreads()) tasks safely being executable
    // without RejectedExecutionException.

    maxUnblockedTaskCount = maxQueueCapacity + config.numThreads();
    queueSlotSemaphore = new Semaphore(maxUnblockedTaskCount);

    executor = makeExecutor(config);
  }

  private static ThreadPoolExecutor makeExecutor(BlockingExecutorServiceConfig config) {
    // Make the queue large enough that it never actually fills, its capacity being guarded by
    // queueSlotSemaphore.
    int actualQueueSize = config.queueSize() + 1;
    
    ThreadPoolExecutor tpe = new ThreadPoolExecutor(config.numThreads(), config.numThreads(), 0L,
        SECONDS, new ArrayBlockingQueue<>(actualQueueSize),
        new ThreadFactoryBuilder().setNameFormat(config.nameFormat()).build());

    // This should never be needed, helps to iron out odd cases where ThreadPoolExecutor is
    // starting up its worker threads and rejects tasks even though it hasn't started all of its
    // core threads yet(!). (This is not hypothetical, but was experimentally determined.)
    tpe.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
    return tpe;
  }

  /**
   * The total number of tasks that can be executed/submitted/invoked without blocking the calling
   * thread.
   * <p>
   * This is exposed only so that tests don't have to duplicate the specific implementation of how
   * this value is determined.
   */
  int maxUnblockedTaskCount() {
    return maxUnblockedTaskCount;
  }

  @Override
  public void execute(Runnable command) {
    rejectIfShutdown();
    checkNotNull(command);
    StoppableTimer blockTimer = stopwatch.startTimer(Operation.BLOCK);
    try {
      queueSlotSemaphore.acquire();
    } catch (InterruptedException ie) {
      throw new RejectedExecutionException(ie);
    } finally {
      blockTimer.stopTimer();
    }

    StoppableTimer queueTimer = stopwatch.startTimer(Operation.QUEUE);
    Runnable wrappedTask = new Runnable() {
      @Override
      public void run() {
        queueTimer.stopTimer();
        try {
          command.run();
        } finally {
          onQueuedTaskFinished();
        }
      }
    };

    try {
      executor.execute(wrappedTask);
    } catch (RejectedExecutionException ree) {
      // This should never happen due to the use of the queueSlotSemaphore to avoid overfilling the
      // queue, but if it does somehow happen, we still need to mark the queue slot as being
      // available again.
      onQueuedTaskFinished();
      throw ree;
    }
  }

  private void onQueuedTaskFinished() {
    queueSlotSemaphore.release();
    if (isQueueShutdown.get()) {
      boolean queueIsEmpty = queueSlotSemaphore.availablePermits() == maxUnblockedTaskCount;
      if (queueIsEmpty) {
        // No more tasks are going to be submitted, so it's safe to shut down the wrapped
        // ExecutorServices.
        executor.shutdown();
        taskSubmittingThread.shutdown();
      }
    }
  }

  @Override
  public void shutdown() {
    isQueueShutdown.set(true);
  }

  /**
   * Shut down this executor service, and attempt to cancel running tasks. Tasks that have not
   * started will be returned. Note: the returned tasks are wrapper Runnable instances that perform
   * internal queue and timer bookkeeping for this executor service, in addition to executing a
   * Runnable or Callable task that a caller submitted via {@code execute}, {@code submit},
   * {@code invokeAll}, or {@code invokeAny}.
   * <p>
   * As a result, none of the submitted tasks will appear as elements of the returned list; that is,
   * the intersection of the set of submitted tasks and the set of tasks returned from this method
   * is the null set.
   * <p>
   * (Also, due to internal implementation details, there may be additional housekeeping tasks
   * present in the list of tasks that is returned, which do not directly correspond to any tasks
   * that a caller submitted.)
   *
   * @return A list of wrapper Runnable instances that execute the submitted tasks (Runnable or
   *         Callable instances).
   */
  @Override
  public List<Runnable> shutdownNow() {
    isQueueShutdown.set(true);
    List<Runnable> queuedSubmittingTasks = taskSubmittingThread.shutdownNow();
    List<Runnable> queuedExecutorTasks = executor.shutdownNow();

    // Each Runnable in queuedExecutorTasks corresponds to a queueSlotSemaphore permit that was
    /// acquired for that task, and awaitTermination uses the queueSlotSemaphore count to determine
    // whether there are any more tasks that are going to run. So we'll need to release the permits
    // that were reserved for the queuedExecutorTasks that didn't run yet (and never will run), so
    // that awaitTermination can accurately determine when no more tasks will be run.
    queueSlotSemaphore.release(queuedExecutorTasks.size());

    return ImmutableList.<Runnable>builder() //
        .addAll(queuedExecutorTasks) //
        .addAll(queuedSubmittingTasks) //
        .build();
  }

  @Override
  public boolean isShutdown() {
    return isQueueShutdown.get();
  }

  @Override
  public boolean isTerminated() {
    return isShutdown() && taskSubmittingThread.isTerminated() && executor.isTerminated();
  }

  @Override
  public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
    checkState(isQueueShutdown.get(), "shutdown has not been called yet.");
    long nanosBeforeQueueWait = this.nanoSource.currentTimeNanoPrecision();

    StoppableTimer terminationTimer = stopwatch.startTimer(Operation.GRACEFUL_SHUTDOWN);
    final boolean queueTerminatedInTime =
        this.queueSlotSemaphore.tryAcquire(this.maxQueueCapacity, timeout, unit);
    if (!queueTerminatedInTime) {
      terminationTimer.stopTimer();
      // We timed out waiting, so just return now.
      return false;
    }
    // In case someone calls awaitTermination again:
    this.queueSlotSemaphore.release(this.maxQueueCapacity);

    long nanosAfterQueueWait = this.nanoSource.currentTimeNanoPrecision();
    long nanosElapsedDuringQueueWait = nanosAfterQueueWait - nanosBeforeQueueWait;
    long remainingTimeoutNanos = unit.toNanos(timeout) - nanosElapsedDuringQueueWait;

    // The queue has already been marked as shut down and is empty now, so we can go ahead and
    // gracefully shut down the ThreadPoolExecutor with whatever portion of the original timeout
    // remains.
    this.executor.shutdown();

    // TODO also await termination of the taskSubmittingThread here.

    // Wait for all the active threads in the ThreadPoolExecutor to finish, as long as that happens
    // within the remaining timeout period.
    final boolean executorTerminatedInTime =
        executor.awaitTermination(remainingTimeoutNanos, NANOSECONDS);
    terminationTimer.stopTimer();
    return executorTerminatedInTime;
  }

  @Override
  public <T> Future<T> submit(Callable<T> task) {
    FutureTask<T> wrapped = new FutureTask<>(task);
    this.execute(wrapped);
    return wrapped;
  }

  @Override
  public <T> Future<T> submit(Runnable task, T result) {
    rejectIfShutdown();
    return submit(new Callable<T>() {
      @Override
      public T call() throws Exception {
        task.run();
        return result;
      }
    });
  }

  @Override
  public Future<?> submit(Runnable task) {
    FutureTask<Void> wrapped = new FutureTask<Void>(task, null);
    execute(wrapped);
    return wrapped;
  }

  private void rejectIfShutdown() {
    if (isQueueShutdown.get()) {
      throw new RejectedExecutionException("The blocking queue has been shut down already.");
    }
  }

  @Override
  public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks)
      throws InterruptedException {
    return invokeAll(tasks, true, -1, SECONDS);
  }

  @Override
  public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout,
      TimeUnit unit) throws InterruptedException {
    return invokeAll(tasks, false, timeout, unit);
  }

  private <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks,
      boolean waitForever, long timeout, TimeUnit unit) throws InterruptedException {
    rejectIfShutdown();

    long remainingTimeoutNanos = unit.toNanos(timeout);

    ArrayBlockingQueue<Future<T>> submittedTasks = new ArrayBlockingQueue<>(tasks.size());

    CountDownLatch tasksStillRunning = new CountDownLatch(tasks.size());
    for (Callable<T> task : tasks) {
      long enqueueStartTimeNanos = nanoSource.currentTimeNanoPrecision();
      Callable<T> wrapped = () -> {
        try {
          return task.call();
        } finally {
          tasksStillRunning.countDown();
        }
      };
      Runnable submitWrapped = () -> {
        Future<T> result = this.submit(wrapped);
        submittedTasks.add(result);
      };

      // Use taskSubmittingThread to submit tasks, to prevent the thread that called invokeAll
      // from blocking (since there may be a timeout that would be exceeded while it's blocked, and
      // we want to return promptly once that timeout has been exceeded).
      long submitStartTimeNanos = nanoSource.currentTimeNanoPrecision();
      long preSubmitDurationNanos = submitStartTimeNanos - enqueueStartTimeNanos;
      remainingTimeoutNanos -= preSubmitDurationNanos;
      boolean timedOut = false;
      Future<?> submitterResult = taskSubmittingThread.submit(submitWrapped);
      try {
        if (waitForever) {
          submitterResult.get();
        } else {
          try {
            submitterResult.get(remainingTimeoutNanos, NANOSECONDS);
          } catch (@SuppressWarnings("unused") TimeoutException te) {
            submitterResult.cancel(true);
            timedOut = true;
          }
        }
      } catch (ExecutionException ee) {
        // This should never happen because submitWrapped shouldn't throw any exceptions.
        throw new RuntimeException("Unexpected exception thrown by task submitter thread.", ee);
      }
      if (waitForever) {
        continue;
      }
      if (timedOut) {
        remainingTimeoutNanos = 0L;
      } else {
        long submitEndTimeNanos = nanoSource.currentTimeNanoPrecision();
        long submitDurationNanos = submitEndTimeNanos - submitStartTimeNanos;
        remainingTimeoutNanos -= submitDurationNanos;
      }
      if (remainingTimeoutNanos <= 0) {
        break;
      }
    }

    if (waitForever) {
      // invokeAll is supposed to return after all tasks are done. So, wait for that to happen.
      tasksStillRunning.await();

      // The task inside the "wrapped" Callable has finished, but the "wrapped" callable may not
      // have exited and become marked as "done" yet. So we'll call get() to ensure that there are
      // no wrapped Callables that are almost-but-not-quite done before returning, since the
      // docs for invokeAll say that the returned tasks must already be done.
      // TODO consider using ListeningExecutorService to avoid this extra complexity.
      for (Future<T> submittedTask : submittedTasks) {
        if (!submittedTask.isDone()) { // This is rare but does happen under load sometimes.
          try {
            submittedTask.get();
          } catch (@SuppressWarnings("unused") ExecutionException ee) {
            // Ignore it; we don't care about the result of get(), just the delay.
          }
        }
      }
      return ImmutableList.copyOf(submittedTasks);
    }

    if (remainingTimeoutNanos > 0) {
      // If we stopped submitting tasks early, we'll need to account for the fact that they will
      // never finish by decreasing the value of tasksStillRunning to match the real figure.
      int numTasksActuallyStarted = submittedTasks.size();
      int numTasksNotStarted = tasks.size() - numTasksActuallyStarted;
      for (int i = 0; i < numTasksNotStarted; i++) {
        tasksStillRunning.countDown();
      }

      // Now, give all of the tasks we did start the remainder of the timeout to finish. (If they
      // finish sooner, this will return as soon as that happens.)
      tasksStillRunning.await(remainingTimeoutNanos, NANOSECONDS);
    }

    ArrayList<Future<T>> succeededTasks = new ArrayList<>(submittedTasks.size());
    for (Future<T> submittedTask : submittedTasks) {
      if (submittedTask.isDone()) {
        if (!submittedTask.isCancelled()) {
          succeededTasks.add(submittedTask);
        }
      } else {
        submittedTask.cancel(true);
      }
    }

    return ImmutableList.copyOf(succeededTasks);
  }

  @Override
  public <T> T invokeAny(Collection<? extends Callable<T>> tasks)
      throws InterruptedException, ExecutionException {
    try {
      return invokeAny(tasks, true, -1, SECONDS);
    } catch (TimeoutException te) {
      // Should never happen, but just in case:
      throw new ExecutionException(te);
    }
  }

  @Override
  public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
      throws InterruptedException, ExecutionException, TimeoutException {
    return invokeAny(tasks, false, timeout, unit);
  }

  private <T> T invokeAny(Collection<? extends Callable<T>> tasks, boolean waitForever,
      long timeout, TimeUnit unit)
      throws InterruptedException, ExecutionException, TimeoutException {

    rejectIfShutdown();

    long timeoutNanosRemaining = unit.toNanos(timeout);
    long enqueueStartTimeNanos = nanoSource.currentTimeNanoPrecision();

    ArrayBlockingQueue<Future<T>> submittedTasks = new ArrayBlockingQueue<>(tasks.size());
    AtomicReference<Future<?>> taskSubmitterRef = new AtomicReference<>();
    AtomicBoolean alreadyHaveSuccessfulResult = new AtomicBoolean(false);

    T result = null;
    // Null could be a valid result, so track success explicitly rather than via != null.
    boolean haveResult = false;
    // Save exceptions for re-throwing if no task succeeds.
    ExecutionException lastExecutionException = null;
    InterruptedException lastInterruptedException = null;
    TimeoutException pollTimedOutException = null;

    try {
      // This CompletionService will present the completed Future<T> instances in the order in which
      // they finished, so we can wait for the first successful result and cancel all the rest right
      // away.
      ExecutorCompletionService<T> blockingCompletionService =
          new ExecutorCompletionService<>(this);

      // Submit until either (1) all tasks are submitted or (2) one has already succeeded.
      for (Callable<T> task : tasks) {
        if (alreadyHaveSuccessfulResult.get()) {
          break;
        } else {
          Callable<T> wrapped = () -> {
            // If this doesn't throw an Exception, we've got a successful result.
            T wrappedResult = task.call();
            // Try to avoid submitting any more tasks since we have a result already.
            alreadyHaveSuccessfulResult.set(true);
            return wrappedResult;
          };
          Runnable submitWrapped = () -> {
            Future<T> submitResult = blockingCompletionService.submit(wrapped);
            submittedTasks.add(submitResult);
          };
          // Use taskSubmittingThread to submit tasks, to prevent the thread that called invokeAny
          // from blocking. (This will allow us to immediately cancel all the other tasks & return
          // the result when one succeeds.)
          Future<?> submitterResult = taskSubmittingThread.submit(submitWrapped);
          taskSubmitterRef.set(submitterResult);
        }
      }

      // If we have a successful result but the taskSubmittingThread is blocked trying to enqueue
      // another task, cancel it, because we don't need for it to run.
      Future<?> submitterResult = taskSubmitterRef.get();
      if (alreadyHaveSuccessfulResult.get() && submitterResult != null) {
        submitterResult.cancel(true);
      }

      // Get task results in the order in which the tasks finished. When the first successful task
      // is
      // encountered, save its result and cancel all the rest. (If no task is successful, we will
      // have
      // already executed every task in order to determine that, so there's nothing left to cancel.)

      long pollStartTimeNanos = nanoSource.currentTimeNanoPrecision();
      long enqueueElapsedNanos = pollStartTimeNanos - enqueueStartTimeNanos;
      timeoutNanosRemaining -= enqueueElapsedNanos;
      long totalPollTime = 0L;
      int numFailuresSoFar = 0;

      for (int i = 0; i < tasks.size(); i++) {
        final Future<T> futureResult;
        if (waitForever) {
          futureResult = blockingCompletionService.take();
        } else {
          if (timeoutNanosRemaining > 0) {
            // We have a specified amount of time to wait for a successful result.
            pollStartTimeNanos = nanoSource.currentTimeNanoPrecision();
            futureResult = blockingCompletionService.poll(timeoutNanosRemaining, NANOSECONDS);
            long pollEndTimeNanos = nanoSource.currentTimeNanoPrecision();
            // In case we got a result from a failed task, subtract the time it took to get that
            // result from timeoutNanosRemaining for use in the next iteration.
            long lastPollDurationNanos = pollEndTimeNanos - pollStartTimeNanos;
            totalPollTime += lastPollDurationNanos;
            timeoutNanosRemaining -= lastPollDurationNanos;
          } else {
            futureResult = null;
          }
        }
        if (futureResult == null) {
          // The timeout expired and poll returned null: no tasks completed, so we should throw a
          // TimeoutException from this method.
          long elapsedBeforeTimeoutMillis =
              NANOSECONDS.toMillis(enqueueElapsedNanos + totalPollTime);
          pollTimedOutException =
              new TimeoutException("Timeout exceeded waiting for a successful result. ("
                  + enqueueElapsedNanos + "ns enqueueing + " + totalPollTime + "ns poll time = "
                  + elapsedBeforeTimeoutMillis + "ms before timeout. " + timeoutNanosRemaining
                  + "ns (" + (timeoutNanosRemaining / 1000000L) + "ms) timeout remaining, "
                  + numFailuresSoFar + " failed results skipped before last poll timed out.)");
          break;
        }
        try {
          result = futureResult.get();
          haveResult = true;
          break;
        } catch (ExecutionException ee) {
          numFailuresSoFar++;
          // TODO take an optional logger via BlockingExecutorServiceConfig and log this.
          lastExecutionException = ee;
        }
      }
    } catch (InterruptedException ie) {
      lastInterruptedException = ie;
    }

    // At this point, all tasks that are going to be enqueued have been enqueued, and we've either:
    // 1) gotten a successful result, possibly leaving some tasks running or done-but-unexamined
    // 2) iterated through all of the tasks, finding only failures
    // 3) been interrupted
    // In cases 1 and 3, there may be tasks scheduled to run or still running which we don't need
    // anymore.
    for (Future<T> submittedTask : submittedTasks) {
      submittedTask.cancel(true); // This is OK to do even if the task is already done or cancelled
    }

    if (haveResult) {
      return result;
    }
    if (pollTimedOutException != null) {
      throw pollTimedOutException;
    }
    if (lastExecutionException != null) {
      throw lastExecutionException;
    }
    if (lastInterruptedException != null) {
      throw lastInterruptedException;
    }
    throw new IllegalStateException(
        "Invalid internal state: no success, no failures, not interrupted.");
  }
}
