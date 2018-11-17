package com.pervasivecode.utils.concurrent.chute;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.annotation.Nonnull;
import com.pervasivecode.utils.time.api.CurrentNanosSource;

public class BufferingChute<E> implements Chute<E> {
  private static class Datum<L> {
    public L wrappedElement;

    /**
     * @param elementToWrap should only be null for the eofDatum instance.
     */
    public Datum(L elementToWrap) {
      wrappedElement = elementToWrap;
    }
  }

  private final ArrayBlockingQueue<Datum<E>> buffer;
  private final CurrentNanosSource nanosSource;

  private final AtomicBoolean isOpen = new AtomicBoolean(true);
  private Datum<E> eofDatum;

  // Lock used to guard against adding a new, non-EOF datum when the chute is closed or closing:
  private final Lock putLock = new ReentrantLock();

  // Lock used to guard against accidentally taking the EOF datum out of the buffer when it's the
  // last element.
  private final Lock takeLock = new ReentrantLock();

  public BufferingChute(int bufferSize, CurrentNanosSource nanosSource) {
    checkArgument(bufferSize > 0, "Buffer size must be at least 1.");
    this.buffer = new ArrayBlockingQueue<>(bufferSize);

    this.nanosSource = checkNotNull(nanosSource);

    E eofValue = null;
    this.eofDatum = new Datum<>(eofValue);
  }

  //
  // Methods from ChuteEntrance
  //

  @Override
  public void close() throws InterruptedException {
    putLock.lockInterruptibly();
    try {
      this.isOpen.set(false);
      buffer.put(eofDatum);
    } finally {
      putLock.unlock();
    }
  }

  @Override
  public boolean isClosed() {
    return !this.isOpen.get();
  }

  @Override
  public void put(@Nonnull E element) throws InterruptedException {
    checkNotNull(element, "Null elements are not allowed");
    putLock.lockInterruptibly();
    try {
      if (!isClosed()) {
        buffer.put(new Datum<>(element));
      } else {
        throw new IllegalStateException("Channel is already closed.");
      }
    } finally {
      putLock.unlock();
    }
  }

  //
  // Methods from ChuteExit
  //

  @Override
  public Optional<E> tryTake(long timeout, TimeUnit timeoutUnit) throws InterruptedException {
    if (isClosedAndEmpty()) {
      return Optional.empty();
    }

    long currentTimeNanosBeforeTryLock = this.nanosSource.currentTimeNanoPrecision();
    boolean gotTakeLockInTime = this.takeLock.tryLock(timeout, timeoutUnit);
    if (!gotTakeLockInTime) {
      return Optional.empty();
    }
    try {
      if (isClosedAndEmpty()) {
        return Optional.empty();
      }

      long nanosElapsedAcquiringLock =
          this.nanosSource.currentTimeNanoPrecision() - currentTimeNanosBeforeTryLock;
      long timeoutNanos = timeoutUnit.toNanos(timeout);
      long remainingTimeoutNanos = timeoutNanos - nanosElapsedAcquiringLock;

      // Important: if timeoutNanos == 0 we still need to get the last datum.
      if (timeoutNanos != 0 && remainingTimeoutNanos <= 0) {
        return Optional.empty();
      }

      Datum<E> datum = buffer.poll(remainingTimeoutNanos, TimeUnit.NANOSECONDS);

      if (datum == null) {
        // Timed out waiting for a datum, but the channel was not closed yet.
        return Optional.empty();
      }

      if (datum == this.eofDatum) {
        // Oops; we didn't mean to take that instance. Put it back.
        buffer.put(datum);
        // The channel is closed.
        return Optional.empty();
      }

      return Optional.of(datum.wrappedElement);
    } finally {
      takeLock.unlock();
    }
  }

  @Override
  public Optional<E> tryTakeNow() {
    if (isClosedAndEmpty()) {
      return Optional.empty();
    }

    // Take a look and see if we can return early, without locking takeLock.
    Datum<E> datum = buffer.peek();
    if (datum == null) {
      return Optional.empty();
    }
    if (datum == this.eofDatum) {
      return Optional.empty();
    }

    boolean gotLock = takeLock.tryLock();
    if (!gotLock) {
      return Optional.empty();
    }

    try {
      // Look at it, but don't take it.
      datum = buffer.peek();
      if (datum == null) {
        return Optional.empty();
      }
      if (datum == this.eofDatum) {
        return Optional.empty();
      }
      // Take it.
      datum = buffer.poll();
      if (datum == null) {
        return Optional.empty();
      }
      if (datum == this.eofDatum) {
        throw new IllegalStateException("Bug: should not be able to take eofDatum at this point.");
      }
      return Optional.of(datum.wrappedElement);
    } finally {
      takeLock.unlock();
    }
  }

  @Override
  public Optional<E> take() throws InterruptedException {
    // Take a look and see if we can return early, without locking takeLock.
    Datum<E> datum = buffer.peek();
    if (datum == this.eofDatum) {
      return Optional.empty();
    }

    takeLock.lockInterruptibly();
    try {
      datum = buffer.peek();
      if (datum == this.eofDatum) {
        return Optional.empty();
      }
      datum = buffer.take();
      if (datum == this.eofDatum) {
        // Oops; we didn't mean to take that instance. Put it back.
        buffer.put(datum);
        // The channel is closed.
        return Optional.empty();
      }
      if (datum == null) {
        throw new IllegalStateException("Bug: should never get a null datum from take().");
      }
      return Optional.of(datum.wrappedElement);
    } finally {
      takeLock.unlock();
    }
  }

  @Override
  public boolean isClosedAndEmpty() {
    if (isClosed()) {
      Datum<E> last = buffer.peek();
      if (last == null || last == this.eofDatum) {
        return true;
      }
    }
    return false;
  }
}
