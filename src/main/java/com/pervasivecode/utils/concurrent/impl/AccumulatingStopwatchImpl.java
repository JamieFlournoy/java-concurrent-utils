package com.pervasivecode.utils.concurrent.impl;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import java.time.Duration;

import com.google.common.collect.ImmutableMap;
import com.pervasivecode.utils.concurrent.api.AccumulatingStopwatch;
import com.pervasivecode.utils.stats.histogram.BucketSelector;
import com.pervasivecode.utils.stats.histogram.BucketSelectors;
import com.pervasivecode.utils.stats.histogram.ConcurrentHistogram;
import com.pervasivecode.utils.stats.histogram.Histogram;
import com.pervasivecode.utils.stats.histogram.ImmutableHistogram;
import com.pervasivecode.utils.stats.histogram.MutableHistogram;
import com.pervasivecode.utils.time.api.CurrentNanosSource;

/**
 * This is a concurrent stopwatch, intended for situations where you want multiple simultaneous
 * timers to be running and then combined into a summary of total time spent, number of stop/start
 * cycles, and a histogram of cycle durations.
 * <p>
 * (If you just want a single stopwatch that can be stopped and started multiple times and you care
 * about the total elapsed time, but not the number of cycles or the histogram, consider using
 * {@link com.google.common.base.Stopwatch} instead.)
 */
// TODO benchmark this class
public abstract class AccumulatingStopwatchImpl<T extends Enum<?>>
    implements AccumulatingStopwatch<T> {
  private static final ConsoleTimingSummaryFormatter SUMMARY_FORMATTER =
      new ConsoleTimingSummaryFormatter();

  private final AtomicLong numRunningTimers = new AtomicLong();
  private final ConcurrentHashMap<T, AtomicLong> numStartStopCycles = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<T, AtomicLong> totalNanos = new ConcurrentHashMap<>();
  private final ImmutableMap<T, MutableHistogram<Long>> latencyHistogramsByType;
  private final String name;
  private final CurrentNanosSource nanoSource;

  public AccumulatingStopwatchImpl(String name, CurrentNanosSource nanoSource) {
    this.name = checkNotNull(name);
    this.nanoSource = checkNotNull(nanoSource);

    // TODO parameterize the bucketer, so we can make more buckets for SAVE_NEW_ENTRY_BATCH.
    final int latencyHistogramBucketCount = 16;
    BucketSelector<Long> latencyHistogramBucketer =
        BucketSelectors.powerOf2LongValues(0, latencyHistogramBucketCount);
    ImmutableMap.Builder<T, MutableHistogram<Long>> latencyHistogramsByTypeBuilder =
        ImmutableMap.builder();

    for (T timerType : this.getTimerTypes()) {
      this.totalNanos.put(timerType, new AtomicLong(0));
      this.numStartStopCycles.put(timerType, new AtomicLong(0));
      latencyHistogramsByTypeBuilder.put(timerType,
          new ConcurrentHistogram<>(latencyHistogramBucketer));
    }
    this.latencyHistogramsByType = latencyHistogramsByTypeBuilder.build();
  }

  // TODO make this a standalone class, and take all the things it needs as a single interface that
  // it takes as a constructor arg.
  public class ActiveTimerImpl implements ActiveTimer {
    private final T timerType;
    private final long startingNanos;
    private final AtomicBoolean isRunning = new AtomicBoolean(true);

    public ActiveTimerImpl(T timerType) {
      this.timerType = timerType;
      this.startingNanos = nanoSource.currentTimeNanoPrecision();
    }

    @Override
    public Duration stopTimer() {
      if (!this.isRunning.get()) {
        throw new IllegalStateException(String.format("Timer already stopped: %s", this.timerType));
      }
      this.isRunning.set(false);

      long endingNanos = nanoSource.currentTimeNanoPrecision();
      long nanosElapsed = endingNanos - this.startingNanos;
      if (nanosElapsed == 0L) {
        throw new IllegalStateException(String.format("Operation %s took 0ns", this.timerType));
      }
      if (nanosElapsed < 0) {
        throw new IllegalStateException(
            String.format("Operation %s took negative time: %d", this.timerType, nanosElapsed));
      }

      long millisElapsed = nanosElapsed / 1_000_000;
      latencyHistogramsByType.get(this.timerType).countValue(millisElapsed);

      AtomicLong timer = AccumulatingStopwatchImpl.this.totalNanos.get(this.timerType);
      long oldTotal = timer.getAndAdd(nanosElapsed);
      long newTotal = timer.get();
      if (newTotal < 0) {
        throw new IllegalStateException(
            String.format("Overflowed nanos counter in timer. %d + %d", oldTotal, nanosElapsed));
      }

      AccumulatingStopwatchImpl.this.numStartStopCycles.get(this.timerType).incrementAndGet();
      AccumulatingStopwatchImpl.this.numRunningTimers.decrementAndGet();

      return Duration.ofMillis(millisElapsed);
    }
  }

  @Override
  public ActiveTimer startTimer(T timertype) {
    checkNotNull(timertype);
    this.numRunningTimers.incrementAndGet();
    return new ActiveTimerImpl(timertype);
  }

  @Override
  public long getTotalElapsedNanos(T timertype) {
    boolean isRunning = this.numRunningTimers.get() != 0;
    checkState(!isRunning, "%s timers are still running", this.numRunningTimers);
    return this.totalNanos.get(timertype).get();
  }

  @Override
  public String getName() {
    return this.name;
  }

  @Deprecated
  public String summarizeToString(T timertype) {
    return SUMMARY_FORMATTER.format(summarize(timertype));
  }

  @Override
  public TimingSummary summarize(T timertype) {
    long totalNanos = getTotalElapsedNanos(timertype);
    long numCycles = this.numStartStopCycles.get(timertype).get();
    return NanosecondBasedRoundingTimingSummary.builder()
        .setNumStartStopCycles(numCycles)
        .setTotalElapsedNanos(totalNanos)
        .build();
  }

  @Override
  public Histogram<Long> timingHistogram(T timertype) {
    return ImmutableHistogram.<Long>copyOf(latencyHistogramsByType.get(timertype));
  }
}
