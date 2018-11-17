package com.pervasivecode.utils.concurrent;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.pervasivecode.utils.concurrent.AccumulatingStopwatch.ActiveTimer;
import com.pervasivecode.utils.time.api.CurrentNanosSource;

/**
 * A ThreadPoolExecutor wrapper that blocks the submitting task when the task queue is full,
 * rather than rejecting the submitted task.
 */
public class BlockingExecutorService implements ExecutorService {
  // TODO write a test for this class.
  public enum Operation { BLOCK, QUEUE, GRACEFUL_SHUTDOWN }

  public static class BlockingExecutorStopwatch extends AccumulatingStopwatchImpl<Operation> {
    public BlockingExecutorStopwatch(String name, CurrentNanosSource nanoSource) {
      super(name, nanoSource);
    }

    @Override
    public Iterable<Operation> getTimerTypes() { return ImmutableList.copyOf(Operation.values()); }
  }

  @AutoValue
  public static abstract class BlockingExecutorServiceConfig {
    public static BlockingExecutorServiceConfig.Builder builder() {
      return new AutoValue_BlockingExecutorService_BlockingExecutorServiceConfig.Builder();
    }

    public abstract CurrentNanosSource currentNanosSource();
    public abstract int maxThreads();
    public abstract int minThreads();
    public abstract String nameFormat();
    public abstract int queueSize();
    public abstract int secondsBeforeIdleThreadExits();
    public abstract BlockingExecutorStopwatch stopwatch();

    @AutoValue.Builder
    public static abstract class Builder {
      public abstract Builder setCurrentNanosSource(CurrentNanosSource nanosSource);
      public abstract Builder setMaxThreads(int maxThreads);
      public abstract Builder setMinThreads(int minThreads);
      public abstract Builder setNameFormat(String nameFormat);
      public abstract Builder setQueueSize(int queueSize);
      public abstract Builder setSecondsBeforeIdleThreadExits(int seconds);
      public abstract Builder setStopwatch(BlockingExecutorStopwatch stopwatch);

      public abstract BlockingExecutorServiceConfig build();
    }
  }

  private final int maxQueueCapacity;
  private final Semaphore queueSlotSemaphore;
  private final ThreadPoolExecutor executor;
  private final BlockingExecutorStopwatch stopwatch;
  private final AtomicBoolean isQueueShutdown = new AtomicBoolean(false);
  private final CurrentNanosSource nanoSource;

  public BlockingExecutorService(BlockingExecutorServiceConfig config) {
    maxQueueCapacity = config.queueSize();
    checkArgument(maxQueueCapacity > 0, "Queue size must be positive.");

    int minThreads = config.minThreads();
    int maxThreads = config.maxThreads();
    checkArgument(maxThreads >= minThreads,
        "maxThreads must be >= minThreads (got max %s, min %s)", maxThreads, minThreads);

    String nameFormat = config.nameFormat();
    checkArgument(nameFormat.contains("%d"), "nameFormat must contain a %%d placeholder.");

    int secondsBeforeIdleThreadExits = config.secondsBeforeIdleThreadExits();
    stopwatch = config.stopwatch();

    this.nanoSource = config.currentNanosSource();

    BlockingQueue<Runnable> taskQueue = new ArrayBlockingQueue<>(maxQueueCapacity);
    ThreadFactory threadFactory = new ThreadFactoryBuilder().setNameFormat(nameFormat).build();
    executor = new ThreadPoolExecutor(
        minThreads, maxThreads, secondsBeforeIdleThreadExits, SECONDS, taskQueue, threadFactory);

    // Make a semaphore with enough permits to fill the queue size exactly.
    queueSlotSemaphore = new Semaphore(maxQueueCapacity);
  }

  @Override
  public void execute(Runnable command) {
    ActiveTimer blockTimer = stopwatch.startTimer(Operation.BLOCK);
    try {
      queueSlotSemaphore.acquire();
    } catch (InterruptedException ie) {
      throw new RejectedExecutionException(ie);
    }
    blockTimer.stopTimer();
    ActiveTimer queueTimer = stopwatch.startTimer(Operation.QUEUE);
    try {
      executor.execute(new Runnable() {
        @Override
        public void run() {
          queueTimer.stopTimer();
          try {
            command.run();
          } finally {
            queueSlotSemaphore.release();
          }
        }});
    } catch (RejectedExecutionException e) {
      // This should never happen due to the use of the semaphore, but...
      queueSlotSemaphore.release();
      throw e;
    }
  }

