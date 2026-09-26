package app.kin

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.kin.solana.CircleData
import app.kin.solana.PublicKey
import app.kin.solana.ScoreData
import app.kin.ui.Actions
import app.kin.ui.KinApp
import app.kin.ui.KinTheme
import app.kin.ui.KinViewModel
import app.kin.ui.Reminders
import app.kin.wallet.WalletSession
import app.kin.watch.CircleWatchWorker
import app.kin.watch.KinPrefs
import app.kin.watch.Notifier
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender

class MainActivity : ComponentActivity() {
    private val vm: KinViewModel by viewModels()
    private lateinit var prefs: KinPrefs
    private var pendingCircle: PublicKey? = null

    private var remindersOn by mutableStateOf(true)
    private var notificationsAllowed by mutableStateOf(true)

    // Registered up front, as required, and used when reminders are switched on.
    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            notificationsAllowed = Notifier.canPost(this)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The design is light, so keep dark status and navigation icons whatever the system theme is.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
        )
        prefs = KinPrefs(this)
        remindersOn = prefs.remindersEnabled
        notificationsAllowed = Notifier.canPost(this)

        // Must be created before the activity is STARTED so MWA can register its result launcher.
        val sender = ActivityResultSender(this)
        vm.attach(WalletSession(sender))
        pendingCircle = parseCircleLink(intent)

        val actions = Actions(
            onConnect = vm::connect,
            onDisconnect = vm::disconnect,
            onRefresh = vm::refresh,
            onOpen = vm::openCircle,
            onClose = vm::closeCircle,
            onJoinLink = { text ->
                val address = parseInviteText(text)
                if (address != null) vm.openCircle(address) else vm.showNotice("That is not a valid Kin invite")
            },
            onCreate = vm::createCircle,
            onContribute = vm::contribute,
            onCover = vm::coverMissed,
            onPayout = vm::payout,
            onClaim = vm::claimBond,
            onJoin = vm::joinCircle,
            onShare = ::shareInvite,
            onShareScore = ::shareScore,
            onLookup = vm::lookupWallet,
            onGetTestFunds = vm::getTestFunds,
            onSetAutopay = vm::setAutopay,
            onCollect = vm::collectDue,
            onLoadProof = vm::loadProof,
            onOpenUrl = ::openUrl,
            onToggleReminders = ::setReminders,
            onDismissNotice = vm::dismissNotice,
        )

        setContent {
            val state by vm.state.collectAsState()

            // Follow the connected wallet: remember it for the background watcher, and open any link that arrived first.
            LaunchedEffect(state.wallet) {
                prefs.wallet = state.wallet
                if (state.wallet == null) {
                    CircleWatchWorker.cancel(this@MainActivity)
                    prefs.clearShown()
                } else {
                    if (prefs.remindersEnabled) startWatching()
                    pendingCircle?.let {
                        pendingCircle = null
                        vm.openCircle(it)
                    }
                }
            }

            KinTheme {
                KinApp(state = state, actions = actions, reminders = Reminders(remindersOn, notificationsAllowed))
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val circle = parseCircleLink(intent) ?: return
        if (vm.state.value.wallet != null) vm.openCircle(circle) else pendingCircle = circle
    }

    override fun onResume() {
        super.onResume()
        notificationsAllowed = Notifier.canPost(this)
    }

    private fun setReminders(on: Boolean) {
        prefs.remindersEnabled = on
        remindersOn = on
        if (on) startWatching() else CircleWatchWorker.cancel(this)
    }

    /** Schedules the background check and asks for notification permission once, when it is first needed. */
    private fun startWatching() {
        CircleWatchWorker.schedule(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !Notifier.canPost(this) && !prefs.askedForNotifications) {
            prefs.askedForNotifications = true
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun openUrl(url: String) {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure { vm.showNotice("No browser found to open the link") }
    }

    private fun shareInvite(c: CircleData) {
        val link = "kin://join/${c.address.toBase58()}"
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "Join my Kin circle \"${c.name}\": $link")
        }
        startActivity(Intent.createChooser(send, "Invite to circle"))
    }

    private fun shareScore(score: ScoreData?) {
        val pct = score?.reliabilityPercent
        val text = if (pct == null || score == null) {
            "I am on Kin, a savings circle on Solana where every payment builds a public reliability score."
        } else {
            "My Kin Score is $pct. ${score.onTime} payments on time, ${score.missed} missed, ${score.circlesCompleted} circles completed. " +
                "It is recorded on Solana, so anyone can check it."
        }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(send, "Share my Kin Score"))
    }

    /** Reads kin://join/CIRCLE (invites) and kin://circle/CIRCLE (notification taps). */
    private fun parseCircleLink(intent: Intent?): PublicKey? {
        val uri = intent?.data ?: return null
        if (uri.scheme != "kin" || (uri.host != "join" && uri.host != "circle")) return null
        return uri.lastPathSegment?.let { runCatching { PublicKey.fromBase58(it) }.getOrNull() }
    }

    private fun parseInviteText(text: String): PublicKey? =
        runCatching { PublicKey.fromBase58(text.substringAfterLast('/').trim()) }.getOrNull()
}
