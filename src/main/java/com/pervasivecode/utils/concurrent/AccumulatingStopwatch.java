package com.pervasivecode.utils.concurrent;

import java.util.concurrent.TimeUnit;
import com.pervasivecode.utils.stats.histogram.Histogram;
import java.time.Duration;

public interface AccumulatingStopwatch<T extends Enum<?>> {
  public interface ActiveTimer {
    // TODO move this method to AccumulatingStopwatch (but keep most of the impl in ActiveTimerImpl)
    public Duration stopTimer();
  }

  public interface TimingSummary {
    /**
     * Total time used by all start/stop cycles of timers belonging to this stowpatch. The value is
     * rounded to the nearest integer of whatever units are specified in the timeUnit parameter.
     * <p>
     * This value is not necessarily equivalent to wall-clock time since a Stopwatch instance may be
     * used to produce ActiveTimer instances belonging to separate, concurrently-executing threads.
     */
    // TODO change from TimeUnit to ChronoUnit.
    public long totalElapsedTime(TimeUnit timeUnit);

    public long numStartStopCycles();
  }

  public ActiveTimer startTimer(T timertype);

  public long getTotalElapsedNanos(T timertype);

  public Iterable<T> getTimerTypes();

  public TimingSummary summarize(T timertype);

  public Histogram<Long> timingHistogram(T timertype);

  public String getName();
}