  @Override
  public void shutdown() {
    isQueueShutdown.set(true);
  }

  @Override
  public List<Runnable> shutdownNow() {
    return executor.shutdownNow();
  }

  @Override
  public boolean isShutdown() {
    return executor.isShutdown();
  }

  @Override
  public boolean isTerminated() {
    return executor.isTerminated();
  }

  @Override
  public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
    checkState(isQueueShutdown.get(), "shutdown has not been called yet.");
    long nanosBeforeQueueWait = this.nanoSource.currentTimeNanoPrecision();

    ActiveTimer terminationTimer = stopwatch.startTimer(Operation.GRACEFUL_SHUTDOWN);
    final boolean queueTerminatedInTime =
        this.queueSlotSemaphore.tryAcquire(this.maxQueueCapacity, timeout, unit);
    if (!queueTerminatedInTime) {
      terminationTimer.stopTimer();
      // We timed out waiting, so just return now.
      return false;
    }

    long nanosAfterQueueWait = this.nanoSource.currentTimeNanoPrecision();
    long nanosElapsedDuringQueueWait = nanosAfterQueueWait - nanosBeforeQueueWait;
    long remainingTimeoutNanos = unit.toNanos(timeout) - nanosElapsedDuringQueueWait;

    // The queue has already been shut down, so we can go ahead and shut down the
    // ThreadPoolExecutor.
    this.executor.shutdown();
    // Wait for all the active threads in the ThreadPoolExecutor to finish.
    final boolean executorTerminatedInTime =
        executor.awaitTermination(remainingTimeoutNanos, NANOSECONDS);
    terminationTimer.stopTimer();
    return executorTerminatedInTime;
  }

  @Override
  public <T> Future<T> submit(Callable<T> task) {
    rejectIfShutdown();
    ActiveTimer blockTimer = stopwatch.startTimer(Operation.BLOCK);
    try {
      queueSlotSemaphore.acquire();
    } catch (InterruptedException ie) {
      throw new RejectedExecutionException(ie);
    }
    blockTimer.stopTimer();
    ActiveTimer queueTimer = stopwatch.startTimer(Operation.QUEUE);
    try {
        return executor.submit(new Callable<T>() {
          @Override
          public T call() throws Exception {
            queueTimer.stopTimer();
            try {
              return task.call();
            } finally {
              queueSlotSemaphore.release();
            }
          }});
    } catch (RejectedExecutionException e) {
      // This should never happen due to the use of the semaphore, but...
      queueSlotSemaphore.release();
      throw e;
    }
  }

  @Override
  public <T> Future<T> submit(Runnable task, T result) {
    rejectIfShutdown();
    return submit(new Callable<T>(){
      @Override public T call() throws Exception {
        task.run();
        return result;
      }
    });
  }

  @Override
  public Future<?> submit(Runnable task) {
    rejectIfShutdown();
    throw new UnsupportedOperationException();
  }

  private void rejectIfShutdown() {
    if (isQueueShutdown.get()) {
      throw new RejectedExecutionException("The blocking queue has been shut down already.");
    }
  }

  @Override
  public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks)
      throws InterruptedException {
    rejectIfShutdown();
    throw new UnsupportedOperationException();
  }

  @Override
  public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout,
      TimeUnit unit) throws InterruptedException {
    rejectIfShutdown();
    // TODO loop, using semaphore.tryAcquire with the timeout, and cancel any tasks that didn't get
    // submitted. Submit each task separately and adjust its timeout by subtracting the amount of
    // time that it had to wait.
    
//    ImmutableList.Builder<Future<T>> futuresBuilder = ImmutableList<Future<T>>.builder();

    throw new UnsupportedOperationException();
  }

  @Override
  public <T> T invokeAny(Collection<? extends Callable<T>> tasks)
      throws InterruptedException, ExecutionException {
    rejectIfShutdown();
    throw new UnsupportedOperationException();
  }

  @Override
  public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit)
      throws InterruptedException, ExecutionException, TimeoutException {
    rejectIfShutdown();
    throw new UnsupportedOperationException();
  }
}
