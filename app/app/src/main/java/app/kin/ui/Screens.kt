package app.kin.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.kin.Config
import app.kin.solana.CircleData
import app.kin.solana.CircleStatus
import app.kin.solana.KinProgram
import app.kin.solana.MemberData
import app.kin.solana.PublicKey
import app.kin.solana.ScoreData
import kotlinx.coroutines.delay

private enum class Route { Home, Create, Simulate, Learn }

private const val REFRESH_MILLIS = 12_000L
private val ContentWidth = 600.dp
private val Gutter = 20.dp

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
    val onShareScore: (ScoreData?) -> Unit,
    val onSetAutopay: (Boolean) -> Unit,
    val onCollect: () -> Unit,
    val onLoadProof: () -> Unit,
    val onOpenUrl: (String) -> Unit,
    val onToggleReminders: (Boolean) -> Unit,
    val onDismissNotice: () -> Unit,
    val onLookup: (String) -> Unit,
    val onGetTestFunds: () -> Unit,
    val onCancelBusy: () -> Unit,
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
    // Saved, because the wallet round trip can recreate the activity and the user must land where they were.
    var route by rememberSaveable { mutableStateOf(Route.Home) }
    var tab by rememberSaveable { mutableStateOf(Tab.Circles) }
    var templateId by rememberSaveable { mutableStateOf<String?>(null) }
    val template = templateId?.let { Templates.byId(it) }
    var learnName by rememberSaveable { mutableStateOf<String?>(null) }
    val learnPage = learnName?.let { name -> LearnPage.entries.firstOrNull { it.name == name } }
    val openLearn: (LearnPage) -> Unit = { learnName = it.name; route = Route.Learn }
    // Connecting from a topic page or the simulation should land on the app, not back on the page.
    LaunchedEffect(state.wallet) {
        if (state.wallet != null && (route == Route.Learn || route == Route.Simulate)) route = Route.Home
    }
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.notice) {
        state.notice?.let {
            snackbar.showSnackbar(it)
            actions.onDismissNotice()
        }
    }

    val detailOpen = state.detail != null
    BackHandler(enabled = detailOpen) { actions.onClose() }
    BackHandler(enabled = !detailOpen && route != Route.Home) { route = Route.Home }
    BackHandler(enabled = !detailOpen && route == Route.Home && state.wallet != null && tab != Tab.Circles) { tab = Tab.Circles }

    Box(Modifier.fillMaxSize().background(KinColors.Paper), contentAlignment = Alignment.TopCenter) {
        Box(Modifier.fillMaxSize().widthIn(max = ContentWidth)) {
            val detail = state.detail
            when {
                state.wallet == null && route == Route.Simulate ->
                    SimulationScreen(onBack = { route = Route.Home }, onConnect = actions.onConnect)
                state.wallet == null && route == Route.Learn && learnPage != null ->
                    LearnScreen(learnPage, connected = false, onBack = { route = Route.Home }, onConnect = actions.onConnect, onStart = {})
                state.wallet == null -> LandingScreen(state.busy, actions.onConnect, onSimulate = { route = Route.Simulate }, onLearn = openLearn)
                detail != null -> DetailScreen(state, detail, actions)
                route == Route.Create -> CreateScreen(
                    template = template,
                    busy = state.busy,
                    onBack = { route = Route.Home },
                    onCreate = {
                        actions.onCreate(it)
                        route = Route.Home
                    },
                )
                route == Route.Simulate -> SimulationScreen(onBack = { route = Route.Home }, onConnect = null)
                route == Route.Learn && learnPage != null -> LearnScreen(
                    learnPage, connected = true, onBack = { route = Route.Home }, onConnect = {},
                    onStart = { templateId = it.id; route = Route.Create },
                )
                else -> Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f)) {
                        when (tab) {
                            Tab.Circles -> CirclesTab(
                                state, actions,
                                onCreate = { templateId = null; route = Route.Create },
                                onPick = { templateId = it.id; route = Route.Create },
                            )
                            Tab.Discover -> DiscoverTab(
                                state, actions,
                                onSimulate = { route = Route.Simulate },
                                onLearn = openLearn,
                            )
                            Tab.You -> YouTab(state, actions, reminders)
                        }
                    }
                    KinBottomBar(tab) { tab = it }
                }
            }
            if (state.busy) {
                Box(
                    Modifier.fillMaxSize().background(Color(0x99FFFFFF)).clickable(enabled = false) {},
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        Modifier.clip(CardShape).background(KinColors.Graphite).padding(horizontal = 28.dp, vertical = 22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator(color = KinColors.Aqua, strokeWidth = 3.dp, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("Working on it", color = Color.White, style = MaterialTheme.typography.labelLarge)
                        Spacer(Modifier.height(4.dp))
                        QuietButton("Cancel", actions.onCancelBusy, color = Color.White.copy(alpha = 0.7f))
                    }
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = if (state.wallet != null && !detailOpen && route == Route.Home) 76.dp else 8.dp))
        }
    }
}

