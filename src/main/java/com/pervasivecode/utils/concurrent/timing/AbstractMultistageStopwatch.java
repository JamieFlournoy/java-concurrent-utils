package com.pervasivecode.utils.concurrent.timing;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.pervasivecode.utils.time.CurrentNanosSource;

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
  public Iterable<T> getTimerTypes() {
    return allEnumValues;
  }

  @Override
  public TimingSummary summarize(T timerType) {
    boolean isRunning = this.numRunningTimers.get() != 0;
    checkState(!isRunning, "%s timers are still running", this.numRunningTimers);

    long numCycles = this.numStartStopCycles.get(timerType).get();
    Duration totalElapsedTime = Duration.ofNanos(totalNanos.get(timerType).get());
    return SimpleTimingSummary.builder() //
        .setNumStartStopCycles(numCycles) //
        .setTotalElapsedTime(totalElapsedTime) //
        .build();
  }

  @Override
  public abstract boolean equals(Object other);

  @Override
  public abstract int hashCode();
}
