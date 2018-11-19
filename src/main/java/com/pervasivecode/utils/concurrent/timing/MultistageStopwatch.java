package com.pervasivecode.utils.concurrent.timing;

import java.util.concurrent.TimeUnit;

/**
 * This stopwatch manages multiple concurrent timers tracking each stage of a user-defined
 * multi-stage operation, aggregating the resulting duration values.
 * <p>
 * Example: a program is tracking triathletes who are running, swimming, and bicycling. The caller
 * creates an Enum called {@code Sport} with elements {@code RUNNING}, {@code SWIMMING}, and
 * {@code BICYCLING}, and uses this as the parameterized type for a
 * {@code MultistageConcurrentStopwatch}:
 * 
 * <pre>
 * MultistageConcurrentStopwatch&lt;Sport&gt; stopwatch = ... // implementation constructed here
 * ...
 * // Now, time one athlete who just started a particular event.
 * ActiveTimer eventTimer = stopwatch.startTimer(BICYCLING);
 * ...
 * // Later, the athlete completes that event, so stop the timer.
 * eventTimer.stop();
 * ...
 * // All of the athletes have finished bicycling, so get a summary of that event.
 * TimingSummary cyclingSummary = stopwatch.summarize(BICYCLING);
 * System.out.println("Total amount of time spent bicycling: " + cyclingSummary.totalElapsedTime());
 * System.out.println("Total number of cycling race legs: " + cyclingSummary.numStartStopCycles());
 *
 * long averageSeconds = cyclingSummary.totalElapsedTime() / cyclingSummary.numStartStopCycles();
 * System.out.println(String.format(
 *     "Average bicycling race leg time across all competitors: %d seconds", averageSeconds));
 * </pre>
 * 
 * @param <T> The enumeration of individual phases that are contained in the measured activities.
 */
public interface MultistageStopwatch<T extends Enum<?>> {
  public interface TimingSummary {
    /**
     * Get the total amount of time used by all start-stop cycles of all timers belonging to this
     * stowpatch. The value is rounded to the nearest integer of whatever units are specified in the
     * timeUnit parameter.
     * <p>
     * This value is not likely to be equivalent to wall-clock time, since a MultistageStopwatch
     * instance may be used to produce ActiveTimer instances belonging to separate,
     * concurrently-executing threads.
     */
    // TODO change the type of timeUnit from TimeUnit to ChronoUnit.
    public long totalElapsedTime(TimeUnit timeUnit);

    /**
     * Get the number of times that all timers belonging to this stopwatch have been started and
     * then stopped.
     * 
     * @return The number of start-then-stop cycles.
     */
    public long numStartStopCycles();
  }

  /**
   * Start a timer tracking one instance of the specified activity.
   * 
   * @param timertype The type of activity that this timer tracks.
   * @return An instance of ActiveTimer that can be used to stop the timer when the activity is
   *         complete.
   */
  public StoppableTimer startTimer(T timertype);

  /**
   * Get a total of the number of elapsed nanoseconds across all timers of the specified type.
   * 
   * @param timertype The type of activity whose total elapsed time is desired.
   * @return A quantity in units of nanoseconds.
   * @see TimingSummary#totalElapsedTime(TimeUnit) for a convenient way to obtain values rounded to
   *      larger units than nanoseconds.
   */
  public long getTotalElapsedNanos(T timertype);

  /**
   * Get an Iterable that will provide all of the timer types
   * 
   * @return
   */
  public Iterable<T> getTimerTypes();

  public TimingSummary summarize(T timertype);
}
