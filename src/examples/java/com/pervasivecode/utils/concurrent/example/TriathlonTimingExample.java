package com.pervasivecode.utils.concurrent.example;

import java.io.PrintWriter;
import java.text.MessageFormat;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import com.pervasivecode.utils.concurrent.timing.MultistageStopwatch;
import com.pervasivecode.utils.concurrent.timing.MultistageStopwatch.TimingSummary;
import com.pervasivecode.utils.concurrent.timing.SimpleMultistageStopwatch;
import com.pervasivecode.utils.concurrent.timing.StoppableTimer;
import com.pervasivecode.utils.time.DurationFormat;
import com.pervasivecode.utils.time.DurationFormats;
import com.pervasivecode.utils.time.DurationFormatter;
import com.pervasivecode.utils.time.testing.FakeNanoSource;

/**
 * Example of how to use {@link MultistageStopwatch}, {@link TimingSummary}, and
 * {@link DurationFormatter} to time multiple stages of activity by multiple concurrent actors, and
 * to represent the captured timing information in a human-readable form.
 */
public class TriathlonTimingExample implements ExampleApplication {
  public enum Sport {
    RUNNING, SWIMMING, BICYCLING
  }

  private static final DurationFormatter DURATION_FORMATTER =
      new DurationFormatter(DurationFormat.builder(DurationFormats.getUsDefaultInstance())
          .setSmallestUnit(ChronoUnit.MILLIS).setNumFractionalDigits(3).build());

  private static class UsTimingSummaryFormatter {
    private static final MessageFormat MESSAGE_FORMAT =
        new MessageFormat("Timing summary for {0}: {1} ({2} avg, "
            + "{3,choice,1#1 cycle|1<{3,number,integer} cycles})");

    public String format(Sport sport, TimingSummary ts) {
      String sportName = sport.name().toLowerCase();
      if (ts.numStartStopCycles() == 0) {
        return String.format("Timing summary for %s: 0s (0 cycles)", sportName);
      }
      long numCycles = ts.numStartStopCycles();
      Duration avgTimePerCycle = ts.totalElapsedTime().dividedBy(numCycles);

      String totalDurationFormatted = DURATION_FORMATTER.format(ts.totalElapsedTime());
      String avgDurationFormatted = DURATION_FORMATTER.format(avgTimePerCycle);
      Object[] args = {sportName, totalDurationFormatted, avgDurationFormatted, numCycles};
      return MESSAGE_FORMAT.format(args);
    }
  }

  private final UsTimingSummaryFormatter summaryFormatter;

  public TriathlonTimingExample() {
    this.summaryFormatter = new UsTimingSummaryFormatter();
  }

  @Override
  public void runExample(PrintWriter output) throws Exception {
    FakeNanoSource nanoSource = new FakeNanoSource();
    MultistageStopwatch<Sport> stopwatch =
        new SimpleMultistageStopwatch<>(nanoSource, Sport.values());

    StoppableTimer r1 = stopwatch.startTimer(Sport.RUNNING);
    StoppableTimer r2 = stopwatch.startTimer(Sport.RUNNING);
    StoppableTimer r3 = stopwatch.startTimer(Sport.RUNNING);

    addSeconds(nanoSource, 3);

    Duration r1Time = r1.stopTimer();
    addSeconds(nanoSource, 1);
    Duration r2Time = r2.stopTimer();
    addSeconds(nanoSource, 1);
    Duration r3Time = r3.stopTimer();

    printIndividualTimes(Sport.RUNNING, output, r1Time, r2Time, r3Time);
    printTimingSummaries(stopwatch, output);
    output.println();

    StoppableTimer s1 = stopwatch.startTimer(Sport.SWIMMING);
    StoppableTimer s2 = stopwatch.startTimer(Sport.SWIMMING);
    StoppableTimer s3 = stopwatch.startTimer(Sport.SWIMMING);

    addSeconds(nanoSource, 7);

    Duration s1Time = s1.stopTimer();
    addSeconds(nanoSource, 1);
    Duration s2Time = s2.stopTimer();
    addSeconds(nanoSource, 1);
    Duration s3Time = s3.stopTimer();

    printIndividualTimes(Sport.SWIMMING, output, s1Time, s2Time, s3Time);
    printTimingSummaries(stopwatch, output);
    output.println();

    StoppableTimer b1 = stopwatch.startTimer(Sport.BICYCLING);
    StoppableTimer b2 = stopwatch.startTimer(Sport.BICYCLING);
    StoppableTimer b3 = stopwatch.startTimer(Sport.BICYCLING);

    addSeconds(nanoSource, 2);

    Duration b1Time = b1.stopTimer();
    addSeconds(nanoSource, 1);
    Duration b2Time = b2.stopTimer();
    addSeconds(nanoSource, 1);
    Duration b3Time = b3.stopTimer();

    printIndividualTimes(Sport.BICYCLING, output, b1Time, b2Time, b3Time);
    printTimingSummaries(stopwatch, output);
  }

  private static void addSeconds(FakeNanoSource nanoSource, int numSeconds) {
    nanoSource.incrementTimeNanos(Duration.ofSeconds(numSeconds).toNanos());
  }

  private static void printIndividualTimes(Sport sport, PrintWriter output, Duration... durations) {
    output.print("Event times for " + sport.name().toLowerCase() + ": ");

    boolean onFirstTimer = true;
    for (Duration d : durations) {
      if (onFirstTimer) {
        onFirstTimer = false;
      } else {
        output.print(", ");
      }
      output.print(DURATION_FORMATTER.format(d));
    }
    output.println();
  }

  private void printTimingSummary(MultistageStopwatch<Sport> stopwatch, Sport sport,
      PrintWriter output) {
    output.println(summaryFormatter.format(sport, stopwatch.summarize(sport)));
  }

  private void printTimingSummaries(MultistageStopwatch<Sport> stopwatch, PrintWriter output) {
    printTimingSummary(stopwatch, Sport.RUNNING, output);
    printTimingSummary(stopwatch, Sport.SWIMMING, output);
    printTimingSummary(stopwatch, Sport.BICYCLING, output);
  }
}
