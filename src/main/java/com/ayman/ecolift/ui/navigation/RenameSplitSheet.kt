package com.ayman.ecolift.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayman.ecolift.ui.theme.EspressoPalette
import com.ayman.ecolift.ui.theme.EspressoType
import com.ayman.ecolift.ui.theme.LogDisplayFontFamily
import com.ayman.ecolift.ui.theme.LogUiFontFamily

/** Names a split (learned or hand-made). Used from the Log ticket's header and the Splits tab. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RenameSplitSheet(
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = EspressoPalette.panel,
        dragHandle = {
            Box(
                Modifier.padding(vertical = 14.dp).size(width = 40.dp, height = 5.dp)
                    .clip(RoundedCornerShape(50)).background(EspressoPalette.hair2),
            )
        },
    ) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(top = 4.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text("NAME THIS SPLIT", style = EspressoType.eyebrow, color = EspressoPalette.faint)
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                textStyle = TextStyle(
                    fontFamily = LogDisplayFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp,
                    color = EspressoPalette.ink,
                ),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = EspressoPalette.ink,
                    unfocusedBorderColor = EspressoPalette.hair2,
                    cursorColor = EspressoPalette.ink,
                    focusedTextColor = EspressoPalette.ink,
                    unfocusedTextColor = EspressoPalette.ink,
                    focusedContainerColor = EspressoPalette.panel2,
                    unfocusedContainerColor = EspressoPalette.panel2,
                ),
            )
            val canSave = name.isNotBlank()
            Box(
                Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(14.dp))
                    .background(EspressoPalette.ink)
                    .alpha(if (canSave) 1f else 0.4f)
                    .clickable(enabled = canSave, role = Role.Button) { onConfirm(name.trim()) }
                    .semantics { contentDescription = "Save name" },
                contentAlignment = Alignment.Center,
            ) {
                Text("Save", fontFamily = LogUiFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = EspressoPalette.onColor)
            }
        }
    }
}