// Circles tab

@Composable
private fun CirclesTab(state: UiState, actions: Actions, onCreate: () -> Unit, onPick: (CircleTemplate) -> Unit) {
    var showJoin by remember { mutableStateOf(false) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column {
                Box(Modifier.statusBarsPadding())
                NoticeStrip("${Config.CLUSTER_LABEL}: balances are test tokens, not real money.")
            }
        }
        item {
            Row(Modifier.padding(horizontal = Gutter), verticalAlignment = Alignment.CenterVertically) {
                Text("Circles", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = actions.onRefresh) { Icon(Icons.Filled.Refresh, contentDescription = "Refresh") }
                Spacer(Modifier.width(4.dp))
                GraphiteButton("New circle", onCreate)
            }
        }
        item { BalanceCard(state, Modifier.padding(horizontal = Gutter)) }
        if (state.circles.isEmpty()) {
            item {
                Column(Modifier.padding(horizontal = Gutter), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (state.loading) {
                        Text("Loading your circles", color = KinColors.Slate, modifier = Modifier.padding(vertical = 8.dp))
                    } else {
                        Text("Start your first circle", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "Pick a ready-made setup below, or start from scratch. You can invite friends with a link once it exists.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = KinColors.Slate,
                        )
                    }
                }
            }
            if (!state.loading) {
                items(Templates.all.take(3), key = { it.id }) { t ->
                    TemplateCard(t, Modifier.padding(horizontal = Gutter)) { onPick(t) }
                }
            }
        } else {
            item { KinLabel("Your circles", Modifier.padding(horizontal = Gutter)) }
            items(state.circles, key = { it.address.toBase58() }) { c ->
                CircleCard(c, Modifier.padding(horizontal = Gutter)) { actions.onOpen(c.address) }
            }
        }
        item {
            OutlineButton("Join with an invite link", { showJoin = true }, Modifier.padding(horizontal = Gutter))
        }
    }
    if (showJoin) {
        JoinDialog(onDismiss = { showJoin = false }) {
            showJoin = false
            actions.onJoinLink(it)
        }
    }
}

@Composable
private fun BalanceCard(state: UiState, modifier: Modifier = Modifier) {
    val live = state.circles.count { it.status == CircleStatus.Active }
    val open = state.circles.count { it.status == CircleStatus.Open }
    val done = state.circles.count { it.status == CircleStatus.Completed }
    GraphiteCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            KinLabel("Balance", Modifier.weight(1f), Color.White.copy(alpha = 0.55f))
            state.wallet?.let { Mono(shortKey(it), color = Color.White.copy(alpha = 0.55f), size = 12) }
        }
        Spacer(Modifier.height(8.dp))
        Text(formatAmount(state.balance), style = MaterialTheme.typography.displayMedium, color = Color.White)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
            DarkStat("Live", live.toString())
            DarkStat("Open", open.toString())
            DarkStat("Done", done.toString())
            if (state.seeker != null) {
                Spacer(Modifier.weight(1f))
                Pill("Seeker verified", KinColors.Aqua, filled = true)
            }
        }
    }
}

@Composable
private fun DarkStat(label: String, value: String) {
    Column {
        Text(value, style = MaterialTheme.typography.headlineSmall, color = Color.White)
        KinLabel(label, color = Color.White.copy(alpha = 0.5f))
    }
}

