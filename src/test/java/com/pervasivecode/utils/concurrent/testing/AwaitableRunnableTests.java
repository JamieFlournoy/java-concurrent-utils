package com.pervasivecode.utils.concurrent.testing;

import static com.google.common.truth.Truth.assertThat;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import com.google.common.truth.Truth;

public class AwaitableRunnableTests {
  static void aNewTask_shouldNotBeFinished(AwaitableRunnable task) {
    assertThat(task.hasTaskFinished()).isFalse();
  }

  static void aTaskThatHasRun_shouldBeFinished(AwaitableRunnable task) {
    task.run();
    assertThat(task.hasTaskFinished()).isTrue();
  }

  static void aTaskThatHasRun_shouldNotBeReRunnable(AwaitableRunnable task) {
    task.run();
    try {
      task.run();
      Truth.assert_().fail("Expected not to be able to re-run the task.");
    } catch (@SuppressWarnings("unused") IllegalStateException ise) {
      // expected
    }
  }

  static void awaitTaskCompletion_withAFinishedTask_shouldReturnImmediately(AwaitableRunnable task)
      throws Exception {
    task.run();
    boolean completedInTime = task.awaitTaskCompletion(1, TimeUnit.SECONDS);
    assertThat(completedInTime).isTrue();
  }

  static void awaitTaskCompletion_shouldWaitUntilTaskCompletes(
      Supplier<AwaitableRunnable> taskCreator) throws Exception {
    ExecutorService executor = Executors.newSingleThreadExecutor();
    for (int i = 0; i < 10000; i++) {
      AwaitableRunnable task = taskCreator.get();
      executor.execute(task);
      task.awaitTaskCompletion();
      assertThat(task.hasTaskFinished()).isTrue();
    }
    executor.shutdown();
  }

}
