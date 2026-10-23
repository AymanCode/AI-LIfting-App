package com.ayman.ecolift.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ayman.ecolift.web.supabase.sync.SyncStatus
import kotlinx.datetime.LocalDate

/** Shared Espresso pieces (spec §Type, §Shape and depth, §Bottom navigation). */
object EspressoType {
    /** Space Grotesk 10sp, tracked 2sp, uppercase. The caller uppercases the text. */
    val eyebrow = TextStyle(fontFamily = SheetSansFontFamily, fontWeight = FontWeight.Medium, fontSize = 10.sp, lineHeight = 12.sp, letterSpacing = 2.sp)
    val kicker = TextStyle(fontFamily = SheetSansFontFamily, fontWeight = FontWeight.Medium, fontSize = 10.5.sp, lineHeight = 12.sp, letterSpacing = 2.sp)
    val mastTitle = TextStyle(fontFamily = LogDisplayFontFamily, fontWeight = FontWeight.Bold, fontSize = 50.sp, lineHeight = 50.sp, letterSpacing = (-2).sp)
    val mastTitleSmall = TextStyle(fontFamily = LogDisplayFontFamily, fontWeight = FontWeight.Bold, fontSize = 34.sp, lineHeight = 36.sp, letterSpacing = (-1).sp)
    val body = TextStyle(fontFamily = LogUiFontFamily, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 17.sp)
    val chip = TextStyle(fontFamily = LogUiFontFamily, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 12.sp)
    val chipBold = TextStyle(fontFamily = LogDisplayFontFamily, fontWeight = FontWeight.Bold, fontSize = 12.sp, lineHeight = 13.sp)
    val name = TextStyle(fontFamily = LogDisplayFontFamily, fontWeight = FontWeight.Bold, fontSize = 15.sp, lineHeight = 18.sp, letterSpacing = (-0.2).sp)
    val tileValue = TextStyle(fontFamily = LogDisplayFontFamily, fontWeight = FontWeight.Bold, fontSize = 22.sp, lineHeight = 24.sp, letterSpacing = (-1).sp)
}

object EspressoShape {
    val masthead = RoundedCornerShape(bottomStart = 30.dp, bottomEnd = 30.dp)
    val card = RoundedCornerShape(24.dp)
    val field = RoundedCornerShape(18.dp)
    val tile = RoundedCornerShape(14.dp)
    val chip = RoundedCornerShape(10.dp)
}

/** "THURSDAY, SEP 11" for a kicker. */
fun espressoLongDate(date: LocalDate): String {
    val months = listOf("JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC")
    return "${date.dayOfWeek.name}, ${months[date.monthNumber - 1]} ${date.dayOfMonth}"
}

/** "Wed, Sep 2" for a small masthead title. */
fun espressoShortDate(date: LocalDate): String {
    val months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    val day = date.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }
    return "$day, ${months[date.monthNumber - 1]} ${date.dayOfMonth}"
}

/**
 * The tab's announcement: kicker, title in the tab colour, a trailing control, one line of
 * context or a row of pills. Deeper tonal step, 30dp bottom corners.
 */
@Composable
fun EspressoMasthead(
    kicker: String,
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    line: (@Composable () -> Unit)? = null,
    kickerEnd: String? = null,
    kickerEndContent: (@Composable () -> Unit)? = null,
    status: SyncStatus? = LocalSyncStatus.current,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(EspressoShape.masthead)
            .background(EspressoPalette.mast)
            .padding(start = 22.dp, end = 22.dp, top = 24.dp, bottom = 20.dp),
    ) {
        // The kicker row can carry a second, right-aligned fact in the same voice (the Log tab
        // puts its exercise and set count there instead of on a line of its own). A blank kicker
        // with nothing at its end draws no row at all (Split's Archive has no kicker).
        if (kicker.isNotBlank() || kickerEnd != null || kickerEndContent != null) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                if (kicker.isNotBlank()) {
                    // The kicker and, 8 dp after its last letter, the sync dot (SyncDot.kt). The dot's
                    // slot is always reserved while a status exists, so the dot appearing never moves
                    // the text.
                    Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = kicker,
                            style = EspressoType.kicker,
                            color = EspressoPalette.dim,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (status != null) {
                            Spacer(Modifier.width(8.dp))
                            SyncDot(status = status)
                        }
                    }
                } else {
                    Spacer(Modifier.weight(1f))
                }
                if (kickerEndContent != null) {
                    // A composed end (the Log's count with its set meter) sizes itself; the caller
                    // guarantees it fits beside the kicker.
                    Spacer(Modifier.width(12.dp))
                    kickerEndContent()
                } else if (kickerEnd != null) {
                    Spacer(Modifier.width(12.dp))
                    Text(text = kickerEnd, style = EspressoType.kicker, color = EspressoPalette.dim, maxLines = 1, softWrap = false)
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.weight(1f)) { title() }
            if (trailing != null) {
                Spacer(Modifier.width(12.dp))
                trailing()
            }
        }
        if (line != null) {
            Spacer(Modifier.height(12.dp))
            line()
        }
    }
}

