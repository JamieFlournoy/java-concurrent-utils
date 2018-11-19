package com.pervasivecode.utils.concurrent.chute;

import javax.annotation.Nonnull;

/**
 * The input side of a chute, allowing callers to put elements into the chute, or to close the chute
 * so that no more elements can be put into it.
 * 
 * @param <E> The type of object that can be put into the chute.
 */
public interface ChuteEntrance<E> {
  /**
   * Close the entrance of the Chute. After this has been called, no more elements will be accepted,
   * but any elements that have not yet been taken from the corresponding ChuteExit will still be
   * available.
   *
   * @throws InterruptedException if the calling thread was interrupted when closing the Chute.
   */
  public void close() throws InterruptedException;

  /**
   * Returns true if the entrance to the Chute has been closed. This does not necessarily mean that
   * all of the elements that were put into the Chute have been removed yet, though.
   *
   * @see ChuteExit#isClosedAndEmpty() for a method that also verifies that all elements have been
   *      taken from the ChuteExit.
   * @return whether the ChuteEntrance has been closed.
   */
  public boolean isClosed();

  /**
   * Put an element into the chute, blocking as long as needed.
   *
   * @param element An element to put in the chute.
   * @throws InterruptedException if the blocked thread is interrupted.
   * @throws IllegalStateException if the Chute is already closed.
   */
  public void put(@Nonnull E element) throws InterruptedException;
}
