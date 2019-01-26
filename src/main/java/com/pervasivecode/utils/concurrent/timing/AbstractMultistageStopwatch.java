package com.pervasivecode.utils.concurrent.timing;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.pervasivecode.utils.time.api.CurrentNanosSource;

abstract class AbstractMultistageStopwatch<T extends Enum<?>> implements MultistageStopwatch<T> {
  protected final AtomicLong numRunningTimers = new AtomicLong();
  protected final Map<T, AtomicInteger> numStartStopCycles;
  protected final Map<T, AtomicLong> totalNanos;
  protected final ImmutableList<T> allEnumValues;
  protected final CurrentNanosSource nanoSource;

  protected AbstractMultistageStopwatch(CurrentNanosSource nanoSource, T[] allEnumValues) {
    this.nanoSource = checkNotNull(nanoSource);
    this.allEnumValues = ImmutableList.copyOf(checkNotNull(allEnumValues));

    ImmutableMap.Builder<T, AtomicLong> totalNanosBuilder = ImmutableMap.builder();
    ImmutableMap.Builder<T, AtomicInteger> cycleCountsBuilder = ImmutableMap.builder();
    for (T enumValue : allEnumValues) {
      totalNanosBuilder.put(enumValue, new AtomicLong());
      cycleCountsBuilder.put(enumValue, new AtomicInteger());
    }
    this.totalNanos = totalNanosBuilder.build();
    this.numStartStopCycles = cycleCountsBuilder.build();
  }

  protected SimpleActiveTimer startSimpleActiveTimer(T timerType) {
    checkNotNull(timerType);
    this.numRunningTimers.incrementAndGet();

    SimpleActiveTimer timer = SimpleActiveTimer.createAndStart(nanoSource);

    timer.addTimerStoppedListener(() -> {
      Duration timerDuration = timer.elapsed();
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
    return totalNanos.get(timerType).get();
  }

  @Override
  public Iterable<T> getTimerTypes() {
    return allEnumValues;
  }

  @Override
  public TimingSummary summarize(T timerType) {
    long totalNanos = getTotalElapsedNanos(timerType);
    long numCycles = this.numStartStopCycles.get(timerType).get();
    return RoundingTimingSummary.builder().setNumStartStopCycles(numCycles)
        .setTotalElapsedNanos(totalNanos).build();
  }

  @Override
  public abstract boolean equals(Object other);

  @Override
  public abstract int hashCode();

  /**
   * Determine whether an instance of another class possibly be equal to an instance of this class.
   * <p>
   * This is used to ensure that instances of subclasses of this class are not considered equal to
   * instances of this class, unless the subclass explicitly states that its instances can be
   * treated interchangeably with instances of this class, via its own {@link #canEqual} method.
   * <p>
   * For more info see: <a href=
   * "https://jqno.nl/equalsverifier/errormessages/coverage-is-not-100-percent/#using-canequal">
   * EqualsVerifier Coverage is not 100%: Using canEqual</a> and
   * 
   * <a href="https://www.artima.com/lejava/articles/equality.html">How to Write an Equality Method
   * in Java </a>.
   *
   * @param other An object that might have the ability to be equal to an instance of this class.
   * @return True if the other object could be equal to this instance.
   */
  public abstract boolean canEqual(Object other);
}
