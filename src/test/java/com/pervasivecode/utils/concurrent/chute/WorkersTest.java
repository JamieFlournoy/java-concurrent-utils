package com.pervasivecode.utils.concurrent.chute;

import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import com.google.common.collect.ImmutableList;
import com.pervasivecode.utils.time.api.CurrentNanosSource;
import com.pervasivecode.utils.time.api.TimeSource;
import repeat.Repeat;
import repeat.RepeatRule;

public class WorkersTest {
  private static final ImmutableList<String> ONE_THROUGH_TEN = ImmutableList.of("one", "two",
      "three", "four", "five", "six", "seven", "eight", "nine", "ten");

  // Use NUM_REPEATS=500 for torture testing.
  private static final int NUM_REPEATS = 5;

  private static final Duration MAX_TIME_BETWEEN_BATCHES = Duration.ofMillis(50L);

  @Rule
  public RepeatRule rule = new RepeatRule();

  private CurrentNanosSource nanosSource;
  private BufferingChute<Object> objectInput;
  private BufferingChute<ImmutableList<Object>> objectOutput;
  private BufferingChute<String> stringInput;
  private BufferingChute<String> stringOutput;
  private TimeSource timeSource;
  private Duration maxTimeBetweenBatches;

  @Before
  public void setup() {
    this.nanosSource = () -> System.nanoTime();

    this.objectInput = new BufferingChute<>(100, nanosSource);
    this.objectOutput = new BufferingChute<>(10, nanosSource);

    this.stringInput = new BufferingChute<>(10, nanosSource);
    this.stringOutput = new BufferingChute<>(10, nanosSource);

    this.timeSource = () -> Instant.now();
    this.maxTimeBetweenBatches = MAX_TIME_BETWEEN_BATCHES;
  }

  private static void putAll(ChuteEntrance<String> entrance, String... s)
      throws InterruptedException {
    for (int i = 0; i < s.length; i++) {
      entrance.put(s[i]);
    }
  }


  // --------------------------------------------------------------------------
  //
  // Tests for transformingWorker
  //
  // --------------------------------------------------------------------------

  @Test(expected = NullPointerException.class)
  public void transformingWorker_withNullInput_shouldThrow() {
    Workers.transformingWorker((ChuteExit<String>) null, stringOutput, String::trim, false);
  }

  @Test(expected = NullPointerException.class)
  public void transformingWorker_withNullOutput_shouldThrow() {
    Workers.transformingWorker(stringInput, (ChuteEntrance<String>) null, String::trim, false);
  }

  @Test(expected = NullPointerException.class)
  public void transformingWorker_withNullConverter_shouldThrow() {
    Workers.transformingWorker(stringInput, stringOutput, null, false);
  }

  @Test
  public void transformingWorker_withValidArgsAndCloseWhenDone_shouldProduceWorkingTransformer()
      throws Exception {
    BufferingChute<Integer> converted = new BufferingChute<>(5, () -> System.nanoTime());
    Runnable worker = Workers.transformingWorker(stringInput, converted, Integer::valueOf, true);

    ExecutorService executor = Executors.newSingleThreadExecutor();
    Future<?> futureTransformResult = executor.submit(worker);

    putAll(stringInput, "345678", "456789", "567890");

    Optional<Integer> taken = converted.tryTake(10, MILLISECONDS);
    assertThat(taken.isPresent()).isTrue();
    assertThat(taken.get()).isEqualTo(345678);
    assertThat(converted.isClosedAndEmpty()).isFalse();

    taken = converted.tryTake(10, MILLISECONDS);
    assertThat(taken.isPresent()).isTrue();
    assertThat(taken.get()).isEqualTo(456789);
    assertThat(converted.isClosedAndEmpty()).isFalse();

    taken = converted.tryTake(10, MILLISECONDS);
    assertThat(taken.isPresent()).isTrue();
    assertThat(taken.get()).isEqualTo(567890);
    assertThat(converted.isClosedAndEmpty()).isFalse();

    stringInput.close();
    futureTransformResult.get(10, MILLISECONDS);
    assertThat(converted.isClosedAndEmpty()).isTrue();

    executor.shutdownNow();
    executor.awaitTermination(1, SECONDS);
  }

