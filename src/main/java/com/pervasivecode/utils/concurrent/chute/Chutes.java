package com.pervasivecode.utils.concurrent.chute;

import static com.google.common.base.Preconditions.checkNotNull;
import java.util.Iterator;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

public class Chutes {
  private Chutes() {}

  private static class OptionalTransformer<I, O> implements Function<Optional<I>, Optional<O>> {
    private final Function<I, O> transformer;

    public OptionalTransformer(Function<I, O> transformer) {
      this.transformer = checkNotNull(transformer);
    }

    @Override
    public Optional<O> apply(Optional<I> maybeInputElement) {
      return maybeInputElement.isPresent() ? Optional.of(transformer.apply(maybeInputElement.get()))
          : Optional.empty();
    }
  }


  private static class TransformingExit<S1, S2> implements ChuteExit<S2> {
    private final ChuteExit<S1> supplier;
    private final OptionalTransformer<S1, S2> transformer;

    public TransformingExit(ChuteExit<S1> supplier, Function<S1, S2> transformer) {
      this.supplier = checkNotNull(supplier);
      this.transformer = new OptionalTransformer<>(checkNotNull(transformer));
    }

    @Override
    public Optional<S2> tryTake(long timeout, TimeUnit timeoutUnit) throws InterruptedException {
      return transformer.apply(supplier.tryTake(timeout, timeoutUnit));
    }

    @Override
    public Optional<S2> tryTakeNow() {
      return transformer.apply(supplier.tryTakeNow());
    }

    @Override
    public Optional<S2> take() throws InterruptedException {
      return transformer.apply(supplier.take());
    }

    @Override
    public boolean isClosedAndEmpty() {
      return supplier.isClosedAndEmpty();
    }
  }


  private static class TransformingEntrance<S1, S2> implements ChuteEntrance<S1> {
    private final ChuteEntrance<S2> receiver;
    private final Function<S1, S2> transformer;

    public TransformingEntrance(ChuteEntrance<S2> receiver, Function<S1, S2> transformer) {
      this.receiver = checkNotNull(receiver);
      this.transformer = checkNotNull(transformer);
    }

    @Override
    public void close() throws InterruptedException {
      receiver.close();
    }

    @Override
    public boolean isClosed() {
      return receiver.isClosed();
    }

    @Override
    public void put(S1 element) throws InterruptedException {
      receiver.put(transformer.apply(element));
    }
  }


  private static class ChuteIterator<T> implements Iterator<T> {
    private boolean interrupted = false;
    private final ChuteExit<T> source;
    private Optional<T> buffer;

    public ChuteIterator(ChuteExit<T> source) {
      this.source = checkNotNull(source);
      this.buffer = Optional.empty();
    }

    private void maybeFillBuffer() {
      if (buffer.isPresent() || interrupted || source.isClosedAndEmpty()) {
        return;
      }
      try {
        buffer = source.take();
      } catch (@SuppressWarnings("unused") InterruptedException e) {
        // Stop iterating. hasNext will return false and next() will throw from this point on.
        interrupted = true;
      }
    }

    @Override
    public boolean hasNext() {
      maybeFillBuffer();
      return buffer.isPresent();
    }

    @Override
    public T next() {
      maybeFillBuffer();
      T bufferedElement = buffer.get();
      buffer = Optional.empty();
      return bufferedElement;
    }
  }


  private static class ChuteIterableAdapter<T> implements Iterable<T> {
    private final ChuteExit<T> source;

    public ChuteIterableAdapter(ChuteExit<T> source) {
      this.source = checkNotNull(source);
    }

    @Override
    public Iterator<T> iterator() {
      return new ChuteIterator<>(source);
    }
  }


  public static <T> Iterable<T> asIterable(ChuteExit<T> source) {
    return new ChuteIterableAdapter<>(source);
  }

  public static <T, V> ChuteEntrance<T> transformingEntrance(ChuteEntrance<V> receiver,
      Function<T, V> transformer) {
    return new TransformingEntrance<T, V>(receiver, transformer);
  }

  public static <T, V> ChuteExit<V> transformingExit(ChuteExit<T> supplier,
      Function<T, V> transformer) {
    return new TransformingExit<T, V>(supplier, transformer);
  }
}
