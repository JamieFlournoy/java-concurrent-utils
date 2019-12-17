package com.pervasivecode.utils.concurrent.timing;

import static com.google.common.truth.Truth.assertThat;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;
import com.google.common.truth.Truth;
import com.pervasivecode.utils.concurrent.timing.ListenableTimer.StateChangeListener;
import com.pervasivecode.utils.time.testing.FakeNanoSource;
import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

public class SimpleActiveTimerTest {

  @Test
  public void createAndStart_shouldStartTimer() {
    FakeNanoSource nanoSource = new FakeNanoSource();
    SimpleActiveTimer timer = SimpleActiveTimer.createAndStart(nanoSource);
    assertThat(timer.isRunning()).isTrue();
  }

  // --------------------------------------------------------------------------
  //
  // Tests for constructor
  //
  // --------------------------------------------------------------------------

  @SuppressWarnings("unused")
  @Test(expected = NullPointerException.class)
  public void constructor_withNullNanoSource_shouldThrow() {
    new SimpleActiveTimer(null);
  }

  @Test
  public void constructor_shouldCreateUnstartedTimer() {
    FakeNanoSource nanoSource = new FakeNanoSource();
    SimpleActiveTimer timer = new SimpleActiveTimer(nanoSource);
    assertThat(timer.hasBeenStarted()).isFalse();
    assertThat(timer.isRunning()).isFalse();
    assertThat(timer.isStopped()).isFalse();
  }

  // --------------------------------------------------------------------------
  //
  // Tests for startTimer
  //
  // --------------------------------------------------------------------------

  @Test
  public void startTimer_withRunningTimer_shouldThrow() {
    FakeNanoSource nanoSource = new FakeNanoSource();
    SimpleActiveTimer timer = new SimpleActiveTimer(nanoSource);
    timer.startTimer();
    try {
      timer.startTimer();
      Truth.assert_().fail("Expected exception when trying to start an already-started timer.");
    } catch (IllegalStateException ise) {
      assertThat(ise).hasMessageThat().contains("can't be started");
      assertThat(ise).hasMessageThat().contains("running");
    }
  }

  @Test
  public void startTimer_withStoppedTimer_shouldThrow() {
    FakeNanoSource nanoSource = new FakeNanoSource();
    SimpleActiveTimer timer = new SimpleActiveTimer(nanoSource);
    timer.startTimer();
    timer.stopTimer();
    try {
      timer.startTimer();
      Truth.assert_().fail("Expected exception when trying to start an already-stopped timer.");
    } catch (IllegalStateException ise) {
      assertThat(ise).hasMessageThat().contains("can't be started");
      assertThat(ise).hasMessageThat().contains("stopped");
    }
  }

  @Test
  public void startTimer_withNewTimer_shouldStartTheTimer() {
    FakeNanoSource nanoSource = new FakeNanoSource();
    SimpleActiveTimer timer = new SimpleActiveTimer(nanoSource);
    timer.startTimer();
    assertThat(timer.hasBeenStarted()).isTrue();
    assertThat(timer.isRunning()).isTrue();
    assertThat(timer.isStopped()).isFalse();
  }

  @Test
  public void startTimer_withNewTimer_shouldNotifyStartListeners() {
    FakeNanoSource nanoSource = new FakeNanoSource();
    SimpleActiveTimer timer = new SimpleActiveTimer(nanoSource);
    AtomicInteger timesStartListenerWasCalled = new AtomicInteger(0);
    AtomicInteger timesStopListenerWasCalled = new AtomicInteger(0);
    StateChangeListener startListener = () -> timesStartListenerWasCalled.incrementAndGet();
    StateChangeListener stopListener = () -> timesStopListenerWasCalled.incrementAndGet();
    timer.addTimerStartedListener(startListener);
    timer.addTimerStoppedListener(stopListener);
    timer.startTimer();
    assertThat(timesStartListenerWasCalled.get()).isEqualTo(1);
    assertThat(timesStopListenerWasCalled.get()).isEqualTo(0);
  }

  // --------------------------------------------------------------------------
  //
  // Tests for elapsed
  //
  // --------------------------------------------------------------------------

  @Test
  public void elapsed_withRunningTimer_shouldReturnElapsedTimeSoFar() {
    FakeNanoSource nanoSource = new FakeNanoSource();
    SimpleActiveTimer timer = SimpleActiveTimer.createAndStart(nanoSource);
    assertThat(timer.isRunning()).isTrue();

    nanoSource.incrementTimeNanos(1078);
    Duration nanosSoFar = timer.elapsed();
    assertThat(nanosSoFar.toNanos()).isEqualTo(1079);
  }

  @Test
  public void elapsed_withStoppedTimer_shouldReturnElapsedDuration() {
    FakeNanoSource nanoSource = new FakeNanoSource();
    SimpleActiveTimer timer = SimpleActiveTimer.createAndStart(nanoSource);
    nanoSource.incrementTimeNanos(1_000_000_000L);
    timer.stopTimer();
    Duration elapsed = timer.elapsed();
    assertThat(elapsed).isAtLeast(Duration.ofSeconds(1));
    assertThat(elapsed).isAtMost(Duration.ofMillis(1001));
  }

  // --------------------------------------------------------------------------
  //
  // Tests for stopTimer
  //
  // --------------------------------------------------------------------------

  @Test
  public void stopTimer_whenTimerIsRunning_shouldChangeStateToStopped() {
    FakeNanoSource nanoSource = new FakeNanoSource();
    SimpleActiveTimer timer = SimpleActiveTimer.createAndStart(nanoSource);
    timer.stopTimer();
    assertThat(timer.hasBeenStarted()).isTrue();
    assertThat(timer.isRunning()).isFalse();
    assertThat(timer.isStopped()).isTrue();
  }

