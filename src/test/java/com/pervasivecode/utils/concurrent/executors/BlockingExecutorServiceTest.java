package com.pervasivecode.utils.concurrent.executors;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.truth.Truth.assertThat;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicIntegerArray;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.truth.Truth;
import com.pervasivecode.utils.concurrent.executors.BlockingExecutorService.Operation;
import com.pervasivecode.utils.concurrent.testing.AwaitableNoOpRunnable;
import com.pervasivecode.utils.concurrent.testing.FailingCallable;
import com.pervasivecode.utils.concurrent.testing.PausingAwaitableNoOpRunnable;
import com.pervasivecode.utils.concurrent.testing.PausingNoOpCallable;
import com.pervasivecode.utils.concurrent.testing.PausingNoOpRunnable;
import com.pervasivecode.utils.concurrent.timing.MultistageStopwatch;
import com.pervasivecode.utils.concurrent.timing.MultistageStopwatch.TimingSummary;
import com.pervasivecode.utils.concurrent.timing.SimpleActiveTimer;
import com.pervasivecode.utils.concurrent.timing.SimpleMultistageStopwatch;
import com.pervasivecode.utils.concurrent.timing.StoppableTimer;
import com.pervasivecode.utils.time.CurrentNanosSource;
import com.pervasivecode.utils.time.testing.FakeNanoSource;
import repeat.Repeat;
import repeat.RepeatRule;

public class BlockingExecutorServiceTest {
  // Use values up to NUM_REPEATS=100 for torture testing. (Above that, timeouts in the test methods
  // are too strict, and may cause intermittent test failures due to timeouts and teardown() finding
  // workers that are still running.)
  private static final int NUM_REPEATS = 5;

  private static final int WAS_CANCELLED = -1;
  private static final int STARTED = 1;
  private static final int FINISHED = 2;

  private FakeNanoSource nanoSource;

  // Warning: Don't use multiple threads in the @Repeat annotation, because RepeatRule doesn't
  // isolate test cases from each other; the instance fields in this class are intended to be set
  // up, used, and torn down by a single test executor thread.
  @Rule
  public RepeatRule rule = new RepeatRule();

  @Mock
  private MultistageStopwatch<Operation> mockStopwatch;

  @Mock
  private StoppableTimer mockTimer;

