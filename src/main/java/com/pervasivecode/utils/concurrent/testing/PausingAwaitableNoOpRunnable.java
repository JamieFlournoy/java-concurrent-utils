package com.pervasivecode.utils.concurrent.testing;

import java.util.concurrent.TimeUnit;

public class PausingAwaitableNoOpRunnable implements AwaitableRunnable, PausingRunnable {
  private final PausingNoOpRunnable pausingTask = new PausingNoOpRunnable();
  private final AwaitableNoOpRunnable awaitableTask = new AwaitableNoOpRunnable();

  @Override
  public void run() {
    pausingTask.run();
    awaitableTask.run();
  }

  @Override
  public boolean hasTaskFinished() {
    return awaitableTask.hasTaskFinished();
  }

  @Override
  public void awaitTaskCompletion() throws InterruptedException {
    awaitableTask.awaitTaskCompletion();
  }

  @Override
  public boolean awaitTaskCompletion(long amount, TimeUnit unit) throws InterruptedException {
    return awaitableTask.awaitTaskCompletion(amount, unit);
  }

  @Override
  public boolean hasPaused() {
    return pausingTask.hasPaused();
  }

  @Override
  public void waitUntilPaused() throws InterruptedException {
    pausingTask.waitUntilPaused();
  }

  @Override
  public boolean waitUntilPaused(long amount, TimeUnit unit) throws InterruptedException {
    return pausingTask.waitUntilPaused(amount, unit);
  }

  @Override
  public void unpause() {
    pausingTask.unpause();
  }

  @Override
  public boolean hasUnpaused() {
    return pausingTask.hasUnpaused();
  }
}
