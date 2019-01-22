package com.pervasivecode.utils.concurrent.timing;

import java.time.temporal.ChronoUnit;
import com.google.auto.value.AutoValue;
import com.pervasivecode.utils.concurrent.timing.MultistageStopwatch.TimingSummary;

@AutoValue
public abstract class RoundingTimingSummary implements TimingSummary {
  public abstract long totalElapsedNanos();

  @Override
  public long totalElapsedTime(ChronoUnit timeUnit) {
    return Math.round(((totalElapsedNanos() * 2) / timeUnit.getDuration().toNanos()) / 2.0d);
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
