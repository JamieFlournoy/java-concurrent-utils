package com.pervasivecode.utils.concurrent.timing;

import java.time.Duration;

public interface QueryableTimer {

  public boolean hasBeenStarted();

  public boolean isRunning();

  public boolean isStopped();

  public Duration elapsed();
}
