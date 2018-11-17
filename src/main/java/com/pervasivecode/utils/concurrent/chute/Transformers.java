package com.pervasivecode.utils.concurrent.chute;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.Callable;
//import java.util.function.BiConsumer;
//import java.util.function.BiPredicate;
//import java.util.function.Function;
import java.util.concurrent.TimeUnit;

import java.time.Duration;
import java.time.Instant;

import com.google.common.collect.ImmutableList;
import com.pervasivecode.utils.time.api.TimeSource;

public class Transformers {
//
//  /**
//   * Returns a Callable that will take all of the elements from the input ChuteExit, process them
//   * with the provided converter, and put them in the output ChuteEntrance.
//   * <p>
//   * If there are errors 
//   */
//  public static <I, O> Callable<Void> functionTransformer(ChuteExit<I> input,
//      ChuteEntrance<O> output, Function<I, O> converter) {
//    return functionTransformer(input, output, converter, (i,t)->true);
//  }

//  /**
//     * Returns a Callable that will take all of the elements from the input ChuteExit, process them
//     * with the provided converter, and put them in the output ChuteEntrance.
//     */
//  public static <I, O, T extends Throwable> Callable<Void> functionTransformer(ChuteExit<I> input,
//      ChuteEntrance<O> output, Function<I, O> converter, BiPredicate<I, T> shouldContinue,
//      BiConsumer<I, T> errorProcessor) {
//
//    // TODO write a test for this.
//    return () -> {
//      boolean inputClosed = false;
//      while (!inputClosed) {
//        Optional<I> taken = input.take();
//        if (taken.isPresent()) {
//          I inputElement = taken.get();
//          O outputElement = converter.apply(inputElement);
//          // TODO add a numContinuableErrors arg (default 0=infinite) to determine how many
//          // exceptions are allowed
//          // ErrorHandlingPolicy arg that lets callers decide whether to:
//          // 1) keep going until the input is closed (default behavior)
//          // 2) return
//          // 3) return, but close the output chute first
//          // 4) keep going, but put the exception+bad element into an error chute
//          // 
//          output.put(outputElement);
//        } else {
//          inputClosed = true;
//        }
//      }
//      return null;
//    };
//  }

  /**
   * Returns a Callable that will take all of the elements from the input ChuteExit, group them into
   * batches of the specified size, and put them in the output ChuteEntrance.
   * <p>
   * The transformer will wait indefinitely for enough input elements to create a batch. For a
   * transformer that will periodically flush incomplete batches, use
   * {@link #batchingPeriodicTransformer(ChuteExit, ChuteEntrance, int, TimeSource, Duration)}
   */
  public static <I> Callable<Void> batchingTransformer(ChuteExit<I> input,
      ChuteEntrance<ImmutableList<I>> output, int batchSize) {
    checkNotNull(input);
    checkNotNull(output);
    checkArgument(batchSize > 0, "batchSize must be nonnegative.");
    return () -> {
      Batcher<I> b = new Batcher<>(batchSize);
      b.transform(input, output);
      return null;
    };
  }

  /**
   * Returns a Callable that will take all of the elements from the input ChuteExit, group them into
   * batches of no larger than the specified size, and put them in the output ChuteEntrance. Once
   * the input ChuteExit is closed and the batch containing the last element has been put into the
   * output ChuteEntrance, this Callable will close the output ChuteEntrance and return.
   * <p>
   * Batches will be sent to the output Chute at least once per maxTimeBetweenBatches, or sooner if
   * the elements appear quickly enough that batchSize is reached before that time has elapsed.
   * <p>
   * If no elements have appeared in the input ChuteExit and the maxTimeBetweenBatches elapses, an
   * empty batch will <b>not</b> be sent to the output ChuteEntrance.
   *
   * @param input A ChuteExit from which individual batchable elements are taken.
   * @param output A ChuteEntrance into which batches are sent.
   * @param batchSize The maximum size of a batch before it should be placed into the output Chute.
   * @param timeSource A source of time, used to determine whether it has been long enough to send a
   *        partial batch.
   * @param maxTimeBetweenBatches The amount of time to wait for a batch to be completed before just
   *        sending a partial batch into the output ChuteEntrance.
   */
  public static <I> Callable<Void> batchingPeriodicTransformer(ChuteExit<I> input,
      ChuteEntrance<ImmutableList<I>> output, int batchSize, TimeSource timeSource,
      Duration maxTimeBetweenBatches) {
    checkNotNull(input);
    checkNotNull(output);
    checkArgument(batchSize > 0, "batchSize must be nonnegative.");
    checkNotNull(timeSource);
    checkNotNull(maxTimeBetweenBatches);
    checkArgument(maxTimeBetweenBatches.toMillis() > 0, "maxTimeBetweenBatches cannot be zero.");
    return () -> {
      PeriodicBatcher<I> b = new PeriodicBatcher<>(batchSize, timeSource, maxTimeBetweenBatches);
      b.transform(input, output);
      return null;
    };
  }