  @Test
  public void transformingWorker_withValidArgsAndNotCloseWhenDone_shouldProduceWorkingTransformer()
      throws Exception {
    BufferingChute<Integer> converted = new BufferingChute<>(5, () -> System.nanoTime());
    Runnable worker = Workers.transformingWorker(stringInput, converted, Integer::valueOf, false);

    ExecutorService executor = Executors.newSingleThreadExecutor();
    Future<?> futureTransformResult = executor.submit(worker);

    putAll(stringInput, "4567890", "5678901", "6789012");

    Optional<Integer> taken = converted.tryTake(10, MILLISECONDS);
    assertThat(taken.isPresent()).isTrue();
    assertThat(taken.get()).isEqualTo(4567890);
    assertThat(converted.isClosedAndEmpty()).isFalse();

    taken = converted.tryTake(10, MILLISECONDS);
    assertThat(taken.isPresent()).isTrue();
    assertThat(taken.get()).isEqualTo(5678901);
    assertThat(converted.isClosedAndEmpty()).isFalse();

    taken = converted.tryTake(10, MILLISECONDS);
    assertThat(taken.isPresent()).isTrue();
    assertThat(taken.get()).isEqualTo(6789012);
    assertThat(converted.isClosedAndEmpty()).isFalse();

    stringInput.close();
    // Closing the input chute should not cause the worker to close the output chute.
    futureTransformResult.get(10, MILLISECONDS);
    assertThat(converted.isClosedAndEmpty()).isFalse();

    // There should not be any results hanging around in the converted chute, so closing should just
    // result in the worker closing the converted chute and then returning.
    converted.close();
    futureTransformResult.get(100, MILLISECONDS);
    assertThat(converted.isClosedAndEmpty()).isTrue();

    executor.shutdownNow();
    executor.awaitTermination(1, SECONDS);
  }

  @Test
  @Repeat(times = NUM_REPEATS)
  public void transformingWorker_withCloseOutputWhenDone_shouldNotCloseOutputWhenInterrupted()
      throws Exception {
    stringOutput = new BufferingChute<>(2, () -> System.nanoTime());
    Runnable worker =
        Workers.transformingWorker(stringInput, stringOutput, (s) -> s.toUpperCase(), true);

    ExecutorService es = Executors.newFixedThreadPool(1);
    Future<?> transformResult = es.submit(worker);

    // Grab an element from the output Chute so that we can be sure the worker is up & running.
    stringInput.put("whatever");
    Optional<String> result = stringOutput.tryTake(10, MILLISECONDS);
    assertThat(result.isPresent()).isTrue();

    // Make sure the worker is blocked waiting to put an element in the output chute.
    stringInput.put("thing 1");
    stringInput.put("thing 2");
    stringInput.put("thing 3"); // should cause the worker to block

    // Now we interrupt the worker, and give it a chance to terminate.
    transformResult.cancel(true);

    // Close the input Chute.
    stringInput.close();

    // Maybe it'll keep running long enough to process these; maybe not.
    result = stringOutput.tryTake(10, MILLISECONDS);
    result = stringOutput.tryTake(10, MILLISECONDS);
    result = stringOutput.tryTake(10, MILLISECONDS);

    // The output Chute should *not* be closed (even though we passed closeOutputWhenDone=true),
    // since it was interrupted.
    assertThat(objectOutput.isClosed()).isFalse();
  }


  // --------------------------------------------------------------------------
  //
  // Tests for batchingWorker
  //
  // --------------------------------------------------------------------------

  @Test(expected = NullPointerException.class)
  @Repeat(times = NUM_REPEATS)
  public void batchingWorker_withNullInput_shouldThrow() {
    Workers.batchingWorker(null, objectOutput, 5, true);
  }

  @Test(expected = NullPointerException.class)
  @Repeat(times = NUM_REPEATS)
  public void batchingWorker_withNullOutput_shouldThrow() {
    Workers.batchingWorker(objectInput, null, 5, true);
  }

