package com.ayman.ecolift.ui.navigation

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayman.ecolift.android.platform.LocalAndroidDeviceActions
import com.ayman.ecolift.data.PendingReview
import com.ayman.ecolift.data.WorkoutSet
import com.ayman.ecolift.data.hub.BacklogItem
import com.ayman.ecolift.data.hub.HubBuild
import com.ayman.ecolift.data.hub.HubDay
import com.ayman.ecolift.data.hub.HubDayKind
import com.ayman.ecolift.data.hub.HubItem
import com.ayman.ecolift.data.hub.UncheckedSetsItem
import com.ayman.ecolift.ui.hub.HubList
import com.ayman.ecolift.ui.hub.HubSwipe
import com.ayman.ecolift.ui.hub.HubWorkoutCard
import com.ayman.ecolift.ui.theme.DataText
import com.ayman.ecolift.ui.theme.EspressoEyebrow
import com.ayman.ecolift.ui.theme.EspressoIconButton
import com.ayman.ecolift.ui.theme.EspressoMasthead
import com.ayman.ecolift.ui.theme.EspressoPalette
import com.ayman.ecolift.ui.theme.EspressoShape
import com.ayman.ecolift.ui.theme.EspressoType
import com.ayman.ecolift.ui.theme.GlassPaletteChoice
import com.ayman.ecolift.ui.theme.LocalGlassPalette
import com.ayman.ecolift.ui.theme.LogDisplayFontFamily
import com.ayman.ecolift.ui.theme.LogUiFontFamily
import com.ayman.ecolift.ui.theme.TAB_ARRIVAL_FRAMES
import com.ayman.ecolift.ui.theme.TabArrival
import com.ayman.ecolift.ui.theme.TabWarmupShell
import com.ayman.ecolift.ui.theme.glassPanel
import com.ayman.ecolift.ui.viewmodel.AiPendingActionUi
import com.ayman.ecolift.ui.viewmodel.ChangelogWin
import com.ayman.ecolift.ui.viewmodel.ClaudeChangeUi
import com.ayman.ecolift.ui.viewmodel.DataPart
import com.ayman.ecolift.ui.viewmodel.DeskDecisionUi
import com.ayman.ecolift.ui.viewmodel.DeskLiftUi
import com.ayman.ecolift.ui.viewmodel.HubTopSets
import com.ayman.ecolift.ui.viewmodel.IronMindDeskUi
import com.ayman.ecolift.ui.viewmodel.NoticedKind
import com.ayman.ecolift.ui.viewmodel.NoticedUi
import com.ayman.ecolift.ui.viewmodel.SlotSessionUi
import com.ayman.ecolift.ui.viewmodel.data
import com.ayman.ecolift.ui.viewmodel.filler
import com.ayman.ecolift.ui.viewmodel.lastFailureParts
import com.ayman.ecolift.ui.viewmodel.syncRowParts
import com.ayman.ecolift.web.LocalRepos
import com.ayman.ecolift.web.demo.DemoGuard
import com.ayman.ecolift.web.offline.Resolution
import com.ayman.ecolift.web.supabase.CloudStamps
import com.ayman.ecolift.web.supabase.sync.SyncStatus
import com.ayman.ecolift.web.util.minusDays
import com.ayman.ecolift.web.util.todayLocalDate
import kotlinx.coroutines.launch

/**
 * Vertical space the floating dock covers at the bottom of the screen. Any list it floats over has
 * to reserve this much, otherwise a long list leaves its final item permanently underneath.
 */
private val DOCK_RESERVE = 66.dp

/** The see-through fade the dock starts with; the list may still scroll under this part. */
private val DOCK_FADE_HEIGHT = 72.dp

sealed class IronMindMessage {
    abstract val timestampMillis: Long

    data class UserMessage(
        val text: String,
        val timestamp: String,
        override val timestampMillis: Long,
        val messageId: Long = 0L
    ) : IronMindMessage()
    data class AiMessage(
        val text: String,
        val timestamp: String,
        override val timestampMillis: Long,
        val card: IronMindCardPayload? = null,
        val recovery: RecoveryPayload? = null
    ) : IronMindMessage()
    data object AiThinking : IronMindMessage() {
        override val timestampMillis: Long = 0L
    }
}

sealed interface IronMindCardPayload

data class StatsPayload(
    val exerciseName: String,
    val pr: String?,
    val estimatedOneRm: String?,
    val lastSession: String?,
    val sessionCount: Int?
) : IronMindCardPayload

data class HistoryCardPayload(
    val exerciseName: String,
    val prLine: String?,
    val sessions: List<HistoryCardRowPayload>,
) : IronMindCardPayload

data class HistoryCardRowPayload(
    val date: String,
    val setLabel: String,
)

data class ChangesCardPayload(
    val changes: List<String>,
) : IronMindCardPayload

data class QuickAction(val label: String, val query: String)

private sealed interface IronMindListItem {
    data class Timestamp(val label: String, val millis: Long) : IronMindListItem
    data class Message(val message: IronMindMessage) : IronMindListItem
}

data class RecoveryPayload(
    val title: String,
    val detail: String,
    val originalText: String,
    val suggestedTemplate: String,
    val saveDate: String,
    val canTryModel: Boolean
)

enum class RecoveryAction {
    EditOriginal,
    UseTemplate,
    SaveForReview,
    TryAi
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserMessageBubble(
    message: IronMindMessage.UserMessage,
    modifier: Modifier = Modifier
) {
    val palette = LocalGlassPalette.current
    val shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 6.dp)
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End
    ) {
        Spacer(modifier = Modifier.weight(1f))
        Column(
            modifier = Modifier.fillMaxWidth(0.78f),
            horizontalAlignment = Alignment.End
        ) {
            Box(
                modifier = Modifier
                    .clip(shape)
                    .background(EspressoPalette.panel2)
                    .padding(horizontal = 14.dp, vertical = 12.dp)
            ) {
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.ink
                )
            }
        }
    }
}

@Composable
fun InlineStatsCard(
    stats: StatsPayload,
    modifier: Modifier = Modifier
) {
    val palette = LocalGlassPalette.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(EspressoPalette.bg)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        EspressoEyebrow(stats.exerciseName)
        Spacer(Modifier.height(6.dp))
        if (stats.pr != null) {
            StatRow("Current PR", stats.pr)
        }
        if (stats.estimatedOneRm != null) {
            StatRow("Est. 1RM", stats.estimatedOneRm)
        }
        if (stats.lastSession != null) {
            StatRow("Last session", stats.lastSession)
        }
        if (stats.sessionCount != null) {
            StatRow("Total sessions", "${stats.sessionCount}")
        }
    }
}

