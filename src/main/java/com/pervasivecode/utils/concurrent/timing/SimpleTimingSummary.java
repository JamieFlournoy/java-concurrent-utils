package com.pervasivecode.utils.concurrent.timing;

import java.time.Duration;
import com.google.auto.value.AutoValue;
import com.pervasivecode.utils.concurrent.timing.MultistageStopwatch.TimingSummary;

/**
 * A basic TimingSummary implememtation that just  
 */
@AutoValue
public abstract class SimpleTimingSummary implements TimingSummary {
  @Override
  public abstract Duration totalElapsedTime();

  @Override
  public abstract long numStartStopCycles();

  public static SimpleTimingSummary.Builder builder() {
    return new AutoValue_SimpleTimingSummary.Builder();
  }

  /**
   * This object will build a {@link SimpleTimingSummary} instance. See {@link TimingSummary} for
   * explanations of what these values mean.
   */
  @AutoValue.Builder
  public abstract static class Builder {
    public abstract SimpleTimingSummary.Builder setTotalElapsedTime(Duration totalElapsedTime);

    public abstract SimpleTimingSummary.Builder setNumStartStopCycles(long numStartStopCycles);

    public abstract SimpleTimingSummary build();

    // TODO validate that if cycles == 0, elapsed == 0.
  }
}
