package com.pervasivecode.utils.concurrent.testing;

import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Tests for validating correctness of a PausingRunnable implementation.
 */
public class PausingRunnableTests {

  static void aStartedTask_shouldBecomePaused(PausingRunnable task) throws Exception {
    ExecutorService executor = Executors.newSingleThreadExecutor();
    executor.execute(task);
    task.waitUntilPaused(1, TimeUnit.SECONDS);
    assertThat(task.hasPaused()).isTrue();

    task.waitUntilPaused();
    assertThat(task.hasPaused()).isTrue();

    executor.shutdownNow();
    boolean finished = executor.awaitTermination(100, MILLISECONDS);
    assertThat(finished).isTrue();
  }

  static void aPausedTask_shouldBeUnpausable(PausingRunnable task) throws Exception {
    PausingRunnable pausingTask = task;

    CountDownLatch taskHasFinished = new CountDownLatch(1);
    Runnable pausableAwaitableTask = () -> {
      pausingTask.run();
      taskHasFinished.countDown();
    };

    ExecutorService executor = Executors.newSingleThreadExecutor();
    executor.execute(pausableAwaitableTask);

    pausingTask.waitUntilPaused(1, TimeUnit.SECONDS);
    assertThat(pausingTask.hasPaused()).isTrue();

    pausingTask.unpause();
    executor.shutdown();

    boolean finishedInTime = taskHasFinished.await(1, TimeUnit.SECONDS);
    assertThat(finishedInTime).isTrue();
    assertThat(pausingTask.hasUnpaused()).isTrue();
  }

  static void aTask_shouldBeUnpausableAtAnyTime(PausingRunnable task) throws Exception {
    PausingRunnable pausingTask = task;
    pausingTask.unpause();

    CountDownLatch taskHasFinished = new CountDownLatch(1);
    Runnable pausableAwaitableTask = () -> {
      pausingTask.run();
      taskHasFinished.countDown();
    };

    ExecutorService executor = Executors.newSingleThreadExecutor();
    // This task should run without pausing since we already unpaused it.
    executor.execute(pausableAwaitableTask);
    executor.shutdown();

    boolean finishedInTime = taskHasFinished.await(1, TimeUnit.SECONDS);
    assertThat(finishedInTime).isTrue();
    assertThat(pausingTask.hasUnpaused()).isTrue();
  }
}
