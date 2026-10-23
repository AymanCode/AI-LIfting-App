package com.ayman.ecolift.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayman.ecolift.ui.viewmodel.DataPart
import com.ayman.ecolift.web.util.keyboardOverlap


/** What the sheet's main button does: removes something (red tone) or goes ahead (a colour). */
sealed interface ConfirmTone {
    data object Destructive : ConfirmTone
    data class Primary(val color: Color) : ConfirmTone
}

/**
 * The one confirmation surface for the lifting side of the app. It slides up from the bottom with
 * a grabber, says what is about to happen in Clash, puts the consequence on a data line (what, how
 * many, which day), and gives the answer a full-width button with a quiet Cancel under it.
 *
 * Every confirmation that used to be a centred dialog is this sheet now; each one still asks.
 * [content] holds anything the question needs besides the data line (a form, a list to pick from).
 * A null [confirmLabel] is a picker: the list answers, so only Cancel remains.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EspressoConfirmSheet(
    title: String,
    onDismiss: () -> Unit,
    consequence: List<DataPart>? = null,
    confirmLabel: String? = null,
    onConfirm: () -> Unit = {},
    tone: ConfirmTone = ConfirmTone.Destructive,
    confirmEnabled: Boolean = true,
    cancelLabel: String = "Cancel",
    content: (@Composable ColumnScope.() -> Unit)? = null,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val keyboard = keyboardOverlap()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = EspressoPalette.panel,
        contentColor = EspressoPalette.ink,
        scrimColor = EspressoPalette.mast.copy(alpha = 0.72f),
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        tonalElevation = 0.dp,
        dragHandle = { EspressoGrabber() },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 22.dp, end = 22.dp, top = 4.dp, bottom = 26.dp + keyboard),
        ) {
            Text(
                text = title,
                style = TextStyle(
                    fontFamily = LogDisplayFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp,
                    lineHeight = 27.sp,
                    letterSpacing = (-0.6).sp,
                ),
                color = EspressoPalette.ink,
            )
            if (consequence != null) {
                Spacer(Modifier.height(8.dp))
                DataText(
                    parts = consequence,
                    size = 14.sp,
                    lineHeight = 19.sp,
                    filler = EspressoPalette.dim,
                    data = EspressoPalette.ink,
                )
            }
            if (content != null) {
                Spacer(Modifier.height(18.dp))
                content()
            }
            Spacer(Modifier.height(22.dp))
            if (confirmLabel != null) {
                ConfirmSheetButton(
                    label = confirmLabel,
                    tone = tone,
                    enabled = confirmEnabled,
                    onClick = onConfirm,
                )
                Spacer(Modifier.height(8.dp))
            }
            ConfirmSheetQuietButton(label = cancelLabel, onClick = onDismiss)
        }
    }
}

/** The sheet's grabber: a short hairline pill, the only affordance a bottom sheet needs. */
@Composable
fun EspressoGrabber(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .padding(top = 10.dp, bottom = 14.dp)
            .size(width = 38.dp, height = 4.dp)
            .clip(RoundedCornerShape(50))
            .background(EspressoPalette.hair2),
    )
}

@Composable
private fun ConfirmSheetButton(
    label: String,
    tone: ConfirmTone,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    val fill = when (tone) {
        ConfirmTone.Destructive -> EspressoPalette.danger
        is ConfirmTone.Primary -> tone.color
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(shape)
            .background(if (enabled) fill else EspressoPalette.panel2)
            .clearAndSetSemantics {
                role = Role.Button
                contentDescription = label
                if (enabled) onClick { onClick(); true }
            }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val ink = if (enabled) EspressoPalette.onColor else EspressoPalette.faint
            if (tone == ConfirmTone.Destructive) {
                TrashGlyph(color = ink, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = label,
                style = TextStyle(fontFamily = LogDisplayFontFamily, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 18.sp),
                color = ink,
            )
        }
    }
}

@Composable
private fun ConfirmSheetQuietButton(label: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(shape)
            .clearAndSetSemantics {
                role = Role.Button
                contentDescription = label
                onClick { onClick(); true }
            }
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Text(
            text = label,
            style = TextStyle(fontFamily = LogUiFontFamily, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 18.sp),
            color = EspressoPalette.dim,
        )
    }
}

/** The card header's trash can, drawn to the same 24-unit grid. */
@Composable
private fun TrashGlyph(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = 1.6.dp.toPx()
        val s = size.width / 24f
        val style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawLine(color, Offset(4f * s, 7f * s), Offset(20f * s, 7f * s), strokeWidth = stroke, cap = StrokeCap.Round)
        drawPath(Path().apply { moveTo(9f * s, 7f * s); lineTo(9f * s, 4f * s); lineTo(15f * s, 4f * s); lineTo(15f * s, 7f * s) }, color, style = style)
        drawPath(Path().apply { moveTo(6f * s, 7f * s); lineTo(7f * s, 20f * s); lineTo(17f * s, 20f * s); lineTo(18f * s, 7f * s) }, color, style = style)
        drawLine(color, Offset(10f * s, 11f * s), Offset(10f * s, 17f * s), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(color, Offset(14f * s, 11f * s), Offset(14f * s, 17f * s), strokeWidth = stroke, cap = StrokeCap.Round)
    }
}
