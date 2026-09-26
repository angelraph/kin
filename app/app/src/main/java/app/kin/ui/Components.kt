package app.kin.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import app.kin.solana.CircleStatus

val CardRadius = 16.dp
val ButtonRadius = 12.dp
val CardShape = RoundedCornerShape(CardRadius)
val ControlShape = RoundedCornerShape(ButtonRadius)
val BigControlShape = RoundedCornerShape(16.dp)
private val Hairline = Color(0x14000000)
private val OnDarkLine = Color(0x21FFFFFF)

/** Small monospaced caption used for technical labels and section markers. */
@Composable
fun KinLabel(text: String, modifier: Modifier = Modifier, color: Color = KinColors.Slate) {
    Text(text.uppercase(), modifier = modifier, style = MaterialTheme.typography.labelMedium, color = color)
}

@Composable
fun Mono(text: String, modifier: Modifier = Modifier, color: Color = KinColors.Ink, size: Int = 13) {
    Text(text, modifier = modifier, color = color, style = TextStyle(fontFamily = KinMono, fontSize = size.sp, letterSpacing = 0.sp))
}

// Buttons

@Composable
fun AquaButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, arrow: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = 54.dp),
        shape = BigControlShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = KinColors.Aqua,
            contentColor = KinColors.Ink,
            disabledContainerColor = KinColors.Cloud,
            disabledContentColor = KinColors.Slate,
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 22.dp, top = 12.dp, end = 16.dp, bottom = 12.dp),
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium)
        if (arrow) {
            Spacer(Modifier.width(8.dp))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
fun GraphiteButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 44.dp),
        shape = ControlShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = KinColors.Graphite,
            contentColor = Color.White,
            disabledContainerColor = KinColors.Steel,
            disabledContentColor = Color.White,
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

@Composable
fun OutlineButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().heightIn(min = 50.dp),
        shape = BigControlShape,
        border = BorderStroke(1.dp, KinColors.Steel),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = KinColors.Ink, disabledContentColor = KinColors.Slate),
    ) { Text(text, style = MaterialTheme.typography.titleMedium) }
}

@Composable
fun OnDarkButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 44.dp),
        shape = BigControlShape,
        colors = ButtonDefaults.buttonColors(containerColor = Color(0x1AFFFFFF), contentColor = Color.White),
        border = BorderStroke(1.dp, OnDarkLine),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp, vertical = 10.dp),
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

@Composable
fun QuietButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, color: Color = KinColors.Slate) {
    TextButton(onClick = onClick, modifier = modifier) { Text(text, color = color, style = MaterialTheme.typography.labelLarge) }
}

// Surfaces

@Composable
fun CloudCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, padding: Dp = 16.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(KinColors.Cloud)
            .border(1.dp, Hairline, CardShape)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(padding),
        content = content,
    )
}

@Composable
fun PaperCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, padding: Dp = 16.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(KinColors.Paper)
            .border(1.dp, KinColors.Steel, CardShape)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(padding),
        content = content,
    )
}

@Composable
fun GraphiteCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, padding: Dp = 20.dp, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(KinColors.Graphite)
            .border(1.dp, OnDarkLine, CardShape)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(padding),
        content = content,
    )
}

/** Full-width highlighter strip used for site-wide notices. */
@Composable
fun NoticeStrip(text: String) {
    Box(Modifier.fillMaxWidth().background(KinColors.Lime).padding(horizontal = 16.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = KinColors.Ink.copy(alpha = 0.8f), textAlign = TextAlign.Center)
    }
}

@Composable
fun Pill(text: String, color: Color = KinColors.Charcoal, filled: Boolean = false, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(50)
    Text(
        text,
        color = color,
        style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp),
        modifier = modifier
            .clip(shape)
            .let { if (filled) it.background(color.copy(alpha = 0.12f)) else it.border(1.dp, KinColors.Steel, shape) }
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

@Composable
fun StatusPill(status: CircleStatus, dark: Boolean = false) {
    val (label, color) = when (status) {
        CircleStatus.Open -> "Open" to if (dark) KinColors.Volt else KinColors.Warn
        CircleStatus.Active -> "Live" to if (dark) KinColors.Aqua else KinColors.Good
        CircleStatus.Completed -> "Done" to if (dark) Color.White.copy(alpha = 0.6f) else KinColors.Slate
    }
    Row(
        Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = if (dark) 0.14f else 0.12f)).padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, color = color, style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp))
    }
}

