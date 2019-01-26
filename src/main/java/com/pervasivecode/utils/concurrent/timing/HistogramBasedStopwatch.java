package com.pervasivecode.utils.concurrent.timing;

import static com.google.common.base.Preconditions.checkNotNull;
import java.time.Duration;
import java.util.Objects;
import java.util.function.Supplier;
import com.google.common.collect.ImmutableMap;
import com.pervasivecode.utils.stats.histogram.Histogram;
import com.pervasivecode.utils.stats.histogram.ImmutableHistogram;
import com.pervasivecode.utils.stats.histogram.MutableHistogram;
import com.pervasivecode.utils.time.api.CurrentNanosSource;

/**
 * This type of MultistageStopwatch populates a timing {@link Histogram} for each type of operation
 * with counts representing how long those operations took.
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
 * {@link com.google.common.base.Stopwatch} instead. This class is useful for timing several related
 * operations that are stages in a larger activity, since it allows examination of duration values
 * of individual stages and of the overall activity.)
 */
public final class HistogramBasedStopwatch<T extends Enum<?>>
    extends AbstractMultistageStopwatch<T> {
  //TODO benchmark this class.

  private final ImmutableMap<T, MutableHistogram<Long>> durationHistogramsByType;
  private final String name;

  /**
   * Make a new instance.
   *
   * @param name The name to use to label this stopwatch in formatted output.
   * @param nanoSource A time source with nanoseconds precision.
   * @param allEnumValues All of the values of the enum that represents the stages of this
   *        MultistageStopwatch.
   * @param histogramSupplier A Supplier of empty MutableHistogram instances which will record
   *        duration values.
   */
  public HistogramBasedStopwatch(String name, CurrentNanosSource nanoSource, T[] allEnumValues,
      Supplier<MutableHistogram<Long>> histogramSupplier) {
    super(nanoSource, allEnumValues);

    this.name = checkNotNull(name);

    ImmutableMap.Builder<T, MutableHistogram<Long>> latencyHistogramsByTypeBuilder =
        ImmutableMap.builder();
    for (T timerType : this.getTimerTypes()) {
      latencyHistogramsByTypeBuilder.put(timerType, histogramSupplier.get());
    }
    this.durationHistogramsByType = latencyHistogramsByTypeBuilder.build();
  }

  @Override
  public StoppableTimer startTimer(T timerType) {
    SimpleActiveTimer timer = startSimpleActiveTimer(timerType);

    timer.addTimerStoppedListener(() -> {
      Duration timerDuration = timer.elapsed();
      durationHistogramsByType.get(timerType).countValue(timerDuration.toMillis());
    });

    return timer;
  }

  /**
   * Get the name of this stopwatch.
   *
   * @return The name of this stopwatch.
   */
  public String getName() {
    return this.name;
  }

  /**
   * Get a copy of the histogram of duration values, for the stage identified by the timerType.
   *
   * @param timerType The histogram to return. (There is one histogram per stage, so if the enum
   *        used as the parameterized type T has four enum values, there will be four histograms.)
   * @return The histogram.
   */
  public Histogram<Long> timingHistogramFor(T timerType) {
    return ImmutableHistogram.<Long>copyOf(durationHistogramsByType.get(timerType));
  }

  @Override
  public boolean equals(Object other) {
    if (other == this) {
      return true;
    }
    if (!(other instanceof HistogramBasedStopwatch)) {
      return false;
    }
    HistogramBasedStopwatch<?> otherTimer = (HistogramBasedStopwatch<?>) other;
    return otherTimer.canEqual(this) //
        && Objects.equals(numRunningTimers, otherTimer.numRunningTimers)
        && Objects.equals(numStartStopCycles, otherTimer.numStartStopCycles)
        && Objects.equals(totalNanos, otherTimer.totalNanos)
        && Objects.equals(allEnumValues, otherTimer.allEnumValues)
        && Objects.equals(nanoSource, otherTimer.nanoSource)
        && Objects.equals(durationHistogramsByType, otherTimer.durationHistogramsByType)
        && Objects.equals(name, otherTimer.name);
  }

  @Override
  public int hashCode() {
    return Objects.hash(numRunningTimers, numStartStopCycles, totalNanos, allEnumValues, nanoSource,
        durationHistogramsByType, name);
  }

  @Override
  public boolean canEqual(Object other) {
    return (other instanceof HistogramBasedStopwatch);
  }
}
