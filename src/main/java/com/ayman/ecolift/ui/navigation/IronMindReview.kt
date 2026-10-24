package com.ayman.ecolift.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ayman.ecolift.android.room.PendingReviewSet
import com.ayman.ecolift.data.Exercise
import com.ayman.ecolift.data.PendingReview
import com.ayman.ecolift.ui.theme.EspressoPalette
import com.ayman.ecolift.ui.theme.EspressoType
import com.ayman.ecolift.ui.viewmodel.AiPendingActionUi
import com.ayman.ecolift.ui.viewmodel.pendingReviewSet

@Composable
internal fun PendingActionCard(action: AiPendingActionUi, busy: Boolean, onApply: () -> Unit, onCancel: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(EspressoPalette.panel).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(action.title, style = EspressoType.eyebrow, color = EspressoPalette.dim)
        Text(action.detail, style = EspressoType.body, color = EspressoPalette.ink)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DeskAction(action.confirmLabel, primary = true, enabled = !busy, onClick = onApply)
            DeskAction("Cancel", primary = false, enabled = !busy, onClick = onCancel)
        }
    }
}

@Composable
internal fun SavedReviewRow(review: PendingReview, busy: Boolean, onResolve: () -> Unit, onDismiss: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(EspressoPalette.panel).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(review.rawInput, style = EspressoType.body, color = EspressoPalette.ink)
        Text(review.dateLogged, style = EspressoType.body, color = EspressoPalette.dim)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DeskAction("Resolve", primary = true, enabled = !busy, onClick = onResolve)
            DeskAction("Dismiss", primary = false, enabled = !busy, onClick = onDismiss)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SavedReviewSheet(
    review: PendingReview,
    exercises: List<Exercise>,
    busy: Boolean,
    error: String?,
    onApply: (PendingReviewSet) -> Unit,
    onDismiss: () -> Unit,
) {
    var exerciseName by remember(review.id) { mutableStateOf("") }
    var weight by remember(review.id) { mutableStateOf("") }
    var reps by remember(review.id) { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    val available = exercises.filter { it.mergedIntoId == null }
    val exercise = available.singleOrNull { it.name.equals(exerciseName.trim(), ignoreCase = true) }
    val input = pendingReviewSet(exercise, weight, reps)
    val colors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = EspressoPalette.ink,
        unfocusedTextColor = EspressoPalette.ink,
        focusedBorderColor = EspressoPalette.ink,
        unfocusedBorderColor = EspressoPalette.hair2,
        focusedContainerColor = EspressoPalette.panel2,
        unfocusedContainerColor = EspressoPalette.panel2,
    )
    ModalBottomSheet(
        onDismissRequest = { if (!busy) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = EspressoPalette.panel,
    ) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Resolve", style = EspressoType.name, color = EspressoPalette.ink)
            Text(review.rawInput, style = EspressoType.body, color = EspressoPalette.ink)
            Text(review.dateLogged, style = EspressoType.body, color = EspressoPalette.dim)
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { if (!busy) expanded = it }) {
                OutlinedTextField(
                    value = exerciseName,
                    onValueChange = { exerciseName = it; expanded = true },
                    label = { Text("Exercise") },
                    enabled = !busy,
                    singleLine = true,
                    colors = colors,
                    modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable),
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.heightIn(max = 240.dp)) {
                    available.filter { it.name.contains(exerciseName, ignoreCase = true) }.sortedBy { it.name }.forEach { candidate ->
                        DropdownMenuItem(
                            text = { Text(candidate.name) },
                            onClick = { exerciseName = candidate.name; expanded = false },
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = weight,
                    onValueChange = { weight = it },
                    label = { Text("Weight (lb)") },
                    enabled = !busy,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = colors,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = reps,
                    onValueChange = { reps = it },
                    label = { Text("Reps") },
                    enabled = !busy,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = colors,
                    modifier = Modifier.weight(1f),
                )
            }
            if (error != null) Text(error, style = EspressoType.body, color = EspressoPalette.ink)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val canApply = !busy && input != null
                Box(Modifier.alpha(if (canApply) 1f else 0.4f)) {
                    DeskAction("Apply", primary = true, enabled = canApply, onClick = { input?.let(onApply) })
                }
                DeskAction("Cancel", primary = false, enabled = !busy, onClick = onDismiss)
            }
        }
    }
}
