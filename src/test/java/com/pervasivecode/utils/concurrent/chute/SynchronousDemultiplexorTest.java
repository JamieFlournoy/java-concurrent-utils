package com.pervasivecode.utils.concurrent.chute;

import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.MILLISECONDS;

import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.Before;
import org.junit.Test;

import com.google.common.truth.Truth;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.pervasivecode.utils.concurrent.chute.BufferingChute;
import com.pervasivecode.utils.concurrent.chute.ChuteEntrance;
import com.pervasivecode.utils.concurrent.chute.SynchronousDemultiplexor;
import com.pervasivecode.utils.time.testing.FakeNanoSource;

public class SynchronousDemultiplexorTest {

  private FakeNanoSource nanosSource;
  private ListeningExecutorService executorService;

  @Before
  public void setUp() throws Exception {
    nanosSource = new FakeNanoSource();
    executorService = MoreExecutors.listeningDecorator(Executors.newFixedThreadPool(4));
  }

  @Test
  public void put_onOpenDemultiplexor_shouldWork() throws Exception {
    int numInputChutes = 2;
    BufferingChute<Integer> chute = new BufferingChute<>(numInputChutes + 1, nanosSource);

    SynchronousDemultiplexor<Integer> demux = new SynchronousDemultiplexor<>(numInputChutes, chute);
    List<ChuteEntrance<Integer>> inputs = demux.inputChutes();

    Callable<Void> producer = () -> {
      inputs.get(0).put(1);
      inputs.get(1).put(2);
      return null;
    };
    Future<?> putResult = executorService.submit(producer);
    putResult.get(10, MILLISECONDS);

    assertThat(chute.isClosed()).isFalse();

    Callable<Void> closer = () -> {
      inputs.get(0).close();
      return null;
    };
    Future<?> close1Result = executorService.submit(closer);
    close1Result.get(10, MILLISECONDS);

    assertThat(chute.isClosed()).isFalse();

    closer = () -> {
      inputs.get(1).close();
      return null;
    };
    Future<?> close2Result = executorService.submit(closer);
    close2Result.get(100, MILLISECONDS);

    assertThat(chute.isClosed()).isTrue();
  }

  @Test
  public void put_onClosedDemultiplexor_shouldFailImmediately() throws Exception {
    int numInputChutes = 2;
    BufferingChute<Integer> chute = new BufferingChute<>(numInputChutes + 1, nanosSource);

    SynchronousDemultiplexor<Integer> demux = new SynchronousDemultiplexor<>(numInputChutes, chute);
    List<ChuteEntrance<Integer>> inputs = demux.inputChutes();

    Callable<Void> closer = () -> {
      inputs.get(0).close();
      inputs.get(1).close();
      return null;
    };
    Future<?> close1Result = executorService.submit(closer);
    close1Result.get(10, MILLISECONDS);

    assertThat(chute.isClosed()).isTrue();

    Callable<Void> producer = () -> {
      inputs.get(1).put(54321);
      return null;
    };
    Future<?> putResult = executorService.submit(producer);

    try {
      putResult.get(10, MILLISECONDS);
      Truth.assert_().fail();
    } catch (ExecutionException ee) {
      assertThat(ee.getCause()).isNotNull();
      assertThat(ee.getCause().getClass()).isEqualTo(IllegalStateException.class);
      assertThat(ee.getCause().getMessage()).contains("already closed");
    }
  }

  @Test
  public void close_onClosedDemultiplexor_shouldFailImmediately() throws Exception {
    int numInputChutes = 2;
    BufferingChute<Integer> chute = new BufferingChute<>(numInputChutes + 1, nanosSource);

    SynchronousDemultiplexor<Integer> demux = new SynchronousDemultiplexor<>(numInputChutes, chute);
    List<ChuteEntrance<Integer>> inputs = demux.inputChutes();

    Callable<Void> closer = () -> {
      inputs.get(0).close();
      inputs.get(1).close();
      return null;
    };
    Future<?> close1Result = executorService.submit(closer);
    close1Result.get(10, MILLISECONDS);

    assertThat(chute.isClosed()).isTrue();

    Callable<Void> closer2 = () -> {
      inputs.get(1).close();
      return null;
    };
    Future<?> close2Result = executorService.submit(closer2);

    try {
      close2Result.get(10, MILLISECONDS);
      Truth.assert_().fail();
    } catch (ExecutionException ee) {
      assertThat(ee.getCause()).isNotNull();
      assertThat(ee.getCause().getClass()).isEqualTo(IllegalStateException.class);
      assertThat(ee.getCause().getMessage()).contains("already closed");
    }
  }
}
