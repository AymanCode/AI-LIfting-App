package com.ayman.ecolift.test

import androidx.arch.core.executor.ArchTaskExecutor
import androidx.arch.core.executor.TaskExecutor
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.coroutines.CoroutineContext

// JVM unit tests have no Android looper. viewModelScope uses Dispatchers.Main, and
// LifecycleRegistry asks ArchTaskExecutor whether it is on the main thread. Both are installed
// for the test. Main is an unconfined dispatcher on this test's scheduler, so a launch runs until
// its first suspend and withTimeout still sees the scheduled delays. Those delays are cancelled
// before runTest drains the scheduler. A rest ticker otherwise schedules another second forever,
// and that drain never returns. Main resets after that drain.
@OptIn(ExperimentalCoroutinesApi::class, InternalCoroutinesApi::class)
fun runVmTest(testBody: suspend TestScope.() -> Unit) {
    ArchTaskExecutor.getInstance().setDelegate(ImmediateMainExecutor)
    val main = TrackedMainDispatcher()
    var installed = false
    try {
        runTest {
            Dispatchers.setMain(main.dispatcher(testScheduler))
            installed = true
            try {
                testBody()
            } finally {
                main.stopDelays()
            }
        }
    } finally {
        ArchTaskExecutor.getInstance().setDelegate(null)
        if (installed) Dispatchers.resetMain()
    }
}

@OptIn(ExperimentalCoroutinesApi::class, InternalCoroutinesApi::class)
private class TrackedMainDispatcher {
    private val delays = mutableListOf<CancellableContinuation<Unit>>()
    private var closed = false

    fun dispatcher(scheduler: TestCoroutineScheduler): CoroutineDispatcher = Gate(this, scheduler)

    fun stopDelays() {
        closed = true
        delays.toList().forEach { continuation ->
            if (continuation.isActive) continuation.cancel()
        }
    }

    private class Gate(
        private val owner: TrackedMainDispatcher,
        scheduler: TestCoroutineScheduler,
    ) : CoroutineDispatcher(), Delay {
        private val delegate = UnconfinedTestDispatcher(scheduler)

        override fun isDispatchNeeded(context: CoroutineContext): Boolean = delegate.isDispatchNeeded(context)

        override fun dispatch(context: CoroutineContext, block: Runnable) {
            delegate.dispatch(context, block)
        }

        override fun scheduleResumeAfterDelay(timeMillis: Long, continuation: CancellableContinuation<Unit>) {
            if (owner.closed) {
                continuation.cancel()
                return
            }
            owner.delays.add(continuation)
            delegate.scheduleResumeAfterDelay(timeMillis, continuation)
        }

        override fun invokeOnTimeout(timeMillis: Long, block: Runnable, context: CoroutineContext): DisposableHandle =
            delegate.invokeOnTimeout(timeMillis, block, context)
    }
}

private object ImmediateMainExecutor : TaskExecutor() {
    override fun executeOnDiskIO(runnable: Runnable) = runnable.run()

    override fun postToMainThread(runnable: Runnable) = runnable.run()

    override fun isMainThread(): Boolean = true
}
