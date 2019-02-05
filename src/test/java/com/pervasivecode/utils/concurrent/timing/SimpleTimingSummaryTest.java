package com.pervasivecode.utils.concurrent.timing;

import org.junit.Ignore;
import org.junit.Test;
import com.google.common.truth.Truth;

public class SimpleTimingSummaryTest {
  @Ignore
  @Test
  public void build_withZeroCyclesAndNonzeroTotalElapsed_shouldThrow() {
    Truth.assert_().fail();
  }

  @Ignore
  @Test
  public void build_withNegativeCycles_shouldThrow() {
    Truth.assert_().fail();
  }

  @Ignore
  @Test
  public void build_withNegativeDuration_shouldThrow() {
    Truth.assert_().fail();
  }

  @Ignore
  @Test
  public void build_withPositiveValues_shouldWork() {
    Truth.assert_().fail();    
  }
}
