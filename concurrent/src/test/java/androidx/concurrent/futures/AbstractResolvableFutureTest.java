/*
 * Copyright 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.concurrent.futures;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import androidx.annotation.Nullable;

import com.google.common.collect.Iterables;
import com.google.common.collect.Range;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.Atomics;
import com.google.common.util.concurrent.Uninterruptibles;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

import junit.framework.AssertionFailedError;
import junit.framework.TestCase;

import org.junit.internal.runners.JUnit38ClassRunner;
import org.junit.runner.RunWith;

/**
 * Tests for {@link AbstractResolvableFuture}.
 *
 * @author Brian Stoler
 */
@RunWith(JUnit38ClassRunner.class)
public class AbstractResolvableFutureTest extends TestCase {

    public void testSuccess() throws ExecutionException, InterruptedException {
        final Object newValue = new Object();
        assertSame(
                newValue,
                new AbstractResolvableFuture<Object>() {
                    {
                        set(newValue);
                    }
                }.get());
    }

    public void testException() throws InterruptedException {
        final Throwable failure = new Throwable();
        AbstractResolvableFuture<String> future =
                new AbstractResolvableFuture<String>() {
                    {
                        setException(failure);
                    }
                };

        ExecutionException ee1 = getExpectingExecutionException(future);
        ExecutionException ee2 = getExpectingExecutionException(future);

        // Ensure we get a unique execution exception on each get
        assertNotSame(ee1, ee2);

        assertSame(failure, ee1.getCause());
        assertSame(failure, ee2.getCause());

        checkStackTrace(ee1);
        checkStackTrace(ee2);
    }

    public void testCancel_notDoneNoInterrupt() throws Exception {
        InterruptibleFuture future = new InterruptibleFuture();
        assertTrue(future.cancel(false));
        assertTrue(future.isCancelled());
        assertTrue(future.isDone());
        assertFalse(future.wasInterrupted());
        assertFalse(future.interruptTaskWasCalled);
        try {
            future.get();
            fail("Expected CancellationException");
        } catch (CancellationException e) {
            // See AbstractFutureCancellationCauseTest for how to set causes
            assertNull(e.getCause());
        }
    }

    public void testCancel_notDoneInterrupt() throws Exception {
        InterruptibleFuture future = new InterruptibleFuture();
        assertTrue(future.cancel(true));
        assertTrue(future.isCancelled());
        assertTrue(future.isDone());
        assertTrue(future.wasInterrupted());
        assertTrue(future.interruptTaskWasCalled);
        try {
            future.get();
            fail("Expected CancellationException");
        } catch (CancellationException e) {
            // See AbstractFutureCancellationCauseTest for how to set causes
            assertNull(e.getCause());
        }
    }

    public void testCancel_done() throws Exception {
        AbstractResolvableFuture<String> future =
                new AbstractResolvableFuture<String>() {
                    {
                        set("foo");
                    }
                };
        assertFalse(future.cancel(true));
        assertFalse(future.isCancelled());
        assertTrue(future.isDone());
    }

    public void testGetWithTimeoutDoneFuture() throws Exception {
        AbstractResolvableFuture<String> future =
                new AbstractResolvableFuture<String>() {
                    {
                        set("foo");
                    }
                };
        assertEquals("foo", future.get(0, TimeUnit.SECONDS));
    }

    public void testRemoveWaiter_interruption() throws Exception {
        final AbstractResolvableFuture<String> future = new AbstractResolvableFuture<String>() {
        };
        WaiterThread waiter1 = new WaiterThread(future);
        waiter1.start();
        waiter1.awaitWaiting();

        WaiterThread waiter2 = new WaiterThread(future);
        waiter2.start();
        waiter2.awaitWaiting();
        // The waiter queue should be waiter2->waiter1

        // This should wake up waiter1 and cause the waiter1 node to be removed.
        waiter1.interrupt();

        waiter1.join();
        waiter2.awaitWaiting(); // should still be blocked

        LockSupport.unpark(waiter2); // spurious wakeup
        waiter2.awaitWaiting(); // should eventually re-park

        future.set(null);
        waiter2.join();
    }

