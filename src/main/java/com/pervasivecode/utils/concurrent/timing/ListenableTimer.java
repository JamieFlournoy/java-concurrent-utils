package com.pervasivecode.utils.concurrent.timing;

import com.pervasivecode.utils.concurrent.timing.SimpleActiveTimer.StateChangeListener;

public interface ListenableTimer {
  public void addTimerStartedListener(StateChangeListener stateChangeListener);

  public void addTimerStoppedListener(StateChangeListener stateChangeListener);
}
