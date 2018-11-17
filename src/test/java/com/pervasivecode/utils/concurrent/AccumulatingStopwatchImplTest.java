package com.pervasivecode.utils.concurrent;

import static com.google.common.truth.Truth.assertThat;
import static com.pervasivecode.utils.concurrent.AccumulatingStopwatchImplTest.TimerType.BAKED_POTATO;
import static com.pervasivecode.utils.concurrent.AccumulatingStopwatchImplTest.TimerType.HARD_BOILED_EGG;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.ImmutableList;
import com.pervasivecode.utils.concurrent.AccumulatingStopwatchImpl;
import com.pervasivecode.utils.concurrent.AccumulatingStopwatch.ActiveTimer;
import com.pervasivecode.utils.concurrent.AccumulatingStopwatch.TimingSummary;
import com.pervasivecode.utils.time.testing.FakeNanoSource;

public class AccumulatingStopwatchImplTest {
  public enum TimerType { HARD_BOILED_EGG, BAKED_POTATO }

  private AccumulatingStopwatchImpl<TimerType> stopwatch;
  private FakeNanoSource fakeNanoSource;

  @Before
  public void setup() {
    fakeNanoSource = new FakeNanoSource();
    stopwatch = new AccumulatingStopwatchImpl<>("boil times", fakeNanoSource) {
      @Override public Iterable<TimerType> getTimerTypes() {
        return ImmutableList.copyOf(TimerType.values());
      }
    };
  }

  @Test
  public void startTimer_shouldReturnAnActiveTimerInstance() {
    ActiveTimer t1 = stopwatch.startTimer(HARD_BOILED_EGG);
    assertThat(t1).isNotNull();

    ActiveTimer t2 = stopwatch.startTimer(BAKED_POTATO);
    assertThat(t2).isNotNull();

    assertThat(t1).isNotEqualTo(t2);
  }

  @Test(expected = IllegalStateException.class)
  public void getTotalElapsedNanos_onRunningTimer_shouldThrow() {
    stopwatch.startTimer(HARD_BOILED_EGG);
    stopwatch.getTotalElapsedNanos(HARD_BOILED_EGG);
  }

  @Test
  public void startThenStop_shouldIncreaseTotalElapsedNanosByType() {
    ActiveTimer t = stopwatch.startTimer(HARD_BOILED_EGG);
    fakeNanoSource.incrementTimeNanos(333L);
    t.stopTimer();
    assertThat(stopwatch.getTotalElapsedNanos(HARD_BOILED_EGG)).isGreaterThan(0L);
  }

  @Test
  public void getName_shouldReturnStopwatchName() {
    assertThat(stopwatch.getName()).isEqualTo("boil times");
  }

  @Test
  public void summarize_shouldSummarizeTimerActivity() {
    ActiveTimer t = stopwatch.startTimer(HARD_BOILED_EGG);
    long durationNanos = 7_670_000L;
    fakeNanoSource.incrementTimeNanos(durationNanos - 1);
    t.stopTimer();
    TimingSummary summary1 = stopwatch.summarize(HARD_BOILED_EGG);
    TimingSummary expectedSummary1 = NanosecondBasedRoundingTimingSummary.builder()
        .setNumStartStopCycles(1)
        .setTotalElapsedNanos(durationNanos)
        .build();
    assertThat(summary1).isEqualTo(expectedSummary1);

    TimingSummary summary2 = stopwatch.summarize(BAKED_POTATO);
    TimingSummary expectedSummary2 = NanosecondBasedRoundingTimingSummary.builder()
        .setNumStartStopCycles(0)
        .setTotalElapsedNanos(0)
        .build();
    assertThat(summary2).isEqualTo(expectedSummary2);
  }

}