    public void testRemoveWaiter_polling() throws Exception {
        final AbstractResolvableFuture<String> future = new AbstractResolvableFuture<String>() {
        };
        WaiterThread waiter = new WaiterThread(future);
        waiter.start();
        waiter.awaitWaiting();
        PollingThread poller = new PollingThread(future);
        poller.start();
        PollingThread poller2 = new PollingThread(future);
        poller2.start();
        PollingThread poller3 = new PollingThread(future);
        poller3.start();
        poller.awaitInLoop();
        poller2.awaitInLoop();
        poller3.awaitInLoop();

        // The waiter queue should be {poller x 3}->waiter1
        waiter.interrupt();

        // This should wake up waiter1 and cause the waiter1 node to be removed.
        waiter.join();
        future.set(null);
        poller.join();
    }

    public void testToString_allUnique() throws Exception {
        // Two futures should not have the same toString, to avoid people asserting on it
        assertThat(ResolvableFuture.create().toString()).isNotEqualTo(
                ResolvableFuture.create().toString());
    }

    public void testToString_notDone() throws Exception {
        AbstractResolvableFuture<Object> testFuture =
                new AbstractResolvableFuture<Object>() {
                    @Override
                    public String pendingToString() {
                        return "cause=[Because this test isn't done]";
                    }
                };
        assertThat(testFuture.toString())
                .matches(
                        "[^\\[]+\\[status=PENDING, info=\\[cause=\\[Because this test isn't "
                                + "done\\]\\]\\]");
        try {
            testFuture.get(1, TimeUnit.NANOSECONDS);
            fail();
        } catch (TimeoutException e) {
            assertThat(e.getMessage()).contains("1 nanoseconds");
            assertThat(e.getMessage()).contains("Because this test isn't done");
        }
    }

    /**
     * This test attempts to cause a future to wait for longer than it was requested to from a timed
     * get() call. As measurements of time are prone to flakiness, it tries to assert based on
     * ranges
     * derived from observing how much time actually passed for various operations.
     */
    @SuppressWarnings({"DeprecatedThreadMethods", "ThreadPriorityCheck"})
    public void testToString_delayedTimeout() throws Exception {
        TimedWaiterThread thread =
                new TimedWaiterThread(new AbstractResolvableFuture<Object>() {
                }, 2, TimeUnit.SECONDS);
        thread.start();
        thread.awaitWaiting();
        thread.suspend();
        // Sleep for enough time to add 1500 milliseconds of overwait to the get() call.
        long toWaitMillis = 3500 - TimeUnit.NANOSECONDS.toMillis(
                System.nanoTime() - thread.startTime);
        Thread.sleep(toWaitMillis);
        thread.setPriority(Thread.MAX_PRIORITY);
        thread.resume();
        thread.join();
        // It's possible to race and suspend the thread just before the park call actually takes
        // effect,
        // causing the thread to be suspended for 3.5 seconds, and then park itself for 2 seconds
        // after
        // being resumed. To avoid a flake in this scenario, calculate how long that thread actually
        // waited and assert based on that time. Empirically, the race where the thread ends up
        // waiting
        // for 5.5 seconds happens about 2% of the time.
        boolean longWait = TimeUnit.NANOSECONDS.toSeconds(thread.timeSpentBlocked) >= 5;
        // Count how long it actually took to return; we'll accept any number between the
        // expected delay
        // and the approximate actual delay, to be robust to variance in thread scheduling.
        char overWaitNanosFirstDigit =
                Long.toString(
                        thread.timeSpentBlocked - TimeUnit.MILLISECONDS.toNanos(
                                longWait ? 5000 : 3000))
                        .charAt(0);
        if (overWaitNanosFirstDigit < '4') {
            overWaitNanosFirstDigit = '9';
        }
        String nanosRegex = "[4-" + overWaitNanosFirstDigit + "][0-9]+";
        assertWithMessage(
                "Spent " + thread.timeSpentBlocked + " ns blocked; slept for " + toWaitMillis
                        + " ms")
                .that(thread.exception)
                .hasMessageThat()
                .matches(
                        "Waited 2 seconds \\(plus "
                                + (longWait ? "3" : "1")
                                + " seconds, "
                                + nanosRegex
                                + " nanoseconds delay\\).*");
    }

    public void testToString_completed() throws Exception {
        AbstractResolvableFuture<Object> testFuture2 =
                new AbstractResolvableFuture<Object>() {
                    @Override
                    public String pendingToString() {
                        return "cause=[Someday...]";
                    }
                };
        AbstractResolvableFuture<Object> testFuture3 = new AbstractResolvableFuture<Object>() {
        };
        testFuture3.setFuture(testFuture2);
        assertThat(testFuture3.toString())
                .matches(
                        "[^\\[]+\\[status=PENDING, info=\\[setFuture="
                                + "\\[[^\\[]+\\[status=PENDING, "
                                + "info=\\[cause=\\[Someday...\\]\\]\\]\\]\\]\\]");
        testFuture2.set("result string");
        assertThat(testFuture3.toString())
                .matches("[^\\[]+\\[status=SUCCESS, result=\\[result string\\]\\]");
    }

