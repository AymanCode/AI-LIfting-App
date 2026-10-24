package com.ayman.ecolift.web

import android.util.Log
import com.ayman.ecolift.web.supabase.CloudFailure
import com.ayman.ecolift.web.supabase.CloudHealth
import com.ayman.ecolift.web.supabase.CloudStamps
import com.ayman.ecolift.web.supabase.sync.SyncStatus
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@Serializable
data class DiagFailure(val cls: String, val msg: String?, val at: String)

@Serializable
data class DiagSync(
    val state: String,
    val pending: Int,
    val since: String?,
    val lastSuccessAt: String?,
    val lastFailure: DiagFailure?,
)

@Serializable
data class DiagSnackbar(val text: String, val at: String)

@Serializable
data class EcoliftDiagnostics(
    val sync: DiagSync,
    val cloudFailures: List<DiagFailure>,
    val snackbars: List<DiagSnackbar>,
    val counters: Map<String, Int> = emptyMap(),
)

/**
 * In-memory diagnostic counters the ported UI already calls.
 * Android has no window.__ecolift. [payload] is the same snapshot, held for a debugger or a test.
 */
@OptIn(ExperimentalTime::class)
object Diagnostics {
    private const val SNACKBAR_CAP = 50
    private var sync = DiagSync(state = "UpToDate", pending = 0, since = null, lastSuccessAt = null, lastFailure = null)
    private val snackbars = ArrayDeque<DiagSnackbar>()
    private val counters = mutableMapOf<String, Int>()

    fun count(name: String) {
        counters[name] = (counters[name] ?: 0) + 1
    }

    internal fun counterSnapshot(): Map<String, Int> = counters.toMap()

    fun update(status: SyncStatus, stamps: CloudStamps) {
        sync = DiagSync(
            state = when (status) {
                SyncStatus.UpToDate -> "UpToDate"
                is SyncStatus.Waiting -> "Waiting"
                is SyncStatus.Offline -> "Offline"
            },
            pending = (status as? SyncStatus.Waiting)?.pending ?: 0,
            since = (status as? SyncStatus.Offline)?.since?.toString(),
            lastSuccessAt = stamps.lastSuccessAt?.toString(),
            lastFailure = stamps.lastFailure?.toDiag(),
        )
    }

    fun snackbar(text: String) {
        snackbars.addLast(DiagSnackbar(text, Clock.System.now().toString()))
        while (snackbars.size > SNACKBAR_CAP) snackbars.removeFirst()
        Log.i("EcoLift", "showed: $text")
    }

    internal fun payload(): String = Json.encodeToString(
        EcoliftDiagnostics(
            sync = sync,
            cloudFailures = CloudHealth.recentFailures.map { it.toDiag() },
            snackbars = snackbars.toList(),
            counters = counterSnapshot(),
        ),
    )

    internal fun reset() {
        sync = DiagSync("UpToDate", 0, null, null, null)
        snackbars.clear()
        counters.clear()
    }

    private fun CloudFailure.toDiag() = DiagFailure(cls = cls, msg = msg, at = at.toString())
}
