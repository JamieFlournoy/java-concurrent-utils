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

/**
 * This Chute implementaton is based around a BlockingQueue, providing a fixed-size nonzero-capacity
 * buffer holding elements that have been put into the ChuteEntrance but not yet taken from the
 * ChuteExit.
 *
 * @param <E> The type of object that can be sent through the BufferingChute.
 */
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
      // TODO figure out a way to close() without blocking when the buffer is full, while still
      // unblocking take() callers who are blocked waiting for an element when the chute is closed.
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

    if (timeout == 0) {
      return tryTakeNow();
    }

    long currentTimeNanosBeforeTryLock = this.nanosSource.currentTimeNanoPrecision();
    boolean gotTakeLockInTime = this.takeLock.tryLock(timeout, timeoutUnit);
    if (!gotTakeLockInTime) {
      return Optional.empty();
    }
    try {
      long nanosElapsedAcquiringLock =
          this.nanosSource.currentTimeNanoPrecision() - currentTimeNanosBeforeTryLock;
      long timeoutNanos = timeoutUnit.toNanos(timeout);
      long remainingTimeoutNanos = timeoutNanos - nanosElapsedAcquiringLock;

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

  private boolean isNullOrEof(Datum<E> datum) {
    return (datum == null || datum == this.eofDatum);
  }

  @Override
  public Optional<E> tryTakeNow() {
    if (isClosedAndEmpty()) {
      return Optional.empty();
    }

    boolean gotLock = takeLock.tryLock();
    if (!gotLock) {
      return Optional.empty();
    }

    try {
      // Look at it, but don't take it.
      Datum<E> datum = buffer.peek();
      if (isNullOrEof(datum)) {
        return Optional.empty();
      }
      // Remove the head of the queue, which we already have a reference to in datum.
      buffer.poll();
      return Optional.of(datum.wrappedElement);
    } finally {
      takeLock.unlock();
    }
  }

  @Override
  public Optional<E> take() throws InterruptedException {
    if (isClosedAndEmpty()) {
      return Optional.empty();
    }
    takeLock.lockInterruptibly();
    try {
      final Datum<E> takenDatum = buffer.take(); // will not return null
      if (takenDatum == this.eofDatum) {
        buffer.put(takenDatum);
        return Optional.empty();
      }
      return Optional.of(takenDatum.wrappedElement);
    } finally {
      takeLock.unlock();
    }
  }

  @Override
  public boolean isClosedAndEmpty() {
    if (isClosed()) {
      Datum<E> last = buffer.peek();
      if (isNullOrEof(last)) {
        return true;
      }
    }
    return false;
  }
}
