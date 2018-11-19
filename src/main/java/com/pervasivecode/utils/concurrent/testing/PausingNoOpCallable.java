package com.pervasivecode.utils.concurrent.testing;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import com.google.common.base.Preconditions;

public class PausingNoOpCallable implements Callable<Integer> {
  private PausingNoOpRunnable blocker = new PausingNoOpRunnable();
  private final int result;

  public PausingNoOpCallable(int inputValue) {
    Preconditions.checkArgument(Math.abs(inputValue) <= 1290,
        "Cubing the value %s will overflow an int.", inputValue);
    result = inputValue * inputValue * inputValue;
  }

  @Override
  public Integer call() throws Exception {
    blocker.run();
    return result;
  }

  public boolean hasPaused() {
    return blocker.hasPaused();
  }

  public void waitUntilPaused() throws InterruptedException {
    blocker.waitUntilPaused();
  }

  public boolean waitUntilPaused(long amount, TimeUnit unit) throws InterruptedException {
    return blocker.waitUntilPaused(amount, unit);
  }

  public void unpause() {
    blocker.unpause();
  }

  public boolean hasUnpaused() {
    return blocker.hasUnpaused();
  }
}
