package app.kin.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.kin.Config
import app.kin.solana.CircleData
import app.kin.solana.CircleStatus
import app.kin.solana.MemberData
import app.kin.solana.PublicKey
import app.kin.solana.ScoreData
import kotlinx.coroutines.delay

private enum class Route { Home, Create }

private const val REFRESH_MILLIS = 12_000L
private val ContentWidth = 600.dp
private val ButtonMinHeight = 56.dp
private val ButtonShape = RoundedCornerShape(16.dp)
private val CardShape = RoundedCornerShape(20.dp)

class Actions(
    val onConnect: () -> Unit,
    val onDisconnect: () -> Unit,
    val onRefresh: () -> Unit,
    val onOpen: (PublicKey) -> Unit,
    val onClose: () -> Unit,
    val onJoinLink: (String) -> Unit,
    val onCreate: (CreateRequest) -> Unit,
    val onContribute: () -> Unit,
    val onCover: (PublicKey) -> Unit,
    val onPayout: (PublicKey) -> Unit,
    val onClaim: () -> Unit,
    val onJoin: (PublicKey, Boolean) -> Unit,
    val onShare: (CircleData) -> Unit,
    val onSetAutopay: (Boolean) -> Unit,
    val onCollect: () -> Unit,
    val onLoadProof: () -> Unit,
    val onOpenUrl: (String) -> Unit,
    val onToggleReminders: (Boolean) -> Unit,
    val onDismissNotice: () -> Unit,
)

/** Whether the user wants reminders, and whether the system currently lets Kin show notifications. */
class Reminders(val on: Boolean, val allowed: Boolean)

class CreateRequest(
    val name: String,
    val contribution: Long,
    val bondMultiple: Int,
    val periodSecs: Long,
    val graceSecs: Long,
    val maxMembers: Int,
    val maxMissed: Long,
    val randomize: Boolean,
    val seekerOnly: Boolean,
)

@Composable
fun KinApp(state: UiState, actions: Actions, reminders: Reminders) {
    var route by remember { mutableStateOf(Route.Home) }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.notice) {
        state.notice?.let {
            snackbar.showSnackbar(it)
            actions.onDismissNotice()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { inset ->
        Box(Modifier.fillMaxSize().padding(inset), contentAlignment = Alignment.TopCenter) {
            Box(Modifier.fillMaxSize().widthIn(max = ContentWidth)) {
                val detail = state.detail
                when {
                    state.wallet == null -> ConnectScreen(state.busy, actions.onConnect)
                    detail != null -> DetailScreen(state, detail, actions)
                    route == Route.Create -> CreateScreen(
                        busy = state.busy,
                        onBack = { route = Route.Home },
                        onCreate = {
                            actions.onCreate(it)
                            route = Route.Home
                        },
                    )
                    else -> HomeScreen(state, actions, reminders) { route = Route.Create }
                }
                if (state.busy) {
                    Box(
                        Modifier.fillMaxSize().background(Color(0x66000000)).clickable(enabled = false) {},
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator(color = KinColors.Green) }
                }
            }
        }
    }
}

@Composable
private fun PrimaryButton(text: String, onClick: () -> Unit, enabled: Boolean = true, colors: androidx.compose.material3.ButtonColors = ButtonDefaults.buttonColors()) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = colors,
        modifier = Modifier.fillMaxWidth().heightIn(min = ButtonMinHeight),
        shape = ButtonShape,
    ) { Text(text, style = MaterialTheme.typography.titleMedium) }
}

