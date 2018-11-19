package com.pervasivecode.utils.concurrent.testing;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * This is a simple Runnable whose only purpose is to signal that it has finished running. Task
 * completion can be awaited via the {@link #awaitTaskCompletion()} method. Task status (finished or
 * not) can be observed via the {@link #hasTaskFinished()} method.
 */
public class AwaitableNoOpRunnable implements AwaitableRunnable {
  private final CountDownLatch isFinished = new CountDownLatch(1);

  @Override
  public void run() {
    if (isFinished.getCount() < 1) {
      throw new IllegalStateException("This instance can only be run once.");
    }
    isFinished.countDown();
  }

  @Override
  public boolean hasTaskFinished() {
    return isFinished.getCount() == 0;
  }

  @Override
  public void awaitTaskCompletion() throws InterruptedException {
    isFinished.await();
  }

  @Override
  public boolean awaitTaskCompletion(long amount, TimeUnit unit) throws InterruptedException {
    return isFinished.await(amount, unit);
  }
}
