package com.pervasivecode.utils.concurrent.timing;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import com.pervasivecode.utils.time.api.CurrentNanosSource;

public class SimpleActiveTimer implements StoppableTimer, ListenableTimer, QueryableTimer {
  private enum TimerState {
    NEW, RUNNING, STOPPED
  }

  public static interface StateChangeListener {
    public void stateChanged();
  }

  public static SimpleActiveTimer createAndStart(CurrentNanosSource nanoSource) {
    return new SimpleActiveTimer(nanoSource).startTimer();
  }

  private TimerState currentState = TimerState.NEW;
  protected final CurrentNanosSource nanoSource;
  protected final ArrayList<StateChangeListener> startListeners;
  protected final ArrayList<StateChangeListener> stopListeners;
  protected long startTime = 0L;
  protected long endTime = 0L;

  public SimpleActiveTimer(CurrentNanosSource nanoSource) {
    this.nanoSource = checkNotNull(nanoSource);
    this.startListeners = new ArrayList<StateChangeListener>();
    this.stopListeners = new ArrayList<StateChangeListener>();
  }

  @Override
  public void addTimerStartedListener(StateChangeListener timerStartedListener) {
    startListeners.add(checkNotNull(timerStartedListener));
  }

  @Override
  public void addTimerStoppedListener(StateChangeListener timerStoppedListener) {
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

  @Deprecated
  protected void changeState(TimerState toState, List<StateChangeListener> listenersToNotify) {
    currentState = toState;
    for (StateChangeListener listener : listenersToNotify) {
      listener.stateChanged();
    }
  }

  public SimpleActiveTimer startTimer() {
    checkState(currentState == TimerState.NEW, "Timer can't be started because it's in state '%s'.",
        currentState.name().toLowerCase());
    changeState(TimerState.RUNNING, this.startListeners);
    startTime = nanoSource.currentTimeNanoPrecision();
    return this;
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

  public boolean canEqual(Object other) {
    return (other instanceof SimpleActiveTimer);
  }
}
