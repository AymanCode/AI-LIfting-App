package com.ayman.ecolift.web.supabase.sync

sealed interface LocalCommit<out T> {
    data class Committed<T>(val value: T) : LocalCommit<T>
    data class NotCommitted(val reason: NotCommittedReason) : LocalCommit<Nothing>
}

enum class NotCommittedReason {
    QuotaExceeded,
    StorageUnavailable,
    NeedsReconciliation,
}

class NotCommittedException(val reason: NotCommittedReason) : IllegalStateException(reason.name)

fun <T> LocalCommit<T>.orThrow(): T = when (this) {
    is LocalCommit.Committed -> value
    is LocalCommit.NotCommitted -> throw NotCommittedException(reason)
}

fun <T, R> LocalCommit<T>.map(transform: (T) -> R): LocalCommit<R> = when (this) {
    is LocalCommit.Committed -> LocalCommit.Committed(transform(value))
    is LocalCommit.NotCommitted -> this
}