  @Test(expected = IllegalArgumentException.class)
  @Repeat(times = NUM_REPEATS)
  public void batchingWorker_withBatchSizeOfZero_shouldThrow() {
    Workers.batchingWorker(objectInput, objectOutput, 0, true);
  }

  @Test
  @Repeat(times = NUM_REPEATS)
  public void batchingWorker_withInitiallyClosedInput_shouldImmediatelyCloseOutput()
      throws Exception {
    objectInput.close();

    assertThat(objectOutput.isClosedAndEmpty()).isFalse();

    Runnable transformer = Workers.batchingWorker(objectInput, objectOutput, 5, true);
    transformer.run();
    assertThat(objectInput.isClosedAndEmpty()).isTrue();
    Optional<ImmutableList<Object>> o = objectOutput.take();
    assertThat(o.isPresent()).isFalse();
    assertThat(objectOutput.isClosedAndEmpty()).isTrue();
  }

  private void testBatchingTransformer(int batchSize, int numElements) throws Exception {
    for (int i = 0; i < numElements; i++) {
      objectInput.put(ONE_THROUGH_TEN.get(i));
    }
    objectInput.close();

    Runnable transformer = Workers.batchingWorker(objectInput, objectOutput, batchSize, true);
    transformer.run();

    Optional<ImmutableList<Object>> possibleBatch;
    int remainingElements = numElements;
    int iterations = 0;
    while (remainingElements > 0 && iterations < numElements) {
      int startIndex = iterations * batchSize;
      int endIndex = Math.min(startIndex + batchSize, numElements);
      List<String> expectedBatch = ONE_THROUGH_TEN.subList(startIndex, endIndex);

      possibleBatch = objectOutput.tryTakeNow();
      assertThat(possibleBatch.isPresent()).isTrue();
      assertThat(possibleBatch.get()).containsExactlyElementsIn(expectedBatch).inOrder();

      iterations++;
      remainingElements -= batchSize;
    }

    assertThat(objectOutput.isClosedAndEmpty()).isTrue();
  }

  @Test
  @Repeat(times = NUM_REPEATS)
  public void batchingWorker_withThreeElementsAndBatchSizeOfOne_shouldWork() throws Exception {
    testBatchingTransformer(1, 3);
  }

  @Test
  @Repeat(times = NUM_REPEATS)
  public void batchingWorker_withThreeElementsAndBatchSizeOfThree_shouldWork() throws Exception {
    testBatchingTransformer(3, 3);
  }

  @Test
  @Repeat(times = NUM_REPEATS)
  public void batchingWorker_withFourElementsAndBatchSizeOfThree_shouldWork() throws Exception {
    testBatchingTransformer(3, 4);
  }

  @Test
  @Repeat(times = NUM_REPEATS)
  public void batchingWorker_withFiveElementsAndBatchSizeOfThree_shouldWork() throws Exception {
    testBatchingTransformer(3, 5);
  }

  @Test
  @Repeat(times = NUM_REPEATS)
  public void batchingWorker_withSixElementsAndBatchSizeOfThree_shouldWork() throws Exception {
    testBatchingTransformer(3, 6);
  }

  @Test
  @Repeat(times = NUM_REPEATS)
  public void batchingWorker_withNotCloseOutputWhenDone_shouldNotCloseOutput() throws Exception {
    Runnable worker = Workers.batchingWorker(objectInput, objectOutput, 5, false);
    ExecutorService es = Executors.newFixedThreadPool(1);
    Future<?> transformResult = es.submit(worker);

    objectInput.put("one");
    objectInput.put("two");
    objectInput.put("three");
    objectInput.close();
    transformResult.get(100, MILLISECONDS);

    Optional<ImmutableList<Object>> output = objectOutput.tryTakeNow();
    assertThat(output.isPresent()).isTrue();
    assertThat(output.get()).containsExactly("one", "two", "three").inOrder();

    assertThat(objectOutput.isClosed()).isFalse();
  }


