package com.pervasivecode.utils.concurrent.chute;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import java.util.concurrent.atomic.AtomicInteger;
import com.google.common.collect.ImmutableList;

/**
 * A SynchronousMultiplexer exposes multiple ChuteEntrance instances which all feed into a single
 * ChuteEntrance, all with the same element type. When all of the ChuteEntrances are closed, the
 * output ChuteEntrance will be closed.
 *
 * @param <E>
 */
public class SynchronousMultiplexer<E> {
  private final AtomicInteger numInputChutesStillOpen;
  private final ImmutableList<ChuteEntrance<E>> inputChutes;
  private final ChuteEntrance<E> outputChute;

  public SynchronousMultiplexer(int numInputs, ChuteEntrance<E> outputChute) {
    // numInputs=1 is redundant (just use the sole outputChute directly), but will work, so allow
    // callers to do that.
    checkArgument(numInputs > 0, "numInputs must be at least 1.");

    this.numInputChutesStillOpen = new AtomicInteger(numInputs);
    this.outputChute = checkNotNull(outputChute);

    ImmutableList.Builder<ChuteEntrance<E>> inputChutesBuilder = ImmutableList.builder();
    for (int i = 0; i < numInputs; i++) {
      inputChutesBuilder.add(new MultiplexingEntrance());
    }
    this.inputChutes = inputChutesBuilder.build();
  }

  public ImmutableList<ChuteEntrance<E>> inputChutes() {
    return inputChutes;
  }

  private class MultiplexingEntrance implements ChuteEntrance<E> {
    private boolean isClosed = false;

    @Override
    public boolean isClosed() {
      return isClosed || outputChute.isClosed();
    }

    @Override
    public void close() throws InterruptedException {
      if (this.isClosed()) {
        return;
      }
      this.isClosed = true;
      int numLeftOpen = numInputChutesStillOpen.decrementAndGet();
      if (numLeftOpen <= 0) {
        outputChute.close();
      }
    }

    @Override
    public void put(E element) throws InterruptedException {
      if (this.isClosed) {
        throw new IllegalStateException("This ChuteEntrance was already closed.");
      }
      outputChute.put(element);
    }
  }
}