@Composable
private fun ConnectScreen(busy: Boolean, onConnect: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Kin", style = MaterialTheme.typography.headlineLarge, color = KinColors.Green)
        Spacer(Modifier.height(12.dp))
        Text("Save in a circle. Get paid in turn.", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))
        Text(
            "Everyone pays in each round and one member takes the pot. Each member locks a bond, so a missed payment never leaves the group short.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(32.dp))
        PrimaryButton("Connect wallet", onConnect, enabled = !busy)
        Spacer(Modifier.height(14.dp))
        Text(
            "You approve every payment in your wallet. Kin never sees your keys. Network: ${Config.CLUSTER_LABEL}.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HomeScreen(state: UiState, actions: Actions, reminders: Reminders, onCreate: () -> Unit) {
    var showJoin by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 112.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Your circles", style = MaterialTheme.typography.headlineMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${shortKey(state.wallet!!)}  ·  ${formatAmount(state.balance)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (state.seeker != null) {
                            Spacer(Modifier.height(6.dp))
                            Tag("Seeker verified", KinColors.Green)
                        }
                    }
                    IconButton(onClick = actions.onRefresh) { Icon(Icons.Filled.Refresh, contentDescription = "Refresh") }
                }
            }
            item { ScoreCard(state.myScore) }
            item {
                ToggleRow(
                    "Reminders",
                    if (reminders.on && !reminders.allowed) "Notifications are blocked. Allow them for Kin in system settings."
                    else "A notification when a payment is due, autopay is ready, or a payout can be sent.",
                    reminders.on,
                    actions.onToggleReminders,
                )
            }
            item {
                OutlinedButton(
                    onClick = { showJoin = true },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    shape = RoundedCornerShape(14.dp),
                ) { Text("Join with an invite link") }
            }
            if (state.circles.isEmpty()) {
                item {
                    Text(
                        if (state.loading) "Loading circles" else "No circles yet. Start one, or join with a link a friend sent you.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            }
            items(state.circles, key = { it.address.toBase58() }) { c -> CircleCard(c) { actions.onOpen(c.address) } }
            item {
                TextButton(onClick = actions.onDisconnect, modifier = Modifier.fillMaxWidth()) { Text("Disconnect wallet") }
            }
        }
        ExtendedFloatingActionButton(
            onClick = onCreate,
            modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(20.dp),
            containerColor = KinColors.Green,
            contentColor = Color.Black,
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            text = { Text("New circle", fontWeight = FontWeight.SemiBold) },
        )
    }
    if (showJoin) {
        JoinDialog(onDismiss = { showJoin = false }) {
            showJoin = false
            actions.onJoinLink(it)
        }
    }
}

@Composable
private fun JoinDialog(onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Join a circle") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text("Invite link or circle address") },
            )
        },
        confirmButton = { TextButton(onClick = { onSubmit(text.trim()) }, enabled = text.isNotBlank()) { Text("Open") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ScoreCard(score: ScoreData?) {
    Card(shape = CardShape, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Kin Score", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    score?.reliabilityPercent?.let { "$it%" } ?: "New",
                    style = MaterialTheme.typography.headlineLarge,
                    color = scoreColor(score?.reliabilityPercent),
                )
                Text("paid on time", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Stat("Streak", (score?.streak ?: 0).toString())
                Stat("Completed", (score?.circlesCompleted ?: 0).toString())
                Stat("Missed", (score?.missed ?: 0).toString())
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(8.dp))
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}

private fun scoreColor(pct: Int?): Color = when {
    pct == null -> KinColors.Muted
    pct >= 90 -> KinColors.Green
    pct >= 70 -> KinColors.Amber
    else -> KinColors.Red
}

@Composable
private fun CircleCard(c: CircleData, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = CardShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    c.name.ifBlank { "Circle" },
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(8.dp))
                StatusChip(c.status)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "${formatAmount(c.contribution)} ${periodLabel(c.periodSecs)}  ·  ${c.memberCount}/${c.maxMembers} members",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (c.status == CircleStatus.Active) {
                Spacer(Modifier.height(12.dp))
                PotBar(c)
            }
        }
    }
}

@Composable
private fun StatusChip(status: CircleStatus) {
    val (label, color) = when (status) {
        CircleStatus.Open -> "Open" to KinColors.Amber
        CircleStatus.Active -> "Active" to KinColors.Green
        CircleStatus.Completed -> "Done" to KinColors.Muted
    }
    Text(
        label,
        color = color,
        style = MaterialTheme.typography.labelLarge,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.14f)).padding(horizontal = 12.dp, vertical = 5.dp),
    )
}

@Composable
private fun PotBar(c: CircleData) {
    val target = if (c.memberCount == 0) 0f else c.resolvedCount.toFloat() / c.memberCount
    val progress by animateFloatAsState(target, label = "pot")
    Column(Modifier.semantics { contentDescription = "${c.resolvedCount} of ${c.memberCount} members paid this round" }) {
        Row(Modifier.fillMaxWidth()) {
            Text("Round ${c.currentRound + 1} of ${c.memberCount}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text("${c.resolvedCount}/${c.memberCount} paid", style = MaterialTheme.typography.bodyMedium, color = KinColors.Green)
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)),
            color = KinColors.Green,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    }
}

