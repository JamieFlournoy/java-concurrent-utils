package com.pervasivecode.utils.concurrent.chute;

import static com.google.common.truth.Truth.assertThat;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import com.google.common.collect.ImmutableList;
import com.pervasivecode.utils.concurrent.chute.BufferingChute;
import com.pervasivecode.utils.concurrent.chute.Transformers;
import com.pervasivecode.utils.time.api.TimeSource;
import com.pervasivecode.utils.time.testing.FakeNanoSource;

public class TransformersTest {
  private static final ImmutableList<String> TEST_STRINGS = ImmutableList.of("one", "two", "three",
      "four", "five", "six", "seven", "eight", "nine", "ten");

  private static final Duration MAX_TIME_BETWEEN_BATCHES =  Duration.ofMillis(500L);

  private FakeNanoSource nanosSource;
  private BufferingChute<Object> input;
  private BufferingChute<ImmutableList<Object>> output;
  private TimeSource timeSource;
  private Duration maxTimeBetweenBatches;

  @Rule
  public Timeout globalTimeout = Timeout.seconds(MAX_TIME_BETWEEN_BATCHES.toSeconds() * 10);

  @Before
  public void setup() {
    this.nanosSource = new FakeNanoSource();
    this.input = new BufferingChute<>(100, nanosSource);
    this.output = new BufferingChute<>(10, nanosSource);
    this.timeSource = () -> Instant.now();
    this.maxTimeBetweenBatches = MAX_TIME_BETWEEN_BATCHES;
  }

  @Test(expected = NullPointerException.class)
  public void batchingTransformer_withNullInput_shouldThrow() {
    input = null;
    Transformers.batchingTransformer(input, output, 5);
  }

  @Test(expected = NullPointerException.class)
  public void batchingTransformer_withNullOutput_shouldThrow() {
    output = null;
    Transformers.batchingTransformer(input, output, 5);
  }

  @Test(expected = IllegalArgumentException.class)
  public void batchingTransformer_withBatchSizeOfZero_shouldThrow() {
    Transformers.batchingTransformer(input, output, 0);
  }

  @Test
  public void batchingTransformer_withInitiallyClosedInput_shouldImmediatelyCloseOutput()
      throws Exception {
    input.close();

    assertThat(output.isClosedAndEmpty()).isFalse();

    Callable<Void> transformer = Transformers.batchingTransformer(input, output, 5);
    transformer.call();
    assertThat(input.isClosedAndEmpty()).isTrue();
    Optional<ImmutableList<Object>> o = output.take();
    assertThat(o.isPresent()).isFalse();
    assertThat(output.isClosedAndEmpty()).isTrue();
  }

  private void testBatchingTransformer(int batchSize, int numElements) throws Exception {
    for (int i = 0; i < numElements; i++) {
      input.put(TEST_STRINGS.get(i));
    }
    input.close();

    Callable<Void> transformer = Transformers.batchingTransformer(input, output, batchSize);
    transformer.call();

    Optional<ImmutableList<Object>> possibleBatch;
    int remainingElements = numElements;
    int iterations = 0;
    while (remainingElements > 0 && iterations < numElements) {
      int startIndex = iterations * batchSize;
      int endIndex = Math.min(startIndex + batchSize, numElements);
      List<String> expectedBatch = TEST_STRINGS.subList(startIndex, endIndex);

      possibleBatch = output.tryTakeNow();
      assertThat(possibleBatch.isPresent()).isTrue();
      assertThat(possibleBatch.get()).containsExactlyElementsIn(expectedBatch).inOrder();

      iterations++;
      remainingElements -= batchSize;
    }

    assertThat(output.isClosedAndEmpty()).isTrue();
  }

  @Test
  public void batchingTransformer_withThreeElementsAndBatchSizeOfOne_shouldWork() throws Exception {
    testBatchingTransformer(1, 3);
  }

