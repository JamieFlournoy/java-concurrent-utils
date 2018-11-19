package com.pervasivecode.utils.concurrent.chute;

import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import com.google.common.truth.Truth;
import com.pervasivecode.utils.time.testing.FakeNanoSource;
import repeat.Repeat;
import repeat.RepeatRule;

public class BufferingChuteTest {
  // Use NUM_REPEATS=500 for torture testing.
  private static final int NUM_REPEATS = 50;

  @Rule
  public RepeatRule rule = new RepeatRule();

  private FakeNanoSource currentNanoSource;

  @Before
  public void setup() {
    this.currentNanoSource = new FakeNanoSource();
  }

  // --------------------------------------------------------------------------
  //
  // Constructor tests
  //
  // --------------------------------------------------------------------------

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

  // --------------------------------------------------------------------------
  //
  // Tests for close, isClosed, and isClosedAndEmpty
  //
  // --------------------------------------------------------------------------

  @Test
  @Repeat(times = NUM_REPEATS)
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
    es.shutdownNow();
  }

  // --------------------------------------------------------------------------
  //
  // Tests for put
  //
  // --------------------------------------------------------------------------

  @Test
  @Repeat(times = NUM_REPEATS)
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
    es.shutdownNow();
  }

  @Test
  @Repeat(times = NUM_REPEATS)
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
    es.shutdownNow();
  }

  @Test
  @Repeat(times = NUM_REPEATS)
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
    es.shutdownNow();
  }

  // --------------------------------------------------------------------------
  //
  // Tests for tryTakeNow
  //
  // --------------------------------------------------------------------------

  @Test
  @Repeat(times = NUM_REPEATS)
  public void tryTakeNow_withEmptyChute_shouldGetNothing() throws Exception {
    Chute<String> c = new BufferingChute<>(1, currentNanoSource);
    Optional<String> taken = c.tryTakeNow();
    assertThat(taken.isPresent()).isFalse();
  }

  @Test
  @Repeat(times = NUM_REPEATS)
  public void tryTakeNow_withElementInChute_shouldGetIt() throws Exception {
    Chute<String> c = new BufferingChute<>(1, currentNanoSource);
    c.put("hiya");
    Optional<String> taken = c.tryTakeNow();
    assertThat(taken.get()).isEqualTo("hiya");
  }

  @Test
  @Repeat(times = NUM_REPEATS)
  public void tryTakeNow_withClosedEmptyChute_shouldGetNothing() throws Exception {
    Chute<String> c = new BufferingChute<>(1, currentNanoSource);
    c.close();
    Optional<String> taken = c.tryTakeNow();
    assertThat(taken.isPresent()).isFalse();
  }

  @Test
  @Repeat(times = NUM_REPEATS)
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

  @Test
  @Repeat(times = NUM_REPEATS)
  public void tryTakeNow_withPriorTryTakeStillBlocked_shouldReturnEmpty() throws Exception {
    ExecutorService es = Executors.newFixedThreadPool(1);
    Chute<String> c = new BufferingChute<>(1, currentNanoSource);

    CountDownLatch aboutToBlock = new CountDownLatch(1);
    Future<Optional<String>> futureResult = es.submit(() -> {
      aboutToBlock.countDown();
      return c.take();
    });

    aboutToBlock.await(1, SECONDS);
    int numTryTakes = 100;
    for (int i = 0; i < numTryTakes; i++) {
      Optional<String> result = c.tryTakeNow();
      assertThat(result.isPresent()).isFalse();
    }

    futureResult.cancel(true);
    es.shutdownNow();
  }

  // --------------------------------------------------------------------------
  //
  // Tests for take
  //
  // --------------------------------------------------------------------------

  @Test
  @Repeat(times = NUM_REPEATS)
  public void take_onEmptyChute_shouldBlockUntilElementArrives() throws Exception {
    ExecutorService es = Executors.newFixedThreadPool(1);
    Chute<String> c = new BufferingChute<>(1, currentNanoSource);

    // This Callable should block rather than returning Optional.empty.
    Future<Optional<String>> futureResult = es.submit(() -> c.take());

    c.put("thanks for waiting");
    Optional<String> taken = futureResult.get(10, MILLISECONDS);
    assertThat(taken.get()).isEqualTo("thanks for waiting");
    es.shutdownNow();
  }

  @Test
  @Repeat(times = NUM_REPEATS)
  public void take_onChuteWithElement_shouldReturnImmediately() throws Exception {
    ExecutorService es = Executors.newFixedThreadPool(1);
    Chute<String> c = new BufferingChute<>(1, currentNanoSource);
    c.put("hello");

    Future<Optional<String>> futureResult = es.submit(() -> c.take());

    futureResult.get(10, MILLISECONDS);
    es.shutdownNow();
  }

  @Test
  @Repeat(times = NUM_REPEATS)
  public void take_onClosedChute_shouldReturnImmediately() throws Exception {
    Chute<String> c = new BufferingChute<>(1, currentNanoSource);
    c.close();

    ExecutorService es = Executors.newFixedThreadPool(1);
    Future<Optional<String>> futureResult = es.submit(() -> c.take());

    futureResult.get(10, MILLISECONDS);
    es.shutdownNow();
  }

  @Test
  @Repeat(times = NUM_REPEATS)
  public void take_onEmptyChute_thatLaterCloses_shouldReturnEmpty() throws Exception {
    Chute<String> c = new BufferingChute<>(1, currentNanoSource);

    ExecutorService es = Executors.newFixedThreadPool(1);

    CountDownLatch aboutToTake = new CountDownLatch(1);
    Callable<Optional<String>> consumer = () -> {
      aboutToTake.countDown();
      return c.take();
    };
    Future<Optional<String>> futureResult = es.submit(consumer);

    aboutToTake.await(100, MILLISECONDS);
    c.tryTake(10, MILLISECONDS);
    c.close();

    Optional<String> result = futureResult.get(100, MILLISECONDS);
    assertThat(result.isPresent()).isFalse();

    es.shutdownNow();
  }

  // --------------------------------------------------------------------------
  //
  // Tests for tryTake
  //
  // --------------------------------------------------------------------------

  @Test
  @Repeat(times = NUM_REPEATS)
  public void tryTake_onEmptyChute_shouldReturnNothing() throws Exception {
    Chute<String> c = new BufferingChute<>(1, currentNanoSource);
    Optional<String> taken = c.tryTake(10, MILLISECONDS);
    assertThat(taken.isPresent()).isFalse();
  }

  @Test
  @Repeat(times = NUM_REPEATS)
  public void tryTake_onEmptyChute_withCompetingTryTake_shouldReturnNothing() throws Exception {
    Chute<String> c = new BufferingChute<>(1, currentNanoSource);

    CountDownLatch aboutToTry = new CountDownLatch(1);

    ExecutorService es = Executors.newFixedThreadPool(1);
    Callable<Optional<String>> patientElementConsumer = () -> {
      aboutToTry.countDown();
      return c.tryTake(10, SECONDS);
    };
    Future<Optional<String>> futureResult = es.submit(patientElementConsumer);

    aboutToTry.await(100, MILLISECONDS);

    // These should all fail, and patientElementConsumer will probably get control of the takeLock
    // for a while. (This section is intended to exercise the if(!gotTakeLockInTime){...} code path
    // in BufferingChute.tryTake.)
    Optional<String> taken = c.tryTake(10, MILLISECONDS);
    assertThat(taken.isPresent()).isFalse();

    taken = c.tryTake(10, MILLISECONDS);
    assertThat(taken.isPresent()).isFalse();

    taken = c.tryTake(10, MILLISECONDS);
    assertThat(taken.isPresent()).isFalse();

    c.close();
    taken = futureResult.get(1, SECONDS);
    assertThat(taken.isPresent()).isFalse();
  }

  @Test
  @Repeat(times = NUM_REPEATS)
  public void tryTake_onChuteContainingElement_shouldImmediatelyReturnElement() throws Exception {
    Chute<String> c = new BufferingChute<>(1, currentNanoSource);
    c.put("foo");
    // Immediately really means immediately: 0ms should be sufficient to grab an element that's
    // already waiting.
    Optional<String> taken = c.tryTake(0, MILLISECONDS);
    assertThat(taken.get()).isEqualTo("foo");
  }

  @Test
  @Repeat(times = NUM_REPEATS)
  public void tryTake_onClosedChute_shouldImmediatelyReturnNothing() throws Exception {
    Chute<String> c = new BufferingChute<>(1, currentNanoSource);
    c.close();
    Optional<String> taken = c.tryTake(10, MILLISECONDS);
    assertThat(taken.isPresent()).isFalse();
  }

  @Test
  @Repeat(times = NUM_REPEATS)
  public void tryTake_onEmptyChute_withOtherTakeGettingLastItemBeforeClose_shouldTimeOutAndReturn()
      throws Exception {
    ExecutorService es = Executors.newFixedThreadPool(2);
    Chute<String> c = new BufferingChute<>(1, currentNanoSource);

    // BufferingChute uses a non-fair locking mechanism, so we can't depend on sequence of calls to
    // take() or tryTake() resulting in an ordering of elements obtained/not obtained. Instead, we
    // will just let a couple of identical threads compete for the result, and make sure that one
    // got it while the other got nothing, and that both returned promptly.

    CountDownLatch readyToTry = new CountDownLatch(2);
    CountDownLatch canTry = new CountDownLatch(1);

    Callable<Optional<String>> consumer = () -> {
      readyToTry.countDown();
      canTry.await();
      return c.tryTake(1, SECONDS);
    };
    Future<Optional<String>> futureResult1 = es.submit(consumer);
    Future<Optional<String>> futureResult2 = es.submit(consumer);

    // Wait until consumer1 and consumer2 are started up, then turn them loose.
    readyToTry.await(1, SECONDS);
    canTry.countDown();
    Thread.sleep(5); // Give them a chance to block.
    c.put("Last element");
    c.close();

    // At this point we expect consumer1 and consumer2 to finish quickly, since whichever one didn't
    // get the last element shouldn't have to wait around on a closed chute (which by definition
    // will never emit any more elements).
    Optional<String> result1 = futureResult1.get(10, MILLISECONDS);
    Optional<String> result2 = futureResult2.get(10, MILLISECONDS);

    if (result1.isPresent()) {
      assertThat(result2.isPresent()).isFalse();
    } else {
      assertThat(result2.isPresent()).isTrue();
    }
    es.shutdownNow();
  }
}
