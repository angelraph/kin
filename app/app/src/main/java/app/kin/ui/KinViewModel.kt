package app.kin.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.kin.Config
import app.kin.solana.ActivityItem
import app.kin.solana.Allowance
import app.kin.solana.CircleData
import app.kin.solana.CircleStatus
import app.kin.solana.FundsProof
import app.kin.solana.Instruction
import app.kin.solana.KinProgram
import app.kin.solana.KinRepository
import app.kin.solana.MemberData
import app.kin.solana.PublicKey
import app.kin.solana.ScoreData
import app.kin.solana.SgtProof
import app.kin.solana.SolanaRpc
import app.kin.solana.TokenInstructions
import app.kin.solana.Transaction
import app.kin.wallet.WalletResult
import app.kin.wallet.WalletSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** On-chain evidence for one circle, loaded on demand. */
data class ProofState(
    val funds: FundsProof? = null,
    val activity: List<ActivityItem> = emptyList(),
    val loading: Boolean = true,
)

data class CircleDetail(
    val circle: CircleData,
    val members: List<MemberData>,
    val scores: Map<PublicKey, ScoreData>,
    val allowances: Map<PublicKey, Allowance> = emptyMap(),
    val proof: ProofState? = null,
)

data class UiState(
    val wallet: PublicKey? = null,
    val circles: List<CircleData> = emptyList(),
    val myScore: ScoreData? = null,
    val balance: Long = 0,
    val seeker: SgtProof? = null,
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
                checkSeeker()
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

    /** Looks for a Seeker Genesis Token on the wallet. A failure only means no badge is shown. */
    private suspend fun checkSeeker() {
        val wallet = _state.value.wallet ?: return
        val sgt = runCatching { repo.findSeekerToken(wallet, Config.SEEKER_AUTHORITY) }.getOrNull()
        _state.update { it.copy(seeker = sgt) }
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
        val wallets = members.map { it.wallet }
        val scores = repo.scores(wallets)
        val allowances = repo.allowances(wallets, circle.mint)
        val keepProof = _state.value.detail?.takeIf { it.circle.address == address }?.proof
        _state.update { it.copy(detail = CircleDetail(circle, members, scores, allowances, keepProof)) }
    }

    /** Loads the on-chain proof for the open circle: vault balances against accounting, and real transactions. */
    fun loadProof() {
        val detail = _state.value.detail ?: return
        _state.update { s -> s.copy(detail = s.detail?.copy(proof = ProofState(loading = true))) }
        viewModelScope.launch {
            val proof = runCatching {
                ProofState(
                    funds = repo.proveFunds(detail.circle, detail.members),
                    activity = repo.activity(detail.circle.address),
                    loading = false,
                )
            }.getOrElse {
                notify(it.message ?: "Could not load proof")
                ProofState(loading = false)
            }
            _state.update { s ->
                if (s.detail?.circle?.address == detail.circle.address) s.copy(detail = s.detail.copy(proof = proof)) else s
            }
        }
    }

    // Each action builds its instructions, has the wallet sign them, waits for confirmation, then reloads.

    fun createCircle(req: CreateRequest) {
        val wallet = _state.value.wallet ?: return
        val id = System.currentTimeMillis() / 1000
        val ix = KinProgram.createCircle(
            wallet, Config.MINT, id, req.name.trim(), req.contribution, req.contribution * req.bondMultiple,
            req.periodSecs, req.graceSecs, req.maxMembers, req.maxMissed,
            req.randomize, req.seekerOnly, if (req.seekerOnly) Config.SEEKER_AUTHORITY else PublicKey.DEFAULT,
        )
        val address = KinProgram.circlePda(wallet, id)
        send(listOf(ix), "Circle created") {
            loadHome()
            loadDetail(address)
        }
    }

    /** Join by circle address. With [autopay], one signature both joins and approves the capped allowance. */
    fun joinCircle(address: PublicKey, autopay: Boolean) = launchBusy {
        val wallet = _state.value.wallet ?: return@launchBusy
        val circle = repo.circle(address)
        if (circle == null) {
            notify("Circle not found on ${Config.CLUSTER_LABEL}")
            return@launchBusy
        }
        var sgt: SgtProof? = null
        if (circle.seekerOnly) {
            sgt = repo.findSeekerToken(wallet, circle.seekerAuthority)
            if (sgt == null) {
                notify("This circle is for Seeker owners. No Genesis Token was found in your wallet.")
                return@launchBusy
            }
        }
        val ixs = mutableListOf<Instruction>()
        if (autopay) ixs += allowanceInstruction(wallet, circle, circle.contribution * circle.maxMembers)
        ixs += KinProgram.joinCircle(wallet, circle, sgt)
        sendNow(ixs, "Joined. Your bond is locked.") {
            loadHome()
            loadDetail(address)
        }
    }

    fun contribute() = detailAction("Payment sent") { w, c -> listOf(KinProgram.contribute(w, c)) }

    fun claimBond() = detailAction("Bond returned") { w, c -> listOf(KinProgram.claimBond(w, c)) }

    fun coverMissed(target: PublicKey) =
        detailAction("Bond covered the missed payment") { w, c -> listOf(KinProgram.coverMissed(w, c, target)) }

    fun payout(recipient: PublicKey) =
        detailAction("Payout sent") { w, c -> listOf(KinProgram.payout(w, c, recipient)) }

    /** Turns this member's autopay on or off for the open circle. Only their own remaining dues are added or removed. */
    fun setAutopay(enabled: Boolean) {
        val wallet = _state.value.wallet ?: return
        val detail = _state.value.detail ?: return
        val member = detail.members.firstOrNull { it.wallet == wallet } ?: return
        val remaining = detail.circle.contribution * (detail.circle.maxMembers - member.roundsResolved)
        launchBusy {
            val current = repo.myAllowance(wallet, detail.circle.mint)
            val ix = if (enabled) {
                allowanceInstruction(wallet, detail.circle, remaining, current)
            } else {
                val next = (current.amount - remaining).coerceAtLeast(0)
                val ata = KinProgram.associatedTokenAddress(wallet, detail.circle.mint)
                if (next == 0L) TokenInstructions.revoke(ata, wallet)
                else TokenInstructions.approve(ata, KinProgram.AUTOPAY, wallet, next)
            }
            sendNow(listOf(ix), if (enabled) "Autopay is on" else "Autopay is off") { loadDetail(detail.circle.address) }
        }
    }

    /** Anyone can collect the round's contribution from members who turned autopay on. */
    fun collectDue() {
        val detail = _state.value.detail ?: return
        val wallet = _state.value.wallet ?: return
        val c = detail.circle
        val due = dueForAutopay(detail, System.currentTimeMillis() / 1000)
        if (due.isEmpty()) {
            notify("Nobody has an autopay payment due right now")
            return
        }
        val ixs = due.take(MAX_COLLECT_PER_TX).map { KinProgram.collect(wallet, c, it.wallet) }
        launchBusy {
            sendNow(ixs, "Collected ${ixs.size} autopay payment${if (ixs.size > 1) "s" else ""}") {
                loadHome()
                loadDetail(c.address)
            }
        }
    }

    /** Raises the wallet's autopay allowance by [addAmount], keeping whatever other circles already rely on. */
    private suspend fun allowanceInstruction(
        wallet: PublicKey,
        circle: CircleData,
        addAmount: Long,
        current: Allowance? = null,
    ): Instruction {
        val existing = current ?: repo.myAllowance(wallet, circle.mint)
        val base = if (existing.delegatedToKin) existing.amount else 0L
        val ata = KinProgram.associatedTokenAddress(wallet, circle.mint)
        return TokenInstructions.approve(ata, KinProgram.AUTOPAY, wallet, base + addAmount)
    }

    private fun detailAction(success: String, build: (PublicKey, CircleData) -> List<Instruction>) {
        val wallet = _state.value.wallet ?: return
        val circle = _state.value.detail?.circle ?: return
        send(build(wallet, circle), success) {
            loadHome()
            loadDetail(circle.address)
        }
    }

    private fun send(ixs: List<Instruction>, success: String, after: suspend () -> Unit) = launchBusy {
        sendNow(ixs, success, after)
    }

    private suspend fun sendNow(ixs: List<Instruction>, success: String, after: suspend () -> Unit) {
        val wallet = _state.value.wallet ?: return
        val budget = listOf(
            TokenInstructions.computeUnitLimit(Config.COMPUTE_UNIT_LIMIT),
            TokenInstructions.computeUnitPrice(Config.PRIORITY_MICRO_LAMPORTS),
        )
        val tx = Transaction.buildUnsigned(wallet, rpc.latestBlockhash(), budget + ixs)
        when (val r = session!!.signAndSend(tx)) {
            is WalletResult.Ok -> {
                _state.update { it.copy(lastSignature = r.value) }
                if (repo.waitForConfirmation(r.value)) {
                    notify(success)
                    after()
                } else {
                    notify("The transaction did not confirm. Open it in the explorer to see why.")
                }
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

    private companion object {
        /** Keeps a collection transaction well inside the size and compute limits. */
        const val MAX_COLLECT_PER_TX = 4
    }
}
