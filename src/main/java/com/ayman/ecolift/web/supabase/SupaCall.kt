package com.ayman.ecolift.web.supabase

import com.ayman.ecolift.web.util.consoleWarn
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/** One failed cloud call: the exception class, its message, and when. */
@OptIn(ExperimentalTime::class)
data class CloudFailure(val cls: String, val msg: String?, val at: Instant)

/**
 * What a guarded call knows about the cloud. [failingSince] is the first failure of the current run and
 * clears on the next success; [lastFailure] stays so the settings sheet can show it.
 */
@OptIn(ExperimentalTime::class)
data class CloudStamps(
    val lastSuccessAt: Instant? = null,
    val lastFailure: CloudFailure? = null,
    val failingSince: Instant? = null,
)

/**
 * App-wide stamps. The Supabase retry wrapper is not ported. Callers on Android record stamps directly.
 */
@OptIn(ExperimentalTime::class)
object CloudHealth {
    private const val RECENT_CAP = 50
    private val _stamps = MutableStateFlow(CloudStamps())
    val stamps: StateFlow<CloudStamps> = _stamps.asStateFlow()
    private val recent = ArrayDeque<CloudFailure>()
    /** The newest [RECENT_CAP] failures, oldest first. */
    val recentFailures: List<CloudFailure> get() = recent.toList()

    fun success(at: Instant = Clock.System.now()) {
        _stamps.update { it.copy(lastSuccessAt = at, failingSince = null) }
    }

    fun failure(t: Throwable, at: Instant = Clock.System.now()) {
        val failure = CloudFailure(cls = t::class.simpleName ?: "Throwable", msg = t.message, at = at)
        recent.addLast(failure)
        while (recent.size > RECENT_CAP) recent.removeFirst()
        consoleWarn("EcoLift cloud request failed: ${failure.cls}: ${failure.msg}")
        _stamps.update { it.copy(lastFailure = failure, failingSince = it.failingSince ?: at) }
    }

    /** A new signed-in session starts clean; tests use it too. */
    fun reset() {
        _stamps.value = CloudStamps()
        recent.clear()
    }
}

/**
 * The channel for messages the user must read. Only a [UserVisibleSyncException] can enter it,
 * so a background failure cannot reach the shell by type; the shell shows `message` as is.
 */
object SyncErrors {
    private val _events = MutableSharedFlow<UserVisibleSyncException>(extraBufferCapacity = 8, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val events: SharedFlow<UserVisibleSyncException> = _events
    fun report(t: UserVisibleSyncException) { _events.tryEmit(t) }
}

class UserVisibleSyncException(
    message: String,
    cause: Throwable? = null,
) : IllegalStateException(message, cause)

/** A message for a write that was dropped: what did not save, then the failure class. */
fun notSaved(message: String, cause: Throwable): UserVisibleSyncException =
    UserVisibleSyncException("$message · ${cause::class.simpleName ?: "Throwable"}", cause)

/** Outcome of trying to refresh a stale session. The Supabase refresh call is not ported. */
sealed interface CloudRecovery {
    data object Recovered : CloudRecovery
    data class Transient(val cause: Throwable) : CloudRecovery
    data object ConfirmedInvalid : CloudRecovery
}