@Composable
fun EspressoMasthead(
    kicker: String,
    titleText: String,
    titleColor: Color,
    modifier: Modifier = Modifier,
    small: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    line: (@Composable () -> Unit)? = null,
) {
    EspressoMasthead(
        kicker = kicker,
        title = {
            // One line, always. The size steps down until the title fits the row rather than
            // breaking inside the word ("Progres / s" was the bug).
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val base = if (small) EspressoType.mastTitleSmall else EspressoType.mastTitle
                val measurer = rememberTextMeasurer()
                val maxPx = constraints.maxWidth
                val fitted = remember(titleText, maxPx, small) {
                    var size = base.fontSize.value
                    while (size > 26f) {
                        val width = measurer.measure(
                            text = titleText,
                            style = base.copy(fontSize = size.sp, lineHeight = (size * 1.02f).sp),
                            softWrap = false,
                            maxLines = 1,
                        ).size.width
                        if (width <= maxPx) break
                        size -= 2f
                    }
                    size
                }
                Text(
                    text = titleText,
                    style = base.copy(fontSize = fitted.sp, lineHeight = (fitted * 1.02f).sp),
                    color = titleColor,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Clip,
                )
            }
        },
        modifier = modifier,
        trailing = trailing,
        line = line,
    )
}

/** The one-line context under a masthead title. */
@Composable
fun EspressoMastLine(text: String, modifier: Modifier = Modifier) {
    Text(text = text, style = EspressoType.body, color = EspressoPalette.dim, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = modifier)
}

