package com.pervasivecode.utils.concurrent.timing;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import com.pervasivecode.utils.time.api.CurrentNanosSource;

/**
 * A timer implementation that is {@link StoppableTimer stoppable}, {@link QueryableTimer
 * queryable}, and {@link ListenableTimer listenable}.
 * <p>
 * This is a "one-shot" timer: it can only be started once, and once it's stopped, it can't be
 * started again.
 * <p>
 * Instances of SimpleActiveTimer are not safe for use by multiple threads.
 */
public final class SimpleActiveTimer implements StoppableTimer, ListenableTimer, QueryableTimer {
  private enum TimerState {
    NEW, RUNNING, STOPPED
  }

  /**
   * Create an instance and start it.
   *
   * @param nanoSource A source of the current time, represented in nanoseconds.
   * @return A SimpleActiveTimer instance that has been started.
   */
  public static SimpleActiveTimer createAndStart(CurrentNanosSource nanoSource) {
    SimpleActiveTimer timer = new SimpleActiveTimer(nanoSource);
    timer.startTimer();
    return timer;
  }

  private TimerState currentState = TimerState.NEW;
  protected final CurrentNanosSource nanoSource;
  protected final ArrayList<StateChangeListener> startListeners;
  protected final ArrayList<StateChangeListener> stopListeners;
  protected long startTime = 0L;
  protected long endTime = 0L;

  /**
   * Create an instance that is not started yet.
   *
   * @param nanoSource A source of the current time, represented in nanoseconds.
   */
  public SimpleActiveTimer(CurrentNanosSource nanoSource) {
    this.nanoSource = checkNotNull(nanoSource);
    this.startListeners = new ArrayList<StateChangeListener>();
    this.stopListeners = new ArrayList<StateChangeListener>();
  }

  @Override
  public void addTimerStartedListener(StateChangeListener timerStartedListener) {
    if (hasBeenStarted()) {
      timerStartedListener.stateChanged();
    }
    startListeners.add(checkNotNull(timerStartedListener));
  }

  @Override
  public void addTimerStoppedListener(StateChangeListener timerStoppedListener) {
    if (isStopped()) {
      timerStoppedListener.stateChanged();
    }
    stopListeners.add(checkNotNull(timerStoppedListener));
  }

  @Override
  public boolean hasBeenStarted() {
    return currentState != TimerState.NEW;
  }

  @Override
  public boolean isRunning() {
    return currentState == TimerState.RUNNING;
  }

  @Override
  public boolean isStopped() {
    return currentState == TimerState.STOPPED;
  }

  private void changeState(TimerState toState, List<StateChangeListener> listenersToNotify) {
    currentState = toState;
    for (StateChangeListener listener : listenersToNotify) {
      listener.stateChanged();
    }
  }

  /**
   * Start the timer.
   *
   * @throws IllegalStateException if the timer has already been started.
   */
  public void startTimer() {
    checkState(currentState == TimerState.NEW, "Timer can't be started because it's in state '%s'.",
        currentState.name().toLowerCase());
    changeState(TimerState.RUNNING, this.startListeners);
    startTime = nanoSource.currentTimeNanoPrecision();
  }

  @Override
  public Duration elapsed() {
    final long nanosElapsed;
    if (currentState == TimerState.STOPPED) {
      nanosElapsed = endTime - startTime;
    } else {
      nanosElapsed = nanoSource.currentTimeNanoPrecision() - startTime;
    }
    checkState(nanosElapsed != 0L, "Operation took 0ns");
    checkState(nanosElapsed >= 0, "Operation took negative time: %s", nanosElapsed);
    return Duration.ofNanos(nanosElapsed);
  }

  @Override
  public Duration stopTimer() {
    checkState(currentState == TimerState.RUNNING,
        "Timer can't be stopped because it was not running.");
    endTime = nanoSource.currentTimeNanoPrecision();
    changeState(TimerState.STOPPED, this.stopListeners);
    return elapsed();
  }

  @Override
  public boolean equals(Object other) {
    if (other == this) {
      return true;
    }
    if (!(other instanceof SimpleActiveTimer)) {
      return false;
    }
    SimpleActiveTimer otherTimer = (SimpleActiveTimer) other;
    return otherTimer.canEqual(this) //
        && (otherTimer.currentState == this.currentState)
        && (otherTimer.startTime == this.startTime) //
        && (otherTimer.endTime == this.endTime)
        && Objects.equals(otherTimer.nanoSource, this.nanoSource)
        && Objects.equals(otherTimer.startListeners, this.startListeners)
        && Objects.equals(otherTimer.stopListeners, this.stopListeners);
  }

  @Override
  public int hashCode() {
    return Objects.hash(currentState, nanoSource, startListeners, stopListeners, startTime,
        endTime);
  }

  /**
   * Determine whether an instance of another class possibly be equal to an instance of this class.
   * <p>
   * This is used to ensure that instances of subclasses of this class are not considered equal to
   * instances of this class, unless the subclass explicitly states that its instances can be
   * treated interchangeably with instances of this class, via its own {@link #canEqual} method.
   * <p>
   * For more info see: <a href=
   * "https://jqno.nl/equalsverifier/errormessages/coverage-is-not-100-percent/#using-canequal">
   * EqualsVerifier Coverage is not 100%: Using canEqual</a> and
   *
   * <a href="https://www.artima.com/lejava/articles/equality.html">How to Write an Equality Method
   * in Java </a>.
   *
   * @param other An object that might have the ability to be equal to an instance of this class.
   * @return True if the other object could be equal to this instance.
   */
  public boolean canEqual(Object other) {
    return (other instanceof SimpleActiveTimer);
  }
}
