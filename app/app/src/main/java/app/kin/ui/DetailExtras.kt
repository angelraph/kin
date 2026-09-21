package app.kin.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.kin.Config
import app.kin.solana.ActivityItem
import app.kin.solana.CircleData
import app.kin.solana.CircleStatus
import app.kin.solana.KinEvent
import app.kin.solana.KinProgram
import app.kin.solana.MemberData
import app.kin.solana.OrderProof
import app.kin.watch.AlertRules

private val ExtraCardShape = RoundedCornerShape(20.dp)

/** Members who owe this round, approved enough autopay, and can still be collected from. */
fun dueForAutopay(detail: CircleDetail, now: Long): List<MemberData> =
    AlertRules.dueForAutopay(detail.circle, detail.members, detail.allowances, now)

@Composable
fun Tag(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(
        text,
        color = color,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.14f)).padding(horizontal = 12.dp, vertical = 5.dp),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CircleTags(c: CircleData) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (c.randomize) Tag("Random order", KinColors.Green) else Tag("Join order", KinColors.Muted)
        if (c.seekerOnly) {
            if (c.seekerAuthority == Config.SEEKER_AUTHORITY) Tag("Seeker only", KinColors.Green)
            else Tag("Custom token gate", KinColors.Amber)
        }
    }
}

@Composable
fun AutopayCard(circle: CircleData, member: MemberData, allowance: app.kin.solana.Allowance?, onChange: (Boolean) -> Unit) {
    val remaining = circle.contribution * (circle.maxMembers - member.roundsResolved)
    val on = allowance != null && allowance.delegatedToKin && allowance.amount >= remaining && remaining > 0
    Card(shape = ExtraCardShape, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Autopay", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (on) "Kin can collect up to ${formatAmount(allowance!!.amount)} from you, only into this circle. Turn it off any time."
                    else "Never miss a round. Allows collecting up to ${formatAmount(remaining)}, your remaining dues here, and nothing else.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = on, onCheckedChange = onChange, enabled = remaining > 0)
        }
    }
}

@Composable
fun ProofSection(
    circle: CircleData,
    proof: ProofState?,
    onLoad: () -> Unit,
    onOpenUrl: (String) -> Unit,
    now: Long,
) {
    Card(shape = ExtraCardShape, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Proof on Solana", style = MaterialTheme.typography.titleLarge)
            Text(
                "Don't take Kin's word for it. These checks read the chain directly.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when {
                proof == null -> OutlinedButton(
                    onClick = onLoad,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    shape = RoundedCornerShape(14.dp),
                ) { Text("Check on-chain") }
                proof.loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.height(22.dp).width(22.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(12.dp))
                    Text("Reading the chain")
                }
                else -> {
                    val f = proof.funds
                    if (f != null) {
                        ProofRow(
                            ok = f.vaultOk,
                            title = "Round vault",
                            detail = "Holds ${formatAmount(f.vaultBalance)}, owes ${formatAmount(f.expectedVault)}",
                            onClick = { onOpenUrl(Config.EXPLORER_ADDRESS.format(KinProgram.vaultPda(circle.address))) },
                        )
                        ProofRow(
                            ok = f.bondVaultOk,
                            title = "Bond vault",
                            detail = "Holds ${formatAmount(f.bondVaultBalance)}, owes ${formatAmount(f.expectedBondVault)}",
                            onClick = { onOpenUrl(Config.EXPLORER_ADDRESS.format(KinProgram.bondVaultPda(circle.address))) },
                        )
                    }
                    if (circle.randomize && circle.status != CircleStatus.Open) {
                        val ok = OrderProof.verifies(circle)
                        ProofRow(
                            ok = ok,
                            title = "Payout order",
                            detail = if (ok) "Recomputed from the on-chain seed and it matches" else "Does not match the on-chain seed",
                            onClick = { onOpenUrl(Config.EXPLORER_ADDRESS.format(circle.address)) },
                        )
                    }
                    ProofRow(
                        ok = true,
                        title = "Program",
                        detail = "Runs ${shortKey(KinProgram.PROGRAM_ID)}. No admin keys can move funds.",
                        onClick = { onOpenUrl(Config.EXPLORER_ADDRESS.format(KinProgram.PROGRAM_ID)) },
                    )
                    Text("Transactions", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp))
                    if (proof.activity.isEmpty()) {
                        Text("Nothing yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    proof.activity.forEach { item -> ActivityRow(item, now, onOpenUrl) }
                    TextButton(onClick = onLoad) { Text("Check again") }
                }
            }
        }
    }
}

@Composable
private fun ProofRow(ok: Boolean, title: String, detail: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick), verticalAlignment = Alignment.Top) {
        Text(if (ok) "✓" else "!", color = if (ok) KinColors.Green else KinColors.Red, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ActivityRow(item: ActivityItem, now: Long, onOpenUrl: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onOpenUrl(Config.EXPLORER_TX.format(item.signature)) }.padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            item.events.forEach { e ->
                Text(describe(e), style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Text(
                "${item.blockTime?.let { agoLabel(now - it) } ?: "just now"}  ·  ${item.signature.take(8)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

fun describe(e: KinEvent): String = when (e) {
    is KinEvent.CircleCreated -> "Circle created, ${formatAmount(e.contribution)} per round for ${e.maxMembers} members"
    is KinEvent.MemberJoined -> "${shortKey(e.wallet)} joined and locked ${formatAmount(e.bond)}" + if (e.started) ". The circle started." else ""
    is KinEvent.OrderDrawn -> if (e.randomized) "Payout order drawn from on-chain randomness" else "Payout order set to join order"
    is KinEvent.Contributed -> "${shortKey(e.wallet)} paid ${formatAmount(e.amount)} for round ${e.round + 1}" +
        (if (e.autopay) " by autopay" else "") + (if (!e.onTime) ", late" else "")
    is KinEvent.MissCovered -> "${shortKey(e.wallet)} missed round ${e.round + 1}. Bond covered ${formatAmount(e.covered)}" +
        (if (e.shortfall > 0) ", ${formatAmount(e.shortfall)} short" else "")
    is KinEvent.PaidOut -> "${shortKey(e.recipient)} received ${formatAmount(e.amount)} for round ${e.round + 1}" +
        (if (e.completed) ". The circle is complete." else "")
    is KinEvent.BondReturned -> "${shortKey(e.wallet)} got ${formatAmount(e.amount)} of bond back"
}

fun agoLabel(seconds: Long): String = when {
    seconds < 60 -> "just now"
    seconds < 3600 -> "${seconds / 60}m ago"
    seconds < 86_400 -> "${seconds / 3600}h ago"
    else -> "${seconds / 86_400}d ago"
}