    public void testToString_cancelled() throws Exception {
        assertThat(new ImmediateFuture.ImmediateCancelledFuture<>().toString())
                .matches("[^\\[]+\\[status=CANCELLED\\]");
    }

    public void testToString_failed() {
        assertThat(
                new ImmediateFuture.ImmediateFailedFuture(new RuntimeException("foo")).toString())
                .matches(
                        "[^\\[]+\\[status=FAILURE, cause=\\[java.lang.RuntimeException: foo\\]\\]");
    }

    public void testToString_misbehaving() throws Exception {
        assertThat(
                new AbstractResolvableFuture<Object>() {
                    @Override
                    public String pendingToString() {
                        throw new RuntimeException("I'm a misbehaving implementation");
                    }
                }.toString())
                .matches(
                        "[^\\[]+\\[status=PENDING, info=\\[Exception thrown from implementation: "
                                + "class java.lang.RuntimeException\\]\\]");
    }

    public void testCompletionFinishesWithDone() {
        ExecutorService executor = Executors.newFixedThreadPool(10);
        for (int i = 0; i < 50000; i++) {
            final AbstractResolvableFuture<String> future = new AbstractResolvableFuture<String>() {
            };
            final AtomicReference<String> errorMessage = Atomics.newReference();
            executor.execute(
                    new Runnable() {
                        @Override
                        public void run() {
                            future.set("success");
                            if (!future.isDone()) {
                                errorMessage.set("Set call exited before future was complete.");
                            }
                        }
                    });
            executor.execute(
                    new Runnable() {
                        @Override
                        public void run() {
                            future.setException(new IllegalArgumentException("failure"));
                            if (!future.isDone()) {
                                errorMessage.set(
                                        "SetException call exited before future was complete.");
                            }
                        }
                    });
            executor.execute(
                    new Runnable() {
                        @Override
                        public void run() {
                            future.cancel(true);
                            if (!future.isDone()) {
                                errorMessage.set("Cancel call exited before future was complete.");
                            }
                        }
                    });
            try {
                future.get();
            } catch (Throwable t) {
                // Ignore, we just wanted to block.
            }
            String error = errorMessage.get();
            assertNull(error, error);
        }
        executor.shutdown();
    }