@Composable
private fun CircleCard(c: CircleData, modifier: Modifier = Modifier, onClick: () -> Unit) {
    CloudCard(modifier, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                c.name.ifBlank { "Circle" },
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.width(8.dp))
            StatusPill(c.status)
        }
        Spacer(Modifier.height(4.dp))
        Mono("${formatAmount(c.contribution)} ${periodLabel(c.periodSecs)}  /  ${c.memberCount} of ${c.maxMembers} members", color = KinColors.Slate, size = 12)
        Spacer(Modifier.height(14.dp))
        when (c.status) {
            CircleStatus.Active -> {
                Segments(List(c.memberCount) { it < c.resolvedCount }, dark = false)
                Spacer(Modifier.height(8.dp))
                Row {
                    Text("Round ${c.currentRound + 1} of ${c.maxMembers}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text("${c.resolvedCount}/${c.memberCount} paid", style = MaterialTheme.typography.bodyMedium, color = KinColors.Good)
                }
            }
            CircleStatus.Open -> {
                Segments(List(c.maxMembers) { it < c.memberCount }, dark = false)
                Spacer(Modifier.height(8.dp))
                Text("Waiting for ${c.maxMembers - c.memberCount} more to join", style = MaterialTheme.typography.bodyMedium, color = KinColors.Warn)
            }
            CircleStatus.Completed -> Text("Every member has been paid", style = MaterialTheme.typography.bodyMedium, color = KinColors.Slate)
        }
    }
}

@Composable
private fun TemplateCard(t: CircleTemplate, modifier: Modifier = Modifier, onClick: () -> Unit) {
    PaperCard(modifier, onClick = onClick) {
        Text(t.title, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(4.dp))
        Text(t.tagline, style = MaterialTheme.typography.bodyMedium, color = KinColors.Charcoal)
        Spacer(Modifier.height(10.dp))
        Mono(t.example, color = KinColors.Slate, size = 12)
    }
}

@Composable
private fun JoinDialog(onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = KinColors.Paper,
        title = { Text("Join a circle") },
        text = { KinField(text, { text = it }, "Invite link or circle address") },
        confirmButton = { TextButton(onClick = { onSubmit(text.trim()) }, enabled = text.isNotBlank()) { Text("Open", color = KinColors.Ink) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = KinColors.Slate) } },
    )
}

// Discover tab

@Composable
private fun DiscoverTab(state: UiState, actions: Actions, onSimulate: () -> Unit, onLearn: (LearnPage) -> Unit) {
    var address by remember { mutableStateOf("") }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Gutter, end = Gutter, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column {
                Box(Modifier.statusBarsPadding())
                Spacer(Modifier.height(16.dp))
                Text("Discover", style = MaterialTheme.typography.headlineLarge)
                Spacer(Modifier.height(4.dp))
                Text("Tools to trust the people in a circle, and how Kin works.", style = MaterialTheme.typography.bodyMedium, color = KinColors.Slate)
            }
        }
        item {
            GraphiteCard(onClick = onSimulate) {
                KinLabel("No wallet needed", color = Color.White.copy(alpha = 0.55f))
                Spacer(Modifier.height(8.dp))
                Text("Watch a circle play out", style = MaterialTheme.typography.headlineSmall, color = Color.White)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Five people, five rounds, one missed payment. See how the bond, autopay and payout rules behave.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.6f),
                )
            }
        }
        item { KinLabel("Check a wallet") }
        item {
            CloudCard {
                Text("Vouch before you invite", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Paste any wallet address to see its Kin Score, read straight from Solana. Anyone can check anyone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = KinColors.Charcoal,
                )
                Spacer(Modifier.height(12.dp))
                KinField(address, { address = it.trim() }, "Wallet address")
                Spacer(Modifier.height(10.dp))
                GraphiteButton("Check Kin Score", { actions.onLookup(address) }, enabled = address.isNotBlank())
                state.lookup?.let { l ->
                    Spacer(Modifier.height(14.dp))
                    LookupResult(l)
                }
            }
        }
        item { KinLabel("Learn more") }
        item { LearnList(onLearn) }
    }
}

