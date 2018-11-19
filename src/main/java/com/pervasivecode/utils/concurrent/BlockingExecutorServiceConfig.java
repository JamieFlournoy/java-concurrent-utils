package com.pervasivecode.utils.concurrent;

import static com.google.common.base.Preconditions.checkArgument;
import com.google.auto.value.AutoValue;
import com.pervasivecode.utils.concurrent.BlockingExecutorService.Operation;
import com.pervasivecode.utils.concurrent.timing.MultistageStopwatch;
import com.pervasivecode.utils.time.api.CurrentNanosSource;

/** This object holds configuration information for a BlockingExecutorService instance. */
@AutoValue
public abstract class BlockingExecutorServiceConfig {
  /**
   * Create an object that will build a {@link BlockingExecutorServiceConfig} instance.
   * 
   * @return an empty config builder.
   */
  public static BlockingExecutorServiceConfig.Builder builder() {
    return new AutoValue_BlockingExecutorServiceConfig.Builder();
  }

  /**
   * A time source with nanoseconds precision.
   * 
   * @return the time source.
   */
  public abstract CurrentNanosSource currentNanosSource();

  /**
   * The maximum numnber of worker threads that should be created and used by the
   * BlockingExecutorService.
   * 
   * @return the number of worker threads.
   */
  public abstract int maxThreads();

  /**
   * The minimum number of threads that should be kept in existence by the BlockingExecutorService
   * even if there is not enough work for them at the moment.
   * 
   * @return the minimum number of worker threads.
   */
  public abstract int minThreads();

  /**
   * The format to use to name worker threads. This must contain a "%d" placeholder which will be
   * replaced with the worker's number.
   * 
   * @return the format string.
   */
  public abstract String nameFormat();

  /**
   * The size of the task queue that should be created to hold pending tasks until they can be run
   * by a worker thread. When this queue is full, calls to {@link BlockingExecutorService#submit}
   * will block. When the queue is not full and the {@link BlockingExecutorService#submit} has not
   * been shut down, calls to {@link BlockingExecutorService#submit} will succeed.
   * 
   * @return the size of the task queue to create.
   */
  public abstract int queueSize();

  /**
   * The number of seconds that idle worker threads (beyond the quantity indicated by
   * {{@link #minThreads()} will wait for another task to work on before exiting. If the number of
   * worker threads is equal to the value of {@link #maxThreads()}, the thread will not exit, even
   * if it has been idle for longer than this number of seconds.
   * 
   * @return a number of seconds to let an idle thread remain idle before it exits.
   */
  public abstract int secondsBeforeIdleThreadExits();

  /**
   * A {@link MultistageStopwatch}{@code <}{@link Operation}{@code >} that will be used to track the
   * amount of time that tasks spend in various parts of the BlockingExecutorService's lifecycle.
   * 
   * @return The stopwatch that will track task lifecycle times.
   */
  public abstract MultistageStopwatch<Operation> stopwatch();

  /**
   * This object will build a {@link BlockingExecutorServiceConfig} instance. See
   * {@link BlockingExecutorServiceConfig} for explanations of what these values mean.
   */
  @AutoValue.Builder
  public static abstract class Builder {
    public abstract BlockingExecutorServiceConfig.Builder setCurrentNanosSource(
        CurrentNanosSource nanosSource);

    public abstract BlockingExecutorServiceConfig.Builder setMaxThreads(int maxThreads);

    public abstract BlockingExecutorServiceConfig.Builder setMinThreads(int minThreads);

    public abstract BlockingExecutorServiceConfig.Builder setNameFormat(String nameFormat);

    public abstract BlockingExecutorServiceConfig.Builder setQueueSize(int queueSize);

    public abstract BlockingExecutorServiceConfig.Builder setSecondsBeforeIdleThreadExits(
        int seconds);

    public abstract BlockingExecutorServiceConfig.Builder setStopwatch(
        MultistageStopwatch<Operation> stopwatch);

    abstract BlockingExecutorServiceConfig buildInternal();

    public BlockingExecutorServiceConfig build() {
      BlockingExecutorServiceConfig config = buildInternal();

      checkArgument(config.queueSize() > 0, "queueSize must be positive.");

      int minThreads = config.minThreads();
      checkArgument(minThreads >= 0, "minThreads cannot be negative.");

      int maxThreads = config.maxThreads();
      checkArgument(maxThreads > 0, "maxThreads must be larger than 0.");

      checkArgument(maxThreads >= minThreads,
          "maxThreads must be >= minThreads (got max %s, min %s)", maxThreads, minThreads);

      checkArgument(config.secondsBeforeIdleThreadExits() >= 0,
          "secondsBeforeIdleThreadExits cannot be negative.");

      checkArgument(config.nameFormat().contains("%d"),
          "nameFormat must contain a %%d placeholder.");

      return config;
    }
  }
}
