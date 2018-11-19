package com.pervasivecode.utils.concurrent.timing;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.pervasivecode.utils.time.api.CurrentNanosSource;

public class SimpleMultistageStopwatch<T extends Enum<?>> implements MultistageStopwatch<T> {
  private final CurrentNanosSource nanoSource;
  private final ImmutableList<T> allEnumValues;
  private final Map<T, AtomicLong> elapsedTimers;
  private final Map<T, AtomicInteger> cycleCounts;

  public SimpleMultistageStopwatch(CurrentNanosSource nanoSource, T[] allEnumValues) {
    this.nanoSource = checkNotNull(nanoSource);
    this.allEnumValues = ImmutableList.copyOf(checkNotNull(allEnumValues));

    ImmutableMap.Builder<T, AtomicLong> elapsedTimersBuilder = ImmutableMap.builder();
    ImmutableMap.Builder<T, AtomicInteger> cycleCountsBuilder = ImmutableMap.builder();
    for (T enumValue : allEnumValues) {
      elapsedTimersBuilder.put(enumValue, new AtomicLong());
      cycleCountsBuilder.put(enumValue, new AtomicInteger());
    }
    elapsedTimers = elapsedTimersBuilder.build();
    cycleCounts = cycleCountsBuilder.build();
  }

  @Override
  public StoppableTimer startTimer(T timerType) {
    checkNotNull(timerType);
    return new SimpleActiveTimer(timerType);
  }

  @Override
  public long getTotalElapsedNanos(T timertype) {
    return elapsedTimers.get(timertype).get();
  }

  @Override
  public Iterable<T> getTimerTypes() {
    return allEnumValues;
  }

  @Override
  public TimingSummary summarize(T timertype) {
    return RoundingTimingSummary.builder() //
        .setNumStartStopCycles(cycleCounts.get(timertype).get()) //
        .setTotalElapsedNanos(elapsedTimers.get(timertype).get()) //
        .build();
  }

  private class SimpleActiveTimer implements StoppableTimer {
    private final long startNanos;
    private final T timerType;
    private boolean stopped = false;

    public SimpleActiveTimer(T timerType) {
      this.startNanos = nanoSource.currentTimeNanoPrecision();
      this.timerType = timerType;
    }

    @Override
    public Duration stopTimer() {
      checkState(!stopped, "This timer is already stopped.");
      stopped = true;
      long elapsedNanos = nanoSource.currentTimeNanoPrecision() - startNanos;
      cycleCounts.get(timerType).incrementAndGet();
      elapsedTimers.get(timerType).addAndGet(elapsedNanos);
      return Duration.of(elapsedNanos, ChronoUnit.NANOS);
    }
  }
}
