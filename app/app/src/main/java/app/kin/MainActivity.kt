package app.kin

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import app.kin.solana.CircleData
import app.kin.solana.PublicKey
import app.kin.ui.Actions
import app.kin.ui.KinApp
import app.kin.ui.KinTheme
import app.kin.ui.KinViewModel
import app.kin.wallet.WalletSession
import com.solana.mobilewalletadapter.clientlib.ActivityResultSender

class MainActivity : ComponentActivity() {
    private val vm: KinViewModel by viewModels()
    private var pendingJoin: PublicKey? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Must be created before the activity is STARTED so MWA can register its result launcher.
        val sender = ActivityResultSender(this)
        vm.attach(WalletSession(sender))
        pendingJoin = parseInvite(intent)

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
            onSetAutopay = vm::setAutopay,
            onCollect = vm::collectDue,
            onLoadProof = vm::loadProof,
            onOpenUrl = ::openUrl,
            onDismissNotice = vm::dismissNotice,
        )

        setContent {
            val state by vm.state.collectAsState()
            KinTheme {
                KinApp(state = state, actions = actions)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        parseInvite(intent)?.let { vm.openCircle(it) }
    }

    override fun onResume() {
        super.onResume()
        // Open an invite once a wallet is connected.
        val p = pendingJoin
        if (p != null && vm.state.value.wallet != null) {
            pendingJoin = null
            vm.openCircle(p)
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

    private fun parseInvite(intent: Intent?): PublicKey? = intent?.data?.let { parseInviteUri(it) }

    private fun parseInviteUri(uri: Uri): PublicKey? =
        if (uri.scheme == "kin" && uri.host == "join") uri.lastPathSegment?.let { runCatching { PublicKey.fromBase58(it) }.getOrNull() } else null

    private fun parseInviteText(text: String): PublicKey? =
        runCatching { PublicKey.fromBase58(text.substringAfterLast('/').trim()) }.getOrNull()
}
