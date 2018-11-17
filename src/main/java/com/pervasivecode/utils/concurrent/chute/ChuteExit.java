package com.pervasivecode.utils.concurrent.chute;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

import javax.annotation.Nonnull;

public interface ChuteExit<E> {
  /**
   * Block for up to a specified amount of time, taking an element. If the timeout expires, or if
   * the channel is closed while the caller is blocked waiting for an element, the return value with
   * be Optional.empty().
   */
  public @Nonnull Optional<E> tryTake(long timeout, @Nonnull TimeUnit timeoutUnit)
      throws InterruptedException;

  /**
   * Take an element if it's available immediately. Otherwise, Optional.empty() is returned. 
   */
  public @Nonnull Optional<E> tryTakeNow();

  /**
   * Block for an unlimited amount of time, taking an element.
   * <p>
   * If the caller blocks on an empty channel and the channel is closed during that time, the return
   * value with be Optional.empty().
   */
  public @Nonnull Optional<E> take() throws InterruptedException;

  /**
   * Return true if both of the following are true:
   * <ul>
   * <li>the channel has been closed (so that new elements cannot be added), and
   * <li>there are no remaining elements in the channel.
   * </ul>
   * This means that this channel will never return an element again.
   * <p>
   * Otherwise, this method will return false, which means that this channel <i>may</i> return
   * elements in the future (it may be empty but not closed). A false return value is not a
   * guarantee that an element will be available in the future, though: if the channel is closed
   * and other threads take all the elements out, for example.
   */
  public boolean isClosedAndEmpty();
}