  @Test
  @Repeat(times = NUM_REPEATS)
  public void batchingWorker_withCloseOutputWhenDone_shouldNotCloseOutputWhenInterrupted()
      throws Exception {
    Runnable worker = Workers.batchingWorker(objectInput, objectOutput, 3, true);

    ExecutorService es = Executors.newFixedThreadPool(1);
    Future<?> transformResult = es.submit(worker);

    // Grab an element from the output Chute so that we can be sure the worker is up & running.
    objectInput.put("thing 1");
    objectInput.put("thing 2");
    objectInput.put("thing 3");
    Optional<ImmutableList<Object>> result = objectOutput.tryTake(10, MILLISECONDS);
    assertThat(result.isPresent()).isTrue();

    // Now we interrupt the worker, and give it a chance to terminate.
    transformResult.cancel(true);
    result = objectOutput.tryTake(10, MILLISECONDS);
    assertThat(result.isPresent()).isFalse();

    // Close the input Chute. The worker should not process this batch.
    objectInput.put("added too late 1");
    objectInput.put("added too late 2");
    objectInput.put("added too late 3");
    objectInput.close();
    result = objectOutput.tryTake(10, MILLISECONDS);
    assertThat(result.isPresent()).isFalse();

    // The output Chute should *not* be closed (even though we passed closeOutputWhenDone=true),
    // since it was interrupted.
    assertThat(objectOutput.isClosed()).isFalse();
  }


  // -------------------------------------------------------------------------
  //
  // Tests for periodicBatchingWorker
  //
  // -------------------------------------------------------------------------

  @Test(expected = NullPointerException.class)
  @Repeat(times = NUM_REPEATS)
  public void periodicBatchingWorker_withNullInput_shouldThrow() {
    objectInput = null;
    Workers.periodicBatchingWorker(objectInput, objectOutput, 5, true, timeSource,
        maxTimeBetweenBatches);
  }

  @Test(expected = NullPointerException.class)
  @Repeat(times = NUM_REPEATS)
  public void periodicBatchingWorker_withNullOutput_shouldThrow() {
    objectOutput = null;
    Workers.periodicBatchingWorker(objectInput, objectOutput, 5, true, timeSource,
        maxTimeBetweenBatches);
  }

  @Test(expected = IllegalArgumentException.class)
  @Repeat(times = NUM_REPEATS)
  public void periodicBatchingWorker_withBatchSizeOfZero_shouldThrow() {
    Workers.periodicBatchingWorker(objectInput, objectOutput, 0, true, timeSource,
        maxTimeBetweenBatches);
  }

  @Test(expected = NullPointerException.class)
  @Repeat(times = NUM_REPEATS)
  public void periodicBatchingWorker_withNullTimeSource_shouldThrow() {
    timeSource = null;
    Workers.periodicBatchingWorker(objectInput, objectOutput, 5, true, timeSource,
        maxTimeBetweenBatches);
  }

  @Test(expected = NullPointerException.class)
  @Repeat(times = NUM_REPEATS)
  public void periodicBatchingWorker_withNullDuration_shouldThrow() {
    maxTimeBetweenBatches = null;
    Workers.periodicBatchingWorker(objectInput, objectOutput, 5, true, timeSource,
        maxTimeBetweenBatches);
  }

  @Test(expected = IllegalArgumentException.class)
  @Repeat(times = NUM_REPEATS)
  public void periodicBatchingWorker_withDurationOfZero_shouldThrow() {
    maxTimeBetweenBatches = Duration.ofMillis(0);
    Workers.periodicBatchingWorker(objectInput, objectOutput, 5, true, timeSource,
        maxTimeBetweenBatches);
  }

  @Test
  @Repeat(times = NUM_REPEATS)
  public void periodicBatchingWorker_withInitiallyClosedInput_shouldImmediatelyCloseOutput()
      throws Exception {
    objectInput.close();

    assertThat(!objectOutput.isClosedAndEmpty()).isTrue();

    Runnable batcher = Workers.periodicBatchingWorker(objectInput, objectOutput, 5, true,
        timeSource, maxTimeBetweenBatches);
    batcher.run();
    assertThat(objectInput.isClosedAndEmpty()).isTrue();
    assertThat(objectOutput.isClosedAndEmpty()).isTrue();
  }

