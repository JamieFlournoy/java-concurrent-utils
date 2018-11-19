package com.pervasivecode.utils.concurrent.testing;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * A simple Runnable that will pause until released. When released, it finishes running immediately.
 */
public class PausingNoOpRunnable implements PausingRunnable {
  private final CountDownLatch notYetPaused = new CountDownLatch(1);
  private final CountDownLatch canFinish = new CountDownLatch(1);
  private volatile Runnable onPauseHandler = null;

  @Override
  public void run() {
    if (notYetPaused.getCount() < 1) {
      throw new IllegalStateException("This instance can only be run once.");
    }

    try {
      notYetPaused.countDown();
      if (onPauseHandler != null) {
        onPauseHandler.run();
      }
      canFinish.await();
    } catch (@SuppressWarnings("unused") InterruptedException e) {
      // Nothing needs to be done here.
    }
  }

  @Override
  public boolean hasPaused() {
    return notYetPaused.getCount() == 0;
  }

  @Override
  public void waitUntilPaused() throws InterruptedException {
    notYetPaused.await();
  }

  @Override
  public boolean waitUntilPaused(long amount, TimeUnit unit) throws InterruptedException {
    return notYetPaused.await(amount, unit);
  }

  @Override
  public void unpause() {
    canFinish.countDown();
  }

  @Override
  public boolean hasUnpaused() {
    return canFinish.getCount() < 1;
  }

  public void onPause(Runnable onPauseHandler) {
    if (hasPaused()) {
      onPauseHandler.run();
      return;
    }
    this.onPauseHandler = onPauseHandler;
  }
}
