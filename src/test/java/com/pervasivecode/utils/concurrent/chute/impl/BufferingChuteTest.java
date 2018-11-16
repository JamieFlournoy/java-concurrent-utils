package com.pervasivecode.utils.concurrent.chute.impl;

import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;

import org.junit.Before;
import org.junit.Test;

import com.google.common.truth.Truth;
import com.pervasivecode.utils.concurrent.chute.api.Chute;
import com.pervasivecode.utils.concurrent.chute.impl.BufferingChute;
import com.pervasivecode.utils.time.testing.FakeNanoSource;

public class BufferingChuteTest {
  private FakeNanoSource currentNanoSource;

  @Before
  public void setup() {
    this.currentNanoSource = new FakeNanoSource();
  }


  //
  // Tests for constructor
  //

  @Test(expected = IllegalArgumentException.class)
  public void constructor_shouldRejectZeroSize() {
    @SuppressWarnings("unused")
    Chute<String> c = new BufferingChute<>(0, currentNanoSource);
  }

  @Test(expected = NullPointerException.class)
  public void constructor_shouldRejectNullNanoSource() {
    @SuppressWarnings("unused")
    Chute<String> c = new BufferingChute<>(1, null);
  }

  //
  // Tests for close, isClosed, and isClosedAndEmpty
  //

  @Test
  public void close_onEmptyChute_shouldImmediatelySucceed() throws Exception {
    ExecutorService es = Executors.newFixedThreadPool(1);
    Chute<String> c = new BufferingChute<>(1, currentNanoSource);

    assertThat(c.isClosed()).isFalse();
    assertThat(c.isClosedAndEmpty()).isFalse();

    Future<?> closeResult = es.submit(() -> {
      try {
        c.close();
      } catch (InterruptedException ie) {
        Truth.assert_().fail("Interrupted when closing: " + ie.getMessage());
      }
   });

    try {
      closeResult.get(10, MILLISECONDS);
    } catch (TimeoutException te) {
      Truth.assert_().fail("close() timed out: " + te.getMessage());
    }

    assertThat(c.isClosed()).isTrue();
    assertThat(c.isClosedAndEmpty()).isTrue();
  }


  //
  // Tests for put method
  //