    /**
     * He did the bash, he did the future bash The future bash, it was a concurrency smash He did
     * the
     * bash, it caught on in a flash He did the bash, he did the future bash
     */
//    @Sequential // messing with class loading MOE:strip_line
    public void testFutureBash() {
        final CyclicBarrier barrier =
                new CyclicBarrier(
                        6 // for the setter threads
                                + 50 // for the listeners
                                + 50 // for the blocking get threads,
                                + 1); // for the main thread
        final ExecutorService executor = Executors.newFixedThreadPool(barrier.getParties());
        final AtomicReference<AbstractResolvableFuture<String>> currentFuture =
                Atomics.newReference();
        final AtomicInteger numSuccessfulSetCalls = new AtomicInteger();
        Callable<Void> completeSucessFullyRunnable =
                new Callable<Void>() {
                    @Override
                    public Void call() {
                        if (currentFuture.get().set("set")) {
                            numSuccessfulSetCalls.incrementAndGet();
                        }
                        awaitUnchecked(barrier);
                        return null;
                    }
                };
        Callable<Void> completeExceptionallyRunnable =
                new Callable<Void>() {
                    Exception failureCause = new Exception("setException");

                    @Override
                    public Void call() {
                        if (currentFuture.get().setException(failureCause)) {
                            numSuccessfulSetCalls.incrementAndGet();
                        }
                        awaitUnchecked(barrier);
                        return null;
                    }
                };
        Callable<Void> cancelRunnable =
                new Callable<Void>() {
                    @Override
                    public Void call() {
                        if (currentFuture.get().cancel(true)) {
                            numSuccessfulSetCalls.incrementAndGet();
                        }
                        awaitUnchecked(barrier);
                        return null;
                    }
                };
        Callable<Void> setFutureCompleteSucessFullyRunnable =
                new Callable<Void>() {
                    ListenableFuture<String> future =
                            new ImmediateFuture.ImmediateSuccessfulFuture<>("setFuture");

                    @Override
                    public Void call() {
                        if (currentFuture.get().setFuture(future)) {
                            numSuccessfulSetCalls.incrementAndGet();
                        }
                        awaitUnchecked(barrier);
                        return null;
                    }
                };
        Callable<Void> setFutureCompleteExceptionallyRunnable =
                new Callable<Void>() {
                    ListenableFuture<String> future =
                            new ImmediateFuture.ImmediateFailedFuture<>(new Exception("setFuture"));

                    @Override
                    public Void call() {
                        if (currentFuture.get().setFuture(future)) {
                            numSuccessfulSetCalls.incrementAndGet();
                        }
                        awaitUnchecked(barrier);
                        return null;
                    }
                };
        Callable<Void> setFutureCancelRunnable =
                new Callable<Void>() {
                    ListenableFuture<String> future =
                            new ImmediateFuture.ImmediateCancelledFuture<>();

                    @Override
                    public Void call() {
                        if (currentFuture.get().setFuture(future)) {
                            numSuccessfulSetCalls.incrementAndGet();
                        }
                        awaitUnchecked(barrier);
                        return null;
                    }
                };
        final Set<Object> finalResults = Collections.synchronizedSet(Sets.newIdentityHashSet());
        Runnable collectResultsRunnable =
                new Runnable() {
                    @Override
                    public void run() {
                        try {
                            String result = Uninterruptibles.getUninterruptibly(
                                    currentFuture.get());
                            finalResults.add(result);
                        } catch (ExecutionException e) {
                            finalResults.add(e.getCause());
                        } catch (CancellationException e) {
                            finalResults.add(CancellationException.class);
                        } finally {
                            awaitUnchecked(barrier);
                        }
                    }
                };
        Runnable collectResultsTimedGetRunnable =
                new Runnable() {
                    @Override
                    public void run() {
                        Future<String> future = currentFuture.get();
                        while (true) {
                            try {
                                String result = Uninterruptibles.getUninterruptibly(future, 0,
                                        TimeUnit.SECONDS);
                                finalResults.add(result);
                                break;
                            } catch (ExecutionException e) {
                                finalResults.add(e.getCause());
                                break;
                            } catch (CancellationException e) {
                                finalResults.add(CancellationException.class);
                                break;
                            } catch (TimeoutException e) {
                                // loop
                            }
                        }
                        awaitUnchecked(barrier);
                    }
                };
        List<Callable<?>> allTasks = new ArrayList<>();
        allTasks.add(completeSucessFullyRunnable);
        allTasks.add(completeExceptionallyRunnable);
        allTasks.add(cancelRunnable);
        allTasks.add(setFutureCompleteSucessFullyRunnable);
        allTasks.add(setFutureCompleteExceptionallyRunnable);
        allTasks.add(setFutureCancelRunnable);
        for (int k = 0; k < 50; k++) {
            // For each listener we add a task that submits it to the executor directly for the
            // blocking
            // get usecase and another task that adds it as a listener to the future to exercise
            // both
            // racing addListener calls and addListener calls completing after the future completes.
            final Runnable listener =
                    k % 2 == 0 ? collectResultsRunnable : collectResultsTimedGetRunnable;
            allTasks.add(Executors.callable(listener));
            allTasks.add(
                    new Callable<Void>() {
                        @Override
                        public Void call() throws Exception {
                            currentFuture.get().addListener(listener, executor);
                            return null;
                        }
                    });
        }
        assertEquals(allTasks.size() + 1, barrier.getParties());
        for (int i = 0; i < 1000; i++) {
            Collections.shuffle(allTasks);
            final AbstractResolvableFuture<String> future = new AbstractResolvableFuture<String>() {
            };
            currentFuture.set(future);
            for (Callable<?> task : allTasks) {
                @SuppressWarnings("unused") // go/futurereturn-lsc
                        Future<?> possiblyIgnoredError = executor.submit(task);
            }
            awaitUnchecked(barrier);
            assertThat(future.isDone()).isTrue();
            // inspect state and ensure it is correct!
            // asserts that all get calling threads received the same value
            Object result = Iterables.getOnlyElement(finalResults);
            if (result == CancellationException.class) {
                assertTrue(future.isCancelled());
                if (future.wasInterrupted()) {
                    // We were cancelled, it is possible that setFuture could have succeeded too.
                    assertThat(numSuccessfulSetCalls.get()).isIn(Range.closed(1, 2));
                } else {
                    assertThat(numSuccessfulSetCalls.get()).isEqualTo(1);
                }
            } else {
                assertThat(numSuccessfulSetCalls.get()).isEqualTo(1);
            }
            // reset for next iteration
            numSuccessfulSetCalls.set(0);
            finalResults.clear();
        }
        executor.shutdown();
    }