  @Test
  @Repeat(times = NUM_REPEATS)
  public void periodicBatchingWorker_withBatchSizeOfOne_shouldWork() throws Exception {
    objectInput.put("one");
    objectInput.put("two");
    objectInput.put("three");
    objectInput.close();

    Runnable batcher = Workers.periodicBatchingWorker(objectInput, objectOutput, 1, true,
        timeSource, maxTimeBetweenBatches);
    batcher.run();
    assertThat(objectInput.isClosedAndEmpty()).isTrue();

    Optional<ImmutableList<Object>> o = objectOutput.tryTakeNow();
    assertThat(o.isPresent()).isTrue();
    assertThat(o.get()).containsExactly("one");

    o = objectOutput.tryTakeNow();
    assertThat(o.isPresent()).isTrue();
    assertThat(o.get()).containsExactly("two");

    o = objectOutput.tryTakeNow();
    assertThat(o.isPresent()).isTrue();
    assertThat(o.get()).containsExactly("three");

    assertThat(objectOutput.isClosedAndEmpty()).isTrue();
  }

  private void testPeriodicBatchingWorkerWithFastInput(int batchSize, int numElements)
      throws Exception {
    for (int i = 0; i < numElements; i++) {
      objectInput.put(ONE_THROUGH_TEN.get(i));
    }
    objectInput.close();

    Runnable transformer = Workers.periodicBatchingWorker(objectInput, objectOutput, batchSize,
        true, timeSource, maxTimeBetweenBatches);
    transformer.run();
    assertThat(objectInput.isClosedAndEmpty()).isTrue();

    transformer.run();

    Optional<ImmutableList<Object>> possibleBatch;
    int remainingElements = numElements;
    int iterations = 0;
    while (remainingElements > 0 && iterations < numElements) {
      int startIndex = iterations * batchSize;
      int endIndex = Math.min(startIndex + batchSize, numElements);
      List<String> expectedBatch = ONE_THROUGH_TEN.subList(startIndex, endIndex);

      possibleBatch = objectOutput.tryTakeNow();
      assertThat(possibleBatch.isPresent()).isTrue();
      assertThat(possibleBatch.get()).containsExactlyElementsIn(expectedBatch).inOrder();

      iterations++;
      remainingElements -= batchSize;
    }

    assertThat(objectOutput.isClosedAndEmpty()).isTrue();
  }

  @Test
  @Repeat(times = NUM_REPEATS)
  public void periodicBatchingWorker_withThreeElementsAndBatchSizeOfOneAndRapidInput_shouldWork()
      throws Exception {
    testPeriodicBatchingWorkerWithFastInput(1, 3);
  }

  @Test
  @Repeat(times = NUM_REPEATS)
  public void periodicBatchingWorker_withThreeElementsAndBatchSizeOfThreeAndRapidInput_shouldWork()
      throws Exception {
    testPeriodicBatchingWorkerWithFastInput(3, 3);
  }

  @Test
  @Repeat(times = NUM_REPEATS)
  public void periodicBatchingWorker_withFourElementsAndBatchSizeOfThreeAndRapidInput_shouldWork()
      throws Exception {
    testPeriodicBatchingWorkerWithFastInput(3, 4);
  }

  @Test
  @Repeat(times = NUM_REPEATS)
  public void periodicBatchingWorker_withFiveElementsAndBatchSizeOfThreeAndRapidInput_shouldWork()
      throws Exception {
    testPeriodicBatchingWorkerWithFastInput(3, 5);
  }

  @Test
  @Repeat(times = NUM_REPEATS)
  public void periodicBatchingWorker_withSixElementsAndBatchSizeOfThreeAndRapidInput_shouldWork()
      throws Exception {
    testPeriodicBatchingWorkerWithFastInput(3, 6);
  }

  @Test
  @Repeat(times = NUM_REPEATS)
  public void periodicBatchingWorker_withTenElementsAndBatchSizeOfFiveAndRapidInput_shouldWork()
      throws Exception {
    testPeriodicBatchingWorkerWithFastInput(5, 10);
  }

