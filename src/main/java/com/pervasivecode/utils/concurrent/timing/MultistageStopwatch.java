package com.pervasivecode.utils.concurrent.timing;

import java.time.Duration;

/**
 * This stopwatch manages multiple concurrent timers tracking each stage of a user-defined
 * multi-stage operation, aggregating the resulting duration values.
 * <p>
 * Example: a program is tracking triathletes who are running, swimming, and bicycling. The caller
 * creates an Enum called {@code Sport} with elements {@code RUNNING}, {@code SWIMMING}, and
 * {@code BICYCLING}, and uses this as the parameterized type for a {@code MultistageStopwatch}:
 *
 * <pre>
 * MultistageStopwatch&lt;Sport&gt; stopwatch = ... // implementation constructed here
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
 * Duration averageTime =
 *     cyclingSummary.totalElapsedTime().dividedBy(cyclingSummary.numStartStopCycles());
 * System.out.println(String.format(
 *     "Average bicycling race leg time across all competitors: %d seconds",
 *     averageTime.toSeconds()));
 * </pre>
 *
 * See {@link com.pervasivecode.utils.concurrent.example.TriathlonTimingExample} for a more complete
 * example.
 *
 * @param <T> The enumeration of individual phases that are contained in the measured activities.
 *
 * @see com.pervasivecode.utils.concurrent.example.TriathlonTimingExample
 */
public interface MultistageStopwatch<T extends Enum<?>> {
  /**
   * A summary of the timer activity for one stage of multistage operation.
   */
  public interface TimingSummary {
    /**
     * Get the total amount of time used by all start-stop cycles of all timers belonging to this
     * stopwatch.
     * <p>
     * This value is not likely to be equivalent to wall-clock time, since a MultistageStopwatch
     * instance may be used to produce ActiveTimer instances belonging to separate,
     * concurrently-executing threads.
     *
     * @return The total amount of time used by all timers belonging to this stopwatch.
     */
    public Duration totalElapsedTime();

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
   * @param timertype The type of activity that this timer tracks. Example: BICYCLING.
   * @return An instance of ActiveTimer that can be used to stop the timer when the activity is
   *         complete.
   */
  public StoppableTimer startTimer(T timertype);

  /**
   * Get an Iterable that will provide all of the enum values that this timer handles.
   * <p>
   * Typically this will be an Iterable containing all of the values of the enum, e.g.
   * SomeEnum.values().
   *
   * @return All of the timer types that this timer can track.
   */
  public Iterable<T> getTimerTypes();

  /**
   * Obtain a summary of the timer activity for a given stage of the activity described by the
   * parameterized type T.
   *
   * @param timertype Which stage's summary should be returned. (There is one summary per stage, so
   *        if the enum used as the parameterized type T has four enum values, there will be four
   *        summaries.) Example: BICYCLING.
   *
   * @return The summary of the specified stage's timer activity.
   */
  public TimingSummary summarize(T timertype);
}