  @Test
  public void batchingTransformer_withThreeElementsAndBatchSizeOfThree_shouldWork()
      throws Exception {
    testBatchingTransformer(3, 3);
  }

  @Test
  public void batchingTransformer_withFourElementsAndBatchSizeOfThree_shouldWork()
      throws Exception {
    testBatchingTransformer(3, 4);
  }

  @Test
  public void batchingTransformer_withFiveElementsAndBatchSizeOfThree_shouldWork()
      throws Exception {
    testBatchingTransformer(3, 5);
  }

  @Test
  public void batchingTransformer_withSixElementsAndBatchSizeOfThree_shouldWork() throws Exception {
    testBatchingTransformer(3, 6);
  }

  // -------------------------------------------------------------------------
  // Batching periodic transformer
  // -------------------------------------------------------------------------

  @Test(expected = NullPointerException.class)
  public void batchingPeriodicTransformer_withNullInput_shouldThrow() {
    input = null;
    Transformers.batchingPeriodicTransformer(input, output, 5, timeSource, maxTimeBetweenBatches);
  }

  @Test(expected = NullPointerException.class)
  public void batchingPeriodicTransformer_withNullOutput_shouldThrow() {
    output = null;
    Transformers.batchingPeriodicTransformer(input, output, 5, timeSource, maxTimeBetweenBatches);
  }

  @Test(expected = IllegalArgumentException.class)
  public void batchingPeriodicTransformer_withBatchSizeOfZero_shouldThrow() {
    Transformers.batchingPeriodicTransformer(input, output, 0, timeSource, maxTimeBetweenBatches);
  }

  @Test(expected = NullPointerException.class)
  public void batchingPeriodicTransformer_withNullTimeSource_shouldThrow() {
    timeSource = null;
    Transformers.batchingPeriodicTransformer(input, output, 5, timeSource, maxTimeBetweenBatches);
  }

  @Test(expected = NullPointerException.class)
  public void batchingPeriodicTransformer_withNullDuration_shouldThrow() {
    maxTimeBetweenBatches = null;
    Transformers.batchingPeriodicTransformer(input, output, 5, timeSource, maxTimeBetweenBatches);
  }

  @Test(expected = IllegalArgumentException.class)
  public void batchingPeriodicTransformer_withDurationOfZero_shouldThrow() {
    maxTimeBetweenBatches = Duration.ofMillis(0);
    Transformers.batchingPeriodicTransformer(input, output, 5, timeSource, maxTimeBetweenBatches);
  }

  @Test
  public void batchingPeriodicTransformer_withInitiallyClosedInput_shouldImmediatelyCloseOutput()
      throws Exception {
    input.close();

    assertThat(!output.isClosedAndEmpty()).isTrue();

    Callable<Void> transformer = Transformers.batchingPeriodicTransformer(input, output, 5,
        timeSource, maxTimeBetweenBatches);
    transformer.call();
    assertThat(input.isClosedAndEmpty()).isTrue();
    assertThat(output.isClosedAndEmpty()).isTrue();
  }

  @Test
  public void batchingPeriodicTransformer_withBatchSizeOfOne_shouldWork() throws Exception {
    input.put("one");
    input.put("two");
    input.put("three");
    input.close();

    Callable<Void> transformer = Transformers.batchingPeriodicTransformer(input, output, 1,
        timeSource, maxTimeBetweenBatches);
    transformer.call();
    assertThat(input.isClosedAndEmpty()).isTrue();

    Optional<ImmutableList<Object>> o = output.tryTakeNow();
    assertThat(o.isPresent()).isTrue();
    assertThat(o.get()).containsExactly("one");

    o = output.tryTakeNow();
    assertThat(o.isPresent()).isTrue();
    assertThat(o.get()).containsExactly("two");

    o = output.tryTakeNow();
    assertThat(o.isPresent()).isTrue();
    assertThat(o.get()).containsExactly("three");

    assertThat(output.isClosedAndEmpty()).isTrue();
  }

