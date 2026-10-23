package com.ayman.ecolift.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayman.ecolift.data.WeightLbs
import com.ayman.ecolift.ui.theme.DataText
import com.ayman.ecolift.ui.theme.EspressoEyebrow
import com.ayman.ecolift.ui.theme.EspressoPalette
import com.ayman.ecolift.ui.viewmodel.DataPart
import com.ayman.ecolift.ui.viewmodel.data
import com.ayman.ecolift.ui.viewmodel.filler
import com.ayman.ecolift.web.offline.ConflictChoice
import com.ayman.ecolift.web.offline.ConflictReviewItem
import com.ayman.ecolift.web.offline.ExerciseReviewItem
import com.ayman.ecolift.web.offline.ExerciseReviewKind
import com.ayman.ecolift.web.offline.ReviewHold
import com.ayman.ecolift.web.supabase.sync.QueuedSet

/*
 * Retained sync work on the IronMind desk (owner's choice, 2026-10-15), reached from the sync block
 * in settings. Every card shows both versions; hiding or leaving one never deletes its evidence.
 */

@Composable
internal fun SyncConflictCard(item: ConflictReviewItem, exerciseName: String?, onChoose: (ConflictChoice) -> Unit) {
    ReviewCard {
        EspressoEyebrow(listOfNotNull("Conflict", item.dates.joinToString(" → "), exerciseName).joinToString(" · "))
        versionLine("This device", item.local)
        versionLine("Cloud", item.remote)
        when (val hold = item.hold) {
            null -> Row(Modifier.padding(top = 5.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DeskAction("Keep this device", true, true) { onChoose(ConflictChoice.KEEP_THIS_DEVICE) }
                DeskAction("Use cloud", false, true) { onChoose(ConflictChoice.USE_CLOUD) }
            }
            else -> ReviewLine(holdLabel(hold), EspressoPalette.down)
        }
    }
}

@Composable
internal fun SyncExerciseCard(item: ExerciseReviewItem, onUseExisting: (Long) -> Unit, onSendAgain: () -> Unit) {
    ReviewCard {
        EspressoEyebrow("New exercise · ${item.name}")
        when (item.kind) {
            ExerciseReviewKind.SAME_NAME_IN_CLOUD -> {
                DataText(
                    parts = listOf(filler("Cloud has ")) + item.candidates.take(3).flatMapIndexed { index, candidate ->
                        listOfNotNull(filler(" · ").takeIf { index > 0 }, data(candidate.name))
                    },
                    size = 13.sp, dataSize = 15.sp, filler = EspressoPalette.dim, data = EspressoPalette.ink, maxLines = 2,
                )
                Row(Modifier.padding(top = 5.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item.candidates.take(2).forEach { candidate ->
                        DeskAction("Use ${candidate.name}", true, true) { onUseExisting(candidate.serverId) }
                    }
                }
                ReviewLine("Or rename it in Log to keep it separate")
            }
            ExerciseReviewKind.UNCONFIRMED -> {
                ReviewLine("Cloud reply unconfirmed · request kept", EspressoPalette.down)
                Row(Modifier.padding(top = 5.dp)) { DeskAction("Send again", true, true, onSendAgain) }
            }
            ExerciseReviewKind.CLOUD_REFUSED -> {
                ReviewLine("Cloud refused · ${item.code.orEmpty()}", EspressoPalette.down)
                ReviewLine("Rename it in Log to send it again")
            }
        }
    }
}

@Composable
private fun ReviewCard(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(EspressoPalette.panel)
            .drawBehind { drawRect(EspressoPalette.down, Offset.Zero, Size(3.dp.toPx(), size.height)) }
            .padding(start = 16.dp, end = 14.dp, top = 14.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) { content() }
}

@Composable
private fun versionLine(label: String, set: QueuedSet?) {
    val parts: List<DataPart> = if (set == null) {
        listOf(filler("$label  "), data("—"))
    } else {
        listOf(
            filler("$label  "),
            data(if (set.isBodyweight) "BW+" + WeightLbs.formatStored(set.weightLbs) else WeightLbs.formatStored(set.weightLbs).ifBlank { "-" }),
            filler(" × "),
            data((set.reps ?: 0).toString()),
            filler(if (set.completed) " · logged" else " · not logged"),
        )
    }
    DataText(
        parts = parts,
        modifier = Modifier.semantics { contentDescription = parts.joinToString("") { it.text } },
        size = 13.sp, dataSize = 15.sp, filler = EspressoPalette.dim, data = EspressoPalette.ink, maxLines = 1,
    )
}

/** The mirror keeps 80 characters of a merged eyebrow. The description keeps the whole line. */
@Composable
private fun ReviewLine(text: String, color: Color = EspressoPalette.dim) {
    EspressoEyebrow(text, color = color, modifier = Modifier.semantics { contentDescription = text })
}

private fun holdLabel(hold: ReviewHold): String = when (hold) {
    ReviewHold.CARD_ONLY -> "Card change refused · kept, no choice yet"
    ReviewHold.WAITING_FOR_CLOUD_VERSION -> "Cloud version loads on next sync"
    ReviewHold.MOVED_TO_ANOTHER_DATE -> "Moved set · kept, no choice yet"
    ReviewHold.LATER_CHANGES_ON_THESE_DATES -> "Later changes on these dates · all kept"
}
