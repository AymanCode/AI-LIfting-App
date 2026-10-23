package com.ayman.ecolift.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayman.ecolift.ui.theme.DataText
import com.ayman.ecolift.ui.theme.EspressoEyebrow
import com.ayman.ecolift.ui.theme.EspressoPalette
import com.ayman.ecolift.ui.theme.EspressoShape
import com.ayman.ecolift.ui.theme.EspressoType
import com.ayman.ecolift.ui.viewmodel.DataPart
import com.ayman.ecolift.ui.viewmodel.UnsavedSetInput
import com.ayman.ecolift.ui.viewmodel.data
import com.ayman.ecolift.ui.viewmodel.filler
import com.ayman.ecolift.web.supabase.sync.NotCommittedReason

/** Edits storage refused, by set key, with the actions the editor offers for them. */
internal class UnsavedSetEditing(
    private val inputs: Map<String, UnsavedSetInput>,
    val onRetry: (String) -> Unit,
    val onDiscard: (String) -> Unit,
) {
    /** The refused edit of this set and its key, if one is retained. */
    fun forSet(id: Long, syncId: String): Pair<String, UnsavedSetInput>? =
        inputs.entries.firstOrNull { (_, input) ->
            if (syncId.isNotBlank()) input.attempted.syncId == syncId else input.attempted.id == id
        }?.let { it.key to it.value }
}

/** Provided by the Log screen. Without it, as in tests that compose a card alone, no strip is drawn. */
internal val LocalUnsavedSetEditing = compositionLocalOf<UnsavedSetEditing?> { null }

/**
 * The owner's choice (2026-10-15): a failed save stays in the editor with Retry. The fields keep
 * showing the saved row, and this strip holds the value that did not save, so a refused number never
 * reads as a saved check or counts toward the day.
 */
@Composable
internal fun UnsavedSetStrip(
    input: UnsavedSetInput,
    loadLabel: String,
    onRetry: () -> Unit,
    onDiscard: () -> Unit,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val parts = unsavedStripParts(input, loadLabel)
    val eyebrow = "Not saved"
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = eyebrow }
            .clip(EspressoShape.tile)
            .background(EspressoPalette.panel2)
            .border(1.dp, EspressoPalette.danger.copy(alpha = 0.55f), EspressoShape.tile)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        EspressoEyebrow(eyebrow, color = EspressoPalette.danger)
        Spacer(Modifier.height(2.dp))
        DataText(
            parts = parts,
            modifier = Modifier
                .fillMaxWidth()
                .semantics { contentDescription = unsavedStripDetail(input, loadLabel) },
            size = 12.sp,
            dataSize = 15.sp,
            lineHeight = 18.sp,
            filler = EspressoPalette.dim,
            data = EspressoPalette.ink,
        )
        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Discard",
                style = EspressoType.chip,
                color = EspressoPalette.dim,
                modifier = Modifier
                    .clip(EspressoShape.chip)
                    .semantics { contentDescription = "Discard unsaved set"; role = Role.Button }
                    .clickable(onClick = onDiscard)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
            )
            Text(
                text = "Retry",
                style = EspressoType.chipBold,
                color = EspressoPalette.onColor,
                modifier = Modifier
                    .clip(EspressoShape.chip)
                    .background(accent)
                    .semantics { contentDescription = "Retry unsaved set"; role = Role.Button }
                    .clickable(onClick = onRetry)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

/** The mark word, or none when the attempt and the saved row already agree. */
internal fun unsavedMarkWord(attemptedCompleted: Boolean, observedCompleted: Boolean): String? =
    if (attemptedCompleted == observedCompleted) null
    else if (attemptedCompleted) "check"
    else "uncheck"

internal fun unsavedStripParts(input: UnsavedSetInput, loadLabel: String): List<DataPart> {
    val attempted = input.attempted
    return buildList {
        add(data(loadLabel))
        add(filler(" × "))
        add(data((attempted.reps ?: 0).toString()))
        unsavedMarkWord(attempted.completed, input.observed.completed)?.let { add(filler(" · $it")) }
        add(filler(" · " + unsavedReasonLabel(input.reason)))
    }
}

/** The line under NOT SAVED, joined the way the strip draws it. */
internal fun unsavedStripDetail(input: UnsavedSetInput, loadLabel: String): String =
    unsavedStripParts(input, loadLabel).joinToString(separator = "") { it.text }

private fun unsavedReasonLabel(reason: NotCommittedReason): String = when (reason) {
    NotCommittedReason.QuotaExceeded -> "device storage full"
    NotCommittedReason.StorageUnavailable -> "device storage unavailable"
    NotCommittedReason.NeedsReconciliation -> "changed on another device"
}
