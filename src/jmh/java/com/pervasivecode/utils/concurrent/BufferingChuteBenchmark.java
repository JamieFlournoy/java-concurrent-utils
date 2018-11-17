package com.pervasivecode.utils.concurrent;

import static java.util.concurrent.TimeUnit.SECONDS;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.pervasivecode.utils.concurrent.chute.BufferingChute;
import com.pervasivecode.utils.concurrent.chute.Chute;
import com.pervasivecode.utils.time.api.CurrentNanosSource;

/**
 * This is an individual JMH microbenchmark, which tests the performance of BufferingChute against a
 * non-closeable ArrayBlockingQueue.
 *
 * Results from a run on a fast-ish desktop PC:
 * Benchmark   Mode  Cnt          Score         Error  Units
 * abq        thrpt   30  147610309.658 ± 3941400.703  ops/s
 * bc         thrpt   30  147362240.724 ± 3689451.154  ops/s
 *
 * abq = BufferingChuteBenchmark.arrayBlockingQueue_size10k_100producers_100consumers
 * bc =  BufferingChuteBenchmark.bufferingChute_size100k_100producers_100consumers
 *
 * BufferingChute is 0.17% (not 17%) slower than ArrayBlockingQueue.
 */
@State(Scope.Thread)
@Measurement(iterations = 3)
public class BufferingChuteBenchmark {
  public enum Mode { ARRAY_BLOCKING_QUEUE, BUFFERING_CHUTE }

  private static final CurrentNanosSource NANOS_SOURCE = ()->System.nanoTime();

  private AtomicReference<Mode> mode = new AtomicReference<>(null);

  private ListeningExecutorService executorService;

  // Execution tracking
  private CountDownLatch startLatch = new CountDownLatch(1);
  private AtomicBoolean shouldShutDown = new AtomicBoolean(false);
  private ImmutableList<ListenableFuture<?>> producerFutures;
  private ImmutableList<ListenableFuture<?>> consumerFutures;
  private CountDownLatch consumerDoneLatch = new CountDownLatch(100);
  private CountDownLatch abqNotYetConsumedCount = new CountDownLatch(10000);

  public static ImmutableList<ImmutableList<String>> generateElements() {
    ImmutableList.Builder<ImmutableList<String>> partitionsBuilder = ImmutableList.builder();
    for (int i = 0; i < 100; i++) {
      ImmutableList.Builder<String> partitionBuilder = ImmutableList.builder();
      for (int j = 0; j < 100; j++) {
        int n = i * 100 + j;
        partitionBuilder.add(String.format("asdfasdfasdf %d", n));
      }
      partitionsBuilder.add(partitionBuilder.build());
    }
    return partitionsBuilder.build();
  }

  private Runnable getAbqProducer(ImmutableList<String> elements,
      ArrayBlockingQueue<String> queue) {
    return () -> {
      try {
        startLatch.await();
        for (String element : elements) {
          queue.put(element);
        }
      } catch (@SuppressWarnings("unused") InterruptedException e) {
        return;
      }
    };
  }

  private Runnable getBcProducer(ImmutableList<String> elements, Chute<String> chute,
      CountDownLatch bcProducerDoneLatch) {
    return ()->{
      try {
        startLatch.await();
        for (String element : elements) {
          chute.put(element);
        }
        bcProducerDoneLatch.countDown();
      } catch (@SuppressWarnings("unused") InterruptedException e) {
        return;
      }
    };
  }

  private Runnable getAbqConsumer(ArrayBlockingQueue<String> queue) {
    return () -> {
      try {
        while (true) {
          String result = queue.poll(1, SECONDS);
          if (result != null) {
            abqNotYetConsumedCount.countDown();
            continue;
          }
          if (shouldShutDown.get()) {
            break;
          }
        } // while
      } catch (@SuppressWarnings("unused") InterruptedException e) {
        return;
      } finally {
        consumerDoneLatch.countDown();
      }
    };
  }

  private Runnable getBcConsumer(Chute<String> chute) {
    return () -> {
      try {
        while (!chute.isClosedAndEmpty()) {
          chute.take();
        }
      } catch (@SuppressWarnings("unused") InterruptedException e) {
        return;
      } finally {
        consumerDoneLatch.countDown();
      }
    };
  }