// Inputs

@Composable
fun KinField(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    isError: Boolean = false,
    singleLine: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        singleLine = singleLine,
        isError = isError,
        keyboardOptions = keyboardOptions,
        shape = ControlShape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = KinColors.Ink,
            unfocusedBorderColor = KinColors.Steel,
            focusedLabelColor = KinColors.Ink,
            unfocusedLabelColor = KinColors.Slate,
            cursorColor = KinColors.Ink,
            focusedContainerColor = KinColors.Paper,
            unfocusedContainerColor = KinColors.Paper,
            errorBorderColor = KinColors.Bad,
        ),
    )
}

@Composable
fun KinSwitch(checked: Boolean, onChange: (Boolean) -> Unit, enabled: Boolean = true) {
    Switch(
        checked = checked,
        onCheckedChange = onChange,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedTrackColor = KinColors.Aqua,
            checkedThumbColor = KinColors.Ink,
            checkedBorderColor = KinColors.Aqua,
            uncheckedTrackColor = KinColors.Cloud,
            uncheckedThumbColor = KinColors.Slate,
            uncheckedBorderColor = KinColors.Steel,
        ),
    )
}

@Composable
fun ToggleRow(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(2.dp))
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = KinColors.Slate)
        }
        Spacer(Modifier.width(12.dp))
        KinSwitch(checked, onChange)
    }
}

@Composable
fun ChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        color = KinColors.Ink,
        modifier = Modifier
            .clip(shape)
            .background(if (selected) KinColors.Aqua else KinColors.Paper)
            .border(1.dp, if (selected) KinColors.Aqua else KinColors.Steel, shape)
            .clickable(onClick = onClick)
            .heightIn(min = 40.dp)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    )
}

@Composable
fun StepperRow(label: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.titleMedium)
        }
        StepButton("−", "Decrease $label", onMinus)
        Box(Modifier.width(56.dp), contentAlignment = Alignment.Center) { Mono(value, size = 16) }
        StepButton("+", "Increase $label", onPlus)
    }
}

@Composable
private fun StepButton(symbol: String, description: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(44.dp)
            .clip(ControlShape)
            .border(1.dp, KinColors.Steel, ControlShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { Text(symbol, style = MaterialTheme.typography.titleLarge) }
}

// Data displays

/** One bar per member: filled once that member has paid. The whole row reads as the round's progress. */
@Composable
fun Segments(paid: List<Boolean>, dark: Boolean, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().height(8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        paid.forEach { p ->
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(50))
                    .background(if (p) KinColors.Aqua else if (dark) Color(0x26FFFFFF) else KinColors.Steel),
            )
        }
    }
}

private val avatarTints = listOf(KinColors.Aqua, KinColors.Lime, KinColors.Volt, Color(0xFFE4E4E7), Color(0xFFCFFAFE))

@Composable
fun Avatar(seed: String, label: String, size: Dp = 40.dp, highlight: Boolean = false) {
    val tint = if (highlight) KinColors.Ink else avatarTints[(seed.hashCode() and 0x7fffffff) % avatarTints.size]
    Box(Modifier.size(size).clip(CircleShape).background(tint), contentAlignment = Alignment.Center) {
        Text(
            label,
            color = if (highlight) KinColors.Aqua else KinColors.Ink,
            style = TextStyle(fontFamily = KinMono, fontWeight = FontWeight.Medium, fontSize = (size.value * 0.36f).sp),
        )
    }
}

@Composable
fun ScoreRing(percent: Int?, size: Dp, dark: Boolean = false) {
    val track = if (dark) Color(0x26FFFFFF) else KinColors.Steel
    val fill = if (dark) KinColors.Aqua else KinColors.Ink
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(size)) {
            val stroke = size.toPx() * 0.09f
            val inset = stroke / 2
            val arcSize = androidx.compose.ui.geometry.Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(track, 0f, 360f, false, topLeft = Offset(inset, inset), size = arcSize, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
            if (percent != null) {
                drawArc(fill, -90f, 360f * percent / 100f, false, topLeft = Offset(inset, inset), size = arcSize, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = StrokeCap.Round))
            }
        }
        Text(
            percent?.let { "$it" } ?: "New",
            style = MaterialTheme.typography.headlineSmall.copy(fontSize = (size.value * 0.3f).sp),
            color = if (dark) Color.White else KinColors.Ink,
        )
    }
}

