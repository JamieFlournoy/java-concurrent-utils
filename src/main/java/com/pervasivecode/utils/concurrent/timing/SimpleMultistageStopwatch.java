package com.pervasivecode.utils.concurrent.timing;

import java.util.Objects;
import com.pervasivecode.utils.time.CurrentNanosSource;

/**
 * This type of MultistageStopwatch keeps a total of elapsed time used by all timers for each type
 * of operation, along with counts representing how long those operations took.
 * <p>
 * Example: this class would serve as the implementation for the example in
 * {@link MultistageStopwatch}. Refer to {@link MultistageStopwatch}'s class javadoc for the
 * example code.  
 */
public final class SimpleMultistageStopwatch<T extends Enum<?>>
    extends AbstractMultistageStopwatch<T> {

  /**
   * Create a new instance.
   *
   * @param nanoSource A time source with nanoseconds precision.
   * @param allEnumValues All of the values of the enum that represents the stages of this
   *        MultistageStopwatch.
   */
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
    return Objects.equals(numRunningTimers, otherTimer.numRunningTimers)
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
}
