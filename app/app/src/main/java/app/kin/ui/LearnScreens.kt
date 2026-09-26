package app.kin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.kin.solana.KinProgram

private val Gutter = 20.dp

/** Each topic gets its own screen so the landing page can stay short and calm. */
enum class LearnPage(val title: String, val blurb: String, val headline: String) {
    HowItWorks("How it works", "Four steps from start to payout", "Four steps, no middleman."),
    UseCases("Use cases", "Savings, rent, business and more", "One idea. Many uses."),
    Features("Core features", "What keeps the money safe", "Built for the moment money goes missing."),
    Proof("Proof", "Check everything on Solana yourself", "Do not take our word for it."),
    Roadmap("Roadmap", "Live today, and where it goes next", "Where Kin is going."),
    Faq("FAQ", "Questions people ask first", "Questions people ask first."),
}

/** A quiet list of topics: title, one line, and an arrow, separated by hairlines. */
@Composable
fun LearnList(onOpen: (LearnPage) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(KinColors.Steel))
        LearnPage.entries.forEach { page ->
            Row(
                Modifier.fillMaxWidth().clickable(role = Role.Button) { onOpen(page) }.padding(vertical = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(page.title, style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(2.dp))
                    Text(page.blurb, style = MaterialTheme.typography.bodyMedium, color = KinColors.Slate)
                }
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = KinColors.Slate, modifier = Modifier.size(20.dp))
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(KinColors.Steel))
        }
    }
}

/**
 * One topic on its own screen. When the visitor has not connected a wallet yet, [onConnect] offers the next step.
 * [onStart] is used by the use case page to begin a circle from a template.
 */
@Composable
fun LearnScreen(
    page: LearnPage,
    connected: Boolean,
    onBack: () -> Unit,
    onConnect: () -> Unit,
    onStart: (CircleTemplate) -> Unit,
) {
    Column(Modifier.fillMaxSize().background(KinColors.Paper)) {
        Box(Modifier.statusBarsPadding())
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            KinLabel(page.title)
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = Gutter)) {
            Spacer(Modifier.height(12.dp))
            Text(page.headline, style = MaterialTheme.typography.displayMedium)
            Spacer(Modifier.height(24.dp))
            when (page) {
                LearnPage.HowItWorks -> HowItWorks()
                LearnPage.UseCases -> {
                    Text(
                        "Rotating savings is how hundreds of millions of people already save, under names like ajo, susu, tanda and chama. Kin makes it safe with people you have not met, too.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = KinColors.Slate,
                    )
                    Spacer(Modifier.height(20.dp))
                    UseCaseExplorer(if (connected) "Start this circle" else "Connect to start this circle", { if (connected) onStart(it) else onConnect() })
                }
                LearnPage.Features -> CoreFeatures()
                LearnPage.Proof -> ProofPanel()
                LearnPage.Roadmap -> Roadmap()
                LearnPage.Faq -> FaqList()
            }
            Spacer(Modifier.height(32.dp))
            if (!connected && page != LearnPage.UseCases) {
                AquaButton("Connect wallet", onConnect)
                Spacer(Modifier.height(16.dp))
            }
            Box(Modifier.navigationBarsPadding())
        }
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
                    Box(
                        Modifier.size(28.dp).clip(RoundedCornerShape(8.dp)).background(if (i == 3) KinColors.Aqua else KinColors.Ink),
                        contentAlignment = Alignment.Center,
                    ) { Mono("${i + 1}", color = if (i == 3) KinColors.Ink else Color.White, size = 13) }
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
private fun ProofPanel() {
    Text(
        "Every circle has a Proof screen that reads Solana directly and checks the money against the rules.",
        style = MaterialTheme.typography.bodyLarge,
        color = KinColors.Slate,
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
}

@Composable
private fun ProofLine(text: String) {
    Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
        Mono("[x]", color = KinColors.Aqua, size = 13)
        Spacer(Modifier.width(10.dp))
        Mono(text, color = Color.White, size = 13)
    }
}