@Composable
private fun LookupResult(l: LookupState) {
    when {
        l.loading -> Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = KinColors.Ink)
            Spacer(Modifier.width(12.dp))
            Text("Reading the chain")
        }
        l.invalid -> Text("That does not look like a Solana address.", color = KinColors.Bad, style = MaterialTheme.typography.bodyMedium)
        else -> PaperCard(padding = 14.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ScoreRing(l.score?.reliabilityPercent, 64.dp)
                Spacer(Modifier.width(16.dp))
                Column {
                    Mono(l.address?.let { shortKey(it) } ?: "", size = 12, color = KinColors.Slate)
                    Spacer(Modifier.height(4.dp))
                    val s = l.score
                    if (s == null) {
                        Text("No history. This wallet has never been in a Kin circle.", style = MaterialTheme.typography.bodyMedium)
                    } else {
                        Text(
                            "${s.onTime} on time, ${s.late} late, ${s.missed} missed. ${s.circlesCompleted} circles completed.",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }
        }
    }
}

// You tab

@Composable
private fun YouTab(state: UiState, actions: Actions, reminders: Reminders) {
    val clipboard = LocalClipboardManager.current
    val wallet = state.wallet
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = Gutter, end = Gutter, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column {
                Box(Modifier.statusBarsPadding())
                Spacer(Modifier.height(16.dp))
                Text("You", style = MaterialTheme.typography.headlineLarge)
            }
        }
        item { ScoreCard(state.myScore, onShare = { actions.onShareScore(state.myScore) }) }
        item {
            CloudCard {
                KinLabel("Wallet")
                Spacer(Modifier.height(6.dp))
                Mono(wallet?.toBase58() ?: "", size = 12)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    GraphiteButton("Copy address", {
                        wallet?.let { clipboard.setText(AnnotatedString(it.toBase58())) }
                    })
                    if (state.seeker != null) Pill("Seeker verified", KinColors.Good, filled = true)
                }
            }
        }
        item {
            CloudCard {
                KinLabel("Test funds")
                Spacer(Modifier.height(6.dp))
                Text("Balance: ${formatAmount(state.balance)}", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(4.dp))
                Text(
                    "On ${Config.CLUSTER_LABEL} you can get free test tokens and a little SOL for network fees, in one tap.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = KinColors.Charcoal,
                )
                Spacer(Modifier.height(12.dp))
                AquaButton("Get test funds", actions.onGetTestFunds, enabled = !state.busy)
            }
        }
        item {
            CloudCard {
                ToggleRow(
                    "Reminders",
                    if (reminders.on && !reminders.allowed) "Notifications are blocked. Allow them for Kin in system settings."
                    else "A notification when a payment is due, autopay is ready, or a payout can be sent.",
                    reminders.on,
                    actions.onToggleReminders,
                )
            }
        }
        item {
            CloudCard(onClick = { actions.onOpenUrl(Config.EXPLORER_ADDRESS.format(KinProgram.PROGRAM_ID)) }) {
                KinLabel("Program")
                Spacer(Modifier.height(6.dp))
                Mono(KinProgram.PROGRAM_ID.toBase58(), size = 12)
                Spacer(Modifier.height(6.dp))
                Text("Open in the explorer. No admin keys can move funds.", style = MaterialTheme.typography.bodyMedium, color = KinColors.Slate)
            }
        }
        item { QuietButton("Disconnect wallet", actions.onDisconnect, Modifier.fillMaxWidth()) }
    }
}

@Composable
private fun ScoreCard(score: ScoreData?, onShare: () -> Unit) {
    GraphiteCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ScoreRing(score?.reliabilityPercent, 96.dp, dark = true)
            Spacer(Modifier.width(18.dp))
            Column(Modifier.weight(1f)) {
                KinLabel("Kin Score", color = Color.White.copy(alpha = 0.55f))
                Spacer(Modifier.height(4.dp))
                Text(
                    if (score?.reliabilityPercent == null) "No history yet. It builds with every payment you make." else "of your payments were on time",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.8f),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            DarkStat("Streak", (score?.streak ?: 0).toString())
            DarkStat("Best", (score?.bestStreak ?: 0).toString())
            DarkStat("Completed", (score?.circlesCompleted ?: 0).toString())
            DarkStat("Missed", (score?.missed ?: 0).toString())
        }
        Spacer(Modifier.height(16.dp))
        OnDarkButton("Share my score", onShare, Modifier.fillMaxWidth())
    }
}