/** 38dp, 14dp radius, a white wash on the masthead tone. */
@Composable
fun EspressoIconButton(
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .size(38.dp)
            .clip(EspressoShape.tile)
            .background(Color.White.copy(alpha = 0.08f))
            .semantics { role = Role.Button; this.contentDescription = contentDescription }
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/**
 * Segment pills for a masthead: the active one on a white wash. [fill] gives every tab an equal share of
 * the row's width with its label centred, so the row never scrolls.
 */
@Composable
fun EspressoTabPills(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    fill: Boolean = false,
) {
    // Labels never break mid-word; unless the row fills its width, a narrow phone scrolls it instead.
    Row(
        modifier = if (fill) modifier.fillMaxWidth() else modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        labels.forEachIndexed { index, label ->
            val on = index == selectedIndex
            Box(
                contentAlignment = Alignment.Center,
                modifier = (if (fill) Modifier.weight(1f) else Modifier)
                    .clip(EspressoShape.tile)
                    .background(if (on) Color.White.copy(alpha = 0.08f) else Color.Transparent)
                    .semantics { role = Role.Tab; this.contentDescription = label; this.selected = on }
                    .clickable(role = Role.Tab) { onSelect(index) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Text(
                    text = label,
                    style = EspressoType.chip.copy(fontSize = 12.5.sp),
                    color = if (on) EspressoPalette.ink else EspressoPalette.dim,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

/** The one range control: every option in a track, the active one on panel2. */
@Composable
internal fun <T> EspressoRangeTrack(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    description: (T) -> String = label,
    fill: Boolean = false,
) {
    val reduced = rememberReducedMotion()
    // Full width (landing, By split): 38dp options plus the 3dp padding make the old landing rail's 44dp.
    val minHeight = if (fill) 38.dp else 36.dp
    Row(
        modifier = modifier
            .then(if (fill) Modifier.fillMaxWidth() else Modifier)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White.copy(alpha = 0.045f))
            .padding(3.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        options.forEach { option ->
            val on = option == selected
            val bg by animateColorAsState(
                if (on) EspressoPalette.panel2 else Color.Transparent,
                tween(if (reduced) 0 else 140),
                label = "range bg",
            )
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .then(if (fill) Modifier.weight(1f) else Modifier)
                    .clip(RoundedCornerShape(11.dp))
                    .background(bg)
                    .semantics { role = Role.Tab; contentDescription = description(option); this.selected = on }
                    .clickable(role = Role.Tab) { onSelect(option) }
                    .heightIn(min = minHeight)
                    .padding(horizontal = 13.dp, vertical = 8.dp),
            ) {
                Text(
                    text = label(option),
                    style = EspressoType.chip.copy(fontSize = 12.5.sp, lineHeight = 14.sp),
                    color = if (on) EspressoPalette.ink else EspressoPalette.dim,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

enum class EspressoTone { Up, Down, Flat }

/** A filled delta chip. Up is green, down is amber, flat is the quiet panel tone. */
@Composable
fun EspressoDeltaChip(text: String, tone: EspressoTone, modifier: Modifier = Modifier) {
    val (fill, ink) = when (tone) {
        EspressoTone.Up -> EspressoPalette.up to EspressoPalette.onColor
        EspressoTone.Down -> EspressoPalette.down to EspressoPalette.onColor
        EspressoTone.Flat -> EspressoPalette.panel2 to EspressoPalette.dim
    }
    Box(
        modifier = modifier.clip(EspressoShape.chip).background(fill).padding(horizontal = 8.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, style = EspressoType.chipBold, color = ink, maxLines = 1, softWrap = false)
    }
}

@Composable
fun EspressoEyebrow(text: String, modifier: Modifier = Modifier, color: Color = EspressoPalette.dim) {
    Text(text = text.uppercase(), style = EspressoType.eyebrow, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = modifier)
}

/** A quiet stat tile: eyebrow, value, caption. */
@Composable
fun EspressoTile(eyebrow: String, value: String, caption: String?, modifier: Modifier = Modifier, valueColor: Color = EspressoPalette.ink) {
    Column(modifier = modifier.clip(RoundedCornerShape(16.dp)).background(EspressoPalette.panel).padding(12.dp)) {
        EspressoEyebrow(eyebrow)
        Spacer(Modifier.height(8.dp))
        Text(text = value, style = EspressoType.tileValue, color = valueColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (caption != null) {
            Spacer(Modifier.height(5.dp))
            Text(text = caption, style = EspressoType.body.copy(fontSize = 10.5.sp, lineHeight = 13.sp), color = EspressoPalette.dim, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Tile whose caption is a data line (numbers bold, words quiet) and never wraps. */
@Composable
fun EspressoTile(
    eyebrow: String,
    value: String,
    captionParts: List<com.ayman.ecolift.ui.viewmodel.DataPart>,
    modifier: Modifier = Modifier,
    valueColor: Color = EspressoPalette.ink,
    valueSuffix: String? = null,
) {
    Column(modifier = modifier.clip(RoundedCornerShape(16.dp)).background(EspressoPalette.panel).padding(12.dp)) {
        EspressoEyebrow(eyebrow)
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(text = value, style = EspressoType.tileValue, color = valueColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (valueSuffix != null) {
                Text(text = valueSuffix, style = EspressoType.body.copy(fontSize = 11.sp, lineHeight = 13.sp), color = EspressoPalette.dim,
                    maxLines = 1, modifier = Modifier.padding(start = 3.dp, bottom = 3.dp))
            }
        }
        Spacer(Modifier.height(5.dp))
        DataText(parts = captionParts, size = 10.5.sp, lineHeight = 13.sp, filler = EspressoPalette.faint, data = EspressoPalette.dim, maxLines = 1)
    }
}

/**
 * A section title: a short tick in the tab's colour, the title, and a hairline that runs to the
 * aside. Sections are not steps, so nobody numbers them (owner, 2026-09-25); [number] is kept
 * only so an old call still compiles. The aside carries data the screen does not already show.
 */
@Composable
fun EspressoSectionHeader(
    number: String? = null,
    title: String,
    aside: String? = null,
    modifier: Modifier = Modifier,
    asideParts: List<com.ayman.ecolift.ui.viewmodel.DataPart>? = null,
    accent: Color = EspressoPalette.dim,
) {
    Row(
        modifier = modifier.fillMaxWidth().padding(top = 6.dp, bottom = 2.dp).semantics(mergeDescendants = true) { heading() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(width = 3.dp, height = 11.dp).clip(RoundedCornerShape(1.5.dp)).background(accent))
        Spacer(Modifier.width(8.dp))
        if (number != null) {
            Text(text = number, style = EspressoType.eyebrow, color = EspressoPalette.faint)
            Spacer(Modifier.width(8.dp))
        }
        Text(text = title.uppercase(), style = EspressoType.eyebrow, color = EspressoPalette.ink, maxLines = 1)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f).height(1.dp).background(EspressoPalette.hair2.copy(alpha = 0.7f)))
        if (asideParts != null) {
            Spacer(Modifier.width(10.dp))
            DataText(parts = asideParts, size = 11.5.sp, data = EspressoPalette.dim, maxLines = 1)
        } else if (aside != null) {
            Spacer(Modifier.width(10.dp))
            Text(text = aside, style = EspressoType.body.copy(fontSize = 11.5.sp, lineHeight = 14.sp), color = EspressoPalette.dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
fun EspressoMuscleDot(color: Color, modifier: Modifier = Modifier, size: Int = 8) {
    Box(modifier = modifier.size(size.dp).clip(RoundedCornerShape(50)).background(color))
}

/** The only shadow in the app: the primary button's, in its own colour. */
fun Modifier.espressoButtonShadow(color: Color, shape: Shape): Modifier =
    shadow(elevation = 12.dp, shape = shape, clip = false, ambientColor = color.copy(alpha = 0.30f), spotColor = color.copy(alpha = 0.30f))
