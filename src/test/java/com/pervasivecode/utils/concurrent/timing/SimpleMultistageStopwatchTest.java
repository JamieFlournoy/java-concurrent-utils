package com.pervasivecode.utils.concurrent.timing;

import static com.google.common.truth.Truth.assertThat;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import org.junit.Before;
import org.junit.Test;
import com.pervasivecode.utils.concurrent.timing.MultistageStopwatch.TimingSummary;
import com.pervasivecode.utils.time.testing.FakeNanoSource;

public class SimpleMultistageStopwatchTest {
  // TODO extract common stuff to a helper, and use that to test HistogramBasedStopwatch too.

  private static final long TEST_TIMER_DURATION_NANOS = 1_234_567_890L;

  public enum RecipeStep {
    CHOP, MIX, BAKE
  };

  private FakeNanoSource nanoSource;

  @Before
  public void setup() {
    nanoSource = new FakeNanoSource();
  }

  @Test(expected = NullPointerException.class)
  public void constructor_withNullNanoSource_shouldThrow() {
    new SimpleMultistageStopwatch<RecipeStep>(null, RecipeStep.values());
  }

  @Test(expected = NullPointerException.class)
  public void constructor_withNullEnumValues_shouldThrow() {
    new SimpleMultistageStopwatch<RecipeStep>(new FakeNanoSource(), null);
  }

  @Test
  public void startTimerThenStopTimer_shouldReturnDuration() {
    SimpleMultistageStopwatch<RecipeStep> stopwatch =
        new SimpleMultistageStopwatch<>(nanoSource, RecipeStep.values());

    long startNanos = nanoSource.currentTimeNanoPrecision();

    StoppableTimer chopOnionsTimer = stopwatch.startTimer(RecipeStep.CHOP);
    nanoSource.incrementTimeNanos(TEST_TIMER_DURATION_NANOS);
    Duration timeToChopOnions = chopOnionsTimer.stopTimer();

    long endNanos = nanoSource.currentTimeNanoPrecision();

    assertThat(timeToChopOnions).isAtLeast(Duration.ofNanos(TEST_TIMER_DURATION_NANOS));
    assertThat(timeToChopOnions).isLessThan(Duration.ofNanos(endNanos - startNanos));
  }

  @Test(expected = IllegalStateException.class)
  public void stopTimer_withStoppedTimer_shouldThrow() {
    SimpleMultistageStopwatch<RecipeStep> stopwatch =
        new SimpleMultistageStopwatch<>(nanoSource, RecipeStep.values());
    StoppableTimer chopCarrotsTimer = stopwatch.startTimer(RecipeStep.CHOP);
    chopCarrotsTimer.stopTimer();
    chopCarrotsTimer.stopTimer();
  }

  @Test
  public void getTotalElapsedNanos_shouldReturnSumOfTimerDurations() {
    SimpleMultistageStopwatch<RecipeStep> stopwatch =
        new SimpleMultistageStopwatch<>(nanoSource, RecipeStep.values());

    StoppableTimer chopCarrotsTimer = stopwatch.startTimer(RecipeStep.CHOP);
    StoppableTimer chopOnionsTimer = stopwatch.startTimer(RecipeStep.CHOP);

    nanoSource.incrementTimeNanos(TEST_TIMER_DURATION_NANOS);
    Duration timeToChopOnions = chopOnionsTimer.stopTimer();

    nanoSource.incrementTimeNanos(TEST_TIMER_DURATION_NANOS);
    Duration timeToChopCarrots = chopCarrotsTimer.stopTimer();

    assertThat(stopwatch.getTotalElapsedNanos(RecipeStep.CHOP))
        .isEqualTo(timeToChopOnions.plus(timeToChopCarrots).toNanos());
  }

  @Test
  public void getTimerTypes_shouldReturnAllEnumValues() {
    SimpleMultistageStopwatch<RecipeStep> stopwatch =
        new SimpleMultistageStopwatch<>(nanoSource, RecipeStep.values());
    assertThat(stopwatch.getTimerTypes()).containsExactlyElementsIn(RecipeStep.values());
  }

  @Test
  public void summarize_shouldReturnValidTimingSummary() {
    SimpleMultistageStopwatch<RecipeStep> stopwatch =
        new SimpleMultistageStopwatch<>(nanoSource, RecipeStep.values());

    StoppableTimer mixDryIngredientsTimer = stopwatch.startTimer(RecipeStep.MIX);
    StoppableTimer mixWetIngredientsTimer = stopwatch.startTimer(RecipeStep.MIX);

    nanoSource.incrementTimeNanos(TEST_TIMER_DURATION_NANOS);

    Duration mixDryDuration = mixDryIngredientsTimer.stopTimer();

    nanoSource.incrementTimeNanos(TEST_TIMER_DURATION_NANOS);

    Duration mixWetDuration = mixWetIngredientsTimer.stopTimer();
    StoppableTimer mixAllIngredientsTimer = stopwatch.startTimer(RecipeStep.MIX);

    nanoSource.incrementTimeNanos(TEST_TIMER_DURATION_NANOS);

    Duration mixAllDuration = mixAllIngredientsTimer.stopTimer();
    StoppableTimer bakeTimer = stopwatch.startTimer(RecipeStep.BAKE);

    nanoSource.incrementTimeNanos(TEST_TIMER_DURATION_NANOS * 7);

    Duration bakeDuration = bakeTimer.stopTimer();

    Duration sumOfMixDurations = mixDryDuration.plus(mixWetDuration).plus(mixAllDuration);

    TimingSummary mixSummary = stopwatch.summarize(RecipeStep.MIX);
    TimingSummary bakeSummary = stopwatch.summarize(RecipeStep.BAKE);

    assertThat(mixSummary.numStartStopCycles()).isEqualTo(3);
    assertThat(mixSummary.totalElapsedTime(ChronoUnit.NANOS))
        .isEqualTo(sumOfMixDurations.toNanos());

    assertThat(bakeSummary.numStartStopCycles()).isEqualTo(1);
    assertThat(bakeSummary.totalElapsedTime(ChronoUnit.NANOS))
        .isEqualTo(bakeDuration.toNanos());
  }
}
