package com.pervasivecode.utils.concurrent.timing;

import java.time.Duration;

/**
 * A timer that can be stopped.
 */
public interface StoppableTimer {
  /**
   * Stop the timer, and obtain the elapsed time since it was started.
   * @return The elapsed time since the timer was started.
   */
  public Duration stopTimer();
}
