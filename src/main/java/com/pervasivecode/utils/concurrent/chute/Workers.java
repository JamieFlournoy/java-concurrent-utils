package com.pervasivecode.utils.concurrent.chute;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Optional;
import java.util.function.Function;
import com.google.common.collect.ImmutableList;
import com.pervasivecode.utils.time.api.TimeSource;

public class Workers {

  private Workers() {};

  /**
   * Returns a Runnable that will take all of the elements from the input ChuteExit, group them into
   * batches of no larger than the specified size, and put them in the output ChuteEntrance. Once
   * the input ChuteExit is closed and the batch containing the last element has been put into the
   * output ChuteEntrance, this Runnable will close the output ChuteEntrance and return.
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
   * @param closeOutputWhenDone Whether to close the output chute after the last batch has been
   *        sent.
   * @param timeSource A source of time, used to determine whether it has been long enough to send a
   *        partial batch.
   * @param maxTimeBetweenBatches The amount of time to wait for a batch to be completed before just
   *        sending a partial batch into the output ChuteEntrance.
   */
  public static <I> Runnable periodicBatchingWorker(ChuteExit<I> input,
      ChuteEntrance<ImmutableList<I>> output, int batchSize, boolean closeOutputWhenDone,
      TimeSource timeSource, Duration maxTimeBetweenBatches) {
    return new PeriodicBatchingWorker<>(input, output, batchSize, closeOutputWhenDone, timeSource,
        maxTimeBetweenBatches);
  }

  private static class PeriodicBatchingWorker<E> implements Runnable {
    private ChuteExit<E> input;
    private ChuteEntrance<ImmutableList<E>> output;
    private final int maxBatchSize;
    private ArrayList<E> builder;
    private final TimeSource timeSource;
    private final Duration maxTimeBetweenBatches;
    private Instant whenToFlush;
    private boolean closeOutputWhenDone;

    public PeriodicBatchingWorker(ChuteExit<E> input, ChuteEntrance<ImmutableList<E>> output,
        int maxBatchSize, boolean closeOutputWhenDone, TimeSource timeSource,
        Duration maxTimeBetweenBatches) {
      this.input = checkNotNull(input);
      this.output = checkNotNull(output);

      checkArgument(maxBatchSize > 0, "maxBatchSize must be greater than 0. Got %s", maxBatchSize);
      this.maxBatchSize = maxBatchSize;
      this.builder = new ArrayList<>(maxBatchSize);
      this.closeOutputWhenDone = closeOutputWhenDone;

      this.timeSource = checkNotNull(timeSource);
      checkArgument(!maxTimeBetweenBatches.equals(Duration.ZERO),
          "maxTimeBetweenBatches cannot be zero.");
      this.maxTimeBetweenBatches = checkNotNull(maxTimeBetweenBatches);
      this.whenToFlush = timeSource.now().plus(maxTimeBetweenBatches);
    }

    @Override
    public void run() {
      try {
        boolean inputClosed = false;
        while (!inputClosed) {
          boolean sendBatch = false;
          Instant now = timeSource.now();
          long millisToWait =
              whenToFlush.isAfter(now) ? Duration.between(now, whenToFlush).toMillis() : 0;
          Optional<E> taken = input.tryTake(millisToWait, MILLISECONDS);
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
        if (closeOutputWhenDone) {
          output.close();
        }
      } catch (@SuppressWarnings("unused") InterruptedException ie) {
        // Just stop processing and exit.
      }
    }
  }


  /**
   * Create a Runnable worker that will transform elements from a ChuteExit using a function,
   * putting the resulting elements into a ChuteEntrance, until the ChuteExit is closed (or the
   * Runnable worker is interrupted).
   *
   * @param input The ChuteExit from which elements should be taken.
   * @param output The ChuteEntrance into which the transformed elements should be put.
   * @param converter The function that transforms input elements into output elements.
   * @param closeOutputWhenDone If true, when the input ChuteExit closes and the last transformed
   *        element has been placed into the output ChuteEntrance, the worker will close the output
   *        ChuteEntrance.
   * @return A Runnable worker that will perform the specified transformation and optional closing
   *         of the output ChuteEntrance.
   */
  public static <T, V> Runnable transformingWorker(ChuteExit<T> input, ChuteEntrance<V> output,
      Function<T, V> converter, boolean closeOutputWhenDone) {
    checkNotNull(input);
    checkNotNull(output);
    checkNotNull(converter);
    return () -> {
      try {
        for (T inputElement : Chutes.asIterable(input)) {
          output.put(converter.apply(inputElement));
        }
        if (closeOutputWhenDone) {
          output.close();
        }
      } catch (@SuppressWarnings("unused") InterruptedException e) {
        // Just stop processing and exit.
      }
    };
  }


  /**
   * Returns a Runnable that will take all of the elements from the input ChuteExit, group them into
   * batches of the specified size, and put them in the output ChuteEntrance.
   * <p>
   * The transformer will wait indefinitely for enough input elements to create a batch, unless the
   * input chute closes, in which case it will send the last batch immediately. For a transformer
   * that will periodically flush batches regardless of size, use
   * {@link #batchingPeriodicTransformer(ChuteExit, ChuteEntrance, int, TimeSource, Duration)}.
   *
   * @param input The source of elements to be collected into batches.
   * @param output The chute into which batches of elements will be placed.
   * @param maxBatchSize The maximum size of each batch. The last batch (created when the input
   *        chute is closed and empty) may be smaller than this size; all others will be exactly
   *        this size.
   * @param closeOutputWhenDone Whether to close the output chute after the last batch has been
   *        sent.
   */
  public static <I> Runnable batchingWorker(ChuteExit<I> input,
      ChuteEntrance<ImmutableList<I>> output, int maxBatchSize, boolean closeOutputWhenDone) {
    return new BatchingWorker<I>(input, output, maxBatchSize, closeOutputWhenDone);
  }


  private static class BatchingWorker<E> implements Runnable {
    private ChuteExit<E> input;
    private ChuteEntrance<ImmutableList<E>> output;
    private final int maxBatchSize;
    private ArrayList<E> builder;
    private boolean closeOutputWhenDone;

    public BatchingWorker(ChuteExit<E> input, ChuteEntrance<ImmutableList<E>> output,
        int maxBatchSize, boolean closeOutputWhenDone) {
      this.input = checkNotNull(input);
      this.output = checkNotNull(output);
      checkArgument(maxBatchSize > 0, "maxBatchSize must be greater than 0. Got %s", maxBatchSize);
      this.maxBatchSize = maxBatchSize;
      this.builder = new ArrayList<>(maxBatchSize);
      this.closeOutputWhenDone = closeOutputWhenDone;
    }

    @Override
    public void run() {
      try {
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
        if (closeOutputWhenDone) {
          output.close();
        }
      } catch (@SuppressWarnings("unused") InterruptedException ie) {
        // Just stop processing and exit.
      }
    }
  }
}
