package com.pervasivecode.utils.concurrent.timing;

import static com.google.common.truth.Truth.assertThat;
import org.junit.Test;
import com.pervasivecode.utils.time.api.CurrentNanosSource;
import nl.jqno.equalsverifier.EqualsVerifier;
import nl.jqno.equalsverifier.Warning;

public class SimpleMultistageTimerTest {
  private enum SomeStage {
    WARMUP, DOTHETHING, COOLDOWN
  };

  @Test(expected = NullPointerException.class)
  public void constructor_withNullNanoSource_shouldThrow() {
    new SimpleMultistageTimer<SomeStage>(null, SomeStage.WARMUP);
  }

  @Test(expected = NullPointerException.class)
  public void constructor_withNullTimerType_shouldThrow() {
    new SimpleMultistageTimer<SomeStage>(() -> System.nanoTime(), null);
  }

  @Test
  public void createAndStart_shouldReturnRunningTimer() {
    SimpleMultistageTimer<SomeStage> warmupTimer =
        SimpleMultistageTimer.createAndStart(() -> System.nanoTime(), SomeStage.WARMUP);
    assertThat(warmupTimer.isRunning()).isTrue();
  }

  @Test
  public void equals_shouldWork() {
    EqualsVerifier.forClass(SimpleMultistageTimer.class) //
        .withRedefinedSuperclass() //
        .withRedefinedSubclass(TestMultistageTimerSubclass.class) //
        .suppress(Warning.NONFINAL_FIELDS) // SimpleMultistageTimer can't be immutable
        .verify();
  }

  // See https://jqno.nl/equalsverifier/errormessages/coverage-is-not-100-percent/#using-canequal
  private static class TestMultistageTimerSubclass extends SimpleMultistageTimer<SomeStage> {
    public TestMultistageTimerSubclass(CurrentNanosSource nanoSource, SomeStage timerType) {
      super(nanoSource, timerType);
    }

    @Override
    public boolean canEqual(Object other) {
      return (other instanceof TestMultistageTimerSubclass);
    }
  }
}
