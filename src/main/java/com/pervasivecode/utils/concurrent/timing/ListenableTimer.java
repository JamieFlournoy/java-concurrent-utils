package com.pervasivecode.utils.concurrent.timing;

public interface ListenableTimer {
  public static interface StateChangeListener {
    public void stateChanged();
  }

  public void addTimerStartedListener(StateChangeListener stateChangeListener);

  public void addTimerStoppedListener(StateChangeListener stateChangeListener);
}
