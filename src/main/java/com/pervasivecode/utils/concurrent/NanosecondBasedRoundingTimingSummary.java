package com.pervasivecode.utils.concurrent;

import java.util.concurrent.TimeUnit;

import com.google.auto.value.AutoValue;
import com.pervasivecode.utils.concurrent.AccumulatingStopwatch.TimingSummary;

@AutoValue
public abstract class NanosecondBasedRoundingTimingSummary implements TimingSummary {
  public abstract long totalElapsedNanos();

  @Override
  public long totalElapsedTime(TimeUnit timeUnit) {
    long doubleTime = timeUnit.convert(totalElapsedNanos() * 2, TimeUnit.NANOSECONDS);
    return Math.round(doubleTime / 2.0d);
  }

  @Override
  public abstract long numStartStopCycles();

  public static NanosecondBasedRoundingTimingSummary.Builder builder() {
    return new AutoValue_NanosecondBasedRoundingTimingSummary.Builder();
  }

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract NanosecondBasedRoundingTimingSummary.Builder setTotalElapsedNanos(
        long totalElapsedNanos);

    public abstract NanosecondBasedRoundingTimingSummary.Builder setNumStartStopCycles(
        long numStartStopCycles);

    public abstract NanosecondBasedRoundingTimingSummary build();
  }
}