  private static class Batcher<E> {
    private ArrayList<E> builder;
    private final int maxBatchSize;

    public Batcher(int maxBatchSize) {
      checkArgument(maxBatchSize > 0, "maxBatchSize must be greater than 0. Got %s", maxBatchSize);
      this.maxBatchSize = maxBatchSize;
      builder = new ArrayList<>(maxBatchSize);
    }

    public void transform(ChuteExit<E> input, ChuteEntrance<ImmutableList<E>> output)
        throws InterruptedException {
      boolean inputClosed = false;
      while (!inputClosed) {
        boolean sendBatch = false;
        Optional<E> taken = input.take();
        if (taken.isPresent()) {
          builder.add(taken.get());
          if (builder.size() >= maxBatchSize) {
            sendBatch = true;
          }
        } else {
          inputClosed = true;
          if (!builder.isEmpty()) {
            sendBatch = true;
          }
        }
        if (sendBatch) {
          ImmutableList<E> batch = ImmutableList.copyOf(builder);
          builder.clear();
          output.put(batch);
        }
      }
      output.close();
    }
  }

  private static class PeriodicBatcher<E> {
    private ArrayList<E> builder;
    private final int maxBatchSize;
    private final TimeSource timeSource;
    private final Duration maxTimeBetweenBatches;
    private Instant whenToFlush;

    public PeriodicBatcher(int maxBatchSize, TimeSource timeSource,
        Duration maxTimeBetweenBatches) {
      checkArgument(maxBatchSize > 0, "maxBatchSize must be greater than 0. Got %s", maxBatchSize);
      this.maxBatchSize = maxBatchSize;
      builder = new ArrayList<>(maxBatchSize);
      this.timeSource = checkNotNull(timeSource);
      this.maxTimeBetweenBatches = checkNotNull(maxTimeBetweenBatches);

      this.whenToFlush = timeSource.now().plus(maxTimeBetweenBatches);
    }

    public void transform(ChuteExit<E> input, ChuteEntrance<ImmutableList<E>> output)
        throws InterruptedException {
      boolean inputClosed = false;
      while (!inputClosed) {
        boolean sendBatch = false;
        Instant now = timeSource.now();
        long millisToWait =
            whenToFlush.isAfter(now) ? Duration.between(now, whenToFlush).toMillis() : 0;
        Optional<E> taken = input.tryTake(millisToWait, TimeUnit.MILLISECONDS);
        if (taken.isPresent()) {
          builder.add(taken.get());
          if (builder.size() >= maxBatchSize) {
            sendBatch = true;
          }
        } else {
          // tryTake didn't return anything. This could mean that the input is closed, or it could
          // mean that we just timed out and should send a partial batch.
          if (input.isClosedAndEmpty()) {
            inputClosed = true;
          }
          if (!builder.isEmpty()) {
            sendBatch = true;
          }
        }
        if (sendBatch) {
          ImmutableList<E> batch = ImmutableList.copyOf(builder);
          builder.clear();
          output.put(batch);
          whenToFlush = timeSource.now().plus(maxTimeBetweenBatches);
        }
      }
      output.close();
    }
  }

}