  @Test
  public void stopTimer_whenTimerIsRunning_shouldNotifyStoppedListeners() {
    FakeNanoSource nanoSource = new FakeNanoSource();
    SimpleActiveTimer timer = SimpleActiveTimer.createAndStart(nanoSource);

    AtomicInteger timesStartListenerWasCalled = new AtomicInteger(0);
    AtomicInteger timesStopListenerWasCalled = new AtomicInteger(0);
    StateChangeListener startListener = () -> timesStartListenerWasCalled.incrementAndGet();
    StateChangeListener stopListener = () -> timesStopListenerWasCalled.incrementAndGet();
    timer.addTimerStartedListener(startListener);
    timer.addTimerStoppedListener(stopListener);
    assertThat(timesStartListenerWasCalled.get()).isEqualTo(1);
    timer.stopTimer();
    assertThat(timesStartListenerWasCalled.get()).isEqualTo(1);
    assertThat(timesStopListenerWasCalled.get()).isEqualTo(1);
  }

  @Test
  public void stopTimer_whenTimerIsRunning_shouldReturnElapsedDuration() {
    FakeNanoSource nanoSource = new FakeNanoSource();
    SimpleActiveTimer timer = SimpleActiveTimer.createAndStart(nanoSource);
    nanoSource.incrementTimeNanos(1_000_000_000L);
    Duration elapsed = timer.stopTimer();
    assertThat(elapsed).isAtLeast(Duration.ofSeconds(1));
    assertThat(elapsed).isAtMost(Duration.ofMillis(1001));
  }

  @Test
  public void stopTimer_whenTimerIsRunning_andNanoSourceGivesCrazyValues_shouldThrow() {
    FakeNanoSource nanoSource = new FakeNanoSource();
    SimpleActiveTimer timer = SimpleActiveTimer.createAndStart(nanoSource);
    nanoSource.incrementTimeNanos(-1);
    try {
      timer.stopTimer();
      Truth.assert_().fail("Expected IllegalStateException since elapsed nanos = 0.");
    } catch (IllegalStateException ise) {
      assertThat(ise).hasMessageThat().contains("0ns");
    }

    timer = SimpleActiveTimer.createAndStart(nanoSource);
    nanoSource.incrementTimeNanos(-1000);
    try {
      timer.stopTimer();
      Truth.assert_().fail("Expected IllegalStateException since elapsed nanos < 0.");
    } catch (IllegalStateException ise) {
      assertThat(ise).hasMessageThat().contains("negative time");
    }
  }

  @Test
  public void stopTimer_whenTimerIsNotStartedYet_shouldThrow() {
    FakeNanoSource nanoSource = new FakeNanoSource();
    SimpleActiveTimer timer = new SimpleActiveTimer(nanoSource);
    assertThat(timer.hasBeenStarted()).isFalse();
    try {
      timer.stopTimer();
      Truth.assert_().fail("Expected IllegalStateException.");
    } catch (IllegalStateException ise) {
      assertThat(ise).hasMessageThat().contains("not running");
    }
  }

  @Test
  public void stopTimer_whenTimerIsAlreadyStopped_shouldThrow() {
    FakeNanoSource nanoSource = new FakeNanoSource();
    SimpleActiveTimer timer = new SimpleActiveTimer(nanoSource);
    assertThat(timer.hasBeenStarted()).isFalse();
    timer.startTimer();
    timer.stopTimer();
    assertThat(timer.isStopped()).isTrue();
    try {
      timer.stopTimer();
      Truth.assert_().fail("Expected IllegalStateException.");
    } catch (IllegalStateException ise) {
      assertThat(ise).hasMessageThat().contains("not running");
    }
  }

  // --------------------------------------------------------------------------
  //
  // Tests for addTimerStartedListener
  //
  // --------------------------------------------------------------------------

  @Test
  public void addTimerStartedListener_whenTimerIsAlreadyStarted_shouldRunListenerImmediately() {
    SimpleActiveTimer timer = SimpleActiveTimer.createAndStart(new FakeNanoSource());
    assertThat(timer.hasBeenStarted()).isTrue();

    AtomicInteger timesStartListenerWasCalled = new AtomicInteger(0);
    StateChangeListener startListener = () -> timesStartListenerWasCalled.incrementAndGet();
    timer.addTimerStartedListener(startListener);
    assertThat(timesStartListenerWasCalled.get()).isEqualTo(1);
  }

  // --------------------------------------------------------------------------
  //
  // Tests for addTimerStoppedListener
  //
  // --------------------------------------------------------------------------

  @Test
  public void addTimerStoppedListener_whenTimerIsAlreadyStopped_shouldRunListenerImmediately() {
    SimpleActiveTimer timer = SimpleActiveTimer.createAndStart(new FakeNanoSource());
    timer.stopTimer();
    assertThat(timer.isStopped()).isTrue();

    AtomicInteger timesStopListenerWasCalled = new AtomicInteger(0);
    StateChangeListener stopListener = () -> timesStopListenerWasCalled.incrementAndGet();
    timer.addTimerStoppedListener(stopListener);
    assertThat(timesStopListenerWasCalled.get()).isEqualTo(1);
  }

  // --------------------------------------------------------------------------
  //
  // Tests for equals
  //
  // --------------------------------------------------------------------------

  @Test
  public void equals_shouldWork() {
    EqualsVerifier.forClass(SimpleActiveTimer.class).suppress(Warning.NONFINAL_FIELDS).verify();
  }
}
