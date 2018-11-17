package com.pervasivecode.utils.concurrent;

import java.util.concurrent.TimeUnit;

import org.joda.time.Period;
import org.joda.time.format.PeriodFormatter;
import org.joda.time.format.PeriodFormatterBuilder;
import com.pervasivecode.utils.concurrent.AccumulatingStopwatch.TimingSummary;

public class ConsoleTimingSummaryFormatter {
  // TODO replace this with ScalingDurationFormatter? (Or a simpler non-JSR363 class?)
  private static final PeriodFormatter PERIOD_FORMATTER = new PeriodFormatterBuilder()
      .appendDays()
      .appendSuffix("d")
      .appendHours()
      .appendSuffix("h")
      .appendMinutes()
      .appendSuffix("m")
      .appendSecondsWithMillis()
      .appendSuffix("s")
      .toFormatter();

  public String format(TimingSummary summary) {
    long numCycles = summary.numStartStopCycles();
    // This is a double so that avgMillisPerCycleFragment can show fractions of a millisecond.
    double totalMillis = summary.totalElapsedTime(TimeUnit.MICROSECONDS) / 1000.0;

    String avgMillisPerCycleFragment = "";
    if (numCycles > 0) {
      double avgMillisPerCycle = totalMillis / numCycles;
      avgMillisPerCycleFragment = String.format("%05.2fms avg, ", avgMillisPerCycle);
    }

    // TODO replace this with some kind of scaling duration formatter that doesn't depend on
    // JSR-363.
    String formattedPeriod = PERIOD_FORMATTER.print(new Period(Math.round(totalMillis)));

    String suffix = numCycles == 1 ? "" : "s";
    return String.format("%s (%s%d cycle%s)", formattedPeriod, avgMillisPerCycleFragment,
        numCycles, suffix);
  }
}