  BlockingExecutorService blockingExecService = null;
  int iteration = 0;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    Mockito.when(mockStopwatch.startTimer(Mockito.any())).thenReturn(mockTimer);
    this.nanoSource = new FakeNanoSource();
    iteration++;
  }

  @After
  public void teardown() throws Exception {
    if (blockingExecService != null) {
      // This is necessary to avoid transient OutOfMemoryErrors due to not being able to create
      // native threads (since each test run makes a bunch of them).
      blockingExecService.shutdown();
      blockingExecService.awaitTermination(100, MILLISECONDS);
      List<Runnable> queuedButNotRunningYet = blockingExecService.shutdownNow();
      assertThat(queuedButNotRunningYet).isEmpty();
      blockingExecService.awaitTermination(10, SECONDS);
      blockingExecService = null;
    }
  }

  private BlockingExecutorServiceConfig.Builder configBuilder() {
    checkNotNull(nanoSource);
    return BlockingExecutorServiceConfig.builder() //
        .setCurrentNanosSource(this.nanoSource) //
        .setNumThreads(3) //
        .setNameFormat("%d") //
        .setQueueSize(1) //
        .setStopwatch(mockStopwatch); //
  }

  // --------------------------------------------------------------------------
  //
  // Tests for Constructor
  //
  // --------------------------------------------------------------------------

  @SuppressWarnings("unused")
  @Test(expected = NullPointerException.class)
  public void constructor_withNullConfig_shouldThrow() {
    new BlockingExecutorService(null);
  }

  @Test(expected = NullPointerException.class)
  public void execute_withNullRunnable_shouldThrow() {
    blockingExecService = new BlockingExecutorService(configBuilder().build());
    blockingExecService.execute(null);
  }

  // --------------------------------------------------------------------------
  //
  // Tests for execute(Runnable)
  //
  // --------------------------------------------------------------------------

  @Test
  @Repeat(times = NUM_REPEATS)
  public void execute_withNothingQueued_shouldRunImmediately() throws Exception {
    blockingExecService = new BlockingExecutorService(configBuilder().build());
    AwaitableNoOpRunnable task = new AwaitableNoOpRunnable();
    blockingExecService.execute(task);
    task.awaitTaskCompletion(1, SECONDS);
  }

  @Test
  @Repeat(times = NUM_REPEATS)
  public void execute_withFullQueue_shouldBlockThenRun() throws Exception {
    BlockingExecutorServiceConfig config = configBuilder().build();
    blockingExecService = new BlockingExecutorService(config);

    BlockingExecutorTestHelper testHelper = new BlockingExecutorTestHelper(blockingExecService);
    ExecutorService taskSubmittingService = Executors.newSingleThreadExecutor();

    // Create exactly enough PausingTask instances to cause the next task to block.
    testHelper.fillThreadsAndQueueWithBlockingTasks();

    // Add one more task, but adding the task to the queue should block since the queue is full.
    CountDownLatch haveEnqueuedExtraTask = new CountDownLatch(1);
    AwaitableNoOpRunnable taskThatShouldNotRunRighAway = new AwaitableNoOpRunnable();
    Runnable enqueueAdditionalTask = () -> {
      blockingExecService.execute(taskThatShouldNotRunRighAway);
      haveEnqueuedExtraTask.countDown();
    };
    taskSubmittingService.execute(enqueueAdditionalTask);

    // Verify that enqueueAdditionalTask cannot finish promptly (since it's blocked).
    boolean hasEnqueuedExtraTaskAlready = haveEnqueuedExtraTask.await(10, MILLISECONDS);
    assertThat(hasEnqueuedExtraTaskAlready).isFalse();

    // Let the blocking tests finish so that taskThatShouldBlock can run.
    testHelper.releaseBlockingTasks();

    // Now, the extra task should enqueue and run soon.
    hasEnqueuedExtraTaskAlready = haveEnqueuedExtraTask.await(1, SECONDS);
    assertThat(hasEnqueuedExtraTaskAlready).isTrue();
    taskThatShouldNotRunRighAway.awaitTaskCompletion(1, SECONDS);

    taskSubmittingService.shutdownNow();
  }

  private static class TimerKeepingMultistageStopwatch implements MultistageStopwatch<Operation> {
    private final ArrayList<SimpleActiveTimer> capturedTimers;
    private final SimpleMultistageStopwatch<Operation> wrapped;

    public TimerKeepingMultistageStopwatch(CurrentNanosSource nanoSource) {
      this.wrapped = new SimpleMultistageStopwatch<Operation>(nanoSource, Operation.values());
      this.capturedTimers = new ArrayList<>();
    }

    public List<SimpleActiveTimer> capturedTimers() {
      return this.capturedTimers;
    }

    @Override
    public StoppableTimer startTimer(Operation timertype) {
      SimpleActiveTimer capturedTimer = wrapped.startTimer(timertype);
      capturedTimers.add(capturedTimer);
      return capturedTimer;
    }

    @Override
    public Iterable<Operation> getTimerTypes() {
      return wrapped.getTimerTypes();
    }

    @Override
    public TimingSummary summarize(Operation timertype) {
      return wrapped.summarize(timertype);
    }
  }

  @Test
  @Repeat(times = NUM_REPEATS)
  public void execute_shouldTimeOperationsUsingMultistageStopwatch() throws Exception {
    TimerKeepingMultistageStopwatch testStopwatch = new TimerKeepingMultistageStopwatch(nanoSource);

    BlockingExecutorServiceConfig config = configBuilder() //
        .setQueueSize(1) //
        .setNumThreads(1) //
        .setStopwatch(testStopwatch)
        .build();
    blockingExecService = new BlockingExecutorService(config);

    // Fill the one thread and the one queue slot with a task that pauses, and a task that finishes
    // as soon as it runs.
    PausingNoOpRunnable slowTask = new PausingNoOpRunnable();
    AwaitableNoOpRunnable queuedTask = new AwaitableNoOpRunnable();
    blockingExecService.execute(slowTask);
    blockingExecService.execute(queuedTask);

    // Executing another task should result in the submitting thread blocking since the
    // blockingService's sole thread is busy with slowTask. So do this on a separate thread, and
    // get a Future that can be used to wait for the execute() method call to finish.
    AwaitableNoOpRunnable blockedTask = new AwaitableNoOpRunnable();
    ExecutorService taskSubmittingService = Executors.newSingleThreadExecutor();
    CountDownLatch enqueueTaskHasStarted = new CountDownLatch(1);
    Runnable enqueueAllPausingTasks = () -> {
      enqueueTaskHasStarted.countDown();
      blockingExecService.execute(blockedTask);
    };
    Future<?> haveEnqueuedBlockedTask = taskSubmittingService.submit(enqueueAllPausingTasks);

    // Wait for slowTask to start up and pause. (It probably has already done so by this point.)
    slowTask.waitUntilPaused(10, MILLISECONDS);

    // Make sure the enqueueAllPausingTasks is going to block before we advance the fake clock, so
    // that it experiences a fake 3-second delay when blocking.
    enqueueTaskHasStarted.await(1, SECONDS);

    // Wait until queuedTask has started its blocking timer.
    Thread.sleep(50); // TODO find a better way to do this.

    // Advance the fake clock by 3 seconds, then let slowTask finish. This will let queuedTask run,
    // which in turn will unblock enqueueAllPausingTasks.
    nanoSource.incrementTimeNanos(3_000_000_000L);
    slowTask.unpause();
    queuedTask.awaitTaskCompletion(10, MILLISECONDS);
    haveEnqueuedBlockedTask.get(10, MILLISECONDS);

    blockedTask.awaitTaskCompletion(1, SECONDS);
    // We now expect that each timer experienced different amounts of blocking and queueing delays.

    List<SimpleActiveTimer> timers = testStopwatch.capturedTimers();

    // slowTask should not have blocked, and should have whizzed through the queue straight to the
    // executor thread.
    SimpleActiveTimer slowTaskBlockTimer = timers.get(0);
    SimpleActiveTimer slowTaskQueueTimer = timers.get(1);
    assertThat(slowTaskBlockTimer.elapsed()).isLessThan(Duration.ofMillis(1));
    assertThat(slowTaskQueueTimer.elapsed()).isLessThan(Duration.ofMillis(1));

    // queuedTask should not have blocked, but should have spent at least 3 seconds in the queue.
    SimpleActiveTimer queuedTaskBlockTimer = timers.get(2);
    SimpleActiveTimer queuedTaskQueueTimer = timers.get(3);
    assertThat(queuedTaskBlockTimer.elapsed()).isLessThan(Duration.ofMillis(1));
    assertThat(queuedTaskQueueTimer.elapsed()).isGreaterThan(Duration.ofSeconds(3));
    assertThat(queuedTaskQueueTimer.elapsed()).isLessThan(Duration.ofMillis(3001));

    // blockedTask should have blocked for at least 3 seconds, but should not have spent much time
    // in the queue since it just had to wait for queuedTask to execute.
    SimpleActiveTimer blockedTaskBlockTimer = timers.get(4);
    SimpleActiveTimer blockedTaskQueueTimer = timers.get(5);
    assertThat(blockedTaskBlockTimer.elapsed()).isGreaterThan(Duration.ofSeconds(3));
    assertThat(blockedTaskBlockTimer.elapsed()).isLessThan(Duration.ofSeconds(4));
    assertThat(blockedTaskQueueTimer.elapsed()).isLessThan(Duration.ofMillis(1));

    taskSubmittingService.shutdownNow();
  }

  @Test
  @Repeat(times = NUM_REPEATS)
  public void execute_withShutdownAlreadyCalled_shouldRejectTask() throws Exception {
    blockingExecService = new BlockingExecutorService(configBuilder().build());
    blockingExecService.shutdown();
    blockingExecService.awaitTermination(10, MILLISECONDS);
    try {
      blockingExecService.execute(() -> {
      });
      Truth.assert_().fail("Expected the task to be rejected.");
    } catch (RejectedExecutionException ree) {
      assertThat(ree).hasMessageThat().contains("shut down");
    }
  }

  // --------------------------------------------------------------------------
  //
  // Tests for shutdown()
  //
  // --------------------------------------------------------------------------

  @Test
  @Repeat(times = NUM_REPEATS)
  public void shutdown_withRunningTask_shouldLetRunningAndQueuedTasksRun() throws Exception {
    BlockingExecutorServiceConfig config = configBuilder() //
        .setQueueSize(1) //
        .setNumThreads(1) //
        .build();
    blockingExecService = new BlockingExecutorService(config);

    // Make a couple of tasks, the first of which can stall the blockingService so we can call
    // shutdown() while we're sure there are jobs waiting.
    PausingNoOpRunnable task1 = new PausingNoOpRunnable();
    Callable<Integer> task2 = () -> 12345;

    blockingExecService.execute(task1);
    Future<Integer> task2IsFinished = blockingExecService.submit(task2);

    task1.waitUntilPaused(10, MILLISECONDS);
    blockingExecService.shutdown();

    task1.unpause();
    Integer task2Output = task2IsFinished.get(1, SECONDS);
    blockingExecService.awaitTermination(1, SECONDS);

    assertThat(task2Output.intValue()).isEqualTo(12345);
    assertThat(task2IsFinished.isDone()).isTrue();
    assertThat(task2IsFinished.isCancelled()).isFalse();
  }

  // --------------------------------------------------------------------------
  //
  // Tests for shutdownNow()
  //
  // --------------------------------------------------------------------------

  @Test
  @Repeat(times = NUM_REPEATS)
  public void shutdownNow_shouldInterruptRunningTasks() throws Exception {
    blockingExecService = new BlockingExecutorService(configBuilder().build());

    PausingAwaitableNoOpRunnable blocker = new PausingAwaitableNoOpRunnable();
    Future<?> blockerFutureResult = blockingExecService.submit(blocker);
    blocker.waitUntilPaused(1, SECONDS);

    @SuppressWarnings("unused")
    List<Runnable> notYetRun = blockingExecService.shutdownNow();

    // PausingNoOpRunnable will catch the InterruptedException, so try{} isn't needed here.
    Object blockerResult = blockerFutureResult.get(10, MILLISECONDS);
    assertThat(blockerFutureResult.isDone()).isTrue();
    assertThat(blockerResult).isNull();
    // Note: interrupting running tasks doesn't mean the same thing as the task being "cancelled",
    // so testing for blockerFutureResult.isCancelled() == true isn't useful here.
  }

  @Test
  @Repeat(times = NUM_REPEATS)
  public void shutdownNow_shouldReturnWrappedVersionsOfQueuedButNotStartedTasks() throws Exception {
    BlockingExecutorServiceConfig config =
        configBuilder().setQueueSize(2).setNumThreads(1).build();
    blockingExecService = new BlockingExecutorService(config);

    BlockingExecutorTestHelper queueHelper = new BlockingExecutorTestHelper(blockingExecService);
    List<PausingNoOpRunnable> blockers = queueHelper.fillThreadsAndQueueWithBlockingTasks();
    // Wait for the solitary worker thread to block while running a task.
    queueHelper.waitForBlockingTasksToPause(1, 10, MILLISECONDS);

    List<PausingNoOpRunnable> expectedNotYetRun =
        ImmutableList.copyOf(Iterables.filter(blockers, (t) -> !t.hasPaused()));

    List<Runnable> notYetRun = blockingExecService.shutdownNow();
    assertThat(notYetRun.size()).isAtLeast(expectedNotYetRun.size());
  }

  // --------------------------------------------------------------------------
  //
  // Tests for isShutdown()
  //
  // --------------------------------------------------------------------------

  @Test
  @Repeat(times = NUM_REPEATS)
  public void isShutdown_withNewInstance_shouldReturnFalse() {
    blockingExecService = new BlockingExecutorService(configBuilder().build());
    assertThat(blockingExecService.isShutdown()).isFalse();
  }

  @Test
  @Repeat(times = NUM_REPEATS)
  public void isShutdown_afterShutdown_shouldReturnTrue() {
    blockingExecService = new BlockingExecutorService(configBuilder().build());
    blockingExecService.shutdown();
    assertThat(blockingExecService.isShutdown()).isTrue();
  }

  @Test
  @Repeat(times = NUM_REPEATS)
  public void isShutdown_afterShutdownNow_shouldReturnTrue() {
    blockingExecService = new BlockingExecutorService(configBuilder().build());
    blockingExecService.shutdownNow();
    assertThat(blockingExecService.isShutdown()).isTrue();
  }

  // --------------------------------------------------------------------------
  //
  // Tests for isTerminated()
  //
  // --------------------------------------------------------------------------

  @Test
  @Repeat(times = NUM_REPEATS)
  public void isTerminated_withNewInstance_shouldReturnFalse() {
    blockingExecService = new BlockingExecutorService(configBuilder().build());
    assertThat(blockingExecService.isTerminated()).isFalse();
  }

  @Test
  @Repeat(times = NUM_REPEATS)
  public void isTerminated_withUnkillableTask_afterShutdownNow_shouldReturnFalse()
      throws Exception {
    blockingExecService = new BlockingExecutorService(configBuilder().build());

    PausingNoOpRunnable blocker1 = new PausingNoOpRunnable();
    PausingNoOpRunnable blocker2 = new PausingNoOpRunnable();
    Runnable hardToKill = () -> {
      blocker1.run(); // this will get interrupted when shutdownNow is called
      blocker2.run(); // this will keep the task alive indefinitely after that
    };
    Future<?> taskFutureResult = blockingExecService.submit(hardToKill);

    blocker1.waitUntilPaused(10, MILLISECONDS);

    blockingExecService.shutdownNow();
    blockingExecService.awaitTermination(10, MILLISECONDS);
    assertThat(blockingExecService.isTerminated()).isFalse();

    blocker2.waitUntilPaused(10, MILLISECONDS);
    assertThat(blockingExecService.isTerminated()).isFalse();

    blocker2.unpause();
    taskFutureResult.get(10, MILLISECONDS);
    blockingExecService.awaitTermination(10, MILLISECONDS);
    assertThat(blockingExecService.isTerminated()).isTrue();
  }

  @Test
  @Repeat(times = NUM_REPEATS)
  public void isTerminated_withRegularTask_afterAwaitTermination_shouldReturnTrue()
      throws Exception {
    blockingExecService = new BlockingExecutorService(configBuilder().build());
    blockingExecService.execute(() -> {
    });

    blockingExecService.shutdownNow();
    blockingExecService.awaitTermination(1, SECONDS);
    assertThat(blockingExecService.isTerminated()).isTrue();
  }

  // --------------------------------------------------------------------------
  //
  // Tests for awaitTermination(long, TimeUnit)
  //
  // --------------------------------------------------------------------------

  @Test
  @Repeat(times = NUM_REPEATS)
  public void awaitTermination_withNoTasks_shouldReturnImmediately() throws Exception {
    blockingExecService = new BlockingExecutorService(configBuilder().build());
    blockingExecService.shutdown();
    boolean stoppedInTime = blockingExecService.awaitTermination(1, MILLISECONDS);
    assertThat(stoppedInTime).isTrue();
    assertThat(blockingExecService.isTerminated()).isTrue();
  }

  @Test
  @Repeat(times = NUM_REPEATS)
  public void awaitTermination_withTaskShorterThanTimeout_shouldWaitForTask() throws Exception {
    blockingExecService = new BlockingExecutorService(configBuilder().build());
    PausingNoOpRunnable task = new PausingNoOpRunnable();
    blockingExecService.execute(task);
    task.waitUntilPaused(10, MILLISECONDS);

    blockingExecService.shutdown();
    boolean stoppedInTime = blockingExecService.awaitTermination(10, MILLISECONDS);
    assertThat(stoppedInTime).isFalse();

    task.unpause();
    blockingExecService.awaitTermination(1, SECONDS);
    assertThat(blockingExecService.isTerminated()).isTrue();
  }

  @Test
  @Repeat(times = NUM_REPEATS)
  public void awaitTermination_withTaskLongerThanTimeout_shouldTimeOut() throws Exception {
    blockingExecService = new BlockingExecutorService(configBuilder().build());
    PausingNoOpRunnable task = new PausingNoOpRunnable();
    blockingExecService.execute(task);
    task.waitUntilPaused(10, MILLISECONDS);

    blockingExecService.shutdown();

    boolean stoppedInTime = blockingExecService.awaitTermination(10, MILLISECONDS);
    assertThat(stoppedInTime).isFalse();
    blockingExecService.shutdownNow();
    blockingExecService.awaitTermination(1, SECONDS);
    assertThat(blockingExecService.isTerminated()).isTrue();
  }

  @Test
  @Repeat(times = NUM_REPEATS)
  public void awaitTermination_withQueuedAndRunningTasksShorterThanTimeout_shouldWait()
      throws Exception {
    BlockingExecutorServiceConfig config = configBuilder() //
        .setNumThreads(1) //
        .setQueueSize(1) //
        .build();
    blockingExecService = new BlockingExecutorService(config);

    PausingNoOpRunnable blockingTask = new PausingNoOpRunnable();
    AwaitableNoOpRunnable queuedTask = new AwaitableNoOpRunnable();
    blockingExecService.execute(blockingTask);
    blockingExecService.execute(queuedTask);

    blockingTask.waitUntilPaused(10, MILLISECONDS);

    blockingExecService.shutdown();
    blockingTask.unpause();

    boolean stoppedInTime = blockingExecService.awaitTermination(10, MILLISECONDS);
    assertThat(stoppedInTime).isTrue();

    blockingExecService.awaitTermination(1, SECONDS);
    assertThat(blockingExecService.isTerminated()).isTrue();
  }

  @Test
  @Repeat(times = NUM_REPEATS)
  public void awaitTermination_withQueuedAndRunningTasksLongerThanTimeout_shouldTimeOut()
      throws Exception {
    BlockingExecutorServiceConfig config = configBuilder().build();
    blockingExecService = new BlockingExecutorService(config);

    BlockingExecutorTestHelper queueHelper = new BlockingExecutorTestHelper(blockingExecService);
    queueHelper.fillThreadsAndQueueWithBlockingTasks();

    blockingExecService.shutdown();

    boolean stoppedInTime = blockingExecService.awaitTermination(100, MILLISECONDS);
    assertThat(stoppedInTime).isFalse();

    queueHelper.releaseBlockingTasks();

    stoppedInTime = blockingExecService.awaitTermination(100, MILLISECONDS);
    assertThat(stoppedInTime).isTrue();
    assertThat(blockingExecService.isTerminated()).isTrue();
  }

  // --------------------------------------------------------------------------
  //
  // Tests for submit(Callable<T>)
  //
  // --------------------------------------------------------------------------

  @Test(expected = NullPointerException.class)
  public void submitCallable_withNullCallable_shouldThrow() {
    blockingExecService = new BlockingExecutorService(configBuilder().build());
    @SuppressWarnings("unused")
    Future<Void> result = blockingExecService.submit((Callable<Void>) null);
  }

  @Test
  @Repeat(times = NUM_REPEATS)
  public void submitCallable_withNothingQueued_shouldRun() throws Exception {
    blockingExecService = new BlockingExecutorService(configBuilder().build());
    Callable<Integer> task = () -> 23456;
    Future<Integer> futureResult = blockingExecService.submit(task);
    Integer result = futureResult.get(10, MILLISECONDS);
    assertThat(result.intValue()).isEqualTo(23456);
  }

  @Test
  @Repeat(times = NUM_REPEATS)
  public void submitCallable_shouldTimeOperationsUsingStopwatch() throws Exception {
    // We will assume that the thorough timer testing in
    // execute_shouldTimeOperationsUsingMultistageStopwatch
    // is sufficient, and will just verify that the stopwatch is affected by submitCallable in some
    // way.

    MultistageStopwatch<Operation> stopwatch =
        new SimpleMultistageStopwatch<>(nanoSource, Operation.values());
    BlockingExecutorServiceConfig config = configBuilder().setStopwatch(stopwatch).build();
    blockingExecService = new BlockingExecutorService(config);

    Future<Object> futureResult = blockingExecService.submit(() -> new Object());
    futureResult.get(1, SECONDS);

    TimingSummary queueTiming = stopwatch.summarize(Operation.QUEUE);
    assertThat(queueTiming.numStartStopCycles()).isEqualTo(1);
    assertThat(queueTiming.totalElapsedTime().toNanos()).isAtLeast(1L);

    TimingSummary blockTiming = stopwatch.summarize(Operation.BLOCK);
    assertThat(blockTiming.numStartStopCycles()).isEqualTo(1);
    assertThat(blockTiming.totalElapsedTime().toNanos()).isAtLeast(1L);
  }

  @SuppressWarnings("FutureReturnValueIgnored")
  @Test(expected = RejectedExecutionException.class)
  public void submitCallable_withShutdownAlreadyCalled_shouldRejectTask() {
    blockingExecService = new BlockingExecutorService(configBuilder().build());
    blockingExecService.shutdown();
    blockingExecService.submit(() -> 45678);
  }

  @Test
  @Repeat(times = NUM_REPEATS)
  public void submitCallable_withFullQueue_shouldBlockThenRun() throws Exception {
    BlockingExecutorServiceConfig config = configBuilder() //
        .setNumThreads(5) //
        .setQueueSize(10) //
        .build();
    blockingExecService = new BlockingExecutorService(config);

    BlockingExecutorTestHelper queueHelper = new BlockingExecutorTestHelper(blockingExecService);
    queueHelper.fillThreadsAndQueueWithBlockingTasks();

    AwaitableNoOpRunnable awaitableTask = new AwaitableNoOpRunnable();
    ExecutorService taskSubmittingService = Executors.newSingleThreadExecutor();
    Callable<Integer> blocksWhileEnqueueingTask = () -> {
      blockingExecService.execute(awaitableTask);
      return 34567;
    };
    Future<Integer> futureIntegerResult = taskSubmittingService.submit(blocksWhileEnqueueingTask);

    // Verify that blocksWhileEnqueueingTask has really blocked.
    try {
      futureIntegerResult.get(10, MILLISECONDS);
      Truth.assert_().fail("Expected futureIntegerResult not to return a value yet.");
    } catch (@SuppressWarnings("unused") TimeoutException expected) {
      // Do nothing; this is the expected behavior.
    }

    queueHelper.releaseBlockingTasks();

    Integer integerResult = futureIntegerResult.get(1, SECONDS);
    assertThat(integerResult.intValue()).isEqualTo(34567);

    awaitableTask.awaitTaskCompletion(10, MILLISECONDS);

    taskSubmittingService.shutdown();
  }

  // --------------------------------------------------------------------------
  //
  // Tests for submit(Runnable)
  //
  // --------------------------------------------------------------------------


  @SuppressWarnings("FutureReturnValueIgnored")
  @Test(expected = NullPointerException.class)
  public void submitRunnable_withNullRunnable_shouldThrow() {
    blockingExecService = new BlockingExecutorService(configBuilder().build());
    blockingExecService.submit((Runnable) null);
  }

  @Test
  @Repeat(times = NUM_REPEATS)
  public void submitRunnable_withNothingQueued_shouldRun() throws Exception {
    blockingExecService = new BlockingExecutorService(configBuilder().build());
    Future<Void> futureResult = blockingExecService.submit(() -> null);
    futureResult.get(10, MILLISECONDS);
  }

  @Test
  @Repeat(times = NUM_REPEATS)
  public void submitRunnable_withFullQueue_shouldBlockThenRun() throws Exception {
    BlockingExecutorServiceConfig config = configBuilder().build();
    blockingExecService = new BlockingExecutorService(config);

    BlockingExecutorTestHelper queueHelper = new BlockingExecutorTestHelper(blockingExecService);
    queueHelper.fillThreadsAndQueueWithBlockingTasks();

    ExecutorService taskSubmittingService = Executors.newSingleThreadExecutor();
    AwaitableNoOpRunnable additionalTask = new AwaitableNoOpRunnable();
    CountDownLatch haveSubmittedTask = new CountDownLatch(1);
    @SuppressWarnings("FutureReturnValueIgnored")
    Runnable submitAdditionalTask = () -> {
      blockingExecService.submit(additionalTask);
      haveSubmittedTask.countDown();
    };
    taskSubmittingService.execute(submitAdditionalTask);

    // submitAdditionalTask is expected to have blocked.
    boolean finishedInTime = haveSubmittedTask.await(10, MILLISECONDS);
    assertThat(finishedInTime).isFalse();

    queueHelper.releaseBlockingTasks();

    finishedInTime = additionalTask.awaitTaskCompletion(10, MILLISECONDS);
    assertThat(finishedInTime).isTrue();

    taskSubmittingService.shutdownNow();
  }

  @SuppressWarnings("FutureReturnValueIgnored")
  @Test(expected = RejectedExecutionException.class)
  public void submitRunnable_withShutdownAlreadyCalled_shouldRejectTask() {
    blockingExecService = new BlockingExecutorService(configBuilder().build());
    blockingExecService.shutdown();
    blockingExecService.submit(() -> {
    });
  }

  // --------------------------------------------------------------------------
  //
  // Tests for submit(Runnable, T)
  //
  // --------------------------------------------------------------------------

  @Test
  @Repeat(times = NUM_REPEATS)
  public void submitRunnableWithResult_withNothingQueued_shouldRun() throws Exception {
    blockingExecService = new BlockingExecutorService(configBuilder().build());
    PausingAwaitableNoOpRunnable task = new PausingAwaitableNoOpRunnable();
    Future<Integer> futureResult = blockingExecService.submit(task, Integer.MAX_VALUE);

    task.waitUntilPaused(10, MILLISECONDS);

    // The constructed future shouldn't be done yet, since the task is still running.
    assertThat(futureResult.isDone()).isFalse();

    task.unpause();
    task.awaitTaskCompletion(1, SECONDS);
    Integer result = futureResult.get(1, TimeUnit.MILLISECONDS);
    assertThat(result).isEqualTo(Integer.MAX_VALUE);
  }

  // --------------------------------------------------------------------------
  //
  // Tests for invokeAll(Collection<? extends Callable<T>>)
  //
  // --------------------------------------------------------------------------

  private void checkThatInvokeAllSucceeds(int numTasks) throws Exception {
    List<Callable<Integer>> tasks = BlockingExecutorTestHelper.buildIntCubingCallables(numTasks);

    ExecutorService taskSubmittingService = Executors.newSingleThreadExecutor();
    Callable<List<Future<Integer>>> callInvokeAll = () -> {
      return blockingExecService.invokeAll(tasks);
    };
    Future<List<Future<Integer>>> futureSubmitResult = taskSubmittingService.submit(callInvokeAll);

    List<Future<Integer>> listOfIndividualFutureResults = futureSubmitResult.get(200, MILLISECONDS);
    assertThat(listOfIndividualFutureResults).hasSize(numTasks);

    // invokeAll's semantics state that all of the futures should already be done before the method
    // returns. So we'll verify that first, without calling get() yet (since that may block and
    // conceal an incorrect implementation that would return an incomplete Future).
    for (int i = 0; i < numTasks; i++) {
      Future<Integer> futureResult = listOfIndividualFutureResults.get(i);
      assertThat(futureResult.isDone()).isTrue();
    }

    // Make sure that we got the correct result values.
    int[] results = new int[listOfIndividualFutureResults.size()];
    for (int i = 0; i < results.length; i++) {
      Future<Integer> futureResult = listOfIndividualFutureResults.get(i);
      // Getting a result should be quick, since every task is already done.
      results[i] = futureResult.get(1, MILLISECONDS);
    }
    BlockingExecutorTestHelper.verifyResultsOfIntCubingCallables(results);

    taskSubmittingService.shutdownNow();
  }

  @Test
  @Repeat(times = NUM_REPEATS)
  public void invokeAll_withTooManyForQueue_shouldRunAll() throws Exception {
    blockingExecService = new BlockingExecutorService(configBuilder().build());
    int numTasks = blockingExecService.maxUnblockedTaskCount() * 2;
    checkThatInvokeAllSucceeds(numTasks);
  }

  @Test
  @Repeat(times = NUM_REPEATS)
  public void invokeAll_withQueueSpaceForAllTasks_shouldQueueAndReturnImmediately()
      throws Exception {
    blockingExecService = new BlockingExecutorService(configBuilder().build());
    int numTasks = blockingExecService.maxUnblockedTaskCount() - 1;
    checkThatInvokeAllSucceeds(numTasks);
  }

  // --------------------------------------------------------------------------
  //
  // Tests for invokeAll(Collection<? extends Callable<T>>, long, TimeUnit)
  //
  // --------------------------------------------------------------------------


  @Test
  @Repeat(times = NUM_REPEATS)
  public void invokeAll_withAllTasksTooSlowForTimeout_shouldCancelAllTasks() throws Exception {
    BlockingExecutorServiceConfig config = configBuilder() //
        .setCurrentNanosSource(() -> System.nanoTime()) //
        .build();
    blockingExecService = new BlockingExecutorService(config);

    int numTasks = 20;
    List<PausingNoOpCallable> tasks = BlockingExecutorTestHelper.buildBlockingCallables(numTasks);

    ExecutorService taskSubmittingService = Executors.newSingleThreadExecutor();
    Callable<List<Future<Integer>>> callInvokeAll = () -> {
      return blockingExecService.invokeAll(tasks, 25, MILLISECONDS);
    };
    Future<List<Future<Integer>>> futureSubmitResult = taskSubmittingService.submit(callInvokeAll);

    List<Future<Integer>> listOfIndividualFutureResults = futureSubmitResult.get(1, SECONDS);
    assertThat(listOfIndividualFutureResults).hasSize(0);

    // Clean up all of those blocked tasks.
    for (PausingNoOpCallable task : tasks) {
      task.unpause();
    }

    taskSubmittingService.shutdownNow();
  }

  @Test
  @Repeat(times = NUM_REPEATS)
  public void invokeAll_withSomeTasksTooSlowForTimeout_shouldCancelSlowTasks() throws Exception {
    BlockingExecutorServiceConfig config = configBuilder().build();
    blockingExecService = new BlockingExecutorService(config);

    int numSlowpokes = config.numThreads() - 1; // Must leave a thread for succeeders to run on
    int numSucceeders = blockingExecService.maxUnblockedTaskCount() * 2;
    List<PausingNoOpCallable> blockers =
        BlockingExecutorTestHelper.buildBlockingCallables(numSlowpokes);
    List<Callable<Integer>> succeeders =
        BlockingExecutorTestHelper.buildIntCubingCallables(numSucceeders);

    ArrayList<Callable<Integer>> tasks = new ArrayList<>();
    tasks.addAll(blockers);
    tasks.addAll(succeeders);

    ExecutorService taskSubmittingService = Executors.newSingleThreadExecutor();
    Callable<List<Future<Integer>>> callInvokeAll = () -> {
      return blockingExecService.invokeAll(tasks, 100, MILLISECONDS);
    };
    Future<List<Future<Integer>>> futureSubmitResult = taskSubmittingService.submit(callInvokeAll);

    List<Future<Integer>> listOfIndividualFutureResults = futureSubmitResult.get(1, SECONDS);
    assertThat(listOfIndividualFutureResults).hasSize(numSucceeders);

    ArrayList<Integer> results = new ArrayList<>();
    for (Future<Integer> futureResult : listOfIndividualFutureResults) {
      results.add(futureResult.get(10, MILLISECONDS));
    }
    BlockingExecutorTestHelper.verifyResultsOfIntCubingCallables(results);

    // Clean up all of those blocked tasks.
    for (PausingNoOpCallable blocker : blockers) {
      blocker.unpause();
    }

    taskSubmittingService.shutdownNow();
  }

  @Test
  @Repeat(times = NUM_REPEATS)
  public void invokeAll_withAllTasksFasterThanTimeout_shouldCancelNone() throws Exception {
    BlockingExecutorServiceConfig config = configBuilder().build();
    blockingExecService = new BlockingExecutorService(config);

    int numSuccessfulTasks = 100;
    List<Callable<Integer>> tasks =
        BlockingExecutorTestHelper.buildIntCubingCallables(numSuccessfulTasks);

    ExecutorService taskSubmittingService = Executors.newSingleThreadExecutor();
    Callable<List<Future<Integer>>> callInvokeAll = () -> {
      return blockingExecService.invokeAll(tasks, 100, MILLISECONDS);
    };
    Future<List<Future<Integer>>> futureSubmitResult = taskSubmittingService.submit(callInvokeAll);

    List<Future<Integer>> listOfIndividualFutureResults = futureSubmitResult.get(1, SECONDS);
    assertThat(listOfIndividualFutureResults).hasSize(numSuccessfulTasks);

    ArrayList<Integer> results = new ArrayList<>();
    for (Future<Integer> futureResult : listOfIndividualFutureResults) {
      results.add(futureResult.get(10, MILLISECONDS));
    }
    BlockingExecutorTestHelper.verifyResultsOfIntCubingCallables(results);

    taskSubmittingService.shutdownNow();
  }

  // --------------------------------------------------------------------------
  //
  // Tests for invokeAny(Collection<? extends Callable<T>>)
  //
  // --------------------------------------------------------------------------

  // invokeAny should not run unnecessary tasks, nor should it let tasks that are running finish
  // once it has a single successful result.
  private void checkInvokeAnyCancelsExtraTasks(
      int numExtraTasks, int indexOfSuccessfulTask) throws Exception {
    // ExecutorService#invokeAny returns a single Future<T> that contains the result of the first
    // task that succeeded. For the purposes of this test we want to observe what happened to the
    // other tasks too, to make sure that they were handled properly. 

    final int totalNumTasks = numExtraTasks + 1;
    final int numCancellableSlowTasks = numExtraTasks - indexOfSuccessfulTask;
    ArrayList<Callable<Long>> tasks = new ArrayList<>(totalNumTasks);

    // Holder of results of the cancellable tasks.
    AtomicIntegerArray taskSideResults = new AtomicIntegerArray(numCancellableSlowTasks);

    // Add tasks whose purpose is to run and immediately fail, prior to the successful run of
    // immediateSuccessTask.
    for (int i = 0; i < indexOfSuccessfulTask; i++) {
      tasks.add(new FailingCallable<Long>());
    }

    // This task should succeed, which should cause invokeAny to cancel (or not submit) any
    // of the remaining tasks.
    final Callable<Long> immediateSuccessTask = () -> 1L;
    tasks.add(immediateSuccessTask);

    Semaphore slowRunningTaskPermit = new Semaphore(numCancellableSlowTasks);
    CountDownLatch canSlowRunningTasksFinish = new CountDownLatch(1);

    // Add the slow-running extra tasks.
    for (int i = 0; i < numCancellableSlowTasks; i++) {
      final long returnValue = 100L + i;
      final int resultIndex = i;
      tasks.add(() -> {
        slowRunningTaskPermit.acquire();
        taskSideResults.set(resultIndex, STARTED);
        try {
          canSlowRunningTasksFinish.await();
        } catch (@SuppressWarnings("unused") InterruptedException ie) {
          taskSideResults.set(resultIndex, WAS_CANCELLED);
        } finally {
          slowRunningTaskPermit.release();
        }
        taskSideResults.set(resultIndex, FINISHED);
        return returnValue;
      });
    }

    ExecutorService taskSubmittingService = Executors.newSingleThreadExecutor();
    Future<Long> futureResultOfInvokeAny =
        taskSubmittingService.submit(() -> blockingExecService.invokeAny(tasks));

    // It shouldn't take long for immediateSuccessTask to be scheduled and to finish.
    Long resultOfInvokeAny = futureResultOfInvokeAny.get(50, MILLISECONDS);
    assertThat(resultOfInvokeAny).isEqualTo(1L);

    // Now, wait for all of the slow-running tasks that started to be cancelled and finish.
    boolean extraTasksFinished = slowRunningTaskPermit.tryAcquire(numExtraTasks, 100, MILLISECONDS);

    // If there are still some that haven't finished, that's a test failure. Unblock those tasks
    // now, and we'll fail later in this test since those tasks will mark taskSideResults with
    // FINISHED.
    if (extraTasksFinished) {
      canSlowRunningTasksFinish.countDown();
      // Give them a chance to finish.
      slowRunningTaskPermit.tryAcquire(numExtraTasks, 100, MILLISECONDS);
    }

    List<Integer> taskSideResultsArray = asList(taskSideResults);
    assertThat(taskSideResultsArray).containsNoneOf(STARTED, FINISHED);

    taskSubmittingService.shutdownNow();
  }

  private static List<Integer> asList(AtomicIntegerArray atomicIntArray) {
    final int size = atomicIntArray.length();
    ArrayList<Integer> results = new ArrayList<>(size);
    for (int i = 0; i < size; i++) {
      results.add(atomicIntArray.get(i));
    }
    return results;
  }

  @Test
  @Repeat(times = NUM_REPEATS)
  public void invokeAny_withManyTasksAllSuccessful_shouldCancelSlowOnesStillRunning()
      throws Exception {
    BlockingExecutorServiceConfig config = configBuilder()
        .setNumThreads(3) //
        .build();
    blockingExecService = new BlockingExecutorService(config);
    checkInvokeAnyCancelsExtraTasks(1, 0);
    checkInvokeAnyCancelsExtraTasks(2, 2);
    checkInvokeAnyCancelsExtraTasks(15, 0);
    checkInvokeAnyCancelsExtraTasks(15, 15);
  }

  @Test
  @Repeat(times = NUM_REPEATS)
  public void invokeAny_withOnlyLastTaskSuccessful_shouldRunAll() throws Exception {
    BlockingExecutorServiceConfig config = configBuilder().build();
    blockingExecService = new BlockingExecutorService(config);

    int numToInvoke = 200;
    List<Callable<Integer>> invokable = new ArrayList<>();
    // Fill invokable with a bunch of failing Callables and one that will succeed.
    invokable.addAll(BlockingExecutorTestHelper.buildFailingCallables(numToInvoke - 1));
    invokable.addAll(BlockingExecutorTestHelper.buildIntCubingCallables(1));

    Callable<Integer> callInvokeAny = () -> blockingExecService.invokeAny(invokable);
    ExecutorService taskSubmittingService = Executors.newSingleThreadExecutor();
    Future<Integer> futureResult = taskSubmittingService.submit(callInvokeAny);

    Integer result = futureResult.get(1, SECONDS);
    assertThat(result).isEqualTo(1);

    taskSubmittingService.shutdownNow();
  }

  @Test
  @Repeat(times = NUM_REPEATS)
  public void invokeAny_withAllTasksFailingQuickly_shouldThrowExecutionException()
      throws Exception {
    BlockingExecutorServiceConfig config = configBuilder().build();
    blockingExecService = new BlockingExecutorService(config);

    List<FailingCallable<Integer>> failers = BlockingExecutorTestHelper.buildFailingCallables(1);
    try {
      blockingExecService.invokeAny(failers);
      Truth.assert_().fail("Expected invokeAny to throw an exception since all tasks fail.");
    } catch (ExecutionException ee) {
      assertThat(ee).hasCauseThat().hasMessageThat().isEqualTo(FailingCallable.FAILURE_MESSAGE);
    }
  }

  @Test
  @Repeat(times = NUM_REPEATS)
  public void invokeAny_cancelledWhileWaitingForEndlessTasks_shouldThrowInterruptedException()
      throws Exception {
    BlockingExecutorServiceConfig config = configBuilder().build();
    blockingExecService = new BlockingExecutorService(config);

    List<PausingNoOpCallable> pausers = BlockingExecutorTestHelper.buildBlockingCallables(1);
    CountDownLatch interruptedExceptionsNeeded = new CountDownLatch(1);
    Callable<Integer> callInvokeAny = () -> {
      try {
        return blockingExecService.invokeAny(pausers);
      } catch (InterruptedException ie) {
        interruptedExceptionsNeeded.countDown();
        throw ie;
      }
    };
    ExecutorService taskSubmittingService = Executors.newSingleThreadExecutor();
    Future<Integer> futureResult = taskSubmittingService.submit(callInvokeAny);

    pausers.get(0).waitUntilPaused(1, SECONDS);

    futureResult.cancel(true);
    boolean invokeAnyThrewExceptionInTime = interruptedExceptionsNeeded.await(100, MILLISECONDS);
    assertThat(invokeAnyThrewExceptionInTime).isTrue();
  }

  // --------------------------------------------------------------------------
  //
  // Tests for invokeAny(Collection<? extends Callable<T>>, long, TimeUnit)
  //
  // --------------------------------------------------------------------------

  @Test
  @Repeat(times = NUM_REPEATS)
  public void invokeAny_withTimeoutLongerThanSuccessfulTask_shouldReturnResult() throws Exception {
    BlockingExecutorServiceConfig config =
        configBuilder().setCurrentNanosSource(() -> System.nanoTime()).build();
    blockingExecService = new BlockingExecutorService(config);

    int numToInvoke = 10;
    List<Callable<Integer>> invokable = new ArrayList<>();
    // Fill invokable with a bunch of failing Callables and one that will succeed.
    invokable.addAll(BlockingExecutorTestHelper.buildFailingCallables(numToInvoke - 1));
    invokable.add(() -> 12345);

    Callable<Integer> callInvokeAny =
        () -> blockingExecService.invokeAny(invokable, 200, MILLISECONDS);
    ExecutorService taskSubmittingService = Executors.newSingleThreadExecutor();
    Future<Integer> futureResult = taskSubmittingService.submit(callInvokeAny);

    Integer result = futureResult.get(1, SECONDS);
    assertThat(result).isEqualTo(12345);

    taskSubmittingService.shutdownNow();
  }

  @Test
  @Repeat(times = NUM_REPEATS)
  public void invokeAny_withTimeoutShorterThanSuccessfulTask_shouldThrowTimeoutException()
      throws Exception {
    BlockingExecutorServiceConfig config = configBuilder().build();
    blockingExecService = new BlockingExecutorService(config);

    int numToInvoke = 10;
    List<Callable<Integer>> invokable = new ArrayList<>();
    // Fill invokable with a bunch of failing Callables and one that will succeed.
    invokable.addAll(BlockingExecutorTestHelper.buildFailingCallables(numToInvoke - 1));

    CountDownLatch timeoutsNeeded = new CountDownLatch(1);

    PausingNoOpRunnable pauser = new PausingNoOpRunnable();
    Callable<Integer> pausingCallable = () -> {
      pauser.run();
      return 1;
    };
    invokable.add(pausingCallable);

    Callable<Integer> callInvokeAny = () -> {
      try {
        return blockingExecService.invokeAny(invokable, 10, MILLISECONDS);
      } catch (@SuppressWarnings("unused") TimeoutException te) {
        timeoutsNeeded.countDown();
      }
      return Integer.MIN_VALUE;
    };
    ExecutorService taskSubmittingService = Executors.newSingleThreadExecutor();
    Future<Integer> futureResult = taskSubmittingService.submit(callInvokeAny);

    pauser.waitUntilPaused(1, SECONDS);
    assertThat(pauser.hasPaused()).isTrue();

    // Verify that callInvokeAny finished quickly (since it should time out after only 10ms).
    futureResult.get(100, MILLISECONDS);

    taskSubmittingService.shutdownNow();
  }
}
