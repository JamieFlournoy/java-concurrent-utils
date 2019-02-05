package com.pervasivecode.utils.concurrent.timing;

import static com.google.common.base.Preconditions.checkState;
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

    protected abstract SimpleTimingSummary buildInternal();
    
    public SimpleTimingSummary build() {
      SimpleTimingSummary summary = buildInternal();
      
      long cycles = summary.numStartStopCycles();
      Duration time = summary.totalElapsedTime();

      checkState(cycles >= 0, "numStartStopCycles must be nonnegative. Got: %s", cycles);
      checkState(!time.isNegative(), "totalElapsedTime must be nonnegative. Got: %s", time);

      if (cycles == 0) {
        checkState(time.equals(Duration.ZERO),
            "Zero start-stop cycles must have a total elapsed time of zero. Elapsed: %s", time);
      }
      
      return summary;
    }
  }
}
