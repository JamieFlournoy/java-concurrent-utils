package com.pervasivecode.utils.concurrent.impl;

import static com.google.common.truth.Truth.assertThat;

import org.junit.Test;
import com.pervasivecode.utils.concurrent.api.AccumulatingStopwatch.TimingSummary;
import com.pervasivecode.utils.concurrent.impl.ConsoleTimingSummaryFormatter;
import com.pervasivecode.utils.concurrent.impl.NanosecondBasedRoundingTimingSummary;

public class ConsoleTimingSummaryFormatterTest {
  @Test
  public void format_withMillionsOfNanos_shouldRoundTotalToMillis() {
    ConsoleTimingSummaryFormatter formatter = new ConsoleTimingSummaryFormatter();
    TimingSummary summary = NanosecondBasedRoundingTimingSummary.builder()
      .setNumStartStopCycles(1)
      .setTotalElapsedNanos(7_670_000L)
      .build();
    assertThat(formatter.format(summary)).isEqualTo("0.008s (07.67ms avg, 1 cycle)");
  }

  @Test
  public void format_withZeroNanos_shouldNotCalculateAverageTime() {
    ConsoleTimingSummaryFormatter formatter = new ConsoleTimingSummaryFormatter();
    TimingSummary summary = NanosecondBasedRoundingTimingSummary.builder()
        .setNumStartStopCycles(0)
        .setTotalElapsedNanos(0)
        .build();
    assertThat(formatter.format(summary)).isEqualTo("0.000s (0 cycles)");
  }

  @Test
  public void format_withBillionsOfNanos_shouldRoundTotalToMillis() {
    ConsoleTimingSummaryFormatter formatter = new ConsoleTimingSummaryFormatter();
    TimingSummary summary = NanosecondBasedRoundingTimingSummary.builder()
      .setNumStartStopCycles(4000)
      .setTotalElapsedNanos(7_670_000_123L)
      .build();
    assertThat(formatter.format(summary)).isEqualTo("7.670s (01.92ms avg, 4000 cycles)");
  }
}
