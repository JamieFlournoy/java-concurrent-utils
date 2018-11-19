package com.pervasivecode.utils.concurrent;

import static com.google.common.truth.Truth.assertThat;
import org.junit.Before;
import org.junit.Test;
import com.google.common.truth.Truth;
import com.pervasivecode.utils.concurrent.BlockingExecutorService.Operation;
import com.pervasivecode.utils.concurrent.timing.MultistageStopwatch;
import com.pervasivecode.utils.concurrent.timing.SimpleMultistageStopwatch;
import com.pervasivecode.utils.time.testing.FakeNanoSource;

public class BlockingExecutorServiceConfigTest {
  private static final String EXPECTED_EXCEPTION_MESSAGE = "An exception should have been thrown.";

  private FakeNanoSource nanoSource;
  private MultistageStopwatch<Operation> stopwatch;

  @Before
  public void setup() {
    nanoSource = new FakeNanoSource();
    stopwatch = new SimpleMultistageStopwatch<>(nanoSource, Operation.values());
  }

  private BlockingExecutorServiceConfig.Builder validBuilder() {
    return BlockingExecutorServiceConfig.builder() //
        .setCurrentNanosSource(nanoSource) //
        .setMinThreads(0) //
        .setMaxThreads(2) //
        .setNameFormat("worker %d") //
        .setQueueSize(10) //
        .setSecondsBeforeIdleThreadExits(10) //
        .setStopwatch(stopwatch);
  }

  private static void assertExceptionWhenBuilding(BlockingExecutorServiceConfig.Builder builder,
      String expectedMessageSubstring) {
    try {
      builder.build();
      Truth.assert_().fail(EXPECTED_EXCEPTION_MESSAGE);
    } catch (IllegalArgumentException iae) {
      assertThat(iae).hasMessageThat().contains(expectedMessageSubstring);
    }
  }

  @Test
  public void build_withInvalidQueueSize_shouldThrow() {
    assertExceptionWhenBuilding(validBuilder().setQueueSize(0), "queueSize");
    assertExceptionWhenBuilding(validBuilder().setQueueSize(-10), "queueSize");
  }

  @Test
  public void build_withInvalidMinThreads_shouldThrow() {
    assertExceptionWhenBuilding(validBuilder().setMinThreads(-10), "minThreads");
  }

  @Test
  public void build_withInvalidMaxThreads_shouldThrow() {
    assertExceptionWhenBuilding(validBuilder().setMaxThreads(0), "maxThreads");
    assertExceptionWhenBuilding(validBuilder().setMaxThreads(-10), "maxThreads");
    assertExceptionWhenBuilding(validBuilder().setMinThreads(10).setMaxThreads(9), "maxThreads");
  }

  @Test
  public void build_withInvalidNameFormat_shouldThrow() {
    assertExceptionWhenBuilding(validBuilder().setNameFormat("asdasdasd"), "nameFormat");
    assertExceptionWhenBuilding(validBuilder().setNameFormat("asdasdasd %f"), "nameFormat");
    assertExceptionWhenBuilding(validBuilder().setNameFormat("asdasdasd %s"), "nameFormat");
  }

  @Test
  public void build_withInvalidSecondsBeforeIdleThreadExits_shouldThrow() {
    assertExceptionWhenBuilding(validBuilder().setSecondsBeforeIdleThreadExits(-10),
        "secondsBeforeIdleThreadExits");
  }

  // Null-checking for setStopwatch is already done by the AutoValue-generated Builder class.

  @Test
  public void build_withValidValues_shouldReturnInstance() {
    BlockingExecutorServiceConfig config = validBuilder().buildInternal();
    assertThat(config).isNotNull();
  }
}