    // setFuture and cancel() interact in more complicated ways than the other setters.
    public void testSetFutureCancelBash() {
        final int size = 50;
        final CyclicBarrier barrier =
                new CyclicBarrier(
                        2 // for the setter threads
                                + size // for the listeners
                                + size // for the get threads,
                                + 1); // for the main thread
        final ExecutorService executor = Executors.newFixedThreadPool(barrier.getParties());
        final AtomicReference<AbstractResolvableFuture<String>> currentFuture =
                Atomics.newReference();
        final AtomicReference<AbstractResolvableFuture<String>> setFutureFuture =
                Atomics.newReference();
        final AtomicBoolean setFutureSetSucess = new AtomicBoolean();
        final AtomicBoolean setFutureCompletionSucess = new AtomicBoolean();
        final AtomicBoolean cancellationSucess = new AtomicBoolean();
        Runnable cancelRunnable =
                new Runnable() {
                    @Override
                    public void run() {
                        cancellationSucess.set(currentFuture.get().cancel(true));
                        awaitUnchecked(barrier);
                    }
                };
        Runnable setFutureCompleteSucessFullyRunnable =
                new Runnable() {
                    @Override
                    public void run() {
                        AbstractResolvableFuture<String> future = setFutureFuture.get();
                        setFutureSetSucess.set(currentFuture.get().setFuture(future));
                        setFutureCompletionSucess.set(future.set("hello-async-world"));
                        awaitUnchecked(barrier);
                    }
                };
        final Set<Object> finalResults = Collections.synchronizedSet(Sets.newIdentityHashSet());
        Runnable collectResultsRunnable =
                new Runnable() {
                    @Override
                    public void run() {
                        try {
                            String result = Uninterruptibles.getUninterruptibly(
                                    currentFuture.get());
                            finalResults.add(result);
                        } catch (ExecutionException e) {
                            finalResults.add(e.getCause());
                        } catch (CancellationException e) {
                            finalResults.add(CancellationException.class);
                        } finally {
                            awaitUnchecked(barrier);
                        }
                    }
                };
        Runnable collectResultsTimedGetRunnable =
                new Runnable() {
                    @Override
                    public void run() {
                        Future<String> future = currentFuture.get();
                        while (true) {
                            try {
                                String result = Uninterruptibles.getUninterruptibly(future, 0,
                                        TimeUnit.SECONDS);
                                finalResults.add(result);
                                break;
                            } catch (ExecutionException e) {
                                finalResults.add(e.getCause());
                                break;
                            } catch (CancellationException e) {
                                finalResults.add(CancellationException.class);
                                break;
                            } catch (TimeoutException e) {
                                // loop
                            }
                        }
                        awaitUnchecked(barrier);
                    }
                };
        List<Runnable> allTasks = new ArrayList<>();
        allTasks.add(cancelRunnable);
        allTasks.add(setFutureCompleteSucessFullyRunnable);
        for (int k = 0; k < size; k++) {
            // For each listener we add a task that submits it to the executor directly for the
            // blocking
            // get usecase and another task that adds it as a listener to the future to exercise
            // both
            // racing addListener calls and addListener calls completing after the future completes.
            final Runnable listener =
                    k % 2 == 0 ? collectResultsRunnable : collectResultsTimedGetRunnable;
            allTasks.add(listener);
            allTasks.add(
                    new Runnable() {
                        @Override
                        public void run() {
                            currentFuture.get().addListener(listener, executor);
                        }
                    });
        }
        assertEquals(allTasks.size() + 1, barrier.getParties()); // sanity check
        for (int i = 0; i < 1000; i++) {
            Collections.shuffle(allTasks);
            final AbstractResolvableFuture<String> future = new AbstractResolvableFuture<String>() {
            };
            final AbstractResolvableFuture<String> setFuture =
                    new AbstractResolvableFuture<String>() {
                    };
            currentFuture.set(future);
            setFutureFuture.set(setFuture);
            for (Runnable task : allTasks) {
                executor.execute(task);
            }
            awaitUnchecked(barrier);
            assertThat(future.isDone()).isTrue();
            // inspect state and ensure it is correct!
            // asserts that all get calling threads received the same value
            Object result = Iterables.getOnlyElement(finalResults);
            if (result == CancellationException.class) {
                assertTrue(future.isCancelled());
                assertTrue(cancellationSucess.get());
                // cancellation can interleave in 3 ways
                // 1. prior to setFuture
                // 2. after setFuture before set() on the future assigned
                // 3. after setFuture and set() are called but before the listener completes.
                if (!setFutureSetSucess.get() || !setFutureCompletionSucess.get()) {
                    // If setFuture fails or set on the future fails then it must be because that
                    // future was
                    // cancelled
                    assertTrue(setFuture.isCancelled());
                    assertTrue(setFuture.wasInterrupted()); // we only call cancel(true)
                }
            } else {
                // set on the future completed
                assertFalse(cancellationSucess.get());
                assertTrue(setFutureSetSucess.get());
                assertTrue(setFutureCompletionSucess.get());
            }
            // reset for next iteration
            setFutureSetSucess.set(false);
            setFutureCompletionSucess.set(false);
            cancellationSucess.set(false);
            finalResults.clear();
        }
        executor.shutdown();
    }

