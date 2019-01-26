package com.pervasivecode.utils.concurrent.timing;

import java.util.Objects;
import com.pervasivecode.utils.time.api.CurrentNanosSource;

// TODO add javadocs.
public final class SimpleMultistageStopwatch<T extends Enum<?>>
    extends AbstractMultistageStopwatch<T> {

  // TODO add javadocs.
  public SimpleMultistageStopwatch(CurrentNanosSource nanoSource, T[] allEnumValues) {
    super(nanoSource, allEnumValues);
  }

  @Override
  public StoppableTimer startTimer(T timerType) {
    return startSimpleActiveTimer(timerType);
  }

  @Override
  public boolean equals(Object other) {
    if (other == this) {
      return true;
    }
    if (!(other instanceof SimpleMultistageStopwatch)) {
      return false;
    }
    SimpleMultistageStopwatch<?> otherTimer = (SimpleMultistageStopwatch<?>) other;
    return otherTimer.canEqual(this) //
        && Objects.equals(numRunningTimers, otherTimer.numRunningTimers)
        && Objects.equals(numStartStopCycles, otherTimer.numStartStopCycles)
        && Objects.equals(totalNanos, otherTimer.totalNanos)
        && Objects.equals(allEnumValues, otherTimer.allEnumValues)
        && Objects.equals(nanoSource, otherTimer.nanoSource);
  }

  @Override
  public int hashCode() {
    return Objects.hash(numRunningTimers, numStartStopCycles, totalNanos, allEnumValues,
        nanoSource);
  }

  @Override
  public boolean canEqual(Object other) {
    return (other instanceof SimpleMultistageStopwatch);
  }
}
