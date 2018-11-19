package com.pervasivecode.utils.concurrent.chute;

import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import com.google.common.collect.ImmutableList;
import com.google.common.truth.Truth;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.pervasivecode.utils.time.testing.FakeNanoSource;

public class SynchronousMultiplexerTest {

  private FakeNanoSource nanosSource;
  private ListeningExecutorService executorService;

  @Before
  public void setUp() throws Exception {
    nanosSource = new FakeNanoSource();
    executorService = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(4));
  }

  @After
  public void teardown() throws Exception {
    executorService.shutdownNow();
    executorService.awaitTermination(1, SECONDS);
  }

  @Test
  public void constructor_withLessThanOneInput_shouldThrow() {
    BufferingChute<Integer> outputChute = new BufferingChute<>(10, nanosSource);
    try {
      new SynchronousMultiplexer<>(0, outputChute);
      Truth.assert_().fail("Exepcted constructor to disallow numInputs = 0.");
    } catch (IllegalArgumentException iae) {
      assertThat(iae).hasMessageThat().contains("at least 1");
    }

    try {
      new SynchronousMultiplexer<>(-100, outputChute);
      Truth.assert_().fail("Exepcted constructor to disallow numInputs = -100.");
    } catch (IllegalArgumentException iae) {
      assertThat(iae).hasMessageThat().contains("at least 1");
    }
  }

  @Test(expected = NullPointerException.class)
  public void constructor_withNullOutputChute_shouldThrow() {
    new SynchronousMultiplexer<>(3, null);
  }

  @Test
  public void constructor_withOneInput_shouldWork() throws Exception {
    BufferingChute<Integer> outputChute = new BufferingChute<>(2, nanosSource);
    SynchronousMultiplexer<Integer> mux = new SynchronousMultiplexer<>(1, outputChute);

    ImmutableList<ChuteEntrance<Integer>> inputs = mux.inputChutes();
    assertThat(inputs).hasSize(1);
    ChuteEntrance<Integer> input = inputs.get(0);
    input.put(3456);
    input.close();

    assertThat(outputChute.isClosed()).isTrue();
    assertThat(outputChute.isClosedAndEmpty()).isFalse();
    Optional<Integer> result = outputChute.tryTakeNow();
    assertThat(result.isPresent()).isTrue();
    assertThat(result.get()).isEqualTo(3456);
    assertThat(outputChute.isClosedAndEmpty()).isTrue();
  }

  @Test
  public void put_onOpenMultiplexerEntrance_shouldWork() throws Exception {
    int numInputChutes = 2;
    BufferingChute<Integer> outputChute = new BufferingChute<>(numInputChutes + 1, nanosSource);

    SynchronousMultiplexer<Integer> mux = new SynchronousMultiplexer<>(numInputChutes, outputChute);
    List<ChuteEntrance<Integer>> inputs = mux.inputChutes();

    Callable<Void> producer = () -> {
      inputs.get(0).put(1);
      inputs.get(1).put(2);
      return null;
    };
    Future<?> putResult = executorService.submit(producer);
    putResult.get(10, MILLISECONDS);

    assertThat(outputChute.isClosed()).isFalse();

    Callable<Void> closer = () -> {
      inputs.get(0).close();
      return null;
    };
    Future<?> close1Result = executorService.submit(closer);
    close1Result.get(10, MILLISECONDS);

    assertThat(outputChute.isClosed()).isFalse();

    closer = () -> {
      inputs.get(1).close();
      return null;
    };
    Future<?> close2Result = executorService.submit(closer);
    close2Result.get(100, MILLISECONDS);

    assertThat(outputChute.isClosed()).isTrue();
  }

  @Test
  public void close_onAllMultiplexerEntrances_shouldCloseOutputChuteEntrance() throws Exception {
    BufferingChute<Integer> outputChute = new BufferingChute<>(1, nanosSource);
    SynchronousMultiplexer<Integer> mux = new SynchronousMultiplexer<>(3, outputChute);
    List<ChuteEntrance<Integer>> inputs = mux.inputChutes();

    assertThat(outputChute.isClosed()).isFalse();

    inputs.get(0).close();
    assertThat(outputChute.isClosed()).isFalse();

    inputs.get(1).close();
    assertThat(outputChute.isClosed()).isFalse();

    inputs.get(2).close();
    assertThat(outputChute.isClosed()).isTrue();
  }

  @Test
  public void put_onClosedMultiplexerEntrance_shouldFailImmediately() throws Exception {
    BufferingChute<Integer> outputChute = new BufferingChute<>(10, nanosSource);
    SynchronousMultiplexer<Integer> mux = new SynchronousMultiplexer<>(2, outputChute);
    List<ChuteEntrance<Integer>> inputs = mux.inputChutes();

    inputs.get(0).close();

    Callable<Void> closer = () -> {
      inputs.get(1).close();
      return null;
    };
    Future<?> close1Result = executorService.submit(closer);
    close1Result.get(10, MILLISECONDS);

    assertThat(inputs.get(0).isClosed()).isTrue();
    assertThat(inputs.get(1).isClosed()).isTrue();

    assertThat(outputChute.isClosed()).isTrue();

    Callable<Void> producer = () -> {
      inputs.get(1).put(54321);
      return null;
    };
    Future<?> putResult = executorService.submit(producer);

    try {
      putResult.get(100, MILLISECONDS);
      Truth.assert_().fail("Expected get() to throw an ExecutionException.");
    } catch (ExecutionException ee) {
      assertThat(ee.getCause()).isNotNull();
      assertThat(ee.getCause()).isInstanceOf(IllegalStateException.class);
      assertThat(ee.getCause()).hasMessageThat().contains("already closed");
    }
  }

  @Test
  public void put_whenOutputChuteIsClosed_shouldFailImmediately() throws Exception {
    BufferingChute<Integer> outputChute = new BufferingChute<>(1, nanosSource);
    SynchronousMultiplexer<Integer> mux = new SynchronousMultiplexer<>(2, outputChute);
    List<ChuteEntrance<Integer>> inputs = mux.inputChutes();

    outputChute.close();
    ChuteEntrance<Integer> input1 = inputs.get(0);
    try {
      input1.put(12345);
      Truth.assert_().fail("Expected put to fail since the ChuteEntrance is closed.");
    } catch (IllegalStateException ise) {
      assertThat(ise).hasMessageThat().contains("closed");
    }
  }

  @Test
  public void close_onClosedMultiplexerEntrance_shouldDoNothing() throws Exception {
    BufferingChute<Integer> outputChute = new BufferingChute<>(1, nanosSource);
    SynchronousMultiplexer<Integer> mux = new SynchronousMultiplexer<>(3, outputChute);
    List<ChuteEntrance<Integer>> inputs = mux.inputChutes();

    assertThat(outputChute.isClosed()).isFalse();

    inputs.get(0).close();
    inputs.get(0).close();
    inputs.get(0).close();
    inputs.get(0).close();
    assertThat(outputChute.isClosed()).isFalse();

    inputs.get(1).put(12345);
    Optional<Integer> outputElement = outputChute.tryTakeNow();
    assertThat(outputElement.isPresent()).isTrue();
    assertThat(outputElement.get()).isEqualTo(12345);

    inputs.get(2).put(23456);
    outputElement = outputChute.tryTakeNow();
    assertThat(outputElement.isPresent()).isTrue();
    assertThat(outputElement.get()).isEqualTo(23456);
  }

  @Test
  public void isClosed_whenOutputChuteIsClosed_shouldReturnTrue() throws Exception {
    BufferingChute<Integer> output = new BufferingChute<>(3, nanosSource);
    SynchronousMultiplexer<Integer> mux = new SynchronousMultiplexer<>(2, output);

    ChuteEntrance<Integer> input1 = mux.inputChutes().get(0);
    ChuteEntrance<Integer> input2 = mux.inputChutes().get(1);

    assertThat(input1.isClosed()).isFalse();
    assertThat(input2.isClosed()).isFalse();

    output.close();
    assertThat(input1.isClosed()).isTrue();
    assertThat(input2.isClosed()).isTrue();
  }
}