    // Test to ensure that when calling setFuture with a done future only setFuture or cancel can
    // return true.
    public void testSetFutureCancelBash_withDoneFuture() {
        final CyclicBarrier barrier =
                new CyclicBarrier(
                        2 // for the setter threads
                                + 1 // for the blocking get thread,
                                + 1); // for the main thread
        final ExecutorService executor = Executors.newFixedThreadPool(barrier.getParties());
        final AtomicReference<AbstractResolvableFuture<String>> currentFuture =
                Atomics.newReference();
        final AtomicBoolean setFutureSuccess = new AtomicBoolean();
        final AtomicBoolean cancellationSucess = new AtomicBoolean();
        Callable<Void> cancelRunnable =
                new Callable<Void>() {
                    @Override
                    public Void call() {
                        cancellationSucess.set(currentFuture.get().cancel(true));
                        awaitUnchecked(barrier);
                        return null;
                    }
                };
        Callable<Void> setFutureCompleteSucessFullyRunnable =
                new Callable<Void>() {
                    final ListenableFuture<String> future =
                            new ImmediateFuture.ImmediateSuccessfulFuture<>("hello");

                    @Override
                    public Void call() {
                        setFutureSuccess.set(currentFuture.get().setFuture(future));
                        awaitUnchecked(barrier);
                        return null;
                    }
                };
        final Set<Object> finalResults = Collections.synchronizedSet(Sets.newIdentityHashSet());
        final Runnable collectResultsRunnable =
                new Runnable() {
                    @Override
                    public void run() {
                        try {
                            String result = Uninterruptibles.getUninterruptibly(
                                    currentFuture.get());
                            finalResults.add(result);
                        } catch (ExecutionException e) {
                            finalResults.add(e.getCause());
                        } catch (CancellationException e) {
                            finalResults.add(CancellationException.class);
                        } finally {
                            awaitUnchecked(barrier);
                        }
                    }
                };
        List<Callable<?>> allTasks = new ArrayList<>();
        allTasks.add(cancelRunnable);
        allTasks.add(setFutureCompleteSucessFullyRunnable);
        allTasks.add(Executors.callable(collectResultsRunnable));
        assertEquals(allTasks.size() + 1, barrier.getParties()); // sanity check
        for (int i = 0; i < 1000; i++) {
            Collections.shuffle(allTasks);
            final AbstractResolvableFuture<String> future = new AbstractResolvableFuture<String>() {
            };
            currentFuture.set(future);
            for (Callable<?> task : allTasks) {
                @SuppressWarnings("unused") // go/futurereturn-lsc
                        Future<?> possiblyIgnoredError = executor.submit(task);
            }
            awaitUnchecked(barrier);
            assertThat(future.isDone()).isTrue();
            // inspect state and ensure it is correct!
            // asserts that all get calling threads received the same value
            Object result = Iterables.getOnlyElement(finalResults);
            if (result == CancellationException.class) {
                assertTrue(future.isCancelled());
                assertTrue(cancellationSucess.get());
                assertFalse(setFutureSuccess.get());
            } else {
                assertTrue(setFutureSuccess.get());
                assertFalse(cancellationSucess.get());
            }
            // reset for next iteration
            setFutureSuccess.set(false);
            cancellationSucess.set(false);
            finalResults.clear();
        }
        executor.shutdown();
    }

