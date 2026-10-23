package com.ayman.ecolift.ui.cardio

import com.ayman.ecolift.web.util.countLabel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ayman.ecolift.cardio.CardioActivitySpecs
import com.ayman.ecolift.cardio.CardioInsights
import com.ayman.ecolift.data.CardioActivityType
import com.ayman.ecolift.ui.theme.DawnPalette
import com.ayman.ecolift.ui.theme.LogUiFontFamily
import com.ayman.ecolift.ui.theme.SheetSerifFontFamily
import com.ayman.ecolift.ui.viewmodel.CardioViewModel
import com.ayman.ecolift.web.util.DAY_ABBREV
import com.ayman.ecolift.web.util.MONTH_ABBREV
import com.ayman.ecolift.web.util.minusDays
import com.ayman.ecolift.web.util.todayLocalDate
import kotlinx.datetime.LocalDate

@Composable
fun CardioSessionsScreen(
    viewModel: CardioViewModel,
    onBack: () -> Unit,
    onEditSession: ((Long) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val today = todayLocalDate()

    val machinesWithSessions = remember(state.sessions) {
        CardioActivityType.entries.filter { type ->
            state.sessions.any { CardioActivitySpecs.fromStoredType(it.activityType) == type }
        }
    }

    val filterOptions = remember(machinesWithSessions) {
        listOf<CardioActivityType?>(null) + machinesWithSessions
    }
    val selectedFilterIdx = remember(state.sessionsFilter, filterOptions) {
        filterOptions.indexOf(state.sessionsFilter).coerceAtLeast(0)
    }

    val filteredSessions = remember(state.sessions, state.sessionsFilter) {
        if (state.sessionsFilter == null) state.sessions
        else state.sessions.filter { CardioActivitySpecs.fromStoredType(it.activityType) == state.sessionsFilter }
    }

    // Grouped by date (newest first)
    val groupedSessions = remember(filteredSessions) {
        filteredSessions.groupBy { it.date }
    }

    val skyGradient = remember {
        Brush.verticalGradient(
            0f to DawnPalette.skyTop,
            0.42f to DawnPalette.skyMiddle,
            1f to DawnPalette.skyBottom,
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(skyGradient),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = DawnPalette.ink,
                        )
                    }
                    Text(
                        text = "All sessions",
                        fontFamily = SheetSerifFontFamily,
                        fontStyle = FontStyle.Italic,
                        fontWeight = FontWeight.Light,
                        fontSize = 28.sp,
                        color = DawnPalette.ink,
                    )
                }
                Text(
                    text = "${countLabel(filteredSessions.size, "session")}",
                    fontFamily = LogUiFontFamily,
                    fontSize = 12.sp,
                    color = DawnPalette.muted,
                )
            }

            // Filter ChipRow
            Box(modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                ChipRow(
                    labels = filterOptions.map { opt ->
                        if (opt == null) "All" else CardioActivitySpecs.specFor(opt).label
                    },
                    selectedIndex = selectedFilterIdx,
                    onSelect = { idx ->
                        viewModel.setSessionsFilter(filterOptions[idx])
                    },
                )
            }

            Spacer(Modifier.height(8.dp))

            // Sessions grouped by day
            if (filteredSessions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    DawnEyebrow("No sessions")
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 48.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    groupedSessions.forEach { (dateStr, sessionsForDay) ->
                        val dateLabel = formatDateLabel(dateStr, today)

                        item(key = "header_$dateStr") {
                            Box(modifier = Modifier.padding(top = 10.dp, bottom = 4.dp)) {
                                DawnSectionTitle(text = dateLabel)
                            }
                        }

                        items(sessionsForDay, key = { it.id }) { session ->
                            val whenLabel = when (session.date) {
                                today.toString() -> "Today"
                                today.minusDays(1L).toString() -> "Yesterday"
                                else -> runCatching { LocalDate.parse(session.date) }.getOrNull()?.let {
                                    DAY_ABBREV.getOrNull(it.dayOfWeek.ordinal) ?: ""
                                } ?: session.date
                            }

                            SessionRow(
                                session = session,
                                whenLabel = whenLabel,
                                onClick = { viewModel.selectSession(session.id) },
                            )
                        }
                    }
                }
            }
        }

        // Shared session sheet overlay
        val selectedId = state.selectedSessionId
        if (selectedId != null) {
            val session = state.sessions.firstOrNull { it.id == selectedId }
            if (session != null) {
                val deltas = remember(session, state.sessions) {
                    CardioInsights.sessionDeltas(session, state.sessions)
                }
                CardioSessionSheet(
                    session = session,
                    deltas = deltas,
                    onEdit = {
                        viewModel.selectSession(null)
                        viewModel.startEditDraft(session.id)
                        onEditSession?.invoke(session.id)
                    },
                    onDelete = {
                        viewModel.deleteSession(session.id)
                    },
                    onMachine = {
                        viewModel.selectSession(null)
                    },
                    onDismiss = {
                        viewModel.selectSession(null)
                    },
                )
            }
        }
    }
}
