package com.pervasivecode.utils.concurrent.testing;

import java.util.concurrent.TimeUnit;


public interface AwaitableRunnable extends Runnable {

  boolean hasTaskFinished();

  void awaitTaskCompletion() throws InterruptedException;

  boolean awaitTaskCompletion(long amount, TimeUnit unit) throws InterruptedException;
}