    // In a previous implementation this would cause a stack overflow after ~2000 futures chained
    // together.  Now it should only be limited by available memory (and time)
    public void testSetFuture_stackOverflow() {
        ResolvableFuture<String> orig = ResolvableFuture.create();
        ResolvableFuture<String> prev = orig;
        for (int i = 0; i < 100000; i++) {
            ResolvableFuture<String> curr = ResolvableFuture.create();
            prev.setFuture(curr);
            prev = curr;
        }
        // prev represents the 'innermost' future
        prev.set("done");
        assertTrue(orig.isDone());
    }

    public void testSetFuture_misbehavingFuture() throws Exception {
        ResolvableFuture<String> future = ResolvableFuture.create();
        ListenableFuture<String> badFuture =
                new ListenableFuture<String>() {
                    @Override
                    public boolean cancel(boolean interrupt) {
                        return false;
                    }

                    @Override
                    public boolean isDone() {
                        return true;
                    }

                    @Override
                    public boolean isCancelled() {
                        return false; // BAD!!
                    }

                    @Override
                    public String get() {
                        throw new CancellationException(); // BAD!!
                    }

                    @Override
                    public String get(long time, TimeUnit unit) {
                        throw new CancellationException(); // BAD!!
                    }

                    @Override
                    public void addListener(Runnable runnable, Executor executor) {
                        executor.execute(runnable);
                    }
                };
        future.setFuture(badFuture);
        ExecutionException expected = getExpectingExecutionException(future);
        assertThat(expected).hasCauseThat().isInstanceOf(IllegalArgumentException.class);
        assertThat(expected).hasCauseThat().hasMessageThat().contains(badFuture.toString());
    }

    public void testCancel_stackOverflow() {
        ResolvableFuture<String> orig = ResolvableFuture.create();
        ResolvableFuture<String> prev = orig;
        for (int i = 0; i < 100000; i++) {
            ResolvableFuture<String> curr = ResolvableFuture.create();
            prev.setFuture(curr);
            prev = curr;
        }
        // orig is the 'outermost future', this should propagate fully down the stack of futures.
        orig.cancel(true);
        assertTrue(orig.isCancelled());
        assertTrue(prev.isCancelled());
        assertTrue(prev.wasInterrupted());
    }

    public void testSetFutureSelf_cancel() {
        ResolvableFuture<String> orig = ResolvableFuture.create();
        orig.setFuture(orig);
        orig.cancel(true);
        assertTrue(orig.isCancelled());
    }

    public void testSetFutureSelf_toString() {
        ResolvableFuture<String> orig = ResolvableFuture.create();
        orig.setFuture(orig);
        assertThat(orig.toString()).contains("[status=PENDING, info=[setFuture=[this future]]]");
    }

    public void testSetSelf_toString() {
        ResolvableFuture<Object> orig = ResolvableFuture.create();
        orig.set(orig);
        assertThat(orig.toString()).contains("[status=SUCCESS, result=[this future]]");
    }

    public void testSetIndirectSelf_toString() {
        final ResolvableFuture<Object> orig = ResolvableFuture.create();
        // unlike the above this indirection defeats the trivial cycle detection and causes a SOE
        orig.set(
                new Object() {
                    @Override
                    public String toString() {
                        return orig.toString();
                    }
                });
        try {
            orig.toString();
            fail();
        } catch (StackOverflowError expected) {
        }
    }

