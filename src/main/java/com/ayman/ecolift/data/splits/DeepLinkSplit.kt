package com.ayman.ecolift.data.splits

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

private fun SplitSwitchOutcome.matchesRequest(requestId: Long): Boolean = when (this) {
    is SplitSwitchOutcome.Applied -> this.requestId == requestId
    is SplitSwitchOutcome.Cancelled -> this.requestId == requestId
    is SplitSwitchOutcome.Failed -> this.requestId == requestId
}

internal suspend fun consumeSplitDeepLink(
    outcomes: Flow<SplitSwitchOutcome>,
    load: suspend () -> Long?,
) {
    coroutineScope {
        val seen = ArrayList<SplitSwitchOutcome>()
        val arrived = Channel<Unit>(Channel.CONFLATED)
        val collect = launch(start = CoroutineStart.UNDISPATCHED) {
            outcomes.collect { outcome ->
                seen.add(outcome)
                arrived.trySend(Unit)
            }
        }
        try {
            val requestId = load() ?: return@coroutineScope
            while (seen.none { it.matchesRequest(requestId) }) arrived.receive()
        } finally {
            collect.cancel()
        }
    }
}