  private void testBatchingPeriodicTransformerWithFastInput(int batchSize, int numElements)
      throws Exception {
    for (int i = 0; i < numElements; i++) {
      input.put(TEST_STRINGS.get(i));
    }
    input.close();

    Callable<Void> transformer = Transformers.batchingPeriodicTransformer(input, output, batchSize,
        timeSource, maxTimeBetweenBatches);
    transformer.call();
    assertThat(input.isClosedAndEmpty()).isTrue();

    transformer.call();

    Optional<ImmutableList<Object>> possibleBatch;
    int remainingElements = numElements;
    int iterations = 0;
    while (remainingElements > 0 && iterations < numElements) {
      int startIndex = iterations * batchSize;
      int endIndex = Math.min(startIndex + batchSize, numElements);
      List<String> expectedBatch = TEST_STRINGS.subList(startIndex, endIndex);

      possibleBatch = output.tryTakeNow();
      assertThat(possibleBatch.isPresent()).isTrue();
      assertThat(possibleBatch.get()).containsExactlyElementsIn(expectedBatch).inOrder();

      iterations++;
      remainingElements -= batchSize;
    }

    assertThat(output.isClosedAndEmpty()).isTrue();
  }

  @Test
  public void batchingPeriodicTransformer_withThreeElementsAndBatchSizeOfOneAndRapidInput_shouldWork()
      throws Exception {
    testBatchingPeriodicTransformerWithFastInput(1, 3);
  }

  @Test
  public void batchingPeriodicTransformer_withThreeElementsAndBatchSizeOfThreeAndRapidInput_shouldWork()
      throws Exception {
    testBatchingPeriodicTransformerWithFastInput(3, 3);
  }

  @Test
  public void batchingPeriodicTransformer_withFourElementsAndBatchSizeOfThreeAndRapidInput_shouldWork()
      throws Exception {
    testBatchingPeriodicTransformerWithFastInput(3, 4);
  }

  @Test
  public void batchingPeriodicTransformer_withFiveElementsAndBatchSizeOfThreeAndRapidInput_shouldWork()
      throws Exception {
    testBatchingPeriodicTransformerWithFastInput(3, 5);
  }

  @Test
  public void batchingPeriodicTransformer_withSixElementsAndBatchSizeOfThreeAndRapidInput_shouldWork()
      throws Exception {
    testBatchingPeriodicTransformerWithFastInput(3, 6);
  }

  @Test
  public void batchingPeriodicTransformer_withTenElementsAndBatchSizeOfFiveAndRapidInput_shouldWork()
      throws Exception {
    testBatchingPeriodicTransformerWithFastInput(5, 10);
  }

  // This test wraps code that needs to run in real-time (ReentrantLock is ultimately responsible
  // for deciding if the timeout has occurred, and it's not really worth the effort to mock that).
  @Test
  public void batchingPeriodicTransformer_withBatchSizeOfThreeAndSlowInput_shouldWork()
      throws Exception {
    Callable<Void> transformer = Transformers.batchingPeriodicTransformer(input, output, 3,
        timeSource, maxTimeBetweenBatches);

    ExecutorService es = Executors.newFixedThreadPool(1);
    Future<Void> transformResult = es.submit(transformer);

    input.put("one");
    input.put("two");

    // Block until the first batch is available.
    Optional<ImmutableList<Object>> o = output.take();
    assertThat(o.isPresent()).isTrue();
    assertThat(o.get()).containsExactly("one", "two");

    input.put("three");
    o = output.take();
    assertThat(o.isPresent()).isTrue();
    assertThat(o.get()).containsExactly("three");

    input.put("four");
    input.close();
    o = output.take();
    assertThat(o.isPresent()).isTrue();
    assertThat(o.get()).containsExactly("four");

    transformResult.get(); // let the transformer finish transformin'

    assertThat(input.isClosedAndEmpty()).isTrue();
    assertThat(output.isClosedAndEmpty()).isTrue();

  }
}