@Composable
private fun ToggleRow(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun Stepper(label: String, onMinus: () -> Unit, onPlus: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
        OutlinedButton(onClick = onMinus, modifier = Modifier.size(48.dp), contentPadding = PaddingValues(0.dp)) { Text("−") }
        Spacer(Modifier.width(8.dp))
        OutlinedButton(onClick = onPlus, modifier = Modifier.size(48.dp), contentPadding = PaddingValues(0.dp)) { Text("+") }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CreateScreen(busy: Boolean, onBack: () -> Unit, onCreate: (CreateRequest) -> Unit) {
    var name by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("10") }
    var members by remember { mutableIntStateOf(4) }
    var bondMultiple by remember { mutableIntStateOf(2) }
    var strict by remember { mutableStateOf(false) }
    var randomOrder by remember { mutableStateOf(true) }
    var seekerOnly by remember { mutableStateOf(false) }
    val periods = listOf("Demo, 1 min" to 60L, "Daily" to 86_400L, "Weekly" to 604_800L, "Monthly" to 2_592_000L)
    var period by remember { mutableLongStateOf(periods[0].second) }

    val contribution = parseAmount(amount)
    val valid = name.isNotBlank() && contribution != null

    Column(
        Modifier.fillMaxSize().imePadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text("New circle", style = MaterialTheme.typography.headlineMedium)
        }
        OutlinedTextField(
            name, { name = it.take(32) },
            label = { Text("Circle name") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            amount, { amount = it },
            label = { Text("Each member pays per round (${Config.TOKEN_SYMBOL})") },
            singleLine = true, modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            isError = contribution == null,
        )
        Text("Rounds run", style = MaterialTheme.typography.labelLarge)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            periods.forEach { (label, secs) ->
                FilterChip(selected = period == secs, onClick = { period = secs }, label = { Text(label) })
            }
        }
        Stepper("Members: $members", { if (members > 2) members-- }, { if (members < 12) members++ })
        Stepper("Bond: ${bondMultiple}x payment", { if (bondMultiple > 1) bondMultiple-- }, { if (bondMultiple < members) bondMultiple++ })
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Only reliable members", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Turn away wallets with a missed payment on record",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = strict, onCheckedChange = { strict = it })
        }
        ToggleRow(
            "Random payout order",
            "Drawn on-chain when the circle fills. Anyone can recompute it and check.",
            randomOrder,
        ) { randomOrder = it }
        ToggleRow(
            "Seeker owners only",
            "The program checks a Seeker Genesis Token before anyone can join.",
            seekerOnly,
        ) { seekerOnly = it }
        if (contribution != null) {
            Text(
                "The pot each round is ${formatAmount(contribution * members)}. Every member locks ${formatAmount(contribution * bondMultiple)} as a bond and gets back whatever is not used.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        PrimaryButton(
            "Create circle",
            onClick = {
                onCreate(
                    CreateRequest(
                        name = name,
                        contribution = contribution!!,
                        bondMultiple = bondMultiple.coerceAtMost(members),
                        periodSecs = period,
                        graceSecs = maxOf(period / 4, 10L),
                        maxMembers = members,
                        maxMissed = if (strict) 0 else 1000,
                        randomize = randomOrder,
                        seekerOnly = seekerOnly,
                    ),
                )
            },
            enabled = valid && !busy,
        )
    }
}

