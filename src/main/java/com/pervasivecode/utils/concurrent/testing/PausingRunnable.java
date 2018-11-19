package com.pervasivecode.utils.concurrent.testing;

import java.util.concurrent.TimeUnit;

/**
 * A Runnable that pauses (waiting for a call to {@link PausingRunnable#unpause()}) at the start of
 * its {@link Runnable#run()} method.
 */
public interface PausingRunnable extends Runnable {
  /** Is the PausingRunnable paused right now? */
  boolean hasPaused();

  /** Block the current thread indefinitely, until the PausingRunnable is paused. */
  void waitUntilPaused() throws InterruptedException;

  /**
   * Block the current thread until the PausingRunnable is paused, unless a timeout has expired
   * while waiting. If the timeout expires, this method will return false.
   * 
   * @return true if the PausingRunnable paused before the timeout expired, or false if the timeout
   *         expired before the PausingRunnable paused.
   */
  boolean waitUntilPaused(long amount, TimeUnit unit) throws InterruptedException;

  /** Release a paused PausingRunnable so that it can continue running. */
  void unpause();

  /** Has the PausingRunnable been paused and then unpaused? */
  boolean hasUnpaused();
}
