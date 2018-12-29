package com.pervasivecode.utils.concurrent.executors;

import static com.google.common.truth.Truth.assertThat;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import com.google.common.collect.ImmutableList;
import com.pervasivecode.utils.concurrent.executors.BlockingExecutorService;
import com.pervasivecode.utils.concurrent.testing.FailingCallable;
import com.pervasivecode.utils.concurrent.testing.PausingNoOpCallable;
import com.pervasivecode.utils.concurrent.testing.PausingNoOpRunnable;

/**
 * This class contains helper methods for testing BlockingExecutorService, to make tests clearer.
 */
class BlockingExecutorTestHelper {
  private final BlockingExecutorService service;
  private ImmutableList<PausingNoOpRunnable> blockingTasks;

  public BlockingExecutorTestHelper(BlockingExecutorService service) {
    this.service = service;
  }

  public static List<PausingNoOpRunnable> buildBlockingRunnables(int numBlockingTasks) {
    ImmutableList.Builder<PausingNoOpRunnable> builder = ImmutableList.builder();
    for (int i = 0; i < numBlockingTasks; i++) {
      PausingNoOpRunnable blocker = new PausingNoOpRunnable();
      builder.add(blocker);
    }
    return builder.build();
  }

  public ImmutableList<PausingNoOpRunnable> fillThreadsAndQueueWithBlockingTasks() {
    int numBlockerTasks = service.maxUnblockedTaskCount();
    blockingTasks = ImmutableList.copyOf(buildBlockingRunnables(numBlockerTasks));
    for (PausingNoOpRunnable blocker : blockingTasks) {
      service.execute(blocker);
    }
    return blockingTasks;
  }

  public void releaseBlockingTasks() {
    for (PausingNoOpRunnable blocker : blockingTasks) {
      blocker.unpause();
    }
  }

  public static List<PausingNoOpCallable> buildBlockingCallables(int numBlockingCallables) {
    ImmutableList.Builder<PausingNoOpCallable> callables = ImmutableList.builder();
    for (int i = 0; i < numBlockingCallables; i++) {
      final int j = i + 1;
      callables.add(new PausingNoOpCallable(j));
    }
    return callables.build();
  }

  public static List<Callable<Integer>> buildIntCubingCallables(int numCallables) {
    ImmutableList.Builder<Callable<Integer>> callables = ImmutableList.builder();
    for (int i = 0; i < numCallables; i++) {
      final int j = i + 1;
      callables.add(() -> j * j * j);
    }
    return callables.build();
  }

  public static void verifyResultsOfIntCubingCallables(int[] resultsOfCallables) {
    Arrays.sort(resultsOfCallables);
    for (int i = 0; i < resultsOfCallables.length; i++) {
      int j = i + 1;
      assertThat(resultsOfCallables[i]).isEqualTo(j * j * j);
    }
  }

  public static void verifyResultsOfIntCubingCallables(List<Integer> resultsOfCallables) {
    int[] results = new int[resultsOfCallables.size()];
    for (int i = 0; i < results.length; i++) {
      results[i] = resultsOfCallables.get(i);
    }
    verifyResultsOfIntCubingCallables(results);
  }

  public static List<FailingCallable<Integer>> buildFailingCallables(int numCallables) {
    ImmutableList.Builder<FailingCallable<Integer>> callables = ImmutableList.builder();
    for (int i = 0; i < numCallables; i++) {
      callables.add(new FailingCallable<Integer>());
    }
    return callables.build();
  }

  public int waitForBlockingTasksToPause(int numTasks, int timeoutAmount, TimeUnit timeoutUnits)
      throws InterruptedException {
    CountDownLatch numTasksNotYetPaused = new CountDownLatch(numTasks);
    for (PausingNoOpRunnable blocker : blockingTasks) {
      if (!blocker.hasUnpaused()) {
        blocker.onPause(() -> numTasksNotYetPaused.countDown());
      }
    }
    numTasksNotYetPaused.await(timeoutAmount, timeoutUnits);
    return numTasks - (int) numTasksNotYetPaused.getCount();
  }
}
