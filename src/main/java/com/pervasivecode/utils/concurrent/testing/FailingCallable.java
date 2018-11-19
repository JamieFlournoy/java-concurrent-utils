package com.pervasivecode.utils.concurrent.testing;

import java.util.concurrent.Callable;

public class FailingCallable<T> implements Callable<T> {
  public static final String FAILURE_MESSAGE = "This Callable always fails.";

  @Override
  public T call() throws Exception {
    throw new Exception(FAILURE_MESSAGE);
  }
}
