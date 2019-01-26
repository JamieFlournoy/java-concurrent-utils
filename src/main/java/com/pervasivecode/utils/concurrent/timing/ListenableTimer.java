package com.pervasivecode.utils.concurrent.timing;

/**
 * A timer that can be observed by a StateChangeListener, which can be invoked when the timer starts
 * or stops (or both).
 */
public interface ListenableTimer {
  /**
   * A listener that will be notified when the state of a ListenableTimer changes.
   */
  public static interface StateChangeListener {
    /**
     * Handle the change of the timer's state.
     */
    public void stateChanged();
  }

  /**
   * Add a StateChangeListener to the list of listeners that will be invoked when the timer is
   * started. If the timer has already been started, calling this method will result in the listener
   * being invoked immediately.
   *
   * @param stateChangeListener The StateChangeListener to add.
   */
  public void addTimerStartedListener(StateChangeListener stateChangeListener);

  /**
   * Add a StateChangeListener to the list of listeners that will be invoked when the timer is
   * stopped. If the timer has already been stopped, calling this method will result in the listener
   * being invoked immediately.
   *
   * @param stateChangeListener The StateChangeListener to add.
   */
  public void addTimerStoppedListener(StateChangeListener stateChangeListener);
}
