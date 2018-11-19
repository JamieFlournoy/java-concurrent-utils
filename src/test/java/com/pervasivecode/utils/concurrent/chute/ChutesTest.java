package com.pervasivecode.utils.concurrent.chute;

import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeoutException;
import org.junit.Before;
import org.junit.Test;
import com.google.common.collect.ImmutableList;
import com.google.common.truth.Truth;

public class ChutesTest {
  private BufferingChute<String> testChute;

  @Before
  public void setup() {
    testChute = new BufferingChute<>(10, () -> System.nanoTime());
  }

  private static ImmutableList<String> putAll(ChuteEntrance<String> entrance, String... s)
      throws InterruptedException {
    for (int i = 0; i < s.length; i++) {
      entrance.put(s[i]);
    }
    return ImmutableList.copyOf(s);
  }

  // --------------------------------------------------------------------------
  //
  // Tests for iterable
  //
  // --------------------------------------------------------------------------

  @Test(expected = NullPointerException.class)
  public void iterable_withNullSource_shouldThrow() {
    Chutes.asIterable(null);
  }

  @Test
  public void iterable_withValidArgs_shouldProduceWorkingIterator() throws Exception {
    ImmutableList<String> elements = putAll(testChute, "a", "b", "c");
    Iterable<String> iterable = Chutes.asIterable(testChute);
    testChute.close();

    ArrayList<String> iterated = new ArrayList<String>();
    for (String s : iterable) {
      iterated.add(s);
    }

    assertThat(iterated).containsExactlyElementsIn(elements);
  }

  @Test
  public void iterable_whenInterrupted_shouldStopIterating() throws Exception {
    Iterable<String> iterable = Chutes.asIterable(testChute);
    Iterator<String> iterator = iterable.iterator();

    ExecutorService es = Executors.newSingleThreadExecutor();

    // next() should block, since there are no elements in the testChute and it is not closed.
    Callable<String> takeFirstFromIterator = () -> iterator.next();

    Future<String> futureFirstResult = es.submit(takeFirstFromIterator);

    try {
      futureFirstResult.get(10, MILLISECONDS);
      Truth.assert_().fail("Expected TimeoutException.");
    } catch (@SuppressWarnings("unused") TimeoutException te) {
      // expected.
    }

    futureFirstResult.cancel(true);
    assertThat(futureFirstResult.isDone()).isTrue();

    try {
      futureFirstResult.get();
      Truth.assert_().fail("Expected futureFirstResult to throw CancellationException.");
    } catch (@SuppressWarnings("unused") CancellationException ce) {
      // Expected; do nothing.
    }

    assertThat(iterator.hasNext()).isFalse();

    es.shutdownNow();
  }

  // --------------------------------------------------------------------------
  //
  // Tests for transformingEntrance
  //
  // --------------------------------------------------------------------------

  @Test(expected = NullPointerException.class)
  public void transformingEntrance_withNullReceiver_shouldThrow() {
    Chutes.transformingEntrance(null, (s) -> s + "!");
  }

  @Test(expected = NullPointerException.class)
  public void transformingEntrance_withNullTransformer_shouldThrow() {
    Chutes.transformingEntrance(testChute, null);
  }

  @Test
  public void transformingEntrance_whenClosed_shouldCloseReceiver() throws Exception {
    ChuteEntrance<String> entrance = Chutes.transformingEntrance(testChute, (s) -> s.toUpperCase());
    assertThat(testChute.isClosed()).isFalse();
    entrance.close();
    assertThat(testChute.isClosed()).isTrue();
  }

  @Test
  public void transformingEntrance_withValidArgs_shouldProduceWorkingChuteEntrance()
      throws Exception {
    ChuteEntrance<String> entrance =
        Chutes.transformingEntrance(testChute, (s) -> s.toUpperCase() + "!");
    assertThat(entrance.isClosed()).isFalse();

    putAll(entrance, "a", "b", "c");
    assertThat(testChute.isClosed()).isFalse();

    testChute.close();
    assertThat(testChute.isClosed()).isTrue();
    assertThat(testChute.isClosedAndEmpty()).isFalse();

    Optional<String> taken = testChute.take();
    assertThat(taken.isPresent()).isTrue();
    assertThat(taken.get()).isEqualTo("A!");
    assertThat(testChute.isClosedAndEmpty()).isFalse();

    taken = testChute.take();
    assertThat(taken.isPresent()).isTrue();
    assertThat(taken.get()).isEqualTo("B!");
    assertThat(testChute.isClosedAndEmpty()).isFalse();

    taken = testChute.take();
    assertThat(taken.isPresent()).isTrue();
    assertThat(taken.get()).isEqualTo("C!");
    assertThat(testChute.isClosedAndEmpty()).isTrue();

    taken = testChute.tryTakeNow();
    assertThat(taken.isPresent()).isFalse();

    taken = testChute.tryTake(1, MILLISECONDS);
    assertThat(taken.isPresent()).isFalse();

    ExecutorService executor = Executors.newSingleThreadExecutor();
    Future<Optional<String>> futureResultOfTake = executor.submit(() -> testChute.take());

    taken = futureResultOfTake.get(10, MILLISECONDS);
    assertThat(taken.isPresent()).isFalse();

    executor.shutdownNow();
    executor.awaitTermination(1, SECONDS);
  }

  // --------------------------------------------------------------------------
  //
  // Tests for TransformingExit
  //
  // --------------------------------------------------------------------------

  @Test(expected = NullPointerException.class)
  public void transformingExit_withNullReceiver_shouldThrow() {
    Chutes.transformingExit(null, (s) -> s + "?");
  }

  @Test(expected = NullPointerException.class)
  public void transformingExit_withNullTransformer_shouldThrow() {
    Chutes.transformingExit(testChute, null);
  }

  @Test
  public void transformingExit_withValidArgs_shouldProduceWorkingChuteExit() throws Exception {
    ChuteExit<Integer> exit = Chutes.transformingExit(testChute, Integer::valueOf);

    assertThat(exit.isClosedAndEmpty()).isFalse();
    putAll(testChute, "12345", "23456", "34567");
    assertThat(exit.isClosedAndEmpty()).isFalse();

    Optional<Integer> taken = exit.tryTakeNow();
    assertThat(taken.isPresent()).isTrue();
    assertThat(taken.get()).isEqualTo(12345);
    assertThat(exit.isClosedAndEmpty()).isFalse();

    taken = exit.tryTake(10, MILLISECONDS);
    assertThat(taken.isPresent()).isTrue();
    assertThat(taken.get()).isEqualTo(23456);
    assertThat(exit.isClosedAndEmpty()).isFalse();

    taken = exit.take();
    assertThat(taken.isPresent()).isTrue();
    assertThat(taken.get()).isEqualTo(34567);
    assertThat(exit.isClosedAndEmpty()).isFalse();

    taken = exit.tryTakeNow();
    assertThat(taken.isPresent()).isFalse();

    testChute.close();
    assertThat(exit.isClosedAndEmpty()).isTrue();
  }
}