  @Test
  public void put_withAvailableCapacity_shouldNotBlock() throws Exception {
    ExecutorService es = Executors.newFixedThreadPool(1);
    Chute<String> c = new BufferingChute<>(1, currentNanoSource);

    Future<Integer> putResult = es.submit(() -> {
      try {
        c.put("hiya");
        return 12345;
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    });

    assertThat(putResult.get(10, MILLISECONDS)).isEqualTo(12345);
  }

  @Test
  public void put_onFullChute_shouldBlockUntilNextTake() throws Exception {
    ExecutorService es = Executors.newFixedThreadPool(1);
    Chute<String> c = new BufferingChute<>(1, currentNanoSource);

    String firstItem = "This shouldn't block, but should fill up the buffer.";
    c.put(firstItem);

    String blockedItem = "This should block, because the buffer is already full.";
    Future<Boolean> putResult = es.submit(() -> {
      try {
        c.put(blockedItem);
        return true;
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    });

    try {
      // The put() should still be blocked, so the Future should not be done yet.
      putResult.get(10, MILLISECONDS);
      Truth.assert_().fail("Expected the get() to time out.");
    } catch (@SuppressWarnings("unused") TimeoutException te) {
      // expected
    }

    Optional<String> taken = c.tryTake(10, MILLISECONDS);
    assertThat(taken.get()).isEqualTo(firstItem);

    // The put() should be done now (or should finish within a few ms).
    Boolean result = putResult.get(10, MILLISECONDS);
    assertThat(result).isEqualTo(Boolean.TRUE);

    taken = c.tryTake(10, MILLISECONDS);
    assertThat(taken.get()).isEqualTo(blockedItem);
  }

  @Test
  public void put_onClosedChute_shouldImmediatelyFail() throws Exception {
    ExecutorService es = Executors.newFixedThreadPool(1);
    Chute<String> c = new BufferingChute<>(1, currentNanoSource);

    Future<?> closeResult = es.submit(() -> {
      try {
        c.close();
      } catch (InterruptedException ie) {
        Truth.assert_().fail("Interrupted when closing: " + ie.getMessage());
      }
    });
    try {
      closeResult.get(10, MILLISECONDS);
    } catch (TimeoutException te) {
      Truth.assert_().fail("close() timed out" + te.getMessage());
    }

    Future<String> putResult = es.submit(() -> {
      try {
        c.put("this should fail, since the chute is closed");
        return "fail; the put was unexpectedly successful";
      } catch (IllegalStateException ise) {
        return ise.getMessage();
      } catch (InterruptedException e) {
        return "interrupted: " + e.getMessage();
      }
    });

    assertThat(putResult.get(10, MILLISECONDS)).isEqualTo("Channel is already closed.");
  }


  //
  // Tests for tryTakeNow method
  //

  @Test
  public void tryTakeNow_withEmptyChute_shouldGetNothing() throws Exception {
    Chute<String> c = new BufferingChute<>(1, currentNanoSource);
    Optional<String> taken = c.tryTakeNow();
    assertThat(taken.isPresent()).isFalse();
  }


  @Test
  public void tryTakeNow_withElementInChute_shouldGetIt() throws Exception {
    Chute<String> c = new BufferingChute<>(1, currentNanoSource);
    c.put("hiya");
    Optional<String> taken = c.tryTakeNow();
    assertThat(taken.get()).isEqualTo("hiya");
  }

  @Test
  public void tryTakeNow_withClosedEmptyChute_shouldGetNothing() throws Exception {
    Chute<String> c = new BufferingChute<>(1, currentNanoSource);
    c.close();
    Optional<String> taken = c.tryTakeNow();
    assertThat(taken.isPresent()).isFalse();
  }

  @Test
  public void tryTakeNow_with100Elements_shouldReturnEachElementInOrder() throws Exception {
    int capacity = 100;
    Chute<Integer> c = new BufferingChute<>(capacity, currentNanoSource);
    for (int i = 0; i < capacity; i++) {
      c.put(i);
    }
    Optional<Integer> taken = c.tryTakeNow();
    assertThat(taken.get()).isEqualTo(0);

    c.close();
    assertThat(c.isClosed()).isTrue();
    for (int i = 1; i < capacity; i++) {
      taken = c.tryTakeNow();
      assertThat(taken.get()).isEqualTo(i);
    }
    assertThat(c.isClosedAndEmpty()).isTrue();

    assertThat(c.tryTakeNow().isPresent()).isFalse();
    assertThat(c.tryTakeNow().isPresent()).isFalse();
    assertThat(c.tryTakeNow().isPresent()).isFalse();
  }


  //
  // Tests for the take method
  //

  @Test
  public void take_onEmptyChute_shouldBlockUntilElementArrives() throws Exception {
    ExecutorService es = Executors.newFixedThreadPool(1);
    Chute<String> c = new BufferingChute<>(1, currentNanoSource);

    // This Callable should block rather than returning Optional.empty.
    Future<Optional<String>> futureResult = es.submit(() -> c.take());

    c.put("thanks for waiting");
    Optional<String> taken = futureResult.get(10, MILLISECONDS);
    assertThat(taken.get()).isEqualTo("thanks for waiting");
  }

  @Test
  public void take_onChuteWithElement_shouldReturnImmediately() throws Exception {
    ExecutorService es = Executors.newFixedThreadPool(1);
    Chute<String> c = new BufferingChute<>(1, currentNanoSource);
    c.put("hello");

    Future<Optional<String>> futureResult = es.submit(() -> c.take());

    futureResult.get(10, MILLISECONDS);
  }

  @Test
  public void take_onClosedChute_shouldReturnImmediately() throws Exception {
    Chute<String> c = new BufferingChute<>(1, currentNanoSource);
    c.close();

    ExecutorService es = Executors.newFixedThreadPool(1);
    Future<Optional<String>> futureResult = es.submit(() -> c.take());

    futureResult.get(10, MILLISECONDS);
  }


  //
  // Tests for tryTake.
  //

  @Test
  public void tryTake_onEmptyChute_shouldReturnNothing() throws Exception {
    Chute<String> c = new BufferingChute<>(1, currentNanoSource);
    Optional<String> taken = c.tryTake(10, MILLISECONDS);
    assertThat(taken.isPresent()).isFalse();
  }

  @Test
  public void tryTake_onChuteContainingElement_shouldImmediatelyReturnElement() throws Exception {
    Chute<String> c = new BufferingChute<>(1, currentNanoSource);
    c.put("foo");
    Optional<String> taken = c.tryTake(10, MILLISECONDS);
    assertThat(taken.get()).isEqualTo("foo");
  }

  @Test
  public void tryTake_onClosedChute_shouldImmediatelyReturnNothing() throws Exception {
    Chute<String> c = new BufferingChute<>(1, currentNanoSource);
    c.close();
    Optional<String> taken = c.tryTake(10, MILLISECONDS);
    assertThat(taken.isPresent()).isFalse();
  }
}
