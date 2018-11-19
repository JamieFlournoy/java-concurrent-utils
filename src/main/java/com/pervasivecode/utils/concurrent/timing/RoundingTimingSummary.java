package com.pervasivecode.utils.concurrent.timing;

import java.util.concurrent.TimeUnit;
import com.google.auto.value.AutoValue;
import com.pervasivecode.utils.concurrent.timing.MultistageStopwatch.TimingSummary;

@AutoValue
public abstract class RoundingTimingSummary implements TimingSummary {
  public abstract long totalElapsedNanos();

  @Override
  public long totalElapsedTime(TimeUnit timeUnit) {
    long doubleTime = timeUnit.convert(totalElapsedNanos() * 2, TimeUnit.NANOSECONDS);
    return Math.round(doubleTime / 2.0d);
  }

  @Override
  public abstract long numStartStopCycles();

  public static RoundingTimingSummary.Builder builder() {
    return new AutoValue_RoundingTimingSummary.Builder();
  }

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract RoundingTimingSummary.Builder setTotalElapsedNanos(long totalElapsedNanos);

    public abstract RoundingTimingSummary.Builder setNumStartStopCycles(long numStartStopCycles);

    public abstract RoundingTimingSummary build();
  }
}