@Composable
private fun StatRow(label: String, value: String) {
    val palette = LocalGlassPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = palette.inkSubtle
        )
        Text(
            text = value,
            fontFamily = LogDisplayFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = EspressoPalette.ink
        )
    }
}

@Composable
fun HistoryResponseCard(
    card: HistoryCardPayload,
    modifier: Modifier = Modifier,
) {
    val palette = LocalGlassPalette.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .glassPanel(palette, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        Text(
            text = card.exerciseName,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = palette.ink
        )
        card.prLine?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = palette.accentStrong,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
        if (card.sessions.isNotEmpty()) {
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = palette.glassStroke
            )
            card.sessions.forEach { row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(row.date, style = MaterialTheme.typography.bodySmall, color = palette.inkSubtle)
                    Text(row.setLabel, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, color = palette.ink)
                }
            }
        }
    }
}

@Composable
fun ChangesResponseCard(
    card: ChangesCardPayload,
    modifier: Modifier = Modifier,
) {
    val palette = LocalGlassPalette.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .glassPanel(palette, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        Text(
            text = "Changes",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = palette.ink
        )
        card.changes.forEach { change ->
            Text(
                text = change,
                style = MaterialTheme.typography.bodySmall,
                color = palette.inkMuted,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

/**
 * What Claude changed through the connector, newest first, each with its own Undo. Undone rows stay
 * greyed so the record reads honestly; the button only appears where the server will accept an undo.
 */
@Composable
fun ClaudeChangesCard(
    changes: List<ClaudeChangeUi>,
    onUndo: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalGlassPalette.current
    Column(
        modifier = modifier
            .glassPanel(palette, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 12.dp)
            .semantics { contentDescription = "Assistant changes" }
    ) {
        Text(
            text = "Changes",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = palette.ink
        )
        changes.forEachIndexed { index, change ->
            if (index > 0) {
                HorizontalDivider(color = palette.glassStroke, modifier = Modifier.padding(vertical = 8.dp))
            } else {
                Spacer(Modifier.height(8.dp))
            }
            val ink = if (change.undone) palette.inkSubtle else palette.ink
            Row(
                modifier = Modifier.semantics {
                    contentDescription = listOfNotNull(
                        change.summary,
                        change.whenLabel.takeIf { it.isNotEmpty() },
                        change.reason?.let { "why: $it" },
                        if (change.undone) "undone" else null,
                        change.note,
                    ).joinToString(" · ")
                },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = change.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (change.undone) FontWeight.Normal else FontWeight.SemiBold,
                        color = ink,
                    )
                    val meta = listOfNotNull(
                        change.whenLabel.takeIf { it.isNotEmpty() },
                        change.reason?.let { "why: $it" },
                        if (change.undone) "undone" else null,
                    ).joinToString("  ·  ")
                    if (meta.isNotEmpty()) {
                        Text(
                            text = meta,
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.inkMuted,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    change.note?.let { note ->
                        Text(
                            text = note,
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.ink,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
                if (change.canUndo) {
                    AssistChip(
                        onClick = { onUndo(change.id) },
                        label = { Text("Undo", style = MaterialTheme.typography.labelMedium) },
                        modifier = Modifier.padding(start = 8.dp).semantics { contentDescription = "Undo ${change.summary}" },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = palette.glassFillStrong,
                            labelColor = palette.ink
                        ),
                        border = AssistChipDefaults.assistChipBorder(enabled = true, borderColor = palette.glassStroke)
                    )
                }
            }
        }
    }
}

@Composable
private fun IronMindResponseCard(
    card: IronMindCardPayload,
    modifier: Modifier = Modifier,
) {
    when (card) {
        is StatsPayload -> InlineStatsCard(card, modifier)
        is HistoryCardPayload -> HistoryResponseCard(card, modifier)
        is ChangesCardPayload -> ChangesResponseCard(card, modifier)
    }
}

@Composable
private fun RecoveryDraftSection(
    recovery: RecoveryPayload,
    onAction: (RecoveryPayload, RecoveryAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalGlassPalette.current
    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalDivider(
            modifier = Modifier.padding(bottom = 8.dp),
            color = palette.glassStroke
        )
        Text(
            text = "Original",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = palette.inkSubtle
        )
        Text(
            text = recovery.originalText,
            style = MaterialTheme.typography.bodySmall,
            color = palette.ink,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .glassPanel(palette, RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 7.dp)
        )
        Text(
            text = "Template",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = palette.inkSubtle,
            modifier = Modifier.padding(top = 8.dp)
        )
        Text(
            text = recovery.suggestedTemplate,
            style = MaterialTheme.typography.bodySmall,
            color = palette.ink,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .glassPanel(palette, RoundedCornerShape(8.dp))
                .padding(horizontal = 8.dp, vertical = 7.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AssistChip(
                onClick = { onAction(recovery, RecoveryAction.EditOriginal) },
                label = { Text("Edit", style = MaterialTheme.typography.labelMedium) },
                modifier = Modifier.weight(1f),
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = palette.glassFillStrong,
                    labelColor = palette.ink
                ),
                border = AssistChipDefaults.assistChipBorder(
                    enabled = true,
                    borderColor = palette.glassStroke
                )
            )
            AssistChip(
                onClick = { onAction(recovery, RecoveryAction.UseTemplate) },
                label = { Text("Use template", style = MaterialTheme.typography.labelMedium) },
                modifier = Modifier.weight(1f),
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = palette.glassFillStrong,
                    labelColor = palette.ink
                ),
                border = AssistChipDefaults.assistChipBorder(
                    enabled = true,
                    borderColor = palette.glassStroke
                )
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AssistChip(
                onClick = { onAction(recovery, RecoveryAction.SaveForReview) },
                label = { Text("Save", style = MaterialTheme.typography.labelMedium) },
                modifier = Modifier.weight(1f),
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = palette.glassFillStrong,
                    labelColor = palette.ink
                ),
                border = AssistChipDefaults.assistChipBorder(
                    enabled = true,
                    borderColor = palette.glassStroke
                )
            )
            AssistChip(
                onClick = { onAction(recovery, RecoveryAction.TryAi) },
                enabled = recovery.canTryModel,
                label = { Text(if (recovery.canTryModel) "Try AI" else "AI offline", style = MaterialTheme.typography.labelMedium) },
                modifier = Modifier.weight(1f),
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = palette.glassFillStrong,
                    labelColor = palette.ink,
                    disabledContainerColor = palette.glassFill,
                    disabledLabelColor = palette.inkSubtle
                ),
                border = AssistChipDefaults.assistChipBorder(
                    enabled = recovery.canTryModel,
                    borderColor = palette.glassStroke
                )
            )
        }
    }
}

@Composable
private fun HeaderChip(text: String, fill: Color, ink: Color, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    Text(
        text = text,
        color = ink,
        fontFamily = LogUiFontFamily,
        fontSize = 9.5.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.1.sp,
        modifier = modifier
            .clip(shape)
            .background(fill.copy(alpha = 0.13f), shape)
            .border(1.dp, fill.copy(alpha = 0.42f), shape)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

/**
 * The masthead's one line as data (audit pass 2026-09-25): how many unchecked sets are waiting,
 * counts in the display face. Empty when nothing is open, because the dock's placeholder already
 * says what the tab is for and the open chip already says "nothing".
 */
internal fun ironMindMastParts(build: HubBuild?): List<DataPart> {
    if (build == null) return emptyList()
    val unchecked = build.days.sumOf { day ->
        day.items.sumOf { item -> if (item is UncheckedSetsItem) item.openSets.size else 0 }
    } + (build.backlog?.setCount ?: 0)
    return buildList {
        if (unchecked > 0) {
            add(data("$unchecked"))
            add(filler(if (unchecked == 1) " unchecked set" else " unchecked sets"))
        }
    }
}

/**
 * Deliberately not a CenterAlignedTopAppBar.
 *
 * IronMind is a home surface rather than a detail screen, so it gets a 48dp identity strip instead
 * of 64dp of centred chrome. The only things allowed beside the name are state: how many items are
 * open, and a warning when the model is unreachable. Neither renders when there is nothing to say,
 * which is why the old permanent "Ready" subtitle is gone -- it occupied a line to report normality.
 */
@Composable
fun HubHeader(
    openCount: Int,
    statusLabel: String?,
    onSettings: () -> Unit,
    modifier: Modifier = Modifier,
    mastParts: List<DataPart> = emptyList(),
    onDesk: (() -> Unit)? = null,
) {
    // Espresso masthead (spec §IronMind): pink title, the open count as an amber chip, the gear.
    EspressoMasthead(
        kicker = "IRONMIND · THE DESK",
        modifier = modifier.statusBarsPadding(),
        kickerEndContent = onDesk?.let { returnToDesk ->
            {
                Text("Desk", style = EspressoType.chipBold, color = EspressoPalette.ink,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(EspressoPalette.panel2)
                        .clickable(role = Role.Button, onClick = returnToDesk).padding(horizontal = 10.dp, vertical = 9.dp))
            }
        },
        title = {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BoxWithConstraints(Modifier.weight(1f)) {
                    val measurer = rememberTextMeasurer()
                    val fittedSize = remember(constraints.maxWidth) {
                        var size = 50f
                        while (size > 26f &&
                            measurer.measure("IronMind", style = EspressoType.mastTitle.copy(fontSize = size.sp),
                                softWrap = false, maxLines = 1).size.width > constraints.maxWidth) {
                                    size -= 2f
                                }
                        size
                    }
                    Text("IronMind", style = EspressoType.mastTitle.copy(fontSize = fittedSize.sp), color = EspressoPalette.arms, maxLines = 1, softWrap = false)
                }
                if (openCount > 0) {
                    Box(
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(EspressoPalette.down)
                            .padding(horizontal = 11.dp, vertical = 8.dp)
                            .semantics { contentDescription = if (openCount == 1) "1 OPEN" else "$openCount OPEN" },
                    ) {
                        Text(
                            text = if (openCount == 1) "1 open" else "$openCount open",
                            style = EspressoType.chipBold,
                            color = EspressoPalette.onColor,
                            maxLines = 1,
                            softWrap = false,
                        )
                    }
                }
                if (statusLabel != null) {
                    Box(
                        modifier = Modifier
                            .padding(top = 6.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(EspressoPalette.danger)
                            .padding(horizontal = 9.dp, vertical = 8.dp),
                    ) {
                        Text(text = statusLabel.uppercase(), style = EspressoType.chipBold.copy(fontSize = 10.sp), color = EspressoPalette.onColor, maxLines = 1)
                    }
                }
            }
        },
        trailing = {
            EspressoIconButton(contentDescription = "Settings", onClick = onSettings) {
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = null,
                    tint = EspressoPalette.ink,
                    modifier = Modifier.size(18.dp),
                )
            }
        },
        line = if (mastParts.isEmpty()) {
            null
        } else {
            {
                DataText(
                    parts = mastParts,
                    size = 14.sp,
                    lineHeight = 18.sp,
                    filler = EspressoPalette.dim,
                    maxLines = 1,
                )
            }
        },
    )
}

/**
 * The assistant speaks as the system, not as a person in a chat.
 *
 * So a reply is text set against an accent rule under a small label, and only the user's own
 * messages are bubbles. That asymmetry is what stops the screen reading as a messaging app, which is
 * the wrong metaphor for a tab whose main job is resolving notifications.
 */
@Composable
fun AiMessageBubble(
    message: IronMindMessage.AiMessage,
    onRecoveryAction: (RecoveryPayload, RecoveryAction) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val palette = LocalGlassPalette.current
    // Espresso: the reply is a panel block with a 6dp bottom-left corner, no label, no rule.
    Column(
        modifier = modifier
            .fillMaxWidth(0.9f)
            .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 6.dp))
            .background(EspressoPalette.panel)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        message.card?.let { IronMindResponseCard(card = it) }
        Text(
            text = message.text,
            style = MaterialTheme.typography.bodyMedium,
            lineHeight = 20.sp,
            color = palette.ink
        )
        if (message.recovery != null) {
            RecoveryDraftSection(
                recovery = message.recovery,
                onAction = onRecoveryAction
            )
        }
    }
}

/**
 * One floating control replaces the old pair of edge-to-edge slabs (a permanent suggestions strip
 * plus an input bar) that together took 139dp of every screen.
 *
 * It floats over the list rather than sitting under it, so the notifications get the full height;
 * the caller is responsible for reserving [DOCK_RESERVE] at the bottom of that list so the last
 * item can always be scrolled clear of this. Suggestions surface only while the field is focused,
 * because a suggestion you are not reading is just chrome.
 */
@Composable
fun IronMindDock(
    query: String,
    quickActions: List<QuickAction>,
    onQueryChange: (String) -> Unit,
    onSend: () -> Unit,
    onQuickAction: (QuickAction) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalGlassPalette.current
    var isFocused by remember { mutableStateOf(false) }
    val canSend = query.isNotBlank()
    val isLive = isFocused || canSend

    Column(modifier = modifier.fillMaxWidth()) {
        // The list scrolls under the dock, so it has to fade out before it reaches it. Without this
        // a half-scrolled card collides with the placeholder and both become unreadable.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(DOCK_FADE_HEIGHT)
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.55f to EspressoPalette.bg.copy(alpha = 0.86f),
                        1f to EspressoPalette.bg.copy(alpha = 0.97f),
                    )
                )
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(palette.pageTop.copy(alpha = 0.97f))
        ) {
            if (quickActions.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    items(quickActions) { action ->
                        Text(
                            text = action.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = palette.inkMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .clip(RoundedCornerShape(11.dp))
                                .background(EspressoPalette.panel, RoundedCornerShape(11.dp))
                                .clickable { onQuickAction(action) }
                                .padding(horizontal = 12.dp, vertical = 9.dp)
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(EspressoPalette.panel)
                    .then(if (isLive) Modifier.border(1.5.dp, EspressoPalette.arms, RoundedCornerShape(18.dp)) else Modifier)
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(EspressoPalette.tint(EspressoPalette.arms)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = EspressoPalette.arms,
                        modifier = Modifier.size(17.dp)
                    )
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged { isFocused = it.isFocused },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = palette.ink),
                    cursorBrush = SolidColor(EspressoPalette.arms),
                    decorationBox = { innerTextField ->
                        if (query.isEmpty()) {
                            Text(
                                text = "Ask IronMind, or log a set",
                                style = MaterialTheme.typography.bodyMedium,
                                color = EspressoPalette.faint
                            )
                        }
                        innerTextField()
                    }
                )
                IconButton(
                    onClick = onSend,
                    enabled = canSend,
                    modifier = Modifier.size(38.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(if (canSend) EspressoPalette.arms else EspressoPalette.panel2),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = if (canSend) EspressoPalette.onColor else EspressoPalette.faint,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
fun AiThinkingBubble(
    modifier: Modifier = Modifier
) {
    val palette = LocalGlassPalette.current
    val bubbleShape = RoundedCornerShape(topStart = 4.dp, topEnd = 14.dp, bottomStart = 14.dp, bottomEnd = 14.dp)
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomEnd = 18.dp, bottomStart = 6.dp))
                .background(EspressoPalette.panel)
                .padding(horizontal = 16.dp, vertical = 14.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                val infiniteTransition = rememberInfiniteTransition(label = "thinking")
                for (i in 0..2) {
                    val alpha by infiniteTransition.animateFloat(
                        initialValue = 1f,
                        targetValue = 0.3f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(durationMillis = 900, delayMillis = i * 150),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "dot_alpha_$i"
                    )
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(palette.inkMuted.copy(alpha = alpha), CircleShape)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun TimestampDivider(
    label: String,
    modifier: Modifier = Modifier,
) {
    val palette = LocalGlassPalette.current
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = palette.inkSubtle,
            modifier = Modifier
                .glassPanel(palette, RoundedCornerShape(50))
                .padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

/**
 * The IronMind settings sheet (audit pass 2026-09-25). It used to be two floating panels on a
 * transparent sheet, with the dock's chips showing through the gap between them, a plain text box
 * for bodyweight and a full-width "Sign out" slab. Now it is one Espresso sheet: a profile card
 * (initial, account, a quiet red sign-out row) and the bodyweight as a large display number with
 * its unit pill. The bodyweight field keeps the exact parsing, demo guard and callback it had.
 */
@Composable
private fun IronMindSettingsSheet(
    selectedPalette: GlassPaletteChoice,
    onPaletteChoiceChange: (GlassPaletteChoice) -> Unit,
    userBodyweightLbs: Int?,
    onUserBodyweightChange: (Int?) -> Unit,
    smartSuggestEnabled: Boolean,
    onSmartSuggestChange: (Boolean) -> Unit,
    accountEmail: String?,
    onSignOut: (() -> Unit)?,
    reviewCount: Int = 0,
    onReviewSync: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var bodyweightText by remember(userBodyweightLbs) {
        mutableStateOf(userBodyweightLbs?.toString().orEmpty())
    }
    val isDemo = DemoGuard.isDemo
    val accountName = when {
        isDemo -> "Demo"
        accountEmail != null -> accountEmail
        else -> "Signed in"
    }
    val initial = accountName.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar()?.toString() ?: "?"

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "Settings",
            style = EspressoType.name.copy(fontSize = 22.sp, lineHeight = 26.sp),
            color = EspressoPalette.ink,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
        )

        // Profile card: who this is, and the one way out.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(EspressoShape.card)
                .background(EspressoPalette.panel)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(EspressoPalette.tint(EspressoPalette.arms))
                        .border(1.dp, EspressoPalette.arms.copy(alpha = 0.45f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = initial,
                        style = EspressoType.tileValue.copy(letterSpacing = 0.sp),
                        color = EspressoPalette.arms,
                    )
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = accountName,
                        fontFamily = LogUiFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 15.sp,
                        lineHeight = 19.sp,
                        color = EspressoPalette.ink,
                        maxLines = 2,
                    )
                    Text(
                        text = if (isDemo) "Account · read-only" else "Account",
                        style = EspressoType.body.copy(fontSize = 12.sp, lineHeight = 15.sp),
                        color = EspressoPalette.faint,
                    )
                }
            }
            if (onSignOut != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .height(1.dp)
                        .background(EspressoPalette.hair)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .semantics { contentDescription = "Sign out"; role = Role.Button }
                        .clickable(onClick = onSignOut)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SignOutGlyph(Modifier.size(18.dp))
                    Text(
                        text = "Sign out",
                        fontFamily = LogUiFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = EspressoPalette.danger,
                    )
                }
            }
        }

        // Smart suggest: whether the app learns splits from workouts on its own.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(EspressoShape.card)
                .background(EspressoPalette.panel)
                .testTag("ironmind_smart_suggest_toggle")
                .semantics {
                    role = Role.Switch
                    contentDescription = "Smart suggest"
                    stateDescription = if (smartSuggestEnabled) "On" else "Off"
                }
                .clickable { onSmartSuggestChange(!smartSuggestEnabled) }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Smart suggest",
                    fontFamily = LogUiFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = EspressoPalette.ink,
                )
                Text(
                    text = "Auto-learn splits from your workouts",
                    fontFamily = LogUiFontFamily,
                    fontSize = 12.sp,
                    color = EspressoPalette.faint,
                )
            }
            Box(
                modifier = Modifier
                    .width(46.dp)
                    .height(26.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(if (smartSuggestEnabled) EspressoPalette.legs else EspressoPalette.hair2)
                    .padding(horizontal = 4.dp),
                contentAlignment = if (smartSuggestEnabled) Alignment.CenterEnd else Alignment.CenterStart,
            ) {
                Box(Modifier.size(18.dp).clip(CircleShape).background(Color.White))
            }
        }

        val deviceActions = LocalAndroidDeviceActions.current
        if (deviceActions != null) {
            SettingsActionRow(label = "Backup", onClick = deviceActions.backup)
            SettingsActionRow(label = "Restore", onClick = deviceActions.restore)
            SettingsActionRow(
                label = "Health Connect",
                onClick = deviceActions.importHealth,
                trailing = deviceActions.healthSessionsAdded?.toString(),
            )
        }

        // SYNC: the same status as the masthead dot, as a data row, plus the last failure class
        // (spec 2026-10-08 §The sync block in settings). Demo has no cloud, so no block.
        if (!isDemo) {
            val repos = LocalRepos.current
            val syncStatus by repos.syncStatus.collectAsState()
            val cloudStamps by repos.cloudStamps.collectAsState()
            SyncCard(status = syncStatus, stamps = cloudStamps, reviewCount = reviewCount, onReview = onReviewSync)
        }

        // Bodyweight: the number in the display face, the unit as a pill. Tapping anywhere on the
        // card puts the caret in the number.
        val focusRequester = remember { FocusRequester() }
        val measurer = rememberTextMeasurer()
        val numberStyle = EspressoType.tileValue.copy(fontSize = 44.sp, lineHeight = 46.sp, letterSpacing = (-1.5).sp)
        val density = LocalDensity.current
        val numberWidth = with(density) {
            measurer.measure(bodyweightText.ifEmpty { "0" }, numberStyle, maxLines = 1, softWrap = false).size.width.toDp() + 4.dp
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(EspressoShape.card)
                .background(EspressoPalette.panel)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { focusRequester.requestFocus() }
                .padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 16.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                EspressoEyebrow("Bodyweight")
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BasicTextField(
                        value = bodyweightText,
                        onValueChange = { rawValue ->
                            if (!DemoGuard.blockIfDemo()) {
                                val parsed = rawValue.filter { it.isDigit() }.take(4).toIntOrNull()
                                val normalizedBodyweight = parsed?.takeIf { it > 0 }
                                bodyweightText = normalizedBodyweight?.toString().orEmpty()
                                onUserBodyweightChange(normalizedBodyweight)
                            }
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        cursorBrush = SolidColor(EspressoPalette.arms),
                        textStyle = numberStyle.copy(color = EspressoPalette.ink),
                        modifier = Modifier
                            .width(numberWidth)
                            .focusRequester(focusRequester)
                            .semantics { contentDescription = "Bodyweight" },
                        decorationBox = { innerTextField ->
                            Box {
                                if (bodyweightText.isEmpty()) {
                                    Text(text = "—", style = numberStyle, color = EspressoPalette.faint)
                                }
                                innerTextField()
                            }
                        }
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "lb",
                        style = EspressoType.chip.copy(fontSize = 12.sp, lineHeight = 14.sp),
                        color = EspressoPalette.dim,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .clip(EspressoShape.chip)
                            .background(EspressoPalette.panel2)
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsActionRow(label: String, onClick: () -> Unit, trailing: String? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(EspressoShape.card)
            .background(EspressoPalette.panel)
            .semantics { role = Role.Button; contentDescription = label }
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontFamily = LogUiFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = EspressoPalette.ink,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) {
            Text(
                text = trailing,
                fontFamily = LogUiFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = EspressoPalette.ink,
            )
        }
    }
}

@Composable
private fun SyncCard(status: SyncStatus, stamps: CloudStamps, reviewCount: Int = 0, onReview: () -> Unit = {}) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(EspressoShape.card)
            .background(EspressoPalette.panel)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        EspressoEyebrow("SYNC")
        DataText(
            parts = syncRowParts(status, stamps),
            size = 13.sp,
            dataSize = 15.sp,
            filler = EspressoPalette.dim,
            data = EspressoPalette.ink,
            maxLines = 1,
        )
        if (reviewCount > 0) {
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(EspressoShape.chip)
                    .semantics { contentDescription = "Review $reviewCount kept sync changes in IronMind"; role = Role.Button }
                    .clickable(onClick = onReview)
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                EspressoEyebrow("TO REVIEW", color = EspressoPalette.down)
                Spacer(Modifier.width(8.dp))
                DataText(
                    parts = listOf(data(reviewCount.toString()), filler(" kept · IronMind ›")),
                    modifier = Modifier.weight(1f),
                    size = 13.sp,
                    dataSize = 15.sp,
                    filler = EspressoPalette.dim,
                    data = EspressoPalette.ink,
                    maxLines = 1,
                )
            }
        }
        lastFailureParts(stamps)?.let { parts ->
            Spacer(Modifier.height(4.dp))
            EspressoEyebrow("LAST FAILURE")
            DataText(
                parts = parts,
                size = 13.sp,
                dataSize = 15.sp,
                filler = EspressoPalette.dim,
                data = EspressoPalette.ink,
                maxLines = 2,
            )
        }
    }
}