    private static void awaitUnchecked(final CyclicBarrier barrier) {
        try {
            barrier.await();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void checkStackTrace(ExecutionException e) {
        // Our call site for get() should be in the trace.
        int index = findStackFrame(e, getClass().getName(), "getExpectingExecutionException");

        assertThat(index).isNotEqualTo(0);

        // Above our method should be the call to get(). Don't assert on the class
        // because it could be some superclass.
        assertThat(e.getStackTrace()[index - 1].getMethodName()).isEqualTo("get");
    }

    private static int findStackFrame(ExecutionException e, String clazz, String method) {
        StackTraceElement[] elements = e.getStackTrace();
        for (int i = 0; i < elements.length; i++) {
            StackTraceElement element = elements[i];
            if (element.getClassName().equals(clazz) && element.getMethodName().equals(method)) {
                return i;
            }
        }
        AssertionFailedError failure =
                new AssertionFailedError(
                        "Expected element " + clazz + "." + method + " not found in stack trace");
        failure.initCause(e);
        throw failure;
    }

    private ExecutionException getExpectingExecutionException(
            AbstractResolvableFuture<String> future)
            throws InterruptedException {
        try {
            String got = future.get();
            fail("Expected exception but got " + got);
        } catch (ExecutionException e) {
            return e;
        }

        // unreachable, but compiler doesn't know that fail() always throws
        return null;
    }

    private static final class WaiterThread extends Thread {
        private final AbstractResolvableFuture<?> future;

        private WaiterThread(AbstractResolvableFuture<?> future) {
            this.future = future;
        }

        @Override
        public void run() {
            try {
                future.get();
            } catch (Exception e) {
                // nothing
            }
        }

        void awaitWaiting() {
            while (!isBlocked()) {
                if (getState() == State.TERMINATED) {
                    throw new RuntimeException("Thread exited");
                }
                Thread.yield();
            }
        }

        private boolean isBlocked() {
            return getState() == Thread.State.WAITING && LockSupport.getBlocker(this) == future;
        }
    }

    static final class TimedWaiterThread extends Thread {
        private final AbstractResolvableFuture<?> future;
        private final long timeout;
        private final TimeUnit unit;
        private Exception exception;
        private volatile long startTime;
        private long timeSpentBlocked;

        TimedWaiterThread(AbstractResolvableFuture<?> future, long timeout, TimeUnit unit) {
            this.future = future;
            this.timeout = timeout;
            this.unit = unit;
        }

        @Override
        public void run() {
            startTime = System.nanoTime();
            try {
                future.get(timeout, unit);
            } catch (Exception e) {
                // nothing
                exception = e;
            } finally {
                timeSpentBlocked = System.nanoTime() - startTime;
            }
        }

        void awaitWaiting() {
            while (!isBlocked()) {
                if (getState() == State.TERMINATED) {
                    throw new RuntimeException("Thread exited");
                }
                Thread.yield();
            }
        }

        private boolean isBlocked() {
            return getState() == Thread.State.TIMED_WAITING && LockSupport.getBlocker(this)
                    == future;
        }
    }

    private static final class PollingThread extends Thread {
        private final AbstractResolvableFuture<?> future;
        private final CountDownLatch completedIteration = new CountDownLatch(10);

        private PollingThread(AbstractResolvableFuture<?> future) {
            this.future = future;
        }

        @Override
        public void run() {
            while (true) {
                try {
                    future.get(0, TimeUnit.SECONDS);
                    return;
                } catch (InterruptedException | ExecutionException e) {
                    return;
                } catch (TimeoutException e) {
                    // do nothing
                } finally {
                    completedIteration.countDown();
                }
            }
        }

        void awaitInLoop() {
            Uninterruptibles.awaitUninterruptibly(completedIteration);
        }
    }

    private static final class InterruptibleFuture extends AbstractResolvableFuture<String> {
        boolean interruptTaskWasCalled;

        @Override
        protected void interruptTask() {
            assertFalse(interruptTaskWasCalled);
            interruptTaskWasCalled = true;
        }
    }
}

abstract class ImmediateFuture<V> implements com.google.common.util.concurrent.ListenableFuture<V> {

    @Override
    public void addListener(Runnable listener, Executor executor) {
        try {
            executor.execute(listener);
        } catch (RuntimeException e) {
            // ListenableFuture's contract is that it will not throw unchecked exceptions, so log
            // the bad
            // runnable and/or executor and swallow it.
        }
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        return false;
    }

    @Override
    public abstract V get() throws ExecutionException;

    @Override
    public V get(long timeout, TimeUnit unit) throws ExecutionException {
        return get();
    }

    @Override
    public boolean isCancelled() {
        return false;
    }

    @Override
    public boolean isDone() {
        return true;
    }

    static class ImmediateSuccessfulFuture<V> extends ImmediateFuture<V> {
        private final @Nullable
        V value;

        ImmediateSuccessfulFuture(@Nullable V value) {
            this.value = value;
        }

        // TODO(lukes): Consider throwing InterruptedException when appropriate.
        @Override
        public V get() {
            return value;
        }

        @Override
        public String toString() {
            // Behaviour analogous to AbstractFuture#toString().
            return super.toString() + "[status=SUCCESS, result=[" + value + "]]";
        }
    }

    static final class ImmediateFailedFuture<V> extends AbstractResolvableFuture<V> {
        ImmediateFailedFuture(Throwable thrown) {
            setException(thrown);
        }
    }

    static final class ImmediateCancelledFuture<V> extends AbstractResolvableFuture<V> {
        ImmediateCancelledFuture() {
            cancel(false);
        }
    }
}
