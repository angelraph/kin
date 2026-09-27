package app.kin.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * The first thing a new install shows: a fast run of oversized words, one per on-chain step,
 * a progress line sweeping under them, and a brand beat at the end. Modelled on the kind of kinetic
 * product-demo cut the user pointed to (big edge-to-edge words, a sliding scrubber, a colour flash
 * between beats, closing on the brand), rebuilt in Kin's own palette. Tapping skips straight through.
 */
private class Beat(val word: String, val bg: Color, val fg: Color)

private val beats = listOf(
    Beat("SAVE", KinColors.Ink, Color.White),
    Beat("BONDED", KinColors.Aqua, KinColors.Ink),
    Beat("VERIFIED", KinColors.Graphite, Color.White),
    Beat("PAID", KinColors.Lime, KinColors.Ink),
)
private const val BEAT_MILLIS = 460
private const val BRAND_MILLIS = 650

@Composable
fun IntroScreen(onDone: () -> Unit) {
    var index by remember { mutableIntStateOf(0) }
    var showBrand by remember { mutableStateOf(false) }
    var skipped by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        for (i in beats.indices) {
            index = i
            delay(BEAT_MILLIS.toLong())
        }
        showBrand = true
        delay(BRAND_MILLIS.toLong())
        if (!skipped) onDone()
    }

    val progress by animateFloatAsState(
        targetValue = if (showBrand) 1f else (index + 1f) / beats.size,
        animationSpec = tween(BEAT_MILLIS, easing = LinearEasing),
        label = "intro-progress",
    )
    val beat = beats[index]
    val bgColor = if (showBrand) KinColors.Ink else beat.bg
    val fgColor = if (showBrand) Color.White else beat.fg

    Box(
        Modifier
            .fillMaxSize()
            .background(bgColor)
            .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) {
                skipped = true
                onDone()
            },
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(
            targetState = if (showBrand) -1 else index,
            transitionSpec = {
                (fadeIn(tween(120)) + slideInVertically(tween(180)) { it / 6 }) togetherWith
                    (fadeOut(tween(90)) + slideOutVertically(tween(120)) { -it / 6 })
            },
            label = "intro-word",
        ) { i ->
            if (i < 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    KinMark(36.dp, color = Color.White, accent = KinColors.Aqua)
                    Spacer(Modifier.width(12.dp))
                    Text("Kin", style = MaterialTheme.typography.displayMedium, color = Color.White)
                }
            } else {
                Text(
                    beats[i].word,
                    style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = fgColor,
                )
            }
        }

        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp)) {
            Box(Modifier.fillMaxWidth().height(2.dp).clip(RoundedCornerShape(50)).background(fgColor.copy(alpha = 0.25f))) {
                Box(
                    Modifier
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .height(2.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (showBrand) KinColors.Aqua else fgColor),
                )
            }
        }
    }
}
