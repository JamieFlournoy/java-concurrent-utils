package com.pervasivecode.utils.concurrent.testing;

import static com.google.common.truth.Truth.assertThat;
import org.junit.Test;

public class PausingNoOpRunnableTest {
  // TODO simplify via com.github.peterwippermann.junit4.parameterizedsuite.ParameterizedSuite;

  @Test
  public void aNewTask_shouldNotBePaused() {
    PausingNoOpRunnable task = new PausingNoOpRunnable();
    assertThat(task.hasPaused()).isFalse();
  }

  @Test
  public void aStartedTask_shouldBecomePaused() throws Exception {
    PausingRunnableTests.aStartedTask_shouldBecomePaused(new PausingNoOpRunnable());
  }

  @Test
  public void aPausedTask_shouldBeUnpausable() throws Exception {
    PausingRunnableTests.aPausedTask_shouldBeUnpausable(new PausingNoOpRunnable());
  }

  @Test
  public void aTask_shouldBeUnpausableAtAnyTime() throws Exception {
    PausingRunnableTests.aTask_shouldBeUnpausableAtAnyTime(new PausingNoOpRunnable());
  }
}
