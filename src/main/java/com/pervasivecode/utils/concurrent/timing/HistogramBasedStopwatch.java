package com.pervasivecode.utils.concurrent.timing;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import com.google.common.collect.ImmutableMap;
import com.pervasivecode.utils.stats.histogram.Histogram;
import com.pervasivecode.utils.stats.histogram.ImmutableHistogram;
import com.pervasivecode.utils.stats.histogram.MutableHistogram;
import com.pervasivecode.utils.time.api.CurrentNanosSource;

/**
 * This type of MultistageConcurrentStopwatch populates a timing {@link Histogram} for each type of
 * operation with counts representing how long those operations took.
 * <p>
 * Example: extending the example in {@link MultistageStopwatch}:
 * 
 * <pre>
 * // Get a histogram describing how long the athletes took to complete the event.
 * Histogram&lt;Long&gt; bicyclingTimes = stopwatch.timingHistogram(BICYCLING);
 *
 * Function&lt;Long, String&gt; longFormatter = (nanos) -&gt; String.format("%d s", nanos / 1_000_000_000L);
 * ConsoleHistogramFormatter&lt;Long&gt; histoFormatter =
 *     new ConsoleHistogramFormatter(longFormatter, 20);
 * System.out.println("Distribution of bicycling race finishing times, in seconds:");
 * System.out.println(histoFormatter.format(bicyclingTimes));
 * </pre>
 * <p>
 * (If you just want a single stopwatch that can be stopped and started multiple times and you care
 * about the total elapsed time, but not the number of cycles or the histogram, consider using
 * {@link com.google.common.base.Stopwatch} instead.)
 */
// TODO benchmark this class
public class HistogramBasedStopwatch<T extends Enum<?>> implements MultistageStopwatch<T> {
  private final AtomicLong numRunningTimers = new AtomicLong();
  private final ConcurrentHashMap<T, AtomicLong> numStartStopCycles = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<T, AtomicLong> totalNanos = new ConcurrentHashMap<>();
  private final ImmutableMap<T, MutableHistogram<Long>> latencyHistogramsByType;
  private final String name;
  private final CurrentNanosSource nanoSource;
  private final T[] timerTypes;

  public HistogramBasedStopwatch(String name, CurrentNanosSource nanoSource, T[] allEnumValues,
      Supplier<MutableHistogram<Long>> histogramSupplier) {
    this.name = checkNotNull(name);
    this.nanoSource = checkNotNull(nanoSource);
    this.timerTypes = checkNotNull(allEnumValues);

    ImmutableMap.Builder<T, MutableHistogram<Long>> latencyHistogramsByTypeBuilder =
        ImmutableMap.builder();
    for (T timerType : this.getTimerTypes()) {
      this.totalNanos.put(timerType, new AtomicLong(0));
      this.numStartStopCycles.put(timerType, new AtomicLong(0));
      latencyHistogramsByTypeBuilder.put(timerType, histogramSupplier.get());
    }
    this.latencyHistogramsByType = latencyHistogramsByTypeBuilder.build();
  }

  @Override
  public StoppableTimer startTimer(T timerType) {
    checkNotNull(timerType);
    this.numRunningTimers.incrementAndGet();

    SimpleMultistageTimer<T> timer = SimpleMultistageTimer.createAndStart(nanoSource, timerType);

    timer.addTimerStoppedListener(() -> {
      Duration timerDuration = timer.elapsed();
      latencyHistogramsByType.get(timerType).countValue(timerDuration.toMillis());
      totalNanos.get(timerType).getAndAdd(timerDuration.toNanos());
      numStartStopCycles.get(timerType).incrementAndGet();
      numRunningTimers.decrementAndGet();
    });

    return timer;
  }

  @Override
  public long getTotalElapsedNanos(T timerType) {
    boolean isRunning = this.numRunningTimers.get() != 0;
    checkState(!isRunning, "%s timers are still running", this.numRunningTimers);
    return this.totalNanos.get(timerType).get();
  }

  public String getName() {
    return this.name;
  }

  @Override
  public TimingSummary summarize(T timerType) {
    long totalNanos = getTotalElapsedNanos(timerType);
    long numCycles = this.numStartStopCycles.get(timerType).get();
    return RoundingTimingSummary.builder().setNumStartStopCycles(numCycles)
        .setTotalElapsedNanos(totalNanos).build();
  }

  public Histogram<Long> timingHistogram(T timerType) {
    return ImmutableHistogram.<Long>copyOf(latencyHistogramsByType.get(timerType));
  }

  @Override
  public Iterable<T> getTimerTypes() {
    return Arrays.asList(this.timerTypes);
  }
}
