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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.kin.Config
import app.kin.solana.KinProgram

private val Gutter = 20.dp

/** What someone sees before connecting a wallet. It has to explain Kin on its own. */
@Composable
fun LandingScreen(busy: Boolean, onConnect: () -> Unit, onSimulate: () -> Unit) {
    Column(Modifier.fillMaxSize().background(KinColors.Paper)) {
        Box(Modifier.statusBarsPadding())
        NoticeStrip("Live on ${Config.CLUSTER_LABEL}. Balances are test tokens.")
        Row(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            KinMark(28.dp)
            Spacer(Modifier.width(8.dp))
            Text("Kin", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            GraphiteButton("Connect", onConnect, enabled = !busy)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(KinColors.Steel))

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Hero(busy, onConnect, onSimulate)
            Section("Use cases", "One idea. Many uses.", "Rotating savings is how hundreds of millions of people already save, under names like ajo, susu, tanda and chama. Kin makes it safe with people you have not met, too.") {
                UseCaseExplorer("Start this circle", { onConnect() })
            }
            Section("How it works", "Four steps, no middleman.", null) { HowItWorks() }
            Section("Core features", "Built for the moment money goes missing.", "Group savings fails when someone stops paying. Everything below exists to make that safe, visible and recoverable.") {
                CoreFeatures()
            }
            Section("Roadmap", "Where Kin is going.", "The core is live today. The rest is the path from a working devnet app to something people rely on.") { Roadmap() }
            ProofBand(onConnect, busy)
            Section("FAQ", "Questions people ask first.", null) { FaqList() }
            Footer(onConnect, busy)
        }
    }
}

@Composable
private fun Section(label: String, title: String, body: String?, content: @Composable () -> Unit) {
    Column(Modifier.padding(horizontal = Gutter).padding(top = 56.dp)) {
        KinLabel(label)
        Spacer(Modifier.height(10.dp))
        Text(title, style = MaterialTheme.typography.displayMedium)
        if (body != null) {
            Spacer(Modifier.height(12.dp))
            Text(body, style = MaterialTheme.typography.bodyLarge, color = KinColors.Slate)
        }
        Spacer(Modifier.height(24.dp))
        content()
    }
}

@Composable
private fun Hero(busy: Boolean, onConnect: () -> Unit, onSimulate: () -> Unit) {
    Column(Modifier.padding(horizontal = Gutter).padding(top = 32.dp, bottom = 12.dp)) {
        Text("Save together.\nGet paid in turn.", style = MaterialTheme.typography.displayLarge)
        Spacer(Modifier.height(16.dp))
        Text(
            "Kin is a savings circle on Solana. Everyone pays the same amount each round and one member takes the whole pot, until every member has had a turn.",
            style = MaterialTheme.typography.bodyLarge,
            color = KinColors.Slate,
        )
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Capability("Bonded")
            Capability("Autopay")
            Capability("Verifiable")
        }
        Spacer(Modifier.height(24.dp))
        AquaButton("Connect wallet", onConnect, enabled = !busy)
        Spacer(Modifier.height(10.dp))
        OutlineButton("See a circle run, no wallet needed", onSimulate)
        Spacer(Modifier.height(6.dp))
        LedgerArt(Modifier.padding(top = 12.dp))
    }
}

@Composable
private fun Capability(label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(KinColors.Ink))
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun HowItWorks() {
    val steps = listOf(
        "Start or join" to "Pick a ready-made circle or make your own. Friends join with a link.",
        "Lock a bond" to "Each member locks a small bond. It comes back at the end unless it was needed.",
        "Pay each round" to "One tap, or switch on autopay and never think about it again.",
        "Take your turn" to "When everyone has paid, the pot goes to that round's member. The order is drawn from on-chain randomness.",
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        steps.forEachIndexed { i, (title, body) ->
            CloudCard {
                Row(verticalAlignment = Alignment.Top) {
                    Box(Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(if (i == 3) KinColors.Aqua else KinColors.Ink), contentAlignment = Alignment.Center) {
                        Mono("${i + 1}", color = if (i == 3) KinColors.Ink else Color.White, size = 13)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(title, style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(4.dp))
                        Text(body, style = MaterialTheme.typography.bodyMedium, color = KinColors.Charcoal)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProofBand(onConnect: () -> Unit, busy: Boolean) {
    Column(Modifier.padding(top = 56.dp).fillMaxWidth().background(KinColors.Ink).padding(horizontal = Gutter, vertical = 44.dp)) {
        KinLabel("Proof", color = Color.White.copy(alpha = 0.5f))
        Spacer(Modifier.height(10.dp))
        Text("Do not take our\nword for it.", style = MaterialTheme.typography.displayMedium, color = Color.White)
        Spacer(Modifier.height(12.dp))
        Text(
            "Every circle has a Proof screen that reads Solana directly and checks the money against the rules.",
            style = MaterialTheme.typography.bodyLarge,
            color = Color.White.copy(alpha = 0.5f),
        )
        Spacer(Modifier.height(20.dp))
        GraphiteCard(padding = 16.dp) {
            ProofLine("vault balance equals what members are owed")
            ProofLine("payout order recomputed from the on-chain seed")
            ProofLine("no admin key can move funds")
            ProofLine("every payment links to the explorer")
            Spacer(Modifier.height(10.dp))
            Mono("program ${shortKey(KinProgram.PROGRAM_ID)}", color = Color.White.copy(alpha = 0.55f), size = 12)
        }
        Spacer(Modifier.height(24.dp))
        AquaButton("Connect wallet", onConnect, enabled = !busy)
    }
}

@Composable
private fun ProofLine(text: String) {
    Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
        Mono("[x]", color = KinColors.Aqua, size = 13)
        Spacer(Modifier.width(10.dp))
        Mono(text, color = Color.White, size = 13)
    }
}

@Composable
private fun Footer(onConnect: () -> Unit, busy: Boolean) {
    Column(Modifier.fillMaxWidth().padding(top = 48.dp).navigationBarsPadding().padding(Gutter)) {
        Text("Ready to try it?", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        AquaButton("Connect wallet", onConnect, enabled = !busy)
        Spacer(Modifier.height(20.dp))
        Text(
            "You approve every payment in your wallet. Kin never sees your keys. Network: ${Config.CLUSTER_LABEL}.",
            style = MaterialTheme.typography.bodyMedium,
            color = KinColors.Slate,
        )
    }
}
