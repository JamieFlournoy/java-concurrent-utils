package com.pervasivecode.utils.concurrent.timing;

import java.time.Duration;

/**
 * A timer that can be asked about its current state.
 * <p>
 * Valid timer states are:
 * <ul>
 * <li>New: not started yet.</li>
 * <li>Running: started, but not stopped yet.</li>
 * <li>Stopped: started and then stopped.</li>
 * </ul>
 */
public interface QueryableTimer {

  /**
   * Has the timer been started?
   *
   * @return True if the timer has been started.
   */
  public boolean hasBeenStarted();

  /**
   * Is the timer currently running?
   *
   * @return True if the timer is currently running.
   */
  public boolean isRunning();

  /**
   * Has the timer been stopped?
   *
   * @return True if the timer has been stopped.
   */
  public boolean isStopped();

  /**
   * What is the elapsed time of this timer?
   * <p>
   * If the timer is running, this is the elapsed time since it was started, until the current time.
   * If the timer has been stopped, this is the elapsed time from the moment it was started until
   * the moment it was stopped. (When the timer has been stopped, this value does not change.)
   *
   * @return The amount of time that has elapsed since the timer was started.
   */
  public Duration elapsed();
}
