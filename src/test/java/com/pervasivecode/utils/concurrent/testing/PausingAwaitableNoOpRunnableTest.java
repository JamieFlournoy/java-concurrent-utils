package com.pervasivecode.utils.concurrent.testing;

import static com.google.common.truth.Truth.assertThat;
import java.util.function.Supplier;
import org.junit.Test;

public class PausingAwaitableNoOpRunnableTest {
  // TODO simplify via com.github.peterwippermann.junit4.parameterizedsuite.ParameterizedSuite;

  @Test
  public void aNewTask_shouldNotBePaused() {
    PausingAwaitableNoOpRunnable task = new PausingAwaitableNoOpRunnable();
    assertThat(task.hasPaused()).isFalse();
  }

  @Test
  public void aStartedTask_shouldBecomePaused() throws Exception {
    PausingRunnableTests.aStartedTask_shouldBecomePaused(new PausingAwaitableNoOpRunnable());
  }

  @Test
  public void aPausedTask_shouldBeUnpausable() throws Exception {
    PausingRunnableTests.aPausedTask_shouldBeUnpausable(new PausingAwaitableNoOpRunnable());
  }

  @Test
  public void aTask_shouldBeUnpausableAtAnyTime() throws Exception {
    PausingRunnableTests.aTask_shouldBeUnpausableAtAnyTime(new PausingAwaitableNoOpRunnable());
  }

  @Test
  public void aNewTask_shouldNotBeFinished() {
    AwaitableRunnableTests.aNewTask_shouldNotBeFinished(new PausingAwaitableNoOpRunnable());
  }

  @Test
  public void aTaskThatHasRun_shouldBeFinished() {
    PausingAwaitableNoOpRunnable task = new PausingAwaitableNoOpRunnable();
    task.unpause();
    AwaitableRunnableTests.aTaskThatHasRun_shouldBeFinished(task);
  }

  @Test
  public void aTaskThatHasRun_shouldNotBeReRunnable() {
    PausingAwaitableNoOpRunnable task = new PausingAwaitableNoOpRunnable();
    task.unpause();
    AwaitableRunnableTests.aTaskThatHasRun_shouldNotBeReRunnable(task);
  }

  @Test
  public void awaitTaskCompletion_withAFinishedTask_shouldReturnImmediately() throws Exception {
    PausingAwaitableNoOpRunnable task = new PausingAwaitableNoOpRunnable();
    task.unpause();
    AwaitableRunnableTests.awaitTaskCompletion_withAFinishedTask_shouldReturnImmediately(task);
  }

  @Test
  public void awaitTaskCompletion_shouldWaitUntilTaskCompletes() throws Exception {
    Supplier<AwaitableRunnable> supplier = () -> {
      PausingAwaitableNoOpRunnable task = new PausingAwaitableNoOpRunnable();
      task.unpause();
      return task;
    };
    AwaitableRunnableTests.awaitTaskCompletion_shouldWaitUntilTaskCompletes(supplier);
  }
}
