package com.ayman.ecolift.data.splits

import com.ayman.ecolift.test.runVmTest
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertTrue

class DeepLinkSplitTest {
    @Test
    fun a_null_load_returns_without_waiting_for_an_outcome() = runVmTest {
        consumeSplitDeepLink(MutableSharedFlow()) { null }
    }

    @Test
    fun an_applied_outcome_for_this_request_finishes() = runVmTest {
        val outcomes = MutableSharedFlow<SplitSwitchOutcome>(extraBufferCapacity = 1)
        withTimeout(300) {
            consumeSplitDeepLink(outcomes) {
                outcomes.emit(SplitSwitchOutcome.Applied(4, "2026-10-23"))
                4L
            }
        }
    }

    @Test
    fun a_cancelled_outcome_for_this_request_finishes() = runVmTest {
        val outcomes = MutableSharedFlow<SplitSwitchOutcome>(extraBufferCapacity = 1)
        withTimeout(300) {
            consumeSplitDeepLink(outcomes) {
                outcomes.emit(SplitSwitchOutcome.Cancelled(4))
                4L
            }
        }
    }

    @Test
    fun a_failed_outcome_for_this_request_finishes() = runVmTest {
        val outcomes = MutableSharedFlow<SplitSwitchOutcome>(extraBufferCapacity = 1)
        withTimeout(300) {
            consumeSplitDeepLink(outcomes) {
                outcomes.emit(SplitSwitchOutcome.Failed(4, "missing"))
                4L
            }
        }
    }

    @Test
    fun a_different_request_does_not_finish_this_wait() = runVmTest {
        val outcomes = MutableSharedFlow<SplitSwitchOutcome>(extraBufferCapacity = 1)
        val failed = runCatching {
            withTimeout(300) {
                consumeSplitDeepLink(outcomes) {
                    outcomes.emit(SplitSwitchOutcome.Applied(9, "2026-10-23"))
                    4L
                }
            }
        }
        assertTrue(failed.exceptionOrNull() is TimeoutCancellationException)
    }
}