  @Setup
  public void startThreads() throws Exception {
    executorService = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(201));
    Chute<String> chute = new BufferingChute<>(100, NANOS_SOURCE);
    ArrayBlockingQueue<String> queue = new ArrayBlockingQueue<>(100);

    CountDownLatch readyToStartLatch = new CountDownLatch(200);
    CountDownLatch bcProducerDoneLatch = new CountDownLatch(100);

    ImmutableList<ImmutableList<String>> elements = generateElements();

    ImmutableList.Builder<ListenableFuture<?>> producerFuturesBuilder = ImmutableList.builder();
    ImmutableList.Builder<ListenableFuture<?>> consumerFuturesBuilder = ImmutableList.builder();

    for (int i = 0; i < 100; i++) {
      ImmutableList<String> myElements = elements.get(i);

      Runnable bcProducer = getBcProducer(myElements, chute, bcProducerDoneLatch);
      Runnable abqProducer = getAbqProducer(myElements, queue);
      Runnable producer = () -> {
        readyToStartLatch.countDown();
        try {
          startLatch.await();
          Mode myMode = mode.get();
          final Runnable activeProducer =
              (myMode == Mode.ARRAY_BLOCKING_QUEUE) ? abqProducer : bcProducer;
          activeProducer.run();
        } catch (@SuppressWarnings("unused") InterruptedException e) {
          return;
        }
      };
      ListenableFuture<?> producerResult = executorService.submit(producer);
      producerFuturesBuilder.add(producerResult);

      Runnable bcConsumer = getBcConsumer(chute);
      Runnable abqConsumer = getAbqConsumer(queue);
      Runnable consumer = () -> {
        readyToStartLatch.countDown();
        try {
          startLatch.await();
          Mode myMode = mode.get();
          final Runnable activeConsumer =
              (myMode == Mode.ARRAY_BLOCKING_QUEUE) ? abqConsumer : bcConsumer;
          activeConsumer.run();
        } catch (@SuppressWarnings("unused") InterruptedException e) {
          return;
        }
      };
      ListenableFuture<?> consumerResult = executorService.submit(consumer);
      consumerFuturesBuilder.add(consumerResult);
    }

    // A Chute closer that just tells BufferingChute producers when they're done.
    Runnable closer = () -> {
      try {
        startLatch.await();
        Mode myMode = mode.get();
        if (myMode == Mode.BUFFERING_CHUTE) {
          bcProducerDoneLatch.await();
          chute.close();
        }
      } catch (@SuppressWarnings("unused") InterruptedException e) {
        return;
      }
    };
    ListenableFuture<?> bcCloserResult = executorService.submit(closer);
    producerFuturesBuilder.add(bcCloserResult);

    producerFutures = producerFuturesBuilder.build();
    consumerFutures = consumerFuturesBuilder.build();

    readyToStartLatch.await();
  }

  @TearDown
  public void awaitThreadShutdown() throws Exception {
    shouldShutDown.set(true);

    Futures.allAsList(producerFutures).get(5, SECONDS);
    Futures.allAsList(consumerFutures).get(5, SECONDS);

    executorService.shutdown();
    executorService.awaitTermination(1, SECONDS);
  }

  @Benchmark
  @Threads(1) // 1 benchmark runner thread, which creates a bunch more threads.
  public void bufferingChute_size100k_100producers_100consumers() throws Exception {
    mode.set(Mode.BUFFERING_CHUTE);
    // Release the hounds!
    startLatch.countDown();

    boolean finished = consumerDoneLatch.await(5, SECONDS);
    if (!finished) {
      String message = String.format("Timed out waiting for consumers to finish. %d still running.",
          consumerDoneLatch.getCount());
      throw new TimeoutException(message);
    }
  }

  @Benchmark
  @Threads(1) // 1 benchmark runner thread, which creates a bunch more threads.
  public void arrayBlockingQueue_size10k_100producers_100consumers() throws Exception {
    mode.set(Mode.ARRAY_BLOCKING_QUEUE);
    // Release the hounds!
    startLatch.countDown();

    boolean finished = abqNotYetConsumedCount.await(5, SECONDS);
    if (!finished) {
      String message = String.format("Timed out waiting for consumers to finish. %d not consumed.",
          abqNotYetConsumedCount.getCount());
      throw new TimeoutException(message);
    }
  }
}