fun scoreColor(pct: Int?): Color = when {
    pct == null -> KinColors.Slate
    pct >= 90 -> KinColors.Good
    pct >= 70 -> KinColors.Warn
    else -> KinColors.Bad
}

// Create

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CreateScreen(template: CircleTemplate?, busy: Boolean, onBack: () -> Unit, onCreate: (CreateRequest) -> Unit) {
    var picked by remember { mutableStateOf(template) }
    var name by remember { mutableStateOf(template?.name.orEmpty()) }
    var amount by remember { mutableStateOf(template?.contribution?.toString() ?: "10") }
    var members by remember { mutableIntStateOf(template?.members ?: 4) }
    var bondMultiple by remember { mutableIntStateOf(template?.bondMultiple ?: 2) }
    var strict by remember { mutableStateOf(template?.onlyReliable ?: false) }
    var randomOrder by remember { mutableStateOf(template?.randomOrder ?: true) }
    var seekerOnly by remember { mutableStateOf(template?.seekerOnly ?: false) }
    val periods = listOf("1 minute" to 60L, "Daily" to 86_400L, "Weekly" to 604_800L, "Monthly" to 2_592_000L)
    var period by remember { mutableLongStateOf(template?.periodSecs ?: 60L) }

    fun apply(t: CircleTemplate?) {
        picked = t
        if (t == null) return
        name = t.name
        amount = t.contribution.toString()
        members = t.members
        bondMultiple = t.bondMultiple
        strict = t.onlyReliable
        randomOrder = t.randomOrder
        seekerOnly = t.seekerOnly
        period = t.periodSecs
    }

    val contribution = parseAmount(amount)
    val valid = name.isNotBlank() && contribution != null

    Column(Modifier.fillMaxSize().background(KinColors.Paper).imePadding()) {
        Box(Modifier.statusBarsPadding())
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text("New circle", style = MaterialTheme.typography.titleLarge)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(KinColors.Steel))
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(Gutter),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            KinLabel("Start from a use case")
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { ChoiceChip("Custom", picked == null) { picked = null } }
                items(Templates.all, key = { it.id }) { t -> ChoiceChip(t.title, picked?.id == t.id) { apply(t) } }
            }
            picked?.let { Text(it.tagline, style = MaterialTheme.typography.bodyMedium, color = KinColors.Slate) }

            KinField(name, { name = it.take(32) }, "Circle name")
            KinField(
                amount, { amount = it },
                "Each member pays per round (${Config.TOKEN_SYMBOL})",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                isError = contribution == null,
            )
            KinLabel("Rounds run")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                periods.forEach { (label, secs) -> ChoiceChip(label, period == secs) { period = secs } }
            }
            StepperRow("Members", "$members", { if (members > 2) members-- }, { if (members < 12) members++ })
            StepperRow("Bond, times payment", "${bondMultiple}x", { if (bondMultiple > 1) bondMultiple-- }, { if (bondMultiple < members) bondMultiple++ })
            ToggleRow("Only reliable members", "Turn away wallets with a missed payment on record.", strict) { strict = it }
            ToggleRow("Random payout order", "Drawn on-chain when the circle fills. Anyone can recompute it and check.", randomOrder) { randomOrder = it }
            ToggleRow("Seeker owners only", "The program checks a Seeker Genesis Token before anyone can join.", seekerOnly) { seekerOnly = it }

            if (contribution != null) {
                val bond = bondMultiple.coerceAtMost(members)
                GraphiteCard {
                    KinLabel("What this means", color = Color.White.copy(alpha = 0.55f))
                    Spacer(Modifier.height(8.dp))
                    Text(formatAmount(contribution * members), style = MaterialTheme.typography.displayMedium, color = Color.White)
                    Text("paid out every round", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.55f))
                    Spacer(Modifier.height(14.dp))
                    SummaryLine("You pay in total", formatAmount(contribution * members))
                    SummaryLine("Bond you lock", formatAmount(contribution * bond))
                    SummaryLine("Circle lasts", spanLabel(members, period))
                    SummaryLine("Payout order", if (randomOrder) "Random, verifiable" else "Order of joining")
                }
            }
            AquaButton(
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
            Box(Modifier.navigationBarsPadding())
        }
    }
}

