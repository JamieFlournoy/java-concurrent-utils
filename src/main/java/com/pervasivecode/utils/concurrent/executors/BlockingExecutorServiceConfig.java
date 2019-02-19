package com.pervasivecode.utils.concurrent.executors;

import static com.google.common.base.Preconditions.checkState;
import com.google.auto.value.AutoValue;
import com.pervasivecode.utils.concurrent.executors.BlockingExecutorService.Operation;
import com.pervasivecode.utils.concurrent.timing.MultistageStopwatch;
import com.pervasivecode.utils.time.CurrentNanosSource;

/** This object holds configuration information for a {@link BlockingExecutorService} instance. */
@AutoValue
public abstract class BlockingExecutorServiceConfig {
  protected BlockingExecutorServiceConfig() {}

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
   * The number of threads that should be created by the BlockingExecutorService for use in running
   * submitted tasks.
   *
   * @return the number of worker threads.
   */
  public abstract int numThreads();

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
    protected Builder() {}

    public abstract BlockingExecutorServiceConfig.Builder setCurrentNanosSource(
        CurrentNanosSource nanosSource);

    public abstract BlockingExecutorServiceConfig.Builder setNumThreads(int minThreads);

    public abstract BlockingExecutorServiceConfig.Builder setNameFormat(String nameFormat);

    public abstract BlockingExecutorServiceConfig.Builder setQueueSize(int queueSize);

    public abstract BlockingExecutorServiceConfig.Builder setStopwatch(
        MultistageStopwatch<Operation> stopwatch);

    abstract BlockingExecutorServiceConfig buildInternal();

    public BlockingExecutorServiceConfig build() {
      BlockingExecutorServiceConfig config = buildInternal();

      checkState(config.queueSize() > 0, "queueSize must be positive.");
      checkState(config.numThreads() > 0, "numThreads must be positive.");
      checkState(config.nameFormat().contains("%d"),
          "nameFormat must contain a %%d placeholder.");

      return config;
    }
  }
}