/** A door bracket with an arrow leaving it, drawn in the danger tone. */
@Composable
private fun SignOutGlyph(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = 1.7.dp.toPx()
        val w = size.width
        val h = size.height
        val door = Path().apply {
            moveTo(w * 0.46f, h * 0.12f)
            lineTo(w * 0.14f, h * 0.12f)
            lineTo(w * 0.14f, h * 0.88f)
            lineTo(w * 0.46f, h * 0.88f)
        }
        drawPath(door, color = EspressoPalette.danger, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawLine(EspressoPalette.danger, Offset(w * 0.38f, h * 0.5f), Offset(w * 0.92f, h * 0.5f), strokeWidth = stroke, cap = StrokeCap.Round)
        val head = Path().apply {
            moveTo(w * 0.72f, h * 0.3f)
            lineTo(w * 0.92f, h * 0.5f)
            lineTo(w * 0.72f, h * 0.7f)
        }
        drawPath(head, color = EspressoPalette.danger, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IronMindScreen(
    sessionLabel: String?,
    messages: List<IronMindMessage>,
    quickActions: List<QuickAction>,
    inputQuery: String,
    isThinking: Boolean,
    onQueryChange: (String) -> Unit,
    onSend: () -> Unit,
    onQuickAction: (QuickAction) -> Unit,
    onSettings: () -> Unit,
    accountEmail: String? = null,
    onSignOut: (() -> Unit)? = null,
    onRecoveryAction: (RecoveryPayload, RecoveryAction) -> Unit = { _, _ -> },
    paletteChoice: GlassPaletteChoice = GlassPaletteChoice.Sage,
    onPaletteChoiceChange: (GlassPaletteChoice) -> Unit = {},
    userBodyweightLbs: Int? = null,
    onUserBodyweightChange: (Int?) -> Unit = {},
    smartSuggestEnabled: Boolean = true,
    onSmartSuggestChange: (Boolean) -> Unit = {},
    desk: IronMindDeskUi = IronMindDeskUi(),
    pendingAction: AiPendingActionUi? = null,
    onApplyPending: () -> Unit = {},
    onCancelPending: () -> Unit = {},
    onResolveReview: (PendingReview) -> Unit = {},
    onDismissReview: (Long) -> Unit = {},
    reviewError: String? = null,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onKeepGroup: (DeskDecisionUi) -> Unit = {},
    onRetireGroup: (DeskDecisionUi) -> Unit = {},
    onOpenGroup: (Long) -> Unit = {},
    onOpenNoticed: (Long) -> Unit = {},
    hubBuild: HubBuild? = null,
    hubExpandedItemId: String? = null,
    hubTopSets: Map<String, HubTopSets> = emptyMap(),
    onHubToggleExpand: (String) -> Unit = {},
    onHubSwipe: (HubItem, HubSwipe) -> Unit = { _, _ -> },
    onHubSetValueChange: (Long, Int?, Int?) -> Unit = { _, _, _ -> },
    onHubToggleChecked: (Long) -> Unit = {},
    onHubRemoveSet: (Long) -> Unit = {},
    onHubAddSet: (String, Long) -> Unit = { _, _ -> },
    onHubConfirm: (UncheckedSetsItem) -> Unit = {},
    onHubClearOpenSets: (UncheckedSetsItem) -> Unit = {},
    hubMessage: String? = null,
    onHubMessageShown: () -> Unit = {},
    claudeChanges: List<ClaudeChangeUi> = emptyList(),
    onUndoClaudeChange: (String) -> Unit = {},
    openSettingsOnStart: Boolean = false,
    onSettingsOpenedOnStart: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val palette = LocalGlassPalette.current
    var isSettingsVisible by remember { mutableStateOf(openSettingsOnStart) }
    LaunchedEffect(Unit) {
        if (openSettingsOnStart) onSettingsOpenedOnStart()
    }
    val settingsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    LaunchedEffect(hubMessage) {
        val message = hubMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message = message)
        onHubMessageShown()
    }
    var showDesk by rememberSaveable { mutableStateOf(true) }
    var cleanupExpanded by rememberSaveable { mutableStateOf(false) }
    val reviewRepos = LocalRepos.current
    val syncReview by reviewRepos.syncReview.collectAsState()
    val reviewExercises by reviewRepos.exercises.exercises.collectAsState()
    val reviewScope = rememberCoroutineScope()
    fun reportResolution(resolution: Resolution) {
        val message = when (resolution) {
            Resolution.Resolved -> return
            Resolution.ChangedSinceReview -> "Changed in the cloud since. Review it again."
            is Resolution.DependentWork -> "Later changes on these dates are kept with it."
            Resolution.Unsupported -> "This change can't be resolved here yet. It stays kept."
            Resolution.NotFound -> "Already resolved."
            is Resolution.NotSaved -> "Not saved on this device. It stays kept."
        }
        reviewScope.launch { snackbarHostState.showSnackbar(message) }
    }
    var conversationTarget by remember { mutableStateOf<Long?>(null) }
    val hasUserMessages = desk.asked.isNotEmpty()
    // Until the user has said something there is no conversation, so nothing is drawn for one. The
    // canned welcome only restated what the dock's own placeholder already says.
    val conversation = if (hasUserMessages) messages else emptyList()
    val listItems = remember(conversation) {
        buildList {
            conversation.forEachIndexed { index, message ->
                val previous = conversation.getOrNull(index - 1)
                val shouldShowTimestamp = previous == null ||
                    message.timestampMillis - previous.timestampMillis > 10 * 60 * 1000L
                val timestampLabel = when (message) {
                    is IronMindMessage.UserMessage -> message.timestamp
                    is IronMindMessage.AiMessage -> message.timestamp
                    else -> null
                }
                if (shouldShowTimestamp && timestampLabel != null) {
                    add(IronMindListItem.Timestamp(timestampLabel, message.timestampMillis))
                }
                add(IronMindListItem.Message(message))
            }
        }
    }

    val showHub = showDesk && cleanupExpanded && desk.uncheckedSets > 0
    val deskListState = rememberLazyListState()
    val conversationListState = rememberLazyListState()
    val listState = if (showDesk) deskListState else conversationListState
    var viewportHeightPx by remember { mutableStateOf(0) }
    LaunchedEffect(showDesk, conversationTarget, listItems.size, pendingAction, isThinking, viewportHeightPx) {
        if (showDesk) return@LaunchedEffect
        val target = conversationTarget
        if (target != null) {
            val index = listItems.indexOfFirst {
                it is IronMindListItem.Message && (it.message as? IronMindMessage.UserMessage)?.messageId == target
            }
            if (index >= 0) {
                conversationListState.scrollToItem(index)
            }
        } else if (listItems.isNotEmpty()) {
            conversationListState.animateScrollToItem(listItems.size + (if (pendingAction != null) 1 else 0) + (if (isThinking) 1 else 0) - 1)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            HubHeader(
                openCount = desk.decisionCount,
                onDesk = if (showDesk) {
                    null
                } else { { showDesk = true } },
                statusLabel = sessionLabel,
                onSettings = {
                    isSettingsVisible = true
                    onSettings()
                }
            )
        },
        containerColor = Color.Transparent
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            // The dock floats over this list, so the bottom inset is what guarantees the last
            // notification can always be scrolled out from under it however long the list gets.
            // The dock grows when it shows quick-action chips, so the inset follows its measured
            // height (less the see-through fade it starts with) instead of a fixed number.
            var dockHeightPx by remember { mutableStateOf(0) }
            val dockReserve = with(LocalDensity.current) {
                (dockHeightPx.toDp() - DOCK_FADE_HEIGHT + 12.dp).coerceAtLeast(DOCK_RESERVE)
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().onSizeChanged { viewportHeightPx = it.height },
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 12.dp,
                    bottom = dockReserve
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (showDesk) {
                    if (syncReview.count > 0) {
                        item(key = "sync-review") { DeskSection("SYNC REVIEW", accent = true) }
                        items(syncReview.conflicts, key = { "conflict:${it.id}" }) { conflict ->
                            SyncConflictCard(
                                item = conflict,
                                exerciseName = (conflict.local ?: conflict.remote)?.exerciseId?.let { id -> reviewExercises.firstOrNull { it.id == id }?.name },
                                onChoose = { choice ->
                                    reviewScope.launch { reportResolution(reviewRepos.resolveSyncConflict(conflict.id, choice, conflict.remote)) }
                                },
                            )
                        }
                        items(syncReview.exercises, key = { "exercise:${it.localId}" }) { exercise ->
                            SyncExerciseCard(
                                item = exercise,
                                onUseExisting = { serverId ->
                                    reviewScope.launch {
                                        if (!reviewRepos.useExistingExercise(exercise.localId, serverId)) {
                                            snackbarHostState.showSnackbar("Both exercises are on one date. Rename it in Log instead.")
                                        }
                                    }
                                },
                                onSendAgain = { reviewScope.launch { reviewRepos.retryExerciseCreate(exercise.localId) } },
                            )
                        }
                    }
                    pendingAction?.let { action ->
                        item(key = "pending-action") {
                            PendingActionCard(action, busy = isThinking, onApply = onApplyPending, onCancel = onCancelPending)
                        }
                    }
                    if (desk.pendingReviews.isNotEmpty()) {
                        item(key = "saved-reviews") { DeskSection("NEEDS REVIEW", accent = true) }
                        items(desk.pendingReviews, key = { "saved-review:${it.id}" }) { review ->
                            SavedReviewRow(review, isThinking, { onResolveReview(review) }, { onDismissReview(review.id) })
                        }
                    }
                    if (reviewError != null) {
                        item(key = "review-error") { Text(reviewError, style = EspressoType.body, color = EspressoPalette.ink) }
                    }
                    item(key = "needs-you") { DeskSection("NEEDS YOU", accent = true, trailing = if (desk.decisions.isEmpty()) "NONE" else null) }
                    items(desk.decisions, key = { it.id }) { decision ->
                        DeskDecisionCard(decision, { onKeepGroup(decision) }, { onRetireGroup(decision) }, { onOpenGroup(decision.slotId) })
                    }
                    item(key = "noticed") { DeskSection("NOTICED", trailing = if (desk.noticed.isEmpty()) "NONE" else null) }
                    items(desk.noticed, key = { "noticed:${it.kind}:${it.exerciseId}" }) { row ->
                        DeskNoticedRow(row, { onOpenNoticed(row.exerciseId) }, onQueryChange)
                    }
                    if (desk.asked.isNotEmpty()) {
                        item(key = "asked") { DeskSection("ASKED") }
                        items(desk.asked, key = { "asked:${it.messageId}" }) { asked ->
                            DeskAskedRow(asked) { conversationTarget = asked.messageId; showDesk = false }
                        }
                    }
                    if (claudeChanges.isNotEmpty()) {
                        item(key = "claude-changes") {
                            ClaudeChangesCard(changes = claudeChanges, onUndo = onUndoClaudeChange, modifier = Modifier.fillMaxWidth())
                        }
                    }
                    if (desk.uncheckedSets > 0) {
                        item(key = "cleanup-line") { DeskCleanupLine(desk.uncheckedSets, cleanupExpanded) { cleanupExpanded = !cleanupExpanded } }
                    }
                }
                if (showHub) {
                    item(key = "hub") {
                        HubList(
                            build = hubBuild ?: HubBuild(days = emptyList(), backlog = null, count = 0),
                            expandedItemId = hubExpandedItemId,
                            onToggleExpand = onHubToggleExpand,
                            onSwipe = onHubSwipe,
                            expandedContent = { item ->
                                when (item) {
                                    is UncheckedSetsItem -> {
                                        val topSets = hubTopSets[item.id]
                                        HubWorkoutCard(
                                            item = item,
                                            lastTopSet = topSets?.last,
                                            bestTopSet = topSets?.best,
                                            onSetValueChange = onHubSetValueChange,
                                            onToggleChecked = onHubToggleChecked,
                                            onRemoveSet = onHubRemoveSet,
                                            onTapField = { _, _ -> },
                                            onAddSet = { onHubAddSet(item.date, item.exerciseId) },
                                            onConfirm = { onHubConfirm(item) },
                                            onClearOpenSets = { onHubClearOpenSets(item) },
                                        )
                                    }
                                    else -> Unit
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                items(if (showDesk) emptyList() else listItems, key = { it.hashCode() }) { item ->
                    when (item) {
                        is IronMindListItem.Timestamp -> TimestampDivider(label = item.label)
                        is IronMindListItem.Message -> when (val message = item.message) {
                            is IronMindMessage.UserMessage -> UserMessageBubble(message = message)
                            is IronMindMessage.AiMessage -> AiMessageBubble(
                                message = message,
                                onRecoveryAction = onRecoveryAction
                            )
                            is IronMindMessage.AiThinking -> AiThinkingBubble()
                        }
                    }
                }
                if (!showDesk && pendingAction != null) {
                    item(key = "pending-action") {
                        PendingActionCard(pendingAction, busy = isThinking, onApply = onApplyPending, onCancel = onCancelPending)
                    }
                }
                if (!showDesk && isThinking) {
                    item(key = "thinking") {
                        AiThinkingBubble()
                    }
                }
            }

            IronMindDock(
                query = inputQuery,
                quickActions = quickActions,
                onQueryChange = onQueryChange,
                onSend = { if (inputQuery.isNotBlank()) { showDesk = false; conversationTarget = null; onSend() } },
                onQuickAction = { showDesk = false; conversationTarget = null; onQuickAction(it) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .onSizeChanged { dockHeightPx = it.height }
            )

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = dockReserve + 8.dp)
            )
        }
    }

    if (isSettingsVisible) {
        ModalBottomSheet(
            onDismissRequest = { isSettingsVisible = false },
            sheetState = settingsSheetState,
            containerColor = EspressoPalette.bg,
            contentColor = EspressoPalette.ink,
            scrimColor = Color.Black.copy(alpha = 0.55f),
            shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp),
            tonalElevation = 0.dp,
            dragHandle = {
                Box(
                    modifier = Modifier
                        .padding(top = 10.dp, bottom = 12.dp)
                        .size(width = 36.dp, height = 4.dp)
                        .clip(CircleShape)
                        .background(EspressoPalette.hair2)
                )
            }
        ) {
            IronMindSettingsSheet(
                selectedPalette = paletteChoice,
                onPaletteChoiceChange = { choice ->
                    onPaletteChoiceChange(choice)
                    isSettingsVisible = false
                },
                userBodyweightLbs = userBodyweightLbs,
                onUserBodyweightChange = onUserBodyweightChange,
                smartSuggestEnabled = smartSuggestEnabled,
                onSmartSuggestChange = onSmartSuggestChange,
                accountEmail = accountEmail,
                onSignOut = onSignOut?.let { signOut -> { isSettingsVisible = false; signOut() } },
                reviewCount = syncReview.count,
                onReviewSync = {
                    isSettingsVisible = false
                    showDesk = true
                    reviewScope.launch { deskListState.scrollToItem(0) }
                },
                modifier = Modifier.navigationBarsPadding()
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Shader warm-up (see ShaderWarmup)
// ---------------------------------------------------------------------------------------------

private const val IRONMIND_WARMUP = "ironmind-tab"
private const val IRONMIND_WARMUP_DELAY_MS = 7_000L

/**
 * Compiles the IronMind tab's shaders while nothing is moving (see [ShaderWarmup]): the real
 * desk with made-up decisions, session dots and both observation rules, drawn in the tab
 * transition's pose behind the app's opaque
 * background (the first visit). The conversation, the hub's expanded card and the settings sheet
 * appear only after a tap. Nothing here reads a view model or changes anything.
 */
@Composable
internal fun IronMindTabWarmup(bottomReserved: Dp) {
    TabWarmupShell(IRONMIND_WARMUP, IRONMIND_WARMUP_DELAY_MS, TAB_ARRIVAL_FRAMES, bottomReserved) {
        val hub = remember { warmupHubBuild() }
        val desk = remember { warmupIronMindDesk() }
        TabArrival { IronMindWarmupScreen(hub, desk) }
    }
}

@Composable
private fun IronMindWarmupScreen(hubBuild: HubBuild?, desk: IronMindDeskUi) {
    IronMindScreen(
        sessionLabel = null,
        messages = emptyList(),
        quickActions = listOf(QuickAction("Trend for Overhead Press", ""), QuickAction("Analyze last workout", "")),
        inputQuery = "",
        isThinking = false,
        onQueryChange = {},
        onSend = {},
        onQuickAction = {},
        onSettings = {},
        hubBuild = hubBuild,
        desk = desk,
    )
}

/** Compact representative evidence keeps both Noticed treatments within the first viewport. */
private fun warmupIronMindDesk(): IronMindDeskUi = IronMindDeskUi(
    decisions = listOf(DeskDecisionUi("warmup-keep", 1, "Chest · Arms",
        listOf(SlotSessionUi("2026-09-09", "SEP", "9"), SlotSessionUi("2026-09-26", "SEP", "26")),
        "2 sessions · Sep 9 – Sep 26",
        listOf(DeskLiftUi(1, "Bench Press", "Chest", listOf(true, false))))),
    noticed = listOf(
        NoticedUi(1, "Bench Press", "Chest", NoticedKind.BeatPrevious, "190 lb",
            "185 → 190 lb · 2 sessions · Sep 9 – Sep 26", listOf(1850, 1900), ChangelogWin("+5 lb", false)),
        NoticedUi(2, "Lat Pulldown", "Back", NoticedKind.FlatLoad, "150 lb",
            "4 sessions · Sep 9 – Sep 26", listOf(1500, 1500, 1500, 1500), question = "Why does Lat Pulldown look flat?"),
    ),
    uncheckedSets = 11,
)

/** Yesterday's Bench Press with two of four sets unchecked, and nine older unchecked sets in the backlog. */
private fun warmupHubBuild(): HubBuild {
    val today = todayLocalDate()
    val day = today.minusDays(1).toString()
    fun set(number: Int, done: Boolean) = WorkoutSet(exerciseId = 1, date = day, setNumber = number, weightLbs = 1850, reps = 5, completed = done)
    val item = UncheckedSetsItem(
        id = "unchecked:$day:1", date = day, exerciseId = 1, exerciseName = "Bench Press", muscleGroups = "Chest",
        isBodyweight = false, openSets = listOf(set(3, false), set(4, false)), referenceSets = listOf(set(1, true), set(2, true)),
    )
    val backlog = BacklogItem("backlog", today.minusDays(40).toString(), today.minusDays(28).toString(), setCount = 9, dayCount = 3)
    return HubBuild(days = listOf(HubDay(day, HubDayKind.Trained, listOf(item))), backlog = backlog, count = 2)
}