  @Test
  @Repeat(times = NUM_REPEATS)
  public void periodicBatchingWorker_withCloseOutputWhenDone_shouldNotCloseOutputWhenInterrupted()
      throws Exception {
    Runnable worker = Workers.periodicBatchingWorker(objectInput, objectOutput, 3, true, timeSource,
        maxTimeBetweenBatches);

    ExecutorService es = Executors.newFixedThreadPool(1);
    Future<?> transformResult = es.submit(worker);

    // Grab an element from the output Chute so that we can be sure the worker is up & running.
    objectInput.put("something");
    long longerThanBatchInterval = maxTimeBetweenBatches.toMillis() * 2;
    Optional<ImmutableList<Object>> result =
        objectOutput.tryTake(longerThanBatchInterval, MILLISECONDS);
    assertThat(result.isPresent()).isTrue();

    // Now we interrupt the worker, and give it a chance to terminate.
    transformResult.cancel(true);
    result = objectOutput.tryTake(10, MILLISECONDS);
    assertThat(result.isPresent()).isFalse();

    // Close the input Chute. The worker should not process this element.
    objectInput.put("added too late");
    objectInput.close();
    result = objectOutput.tryTake(longerThanBatchInterval, MILLISECONDS);
    assertThat(result.isPresent()).isFalse();

    // The output Chute should *not* be closed (even though we passed closeOutputWhenDone=true),
    // since it was interrupted.
    assertThat(objectOutput.isClosed()).isFalse();
  }

  @Test
  @Repeat(times = NUM_REPEATS)
  public void periodicBatchingWorker_withNotCloseOutputWhenDone_shouldNotCloseOutput()
      throws Exception {
    Runnable worker = Workers.periodicBatchingWorker(objectInput, objectOutput, 5, false,
        timeSource, maxTimeBetweenBatches);
    ExecutorService es = Executors.newFixedThreadPool(1);
    Future<?> transformResult = es.submit(worker);

    objectInput.put("one");
    objectInput.put("two");
    objectInput.close();
    transformResult.get(100, MILLISECONDS);

    Optional<ImmutableList<Object>> output = objectOutput.tryTakeNow();
    assertThat(output.isPresent()).isTrue();
    assertThat(output.get()).containsExactly("one", "two").inOrder();

    assertThat(objectOutput.isClosed()).isFalse();
  }


  @Test
  @Repeat(times = NUM_REPEATS)
  public void periodicBatchingWorker_withBatchSizeOfThreeAndSlowInput_shouldWork()
      throws Exception {
    Duration maxTimeBetweenBatches = Duration.ofMillis(10);
    Runnable batcher = Workers.periodicBatchingWorker(objectInput, objectOutput, 3, true,
        timeSource, maxTimeBetweenBatches);

    ExecutorService es = Executors.newFixedThreadPool(1);
    Future<?> transformResult = es.submit(batcher);

    objectInput.put("one");
    objectInput.put("two");

    // Block until the first batch is available.
    Optional<ImmutableList<Object>> o = objectOutput.take();
    assertThat(o.isPresent()).isTrue();
    assertThat(o.get()).containsExactly("one", "two").inOrder();

    // Make the worker wait for longer than its maxTimeBetweenBatches, to ensure that it won't send
    // a batch that contains 0 elements.
    objectOutput.tryTake(maxTimeBetweenBatches.toMillis() * 2, MILLISECONDS);

    objectInput.put("three");
    o = objectOutput.take();
    assertThat(o.isPresent()).isTrue();
    assertThat(o.get()).containsExactly("three");

    objectInput.put("four");
    objectInput.close();
    o = objectOutput.take();
    assertThat(o.isPresent()).isTrue();
    assertThat(o.get()).containsExactly("four");

    transformResult.get(); // let the transformer finish transformin'

    assertThat(objectInput.isClosedAndEmpty()).isTrue();
    assertThat(objectOutput.isClosedAndEmpty()).isTrue();

    es.shutdownNow();
  }
}
