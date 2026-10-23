package com.ayman.ecolift.ui.navigation

import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayman.ecolift.ui.theme.EspressoPalette
import com.ayman.ecolift.ui.viewmodel.ExerciseSwapUi
import com.ayman.ecolift.ui.viewmodel.SwapSheetUi

private data class ActiveSwapPresentation(val swap: ExerciseSwapUi?, val undo: Boolean)

/** The active-card entry point uses the same visual picker as the empty-day ledger. */
@Composable
internal fun ExerciseSwapSheet(
    state: ExerciseSwapUi?,
    undoBusy: Boolean,
    accent: Color,
    onQuery: (String) -> Unit,
    onPick: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val presentation = if (state != null || undoBusy) ActiveSwapPresentation(state, undoBusy) else null
    SwapSheetFrame(presentation, busy = state?.busy == true || undoBusy, centered = undoBusy, onDismiss = onDismiss) {
        shown, interactive, tight, closeFocus, dismiss ->
        if (shown.undo) {
            Row(Modifier.padding(22.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), color = EspressoPalette.ink, strokeWidth = 2.dp)
                Text("Undoing swap…", Modifier.padding(start = 14.dp), fontSize = 16.sp)
            }
        } else shown.swap?.let { swap ->
            val scope = if (swap.date == com.ayman.ecolift.data.WorkoutDates.today()) "today" else "for this workout"
            val ui = swap.context ?: SwapSheetUi("exercise:${swap.sourceId}", swap.sourceId, swap.sourceId,
                "Swap ${swap.sourceName}", null, emptyList(), emptyList(), false, 0)
            SwapPickerContent(ui, swap.candidates, swap.query, onQuery, onPick, interactive, tight, closeFocus, dismiss,
                primaryActionLabel = { "Use $it $scope" }, retainedSets = swap.retainedSetCount,
                busy = swap.busy, error = swap.error)
        }
    }
}
