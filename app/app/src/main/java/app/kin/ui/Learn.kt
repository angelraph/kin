package app.kin.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.kin.Config

/*
 * Reading material shared by the landing page and the Discover tab: use cases, core features, the roadmap
 * and the FAQ. Everything stated here is something the program or the app does today, or is clearly
 * labelled as planned.
 */

// Use case explorer

/** A filter row of use cases. The selected one gets an aqua tile and its setup is shown as a card below. */
@Composable
fun UseCaseExplorer(actionLabel: String, onAction: (CircleTemplate) -> Unit, modifier: Modifier = Modifier) {
    var selectedId by rememberSaveable { mutableStateOf(Templates.all.first().id) }
    val selected = Templates.byId(selectedId) ?: Templates.all.first()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            items(Templates.all, key = { it.id }) { t ->
                val on = t.id == selectedId
                Row(
                    Modifier
                        .clickable(role = Role.Tab) { selectedId = t.id }
                        .height(48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(14.dp).clip(RoundedCornerShape(4.dp)).background(if (on) KinColors.Aqua else KinColors.Steel))
                    Spacer(Modifier.width(8.dp))
                    Text(t.short, style = MaterialTheme.typography.labelLarge, color = if (on) KinColors.Ink else KinColors.Slate)
                }
            }
        }
        Text(selected.tagline, style = MaterialTheme.typography.bodyLarge, color = KinColors.Charcoal)
        GraphiteCard(padding = 18.dp) {
            KinLabel(selected.title, color = Color.White.copy(alpha = 0.55f))
            Spacer(Modifier.height(12.dp))
            val pot = selected.contribution * selected.members
            CodeLine("circle", selected.name)
            CodeLine("members", "${selected.members}")
            CodeLine("each pays", "${selected.contribution} ${Config.TOKEN_SYMBOL} ${periodLabel(selected.periodSecs)}")
            CodeLine("pot", "$pot ${Config.TOKEN_SYMBOL} each round")
            CodeLine("bond", "${selected.contribution * selected.bondMultiple} ${Config.TOKEN_SYMBOL}")
            CodeLine("lasts", spanLabel(selected.members, selected.periodSecs))
            CodeLine("order", if (selected.randomOrder) "random, verifiable" else "order of joining")
            if (selected.onlyReliable) CodeLine("entry", "clean record only")
            if (selected.seekerOnly) CodeLine("entry", "Seeker Genesis Token")
        }
        AquaButton(actionLabel, { onAction(selected) })
    }
}

@Composable
private fun CodeLine(key: String, value: String) {
    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
        Mono(key.padEnd(10), color = Color.White.copy(alpha = 0.45f), size = 13)
        Mono(value, color = Color.White, size = 13)
    }
}

// Core features

private class Feature(val tag: String, val title: String, val body: String)

private val features = listOf(
    Feature(
        "01", "Bonded circles",
        "Every member locks a bond when they join. If someone misses a round, their bond pays their share after a short grace window, so the person due to be paid is never left short.",
    ),
    Feature(
        "02", "Autopay you can revoke",
        "Approve a capped allowance once. Anyone can then collect your dues, but only into that one circle and only up to the cap. Turn it off and it stops.",
    ),
    Feature(
        "03", "A payout order nobody can rig",
        "The order is drawn from on-chain data when the circle fills. The app recomputes it from the stored seed, and so can you.",
    ),
    Feature(
        "04", "Kin Score",
        "Every payment you make is recorded against your wallet: on time, late, or missed. Anyone can read it before they let you into a circle, and it follows you to every circle you join.",
    ),
    Feature(
        "05", "Seeker gate",
        "A circle can require a Seeker Genesis Token. The program checks the token itself, so each member is one verified person holding one device.",
    ),
    Feature(
        "06", "Proof, not promises",
        "Each circle has a Proof screen that reads Solana directly: vault balances against what is owed, the payout order, and every payment, with links to the explorer.",
    ),
)

@Composable
fun CoreFeatures(modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        features.forEach { f ->
            if (f.tag == "04") {
                CloudCard {
                    KinLabel(f.tag)
                    Spacer(Modifier.height(6.dp))
                    Text(f.title, style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(6.dp))
                    Text(f.body, style = MaterialTheme.typography.bodyMedium, color = KinColors.Charcoal)
                    Spacer(Modifier.height(14.dp))
                    GraphiteCard(padding = 16.dp) {
                        Mono("KinScore {", color = Color.White, size = 13)
                        listOf("wallet", "on_time", "late", "missed", "circles_completed", "streak", "best_streak").forEach {
                            Mono("  $it", color = Color.White.copy(alpha = 0.6f), size = 13)
                        }
                        Mono("}", color = Color.White, size = 13)
                    }
                }
            } else {
                CloudCard {
                    KinLabel(f.tag)
                    Spacer(Modifier.height(6.dp))
                    Text(f.title, style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(6.dp))
                    Text(f.body, style = MaterialTheme.typography.bodyMedium, color = KinColors.Charcoal)
                }
            }
        }
    }
}

// Roadmap

private class Phase(val label: String, val title: String, val current: Boolean, val items: List<String>)