/** Three linked dots: the Kin mark. */
@Composable
fun KinMark(size: Dp, color: Color = KinColors.Ink, accent: Color = KinColors.Aqua) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val top = Offset(w * 0.5f, w * 0.2f)
        val left = Offset(w * 0.2f, w * 0.72f)
        val right = Offset(w * 0.8f, w * 0.72f)
        val line = w * 0.05f
        drawLine(color, top, left, line, StrokeCap.Round)
        drawLine(color, top, right, line, StrokeCap.Round)
        drawLine(color, left, right, line, StrokeCap.Round)
        drawCircle(accent, w * 0.13f, top)
        drawCircle(color, w * 0.11f, left)
        drawCircle(color, w * 0.11f, right)
    }
}

/** Layered technical sheets around a graphite tile, the visual signature of the app. */
@Composable
fun LedgerArt(modifier: Modifier = Modifier, headline: String = "250", caption: String = "POT / ROUND 3") {
    Box(modifier.fillMaxWidth().height(230.dp), contentAlignment = Alignment.Center) {
        Sheet(Modifier.offset(x = (-34).dp, y = 10.dp).rotate(-7f), KinColors.Cloud, "PAID 4/5", "AMARA  ....  20")
        Sheet(Modifier.offset(x = 34.dp, y = 6.dp).rotate(6f), KinColors.Paper, "BOND LOCKED", "40 / 40")
        Column(
            Modifier
                .offset(y = (-6).dp)
                .shadow(18.dp, CardShape, ambientColor = Color(0x33000000), spotColor = Color(0x33000000))
                .clip(CardShape)
                .background(KinColors.Graphite)
                .border(1.dp, OnDarkLine, CardShape)
                .padding(horizontal = 22.dp, vertical = 18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                KinMark(22.dp, Color.White)
                Spacer(Modifier.width(8.dp))
                KinLabel(caption, color = Color.White.copy(alpha = 0.55f))
            }
            Spacer(Modifier.height(10.dp))
            Text(headline, style = MaterialTheme.typography.displayMedium, color = Color.White)
            Spacer(Modifier.height(10.dp))
            Box(Modifier.width(120.dp).height(3.dp).clip(RoundedCornerShape(50)).background(KinColors.Sweep))
        }
    }
}

@Composable
private fun Sheet(modifier: Modifier, fill: Color, top: String, bottom: String) {
    Column(
        modifier
            .size(width = 168.dp, height = 132.dp)
            .shadow(4.dp, CardShape, ambientColor = Color(0x14000000), spotColor = Color(0x14000000))
            .clip(CardShape)
            .background(fill)
            .border(1.dp, KinColors.Steel, CardShape)
            .padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        KinLabel(top)
        Mono(bottom, color = KinColors.Slate, size = 11)
    }
}

// Navigation

enum class Tab(val label: String, val icon: ImageVector) {
    Circles("Circles", Icons.Filled.Groups),
    Discover("Discover", Icons.Filled.Explore),
    You("You", Icons.Filled.Person),
}

@Composable
fun KinBottomBar(selected: Tab, onSelect: (Tab) -> Unit) {
    Column(Modifier.fillMaxWidth().background(KinColors.Paper)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(KinColors.Steel))
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 6.dp)) {
            Tab.entries.forEach { tab -> BarItem(tab, tab == selected) { onSelect(tab) } }
        }
    }
}

@Composable
private fun RowScope.BarItem(tab: Tab, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.weight(1f).clip(ControlShape).clickable(onClick = onClick).heightIn(min = 52.dp).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.clip(RoundedCornerShape(50)).background(if (selected) KinColors.Aqua else Color.Transparent).padding(horizontal = 18.dp, vertical = 4.dp),
        ) {
            Icon(tab.icon, contentDescription = null, tint = if (selected) KinColors.Ink else KinColors.Slate, modifier = Modifier.size(22.dp))
        }
        Text(tab.label, style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.sp), color = if (selected) KinColors.Ink else KinColors.Slate)
    }
}

@Composable
fun SectionTitle(title: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
        trailing?.invoke()
    }
}
