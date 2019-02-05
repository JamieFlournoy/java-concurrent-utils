package com.pervasivecode.utils.concurrent.timing;

import static com.google.common.truth.Truth.assertThat;
import java.time.Duration;
import org.junit.Test;
import com.google.common.truth.Truth;
import com.pervasivecode.utils.concurrent.timing.MultistageStopwatch.TimingSummary;

public class SimpleTimingSummaryTest {
  @Test
  public void build_withZeroCyclesAndNonzeroTotalElapsed_shouldThrow() {
    try {
      SimpleTimingSummary.builder() //
          .setNumStartStopCycles(0) //
          .setTotalElapsedTime(Duration.ofSeconds(1)) //
          .build();
      Truth.assert_().fail("Expected an exception here.");
    } catch (IllegalStateException ise) {
      assertThat(ise).hasMessageThat().contains("zero");
    }
  }

  @Test
  public void build_withNegativeCycles_shouldThrow() {
    try {
      SimpleTimingSummary.builder() //
          .setNumStartStopCycles(-1) //
          .setTotalElapsedTime(Duration.ofSeconds(1)) //
          .build();
      Truth.assert_().fail("Expected an exception here.");
    } catch (IllegalStateException ise) {
      assertThat(ise).hasMessageThat().contains("negative");
      assertThat(ise).hasMessageThat().contains("Cycles");
    }
  }

  @Test
  public void build_withNegativeDuration_shouldThrow() {
    try {
      SimpleTimingSummary.builder() //
          .setNumStartStopCycles(1) //
          .setTotalElapsedTime(Duration.ofSeconds(-1)) //
          .build();
      Truth.assert_().fail("Expected an exception here.");
    } catch (IllegalStateException ise) {
      assertThat(ise).hasMessageThat().contains("negative");
      assertThat(ise).hasMessageThat().contains("Elapsed");
    }
  }

  @Test
  public void build_withPositiveValues_shouldWork() {
    TimingSummary ts = SimpleTimingSummary.builder() //
        .setNumStartStopCycles(2) //
        .setTotalElapsedTime(Duration.ofSeconds(20)) //
        .build();
    
    assertThat(ts.numStartStopCycles()).isEqualTo(2);
    assertThat(ts.totalElapsedTime()).isEqualTo(Duration.ofSeconds(20));
  }
}
