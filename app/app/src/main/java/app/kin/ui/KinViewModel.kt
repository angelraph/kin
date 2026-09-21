package app.kin.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kin.Config
import app.kin.solana.CircleData
import app.kin.solana.Instruction
import app.kin.solana.KinProgram
import app.kin.solana.KinRepository
import app.kin.solana.MemberData
import app.kin.solana.PublicKey
import app.kin.solana.ScoreData
import app.kin.solana.SolanaRpc
import app.kin.solana.Transaction
import app.kin.wallet.WalletResult
import app.kin.wallet.WalletSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CircleDetail(
    val circle: CircleData,
    val members: List<MemberData>,
    val scores: Map<PublicKey, ScoreData>,
)

data class UiState(
    val wallet: PublicKey? = null,
    val circles: List<CircleData> = emptyList(),
    val myScore: ScoreData? = null,
    val balance: Long = 0,
    val detail: CircleDetail? = null,
    val loading: Boolean = false,
    val busy: Boolean = false,
    val notice: String? = null,
    val lastSignature: String? = null,
)

class KinViewModel : ViewModel() {
    private val rpc = SolanaRpc(Config.RPC_URL)
    private val repo = KinRepository(rpc)
    private var session: WalletSession? = null

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state

    fun attach(session: WalletSession) {
        this.session = session
    }

    fun dismissNotice() = _state.update { it.copy(notice = null) }

    fun showNotice(message: String) = notify(message)

    fun connect() = launchBusy {
        when (val r = session!!.connect()) {
            is WalletResult.Ok -> {
                _state.update { it.copy(wallet = r.value) }
                loadHome()
            }
            is WalletResult.NoWallet -> notify("No wallet app found. Install a Solana wallet (Seed Vault Wallet on Seeker).")
            is WalletResult.Error -> notify(r.message)
        }
    }

    fun disconnect() {
        session?.disconnect()
        _state.value = UiState()
    }

    fun refresh() = viewModelScope.launch {
        runCatching {
            loadHome()
            _state.value.detail?.let { loadDetail(it.circle.address) }
        }.onFailure { notify(it.message ?: "Refresh failed") }
    }

    private suspend fun loadHome() {
        val wallet = _state.value.wallet ?: return
        _state.update { it.copy(loading = true) }
        try {
            val circles = repo.circlesFor(wallet)
            val score = repo.score(wallet)
            val balance = repo.tokenBalance(wallet, Config.MINT)
            _state.update { it.copy(circles = circles, myScore = score, balance = balance, loading = false) }
        } catch (e: Exception) {
            _state.update { it.copy(loading = false, notice = e.message ?: "Could not load circles") }
        }
    }

    fun openCircle(address: PublicKey) = launchBusy {
        loadDetail(address)
    }

    fun closeCircle() = _state.update { it.copy(detail = null) }

    private suspend fun loadDetail(address: PublicKey) {
        val circle = repo.circle(address)
        if (circle == null) {
            notify("Circle not found on ${Config.CLUSTER_LABEL}")
            return
        }
        val members = repo.members(address)
        val scores = repo.scores(members.map { it.wallet })
        _state.update { it.copy(detail = CircleDetail(circle, members, scores)) }
    }

    // Each action builds one instruction, has the wallet sign it, then reloads.

    fun createCircle(req: CreateRequest) {
        val wallet = _state.value.wallet ?: return
        val id = System.currentTimeMillis() / 1000
        val ix = KinProgram.createCircle(
            wallet, Config.MINT, id, req.name.trim(), req.contribution, req.contribution * req.bondMultiple,
            req.periodSecs, req.graceSecs, req.maxMembers, req.maxMissed,
        )
        val address = KinProgram.circlePda(wallet, id)
        send(ix, "Circle created") {
            loadHome()
            loadDetail(address)
        }
    }

    /** Join by circle address (from an invite link). */
    fun joinCircle(address: PublicKey) = launchBusy {
        val wallet = _state.value.wallet ?: return@launchBusy
        val circle = repo.circle(address)
        if (circle == null) {
            notify("Circle not found on ${Config.CLUSTER_LABEL}")
            return@launchBusy
        }
        sendNow(KinProgram.joinCircle(wallet, circle), "Joined. Your bond is locked.") {
            loadHome()
            loadDetail(address)
        }
    }

    fun contribute() = detailAction("Payment sent") { w, c -> KinProgram.contribute(w, c) }

    fun claimBond() = detailAction("Bond returned") { w, c -> KinProgram.claimBond(w, c) }

    fun coverMissed(target: PublicKey) =
        detailAction("Bond covered the missed payment") { w, c -> KinProgram.coverMissed(w, c, target) }

    fun payout(recipient: PublicKey) =
        detailAction("Payout sent") { w, c -> KinProgram.payout(w, c, recipient) }

    private fun detailAction(success: String, build: (PublicKey, CircleData) -> Instruction) {
        val wallet = _state.value.wallet ?: return
        val circle = _state.value.detail?.circle ?: return
        send(build(wallet, circle), success) {
            loadHome()
            loadDetail(circle.address)
        }
    }

    private fun send(ix: Instruction, success: String, after: suspend () -> Unit) = launchBusy {
        sendNow(ix, success, after)
    }

    private suspend fun sendNow(ix: Instruction, success: String, after: suspend () -> Unit) {
        val wallet = _state.value.wallet ?: return
        val tx = Transaction.buildUnsigned(wallet, rpc.latestBlockhash(), listOf(ix))
        when (val r = session!!.signAndSend(tx)) {
            is WalletResult.Ok -> {
                _state.update { it.copy(lastSignature = r.value) }
                notify(success)
                kotlinx.coroutines.delay(2500) // let the cluster confirm before re-reading
                after()
            }
            is WalletResult.NoWallet -> notify("No wallet app found")
            is WalletResult.Error -> notify(r.message)
        }
    }

    private fun launchBusy(block: suspend () -> Unit) = viewModelScope.launch {
        _state.update { it.copy(busy = true) }
        try {
            block()
        } catch (e: Exception) {
            notify(e.message ?: "Something went wrong")
        } finally {
            _state.update { it.copy(busy = false) }
        }
    }

    private fun notify(msg: String) = _state.update { it.copy(notice = msg) }
}
