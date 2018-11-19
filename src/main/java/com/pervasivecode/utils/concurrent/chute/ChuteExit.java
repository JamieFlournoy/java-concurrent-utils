package com.pervasivecode.utils.concurrent.chute;

import java.util.Optional;
import java.util.concurrent.TimeUnit;
import javax.annotation.Nonnull;

/**
 * The output side of a chute, allowing callers to take elements from a chute until it is closed.
 * 
 * @param <E> The type of object that can be taken from the chute.
 */
public interface ChuteExit<E> {
  /**
   * Block for up to a specified amount of time, taking an element. If the timeout expires, or if
   * the channel is closed while the caller is blocked waiting for an element, the return value with
   * be Optional.empty().
   *
   * @param timeout The magnitute of the timeout value.
   * @param timeoutUnit The units of the timeout value.
   * @return An element (if one was available in time), or Optional.empty() otherwise.
   * @throws InterruptedException if the calling thread is interrupted while waiting.
   */
  public @Nonnull Optional<E> tryTake(long timeout, @Nonnull TimeUnit timeoutUnit)
      throws InterruptedException;

  /**
   * Take an element if it's available immediately. Otherwise, Optional.empty() is returned.
   * 
   * @return An immediately-available element, or Optional.empty if no element was available.
   */
  public @Nonnull Optional<E> tryTakeNow();

  /**
   * Block for an unlimited amount of time, taking an element.
   * <p>
   * If the caller blocks on an empty channel and the channel is closed during that time, the return
   * value with be Optional.empty().
   * 
   * @return An element if one was available before the ChuteExit was closed, or Optional.empty if
   *         none became available before it was closed.
   * @throws InterruptedException if the calling thread is interrupted while waiting.
   */
  public @Nonnull Optional<E> take() throws InterruptedException;

  /**
   * Return true if both of the following are true:
   * <ul>
   * <li>the chute has been closed (so that new elements cannot be added), and
   * <li>there are no remaining elements in the channel.
   * </ul>
   * This means that this chute will never return an element again.
   * <p>
   * Otherwise, this method will return false, which means that this chute <i>may</i> return
   * elements in the future (it may be empty but not closed). A false return value is not a
   * guarantee that an element will be available in the future, though: if the chute is closed and
   * other threads take all the elements out, for example.
   * 
   * @return Whether the chute is both closed and empty.
   */
  public boolean isClosedAndEmpty();
}
