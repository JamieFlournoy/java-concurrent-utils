package com.pervasivecode.utils.concurrent.timing;

import static com.google.common.truth.Truth.assertThat;
import static com.pervasivecode.utils.concurrent.timing.HistogramBasedStopwatchTest.TimerType.BAKED_POTATO;
import static com.pervasivecode.utils.concurrent.timing.HistogramBasedStopwatchTest.TimerType.HARD_BOILED_EGG;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.time.Duration;
import java.util.function.Supplier;
import org.junit.Before;
import org.junit.Test;
import com.google.common.truth.Truth;
import com.pervasivecode.utils.concurrent.timing.MultistageStopwatch.TimingSummary;
import com.pervasivecode.utils.stats.histogram.BucketSelector;
import com.pervasivecode.utils.stats.histogram.BucketSelectors;
import com.pervasivecode.utils.stats.histogram.ConcurrentHistogram;
import com.pervasivecode.utils.stats.histogram.Histogram;
import com.pervasivecode.utils.stats.histogram.MutableHistogram;
import com.pervasivecode.utils.time.api.CurrentNanosSource;
import com.pervasivecode.utils.time.testing.FakeNanoSource;
import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

public class HistogramBasedStopwatchTest {
  public enum TimerType {
    HARD_BOILED_EGG, BAKED_POTATO
  }

  private FakeNanoSource fakeNanoSource;
  private MutableHistogram<Long> histogram;
  private HistogramBasedStopwatch<TimerType> stopwatch;
  private BucketSelector<Long> bucketer;

  @Before
  public void setup() {
    fakeNanoSource = new FakeNanoSource();
    bucketer = BucketSelectors.exponentialLong(5, 1, 4);
    histogram = new ConcurrentHistogram<Long>(bucketer);
    Supplier<MutableHistogram<Long>> hs = () -> histogram;
    stopwatch = new HistogramBasedStopwatch<TimerType>("boil times", fakeNanoSource,
        TimerType.values(), hs);
  }

  @Test
  public void startTimer_shouldReturnAnActiveTimerInstance() {
    StoppableTimer t1 = stopwatch.startTimer(HARD_BOILED_EGG);
    assertThat(t1).isNotNull();

    StoppableTimer t2 = stopwatch.startTimer(BAKED_POTATO);
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
    StoppableTimer t = stopwatch.startTimer(HARD_BOILED_EGG);
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
    StoppableTimer t = stopwatch.startTimer(HARD_BOILED_EGG);
    long durationNanos = 7_670_000L;
    fakeNanoSource.incrementTimeNanos(durationNanos - 1);
    t.stopTimer();
    TimingSummary summary1 = stopwatch.summarize(HARD_BOILED_EGG);
    TimingSummary expectedSummary1 = RoundingTimingSummary.builder().setNumStartStopCycles(1)
        .setTotalElapsedNanos(durationNanos).build();
    assertThat(summary1).isEqualTo(expectedSummary1);

    TimingSummary summary2 = stopwatch.summarize(BAKED_POTATO);
    TimingSummary expectedSummary2 =
        RoundingTimingSummary.builder().setNumStartStopCycles(0).setTotalElapsedNanos(0).build();
    assertThat(summary2).isEqualTo(expectedSummary2);
  }

  @Test
  public void stopTimer_withStoppedTimer_shouldThrow() {
    StoppableTimer t = stopwatch.startTimer(HARD_BOILED_EGG);
    t.stopTimer();
    try {
      t.stopTimer();
      Truth.assert_().fail("Expected stopTimer() to throw an exception.");
    } catch (IllegalStateException ise) {
      assertThat(ise).hasMessageThat().contains("not running");
    }
  }

  @Test
  public void stopTimer_withBadNanoSource_shouldThrow() {
    CurrentNanosSource mockNanoSource = mock(CurrentNanosSource.class);

    long nanos = 12345L;
    when(mockNanoSource.currentTimeNanoPrecision()).thenReturn(nanos);
    stopwatch = new HistogramBasedStopwatch<>("boil times", mockNanoSource, TimerType.values(),
        () -> histogram);
    StoppableTimer t = stopwatch.startTimer(HARD_BOILED_EGG);
    try {
      t.stopTimer();
      Truth.assert_().fail("Expected and exception when the timer has zero duration.");
    } catch (IllegalStateException ise) {
      assertThat(ise).hasMessageThat().contains("0ns");
    }

    when(mockNanoSource.currentTimeNanoPrecision()).thenReturn(nanos, nanos - 1);
    t = stopwatch.startTimer(BAKED_POTATO);
    try {
      t.stopTimer();
      Truth.assert_().fail("Expected and exception when the timer has negative duration.");
    } catch (IllegalStateException ise) {
      assertThat(ise).hasMessageThat().contains("negative");
    }
  }

  @Test
  public void timingHistogram_shouldReturnAHistogramOfStartsAndStops() {
    StoppableTimer t = stopwatch.startTimer(HARD_BOILED_EGG);
    this.fakeNanoSource.incrementTimeNanos(100); // must be a really small egg to boil that quickly
    Duration eggTime = t.stopTimer();

    Histogram<Long> histogram = stopwatch.timingHistogramFor(HARD_BOILED_EGG);
    int expectedIndex = bucketer.bucketIndexFor(eggTime.toMillis());
    assertThat(expectedIndex).isEqualTo(0);
    assertThat(histogram.countInBucket(expectedIndex)).isEqualTo(1);
  }

  @Test
  public void equals_shouldWork() {
    EqualsVerifier.forClass(HistogramBasedStopwatch.class)
        .suppress(Warning.NONFINAL_FIELDS)
        .verify();
  }
}