@Composable
private fun DetailScreen(state: UiState, detail: CircleDetail, actions: Actions) {
    val c = detail.circle
    val me = state.wallet!!
    val myMember = detail.members.firstOrNull { it.wallet == me }
    var now by remember { mutableLongStateOf(System.currentTimeMillis() / 1000) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis() / 1000
            delay(1000)
        }
    }
    // Keep the open circle current: other members pay, autopay becomes due, rounds roll over.
    LaunchedEffect(c.address) {
        while (true) {
            delay(REFRESH_MILLIS)
            actions.onRefresh()
        }
    }

    var joinWithAutopay by remember { mutableStateOf(true) }
    val dueAutopay = dueForAutopay(detail, now)
    val active = c.status == CircleStatus.Active
    val iResolved = myMember != null && myMember.roundsResolved > c.currentRound
    val canPay = active && myMember != null && !iResolved && now <= c.graceEndTs
    val pendingAfterGrace = if (active && now > c.graceEndTs) detail.members.filter { it.roundsResolved <= c.currentRound } else emptyList()
    val recipient = if (active) detail.members.firstOrNull { it.index == c.payoutOrder[c.currentRound] } else null
    val canPayout = active && c.resolvedCount == c.memberCount && now >= c.roundEndTs && recipient != null

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, top = 8.dp, end = 20.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = actions.onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                Text(
                    c.name.ifBlank { "Circle" },
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                IconButton(onClick = { actions.onShare(c) }) { Icon(Icons.Filled.Share, contentDescription = "Invite friends") }
            }
        }
        item {
            Card(shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Pot this round", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(formatAmount(c.contribution * c.memberCount), style = MaterialTheme.typography.headlineLarge)
                        }
                        StatusChip(c.status)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "${formatAmount(c.contribution)} ${periodLabel(c.periodSecs)}  ·  bond ${formatAmount(c.bond)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (active) {
                        Spacer(Modifier.height(16.dp))
                        PotBar(c)
                        Spacer(Modifier.height(10.dp))
                        Text(
                            when {
                                now <= c.roundEndTs -> "Round closes in ${formatDuration(c.roundEndTs - now)}"
                                now <= c.graceEndTs -> "Grace window: ${formatDuration(c.graceEndTs - now)} left, then bonds cover misses"
                                else -> "Grace window is over"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (now > c.roundEndTs) KinColors.Amber else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        item { CircleTags(c) }

        item {
            when {
                c.status == CircleStatus.Open && myMember == null -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ToggleRow(
                        "Turn on autopay",
                        "Allows collecting up to ${formatAmount(c.contribution * c.maxMembers)}, your total dues here. Revoke any time.",
                        joinWithAutopay,
                    ) { joinWithAutopay = it }
                    PrimaryButton("Join and lock ${formatAmount(c.bond)} bond", { actions.onJoin(c.address, joinWithAutopay) })
                }
                c.status == CircleStatus.Open ->
                    Text(
                        "Waiting for members: ${c.memberCount}/${c.maxMembers}. Share the invite link.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                canPay -> PrimaryButton("Pay ${formatAmount(c.contribution)}", actions.onContribute)
                c.status == CircleStatus.Completed && myMember != null && !myMember.bondClaimed ->
                    PrimaryButton("Claim back ${formatAmount(myMember.bondLocked - myMember.bondUsed)}", actions.onClaim)
                iResolved && active ->
                    Text("You are paid up for this round.", color = KinColors.Green, style = MaterialTheme.typography.titleMedium)
            }
        }

        if (myMember != null && c.status != CircleStatus.Completed) {
            item {
                AutopayCard(c, myMember, detail.allowances[me], actions.onSetAutopay)
            }
        }
        if (dueAutopay.isNotEmpty()) {
            item {
                OutlinedButton(
                    onClick = actions.onCollect,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                    shape = RoundedCornerShape(14.dp),
                ) { Text("Collect autopay from ${dueAutopay.size} member${if (dueAutopay.size > 1) "s" else ""}") }
            }
        }

        if (canPayout) {
            item {
                PrimaryButton(
                    "Send pot to ${if (recipient.wallet == me) "you" else shortKey(recipient.wallet)}",
                    { actions.onPayout(recipient.wallet) },
                    colors = ButtonDefaults.buttonColors(containerColor = KinColors.GreenDark, contentColor = Color.White),
                )
            }
        }
        items(pendingAfterGrace, key = { "cover-" + it.address.toBase58() }) { m ->
            OutlinedButton(
                onClick = { actions.onCover(m.wallet) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                shape = RoundedCornerShape(14.dp),
            ) { Text("Cover ${shortKey(m.wallet)} from their bond") }
        }

        item { Text("Members", style = MaterialTheme.typography.titleLarge) }
        items(detail.members, key = { it.address.toBase58() }) { m ->
            MemberRow(c, m, m.wallet == me, detail.scores[m.wallet])
        }
        item { ProofSection(c, detail.proof, actions.onLoadProof, actions.onOpenUrl, now) }
    }
}

@Composable
private fun MemberRow(c: CircleData, m: MemberData, isMe: Boolean, score: ScoreData?) {
    val paid = m.roundsResolved > c.currentRound
    val isRecipient = c.status == CircleStatus.Active && c.roundOf(m.index) == c.currentRound
    Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(38.dp).clip(CircleShape).background(if (isRecipient) KinColors.Green else MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "${m.index + 1}",
                    color = if (isRecipient) Color.Black else MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(if (isMe) "You" else shortKey(m.wallet), style = MaterialTheme.typography.titleMedium)
                Text(
                    buildString {
                        c.roundOf(m.index)?.let { append("Paid in round ${it + 1}  ·  ") }
                        append(score?.reliabilityPercent?.let { "$it% on time" } ?: "New member")
                        if (m.missed > 0) append("  ·  ${m.missed} missed here")
                        if (m.bondUsed > 0) append("  ·  bond used ${formatAmount(m.bondUsed)}")
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = scoreColor(score?.reliabilityPercent),
                )
            }
            Spacer(Modifier.width(8.dp))
            if (c.status == CircleStatus.Active) {
                Text(
                    if (isRecipient) "Receives" else if (paid) "Paid" else "Due",
                    color = if (isRecipient || paid) KinColors.Green else KinColors.Amber,
                    style = MaterialTheme.typography.labelLarge,
                )
            } else if (m.received) {
                Text("Received", color = KinColors.Muted, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
