package com.ayman.ecolift.ui.navigation

import kotlinx.datetime.LocalDate

/** Only scalar URL arguments travel with a report; the originating screen stays on the stack. */
data class ExerciseReportOrigin(
    val tab: String = "progress",
    val logDate: LocalDate? = null,
    val exerciseNumber: Int? = null,
) {
    val backLabel: String
        get() = when (tab) {
            "log" -> logDate?.let { "Log · ${reportOriginDateLabel(it)}" } ?: "Progress"
            "split" -> "Split"
            "month" -> "Month"
            "ironmind" -> "IronMind"
            else -> "Progress"
        }
}

internal fun reportOriginDateLabel(date: LocalDate): String {
    val weekday = date.dayOfWeek.name.lowercase().take(3).replaceFirstChar { it.uppercase() }
    val month = date.month.name.lowercase().take(3).replaceFirstChar { it.uppercase() }
    return "$weekday $month ${date.dayOfMonth}"
}

internal fun parseExerciseReportOrigin(origin: String?, date: String?, ex: String?): ExerciseReportOrigin =
    when (origin) {
        "log" -> runCatching { date?.let(LocalDate::parse) }.getOrNull()?.let {
            ExerciseReportOrigin("log", it, ex?.toIntOrNull()?.takeIf { number -> number > 0 })
        } ?: ExerciseReportOrigin()
        "split" -> ExerciseReportOrigin("split")
        "month" -> ExerciseReportOrigin("month")
        "ironmind" -> ExerciseReportOrigin("ironmind")
        else -> ExerciseReportOrigin()
    }

internal const val EXERCISE_REPORT_ROUTE = "progress/{exerciseId}?origin={origin}&date={date}&ex={ex}"

internal fun buildProgressRouteForExercise(
    exerciseId: Long,
    origin: ExerciseReportOrigin = ExerciseReportOrigin(),
): String = when (origin.tab) {
    "log" -> if (origin.logDate != null) {
        "progress/$exerciseId?origin=log&date=${origin.logDate}" +
            (origin.exerciseNumber?.takeIf { it > 0 }?.let { "&ex=$it" } ?: "")
    } else "progress/$exerciseId"
    "split" -> "progress/$exerciseId?origin=split"
    "month" -> "progress/$exerciseId?origin=month"
    "ironmind" -> "progress/$exerciseId?origin=ironmind"
    else -> "progress/$exerciseId"
}

/** Also used by URL tests; unknown/malformed optional arguments always mean Progress. */
internal fun parseExerciseReportRoute(route: String): Pair<Long, ExerciseReportOrigin>? {
    val path = route.substringBefore('?').split('/')
    if (path.size != 2 || path[0] != "progress") return null
    val exerciseId = path[1].toLongOrNull()?.takeIf { it > 0 } ?: return null
    val args = route.substringAfter('?', "").split('&').mapNotNull {
        val parts = it.split('=', limit = 2)
        if (parts.size == 2) parts[0] to parts[1] else null
    }.toMap()
    return exerciseId to parseExerciseReportOrigin(args["origin"], args["date"], args["ex"])
}