@Composable
private fun SummaryLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.6f), modifier = Modifier.weight(1f))
        Mono(value, color = Color.White, size = 13)
    }
}

// Detail

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

    Column(Modifier.fillMaxSize().background(KinColors.Paper)) {
        Box(Modifier.statusBarsPadding())
        Row(Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = actions.onClose) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            Text(
                c.name.ifBlank { "Circle" },
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            IconButton(onClick = { actions.onShare(c) }) { Icon(Icons.Filled.Share, contentDescription = "Invite friends") }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(KinColors.Steel))
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(start = Gutter, top = 16.dp, end = Gutter, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                GraphiteCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        KinLabel(if (active) "Pot, round ${c.currentRound + 1} of ${c.maxMembers}" else "Pot per round", Modifier.weight(1f), Color.White.copy(alpha = 0.55f))
                        StatusPill(c.status, dark = true)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(formatAmount(c.contribution * c.memberCount.coerceAtLeast(1)), style = MaterialTheme.typography.displayMedium, color = Color.White)
                    Spacer(Modifier.height(4.dp))
                    Mono("${formatAmount(c.contribution)} ${periodLabel(c.periodSecs)}  /  bond ${formatAmount(c.bond)}", color = Color.White.copy(alpha = 0.55f), size = 12)
                    if (active) {
                        Spacer(Modifier.height(16.dp))
                        Segments(detail.members.sortedBy { it.index }.map { it.roundsResolved > c.currentRound }, dark = true)
                        Spacer(Modifier.height(10.dp))
                        Text(
                            when {
                                now <= c.roundEndTs -> "${c.resolvedCount} of ${c.memberCount} paid. Round closes in ${formatDuration(c.roundEndTs - now)}"
                                now <= c.graceEndTs -> "Grace window: ${formatDuration(c.graceEndTs - now)} left, then bonds cover misses"
                                else -> "Grace window is over"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (now > c.roundEndTs) KinColors.Volt else Color.White.copy(alpha = 0.8f),
                        )
                    }
                }
            }

            item { CircleTags(c) }

            item {
                when {
                    c.status == CircleStatus.Open && myMember == null -> Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CloudCard {
                            ToggleRow(
                                "Turn on autopay",
                                "Allows collecting up to ${formatAmount(c.contribution * c.maxMembers)}, your total dues here. Revoke any time.",
                                joinWithAutopay,
                            ) { joinWithAutopay = it }
                        }
                        AquaButton("Join and lock ${formatAmount(c.bond)} bond", { actions.onJoin(c.address, joinWithAutopay) })
                    }
                    c.status == CircleStatus.Open ->
                        CloudCard {
                            Text("Waiting for members: ${c.memberCount}/${c.maxMembers}", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(10.dp))
                            Segments(List(c.maxMembers) { it < c.memberCount }, dark = false)
                            Spacer(Modifier.height(10.dp))
                            Text("Share the invite link. The circle starts when it fills.", style = MaterialTheme.typography.bodyMedium, color = KinColors.Slate)
                            Spacer(Modifier.height(12.dp))
                            GraphiteButton("Invite friends", { actions.onShare(c) })
                        }
                    canPay -> AquaButton("Pay ${formatAmount(c.contribution)}", actions.onContribute)
                    c.status == CircleStatus.Completed && myMember != null && !myMember.bondClaimed ->
                        AquaButton("Claim back ${formatAmount(myMember.bondLocked - myMember.bondUsed)}", actions.onClaim)
                    iResolved && active ->
                        Text("You are paid up for this round.", color = KinColors.Good, style = MaterialTheme.typography.titleMedium)
                }
            }

            if (myMember != null && c.status != CircleStatus.Completed) {
                item { AutopayCard(c, myMember, detail.allowances[me], actions.onSetAutopay) }
            }
            if (dueAutopay.isNotEmpty()) {
                item {
                    OutlineButton("Collect autopay from ${dueAutopay.size} member${if (dueAutopay.size > 1) "s" else ""}", actions.onCollect)
                }
            }
            if (canPayout) {
                item {
                    AquaButton("Send pot to ${if (recipient.wallet == me) "you" else shortKey(recipient.wallet)}", { actions.onPayout(recipient.wallet) })
                }
            }
            items(pendingAfterGrace, key = { "cover-" + it.address.toBase58() }) { m ->
                OutlineButton("Cover ${shortKey(m.wallet)} from their bond", { actions.onCover(m.wallet) })
            }

            if (myMember != null) {
                Schedule.positionNote(c, myMember)?.let { note ->
                    item {
                        CloudCard {
                            KinLabel("Your position")
                            Spacer(Modifier.height(6.dp))
                            Text(note, style = MaterialTheme.typography.bodyMedium, color = KinColors.Charcoal)
                        }
                    }
                }
            }

            item { KinLabel("Payout calendar") }
            item { CalendarCard(c, detail.members, me, now) }

            item { KinLabel("Members") }
            items(detail.members, key = { it.address.toBase58() }) { m ->
                MemberRow(c, m, m.wallet == me, detail.scores[m.wallet])
            }
            item { ProofSection(c, detail.proof, actions.onLoadProof, actions.onOpenUrl, now) }
            item { Box(Modifier.navigationBarsPadding()) }
        }
    }
}

