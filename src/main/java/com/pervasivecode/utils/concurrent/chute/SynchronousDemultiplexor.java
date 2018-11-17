package com.pervasivecode.utils.concurrent.chute;

import java.util.concurrent.atomic.AtomicInteger;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;

public class SynchronousDemultiplexor<E> {
  private final AtomicInteger numInputChutesStillOpen;
  private final ImmutableList<ChuteEntrance<E>> inputChutes;
  private final ChuteEntrance<E> outputChute;

  public SynchronousDemultiplexor(int numInputs, ChuteEntrance<E> outputChute) {
    Preconditions.checkArgument(numInputs > 1, "numInputs must be at least 2.");
    this.numInputChutesStillOpen = new AtomicInteger(numInputs);
    this.outputChute = Preconditions.checkNotNull(outputChute);

    ImmutableList.Builder<ChuteEntrance<E>> inputChutesBuilder = ImmutableList.builder();
    for (int i = 0; i < numInputs; i++) {
      inputChutesBuilder.add(new DemultiplexingEntrance());
    }
    this.inputChutes = inputChutesBuilder.build();
  }

  public ImmutableList<ChuteEntrance<E>> inputChutes() {
    return inputChutes;
  }

  private class DemultiplexingEntrance implements ChuteEntrance<E> {
    private boolean isClosed = false;

    @Override
    public boolean isClosed() {
      return isClosed;
    }

    private void checkIsOpen() {
      if (this.isClosed) {
        throw new IllegalStateException("This ChuteEntrance was already closed.");
      }
    }

    @Override
    public void close() throws InterruptedException {
      checkIsOpen();
      this.isClosed = true;
      int numLeftOpen = numInputChutesStillOpen.decrementAndGet();
      if (numLeftOpen == 0) {
        outputChute.close();
      }
      if (numLeftOpen < 0) {
        // This should really never happen.
        throw new IllegalStateException(
            "All ChuteEntrances for this multiplexor were already closed.");
      }
    }

    @Override
    public void put(E element) throws InterruptedException {
      checkIsOpen();
      outputChute.put(element);
    }
  }
}
