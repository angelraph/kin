package app.kin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.kin.Config

/** Walks through a whole circle locally, including a missed payment, so the rules are clear before any money moves. */
@Composable
fun SimulationScreen(onBack: () -> Unit, onConnect: (() -> Unit)?) {
    var sim by remember { mutableStateOf(SimState()) }
    Column(Modifier.fillMaxSize().background(KinColors.Paper)) {
        Box(Modifier.statusBarsPadding())
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text("A circle, played out", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Pill("Simulation", KinColors.Warn, filled = true)
            Spacer(Modifier.width(12.dp))
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(KinColors.Steel))

        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    "Five people, $PAY tokens a round. Play each round and watch what the rules do. Nothing here touches the network.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = KinColors.Slate,
                )
            }
            item { SimHero(sim) }
            item {
                if (sim.done) {
                    CloudCard {
                        Text("What just happened", style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Diego missed a payment in round 2. His bond covered it, the pot still paid out on time, and nobody else was left short. His Kin Score now shows the miss in every circle he joins.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = KinColors.Charcoal,
                        )
                    }
                }
            }
            item {
                if (sim.done) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (onConnect != null) AquaButton("Start a real circle", onConnect)
                        OutlineButton("Play again", { sim = SimState() })
                    }
                } else {
                    AquaButton("Play round ${sim.round + 1}", { sim = Simulation.playRound(sim) })
                }
            }
            item { KinLabel("Members") }
            items(sim.members, key = { it.name }) { m -> SimMemberRow(m, sim) }
            if (sim.events.isNotEmpty()) {
                item { KinLabel("What happened") }
                itemsIndexed(sim.events.reversed(), key = { i, _ -> sim.events.size - i }) { _, e -> SimEventRow(e) }
            }
            item { Box(Modifier.navigationBarsPadding()) }
        }
    }
}

@Composable
private fun SimHero(sim: SimState) {
    GraphiteCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            KinLabel(if (sim.done) "Complete" else "Round ${sim.round + 1} of $ROUNDS", Modifier.weight(1f), Color.White.copy(alpha = 0.55f))
            Pill(if (sim.done) "Done" else "Live", if (sim.done) Color.White.copy(alpha = 0.7f) else KinColors.Aqua, filled = true)
        }
        Spacer(Modifier.height(10.dp))
        Text("${sim.pot} ${Config.TOKEN_SYMBOL}", style = MaterialTheme.typography.displayMedium, color = Color.White)
        Text("pot each round", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.55f))
        Spacer(Modifier.height(16.dp))
        Segments(List(ROUNDS) { it < sim.round }, dark = true)
        Spacer(Modifier.height(10.dp))
        Text(
            sim.recipient?.let { "Next payout: $it" } ?: "Every member has been paid once",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.8f),
        )
    }
}

@Composable
private fun SimMemberRow(m: SimMember, sim: SimState) {
    PaperCard(padding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(m.name, m.name.take(1), 40.dp, highlight = m.name == "You")
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(m.name, style = MaterialTheme.typography.titleMedium)
                val score = m.scorePercent?.let { "Kin Score $it" } ?: "No history yet"
                val bond = "bond ${m.bondLeft}"
                Text("$score  ·  $bond", style = MaterialTheme.typography.bodyMedium, color = if (m.missed > 0) KinColors.Bad else KinColors.Slate)
            }
            if (m.received > 0) Pill("Received ${m.received}", KinColors.Good, filled = true)
            else if (!sim.done && sim.recipient == m.name) Pill("Next", KinColors.Ink, filled = true)
        }
    }
}

@Composable
private fun SimEventRow(e: SimEvent) {
    val color = when (e.tone) {
        SimEvent.Tone.Good -> KinColors.Good
        SimEvent.Tone.Bad -> KinColors.Bad
        SimEvent.Tone.Normal -> KinColors.Slate
    }
    Row(verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 7.dp).size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(e.text, style = MaterialTheme.typography.bodyMedium)
            Mono("ROUND ${e.round + 1}", color = KinColors.Slate, size = 10)
        }
    }
}
