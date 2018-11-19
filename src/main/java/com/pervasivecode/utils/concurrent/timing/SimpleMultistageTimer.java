package com.pervasivecode.utils.concurrent.timing;

import java.util.Objects;
import com.google.common.base.Preconditions;
import com.pervasivecode.utils.time.api.CurrentNanosSource;

public class SimpleMultistageTimer<T extends Enum<?>> extends SimpleActiveTimer {

  public static <T extends Enum<?>> SimpleMultistageTimer<T> createAndStart(
      CurrentNanosSource nanoSource, T timerType) {
    SimpleMultistageTimer<T> timer = new SimpleMultistageTimer<>(nanoSource, timerType);
    timer.startTimer();
    return timer;
  }

  private final T timerType;

  public SimpleMultistageTimer(CurrentNanosSource nanoSource, T timerType) {
    super(nanoSource);
    this.timerType = Preconditions.checkNotNull(timerType);
  }

  @Override
  public final int hashCode() {
    return Objects.hash(timerType, super.hashCode());
  }

  @Override
  public boolean equals(Object other) {
    if (other == this) {
      return true;
    }
    if (!(other instanceof SimpleMultistageTimer)) {
      return false;
    }
    SimpleMultistageTimer<?> otherTimer = (SimpleMultistageTimer<?>) other;
    if (!otherTimer.canEqual(this)) {
      return false;
    }
    return super.equals(otherTimer) //
        && Objects.equals(timerType, otherTimer.timerType);
  }

  @Override
  public boolean canEqual(Object other) {
    return (other instanceof SimpleMultistageTimer);
  }
}