private val phases = listOf(
    Phase(
        "Now", "Live on devnet", true,
        listOf(
            "Bonded circles with a bond that covers a missed payment",
            "Autopay with a capped, revocable allowance",
            "Verifiable random payout order",
            "Kin Score on-chain for every wallet",
            "Seeker Genesis Token gate",
            "Proof screen and reminders",
            "One tap test funds",
        ),
    ),
    Phase(
        "Next", "Mainnet", false,
        listOf(
            "Independent security review of the program",
            "Real USDC with small caps on each pot",
            "Bonds and fees payable in SKR",
            "Listing on the Solana dApp Store",
            "Invite links that work before the app is installed",
        ),
    ),
    Phase(
        "Later", "Kin Score as a credential", false,
        listOf(
            "A small SDK so other Solana apps can read a Kin Score",
            "Lending and rent deposits that lean on a good score",
            "Shared goals and group treasuries beyond rotation",
            "Local currency circles through stablecoin on and off ramps",
        ),
    ),
)

@Composable
fun Roadmap(modifier: Modifier = Modifier) {
    Column(modifier) {
        phases.forEachIndexed { i, p ->
            Row(Modifier.height(IntrinsicSize.Min)) {
                Column(Modifier.width(28.dp).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .padding(top = 4.dp)
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(if (p.current) KinColors.Aqua else KinColors.Paper)
                            .border(1.dp, if (p.current) KinColors.Aqua else KinColors.Steel, CircleShape),
                    )
                    if (i < phases.lastIndex) Box(Modifier.weight(1f).width(1.dp).background(KinColors.Steel))
                }
                Column(Modifier.padding(bottom = 24.dp)) {
                    KinLabel(p.label, color = if (p.current) KinColors.Good else KinColors.Slate)
                    Spacer(Modifier.height(4.dp))
                    Text(p.title, style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(8.dp))
                    p.items.forEach { item ->
                        Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.Top) {
                            Mono(if (p.current) "[x]" else "[ ]", color = if (p.current) KinColors.Good else KinColors.Slate, size = 12)
                            Spacer(Modifier.width(10.dp))
                            Text(item, style = MaterialTheme.typography.bodyMedium, color = KinColors.Charcoal)
                        }
                    }
                }
            }
        }
        Text(
            "Plans, not promises. What ships next depends on the security review and on what people using Kin ask for.",
            style = MaterialTheme.typography.bodyMedium,
            color = KinColors.Slate,
        )
    }
}

// FAQ

private class Faq(val q: String, val a: String)

private val faqs = listOf(
    Faq(
        "Is this real money?",
        "Not yet. Kin runs on Solana devnet with test tokens, which is why balances here are labelled tUSDC. You can get free test funds in the app. Mainnet comes after an independent security review.",
    ),
    Faq(
        "Where does the money sit?",
        "In vaults owned by the program, not by us. There is no admin key that can move funds out of a circle. The Proof screen compares each vault with what members are owed.",
    ),
    Faq(
        "What if someone stops paying?",
        "After a short grace window anyone can cover the missed payment from that member's bond. The pot still pays out on time and their Kin Score records the miss. If a bond is fully used up, later misses make the pot smaller rather than stopping the circle.",
    ),
    Faq(
        "How is the payout order decided?",
        "Circles can use join order or a random order. Random order is drawn from on-chain data when the circle fills, and the app recomputes it to check it matches.",
    ),
    Faq(
        "What is a bond, and do I get it back?",
        "A bond is a deposit you lock when you join, usually one to three payments. When the circle finishes you claim back whatever was not used to cover a missed payment.",
    ),
    Faq(
        "Can autopay take more than I agreed?",
        "No. Autopay is a capped allowance you approve once. It can only move your dues into that one circle, never anywhere else, and you can revoke it in a tap.",
    ),
    Faq(
        "Do I need a Seeker phone?",
        "No. Any Android phone with a Solana wallet works. A Seeker Genesis Token only matters for circles that choose to require one.",
    ),
    Faq(
        "What does Kin cost?",
        "Kin takes no fee. You pay the Solana network fee, a fraction of a cent, and nothing else.",
    ),
    Faq(
        "Can I leave a circle?",
        "Once a circle is running you stay until it finishes, because everyone else is counting on your payments. If a circle never fills within 14 days, every member can take their bond back.",
    ),
    Faq(
        "Who can see my Kin Score?",
        "Anyone, because it is stored on Solana against your wallet address. That is the point: it lets people trust you before they have met you, and it goes with you to new circles.",
    ),
)

@Composable
fun FaqList(modifier: Modifier = Modifier) {
    var open by rememberSaveable { mutableStateOf<Int?>(null) }
    Column(modifier) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(KinColors.Steel))
        faqs.forEachIndexed { i, f ->
            val expanded = open == i
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button) { open = if (expanded) null else i }
                    .animateContentSize()
                    .padding(vertical = 16.dp),
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(f.q, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(12.dp))
                    Mono(if (expanded) "-" else "+", color = KinColors.Slate, size = 18)
                }
                if (expanded) {
                    Spacer(Modifier.height(8.dp))
                    Text(f.a, style = MaterialTheme.typography.bodyMedium, color = KinColors.Charcoal)
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(KinColors.Steel))
        }
    }
}