@Composable
private fun CalendarCard(c: CircleData, members: List<MemberData>, me: PublicKey, now: Long) {
    val rows = Schedule.rows(c)
    PaperCard(padding = 4.dp) {
        rows.forEachIndexed { i, row ->
            val member = row.memberIndex?.let { idx -> members.firstOrNull { it.index == idx } }
            val who = when {
                member == null -> "Order set when the circle fills"
                member.wallet == me -> "You"
                else -> shortKey(member.wallet)
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(26.dp).clip(androidx.compose.foundation.shape.CircleShape).background(
                        when (row.state) {
                            RoundState.Done -> KinColors.Ink
                            RoundState.Current -> KinColors.Aqua
                            RoundState.Upcoming -> KinColors.Cloud
                        },
                    ),
                    contentAlignment = Alignment.Center,
                ) {
                    Mono("${row.round + 1}", color = if (row.state == RoundState.Done) Color.White else KinColors.Ink, size = 11)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(who, style = MaterialTheme.typography.titleMedium, color = if (member?.wallet == me) KinColors.Ink else KinColors.Charcoal)
                    row.opensAt?.let {
                        Text(
                            if (row.state == RoundState.Current) "Closes in ${formatDuration(it - now)}" else "Earliest in ${formatDuration(it - now)}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = KinColors.Slate,
                        )
                    }
                }
                Text(
                    when (row.state) {
                        RoundState.Done -> "Paid out"
                        RoundState.Current -> "Now"
                        RoundState.Upcoming -> "Upcoming"
                    },
                    style = MaterialTheme.typography.labelLarge,
                    color = if (row.state == RoundState.Current) KinColors.Good else KinColors.Slate,
                )
            }
            if (i < rows.lastIndex) Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp).height(1.dp).background(KinColors.Cloud))
        }
    }
}

@Composable
private fun MemberRow(c: CircleData, m: MemberData, isMe: Boolean, score: ScoreData?) {
    val paid = m.roundsResolved > c.currentRound
    val isRecipient = c.status == CircleStatus.Active && c.roundOf(m.index) == c.currentRound
    CloudCard(padding = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(m.wallet.toBase58(), "${m.index + 1}", 40.dp, highlight = isRecipient)
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
                    color = if (isRecipient || paid) KinColors.Good else KinColors.Warn,
                    style = MaterialTheme.typography.labelLarge,
                )
            } else if (m.received) {
                Text("Received", color = KinColors.Slate, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
