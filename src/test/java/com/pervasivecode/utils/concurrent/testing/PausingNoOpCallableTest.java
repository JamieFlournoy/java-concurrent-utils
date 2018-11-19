package com.pervasivecode.utils.concurrent.testing;

import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import com.google.common.truth.Truth;

public class PausingNoOpCallableTest {

  private ExecutorService executor;

  @Before
  public void setup() {
    executor = Executors.newSingleThreadExecutor();
  }

  @After
  public void teardown() throws Exception {
    executor.shutdownNow();
    executor.awaitTermination(1, SECONDS);
  }

  @Test
  public void constructor_shouldRejectValuesThatWouldOverflow() {
    try {
      new PausingNoOpCallable(65432);
      Truth.assert_().fail("Expected an exception due to an overly large value.");
    } catch (IllegalArgumentException ie) {
      assertThat(ie).hasMessageThat().contains("overflow");
    }

    try {
      new PausingNoOpCallable(-65432);
      Truth.assert_().fail("Expected an exception due to an overly large value.");
    } catch (IllegalArgumentException ie) {
      assertThat(ie).hasMessageThat().contains("overflow");
    }
  }


  @Test
  public void aNewTask_shouldNotBePaused() {
    PausingNoOpCallable task = new PausingNoOpCallable(54);
    assertThat(task.hasPaused()).isFalse();
  }

  @Test
  public void aStartedTask_shouldBecomePaused() throws Exception {
    PausingNoOpCallable task = new PausingNoOpCallable(43);

    @SuppressWarnings("unused")
    Future<Integer> futureResult = executor.submit(task);

    task.waitUntilPaused(1, SECONDS);
    assertThat(task.hasPaused()).isTrue();

    task.waitUntilPaused();
    assertThat(task.hasPaused()).isTrue();

    executor.shutdownNow();
    boolean finished = executor.awaitTermination(100, MILLISECONDS);
    assertThat(finished).isTrue();
  }

  @Test
  public void aPausedTask_shouldBeUnpausable() throws Exception {
    PausingNoOpCallable task = new PausingNoOpCallable(654);

    @SuppressWarnings("unused")
    Future<Integer> futureResult = executor.submit(task);

    task.waitUntilPaused(1, SECONDS);
    assertThat(task.hasPaused()).isTrue();

    task.unpause();
    Integer result = futureResult.get(1, SECONDS);
    assertThat(result).isEqualTo(654 * 654 * 654);

    assertThat(task.hasUnpaused()).isTrue();
  }

  @Test
  public void aTask_shouldBeUnpausableAtAnyTime() throws Exception {
    PausingNoOpCallable task = new PausingNoOpCallable(432);
    task.unpause();
    Future<Integer> futureResult = executor.submit(task);
    futureResult.get(1, SECONDS);

    assertThat(task.hasUnpaused()).isTrue();
  }
}
