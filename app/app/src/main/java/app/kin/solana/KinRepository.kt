package app.kin.solana

/** One entry in a circle's activity feed: a real transaction and the events it emitted. */
data class ActivityItem(
    val signature: String,
    val blockTime: Long?,
    val events: List<KinEvent>,
)

/**
 * Result of comparing the vaults' real token balances with what the program's accounting says is owed.
 * A vault holding more than owed is fine (someone sent it extra). Holding less would be a red flag.
 */
data class FundsProof(
    val vaultBalance: Long,
    val expectedVault: Long,
    val bondVaultBalance: Long,
    val expectedBondVault: Long,
) {
    val vaultOk get() = vaultBalance >= expectedVault
    val bondVaultOk get() = bondVaultBalance >= expectedBondVault
    val ok get() = vaultOk && bondVaultOk
}

/** What a member has approved for autopay on their token account. */
data class Allowance(val delegatedToKin: Boolean, val amount: Long)

/** Reads Kin state from chain. All methods are read-only. */
class KinRepository(private val rpc: SolanaRpc) {
    private val eventCache = HashMap<String, List<KinEvent>>()

    suspend fun circle(address: PublicKey): CircleData? {
        val acc = rpc.accountInfo(address) ?: return null
        if (acc.owner != KinProgram.PROGRAM_ID) return null
        return KinProgram.decodeCircle(address, acc.data)
    }

    /** Circles the wallet has joined (found via its Member accounts). */
    suspend fun circlesFor(wallet: PublicKey): List<CircleData> {
        val members = rpc.programAccounts(
            KinProgram.PROGRAM_ID,
            listOf(
                0 to KinProgram.accountDiscriminator("Member"),
                (8 + 32) to wallet.bytes, // Member.wallet
            ),
        ).map { KinProgram.decodeMember(it.address, it.data) }
        val addresses = members.map { it.circle }.distinct()
        return rpc.multipleAccounts(addresses)
            .mapIndexedNotNull { i, acc -> acc?.let { KinProgram.decodeCircle(addresses[i], it.data) } }
            .sortedByDescending { it.createdTs }
    }

    suspend fun members(circle: PublicKey): List<MemberData> = rpc.programAccounts(
        KinProgram.PROGRAM_ID,
        listOf(
            0 to KinProgram.accountDiscriminator("Member"),
            8 to circle.bytes, // Member.circle
        ),
    ).map { KinProgram.decodeMember(it.address, it.data) }.sortedBy { it.index }

    suspend fun score(wallet: PublicKey): ScoreData? {
        val acc = rpc.accountInfo(KinProgram.scorePda(wallet)) ?: return null
        return KinProgram.decodeScore(acc.data)
    }

    suspend fun scores(wallets: List<PublicKey>): Map<PublicKey, ScoreData> {
        val pdas = wallets.map { KinProgram.scorePda(it) }
        val accounts = rpc.multipleAccounts(pdas)
        return wallets.zip(accounts).mapNotNull { (w, acc) ->
            acc?.let { w to KinProgram.decodeScore(it.data) }
        }.toMap()
    }

    suspend fun tokenBalance(owner: PublicKey, mint: PublicKey): Long =
        rpc.tokenBalance(KinProgram.associatedTokenAddress(owner, mint))

    /** Each wallet's autopay allowance for this circle's token. Wallets without a token account get no entry. */
    suspend fun allowances(wallets: List<PublicKey>, mint: PublicKey): Map<PublicKey, Allowance> {
        val atas = wallets.map { KinProgram.associatedTokenAddress(it, mint) }
        val accounts = rpc.multipleAccounts(atas)
        return wallets.zip(accounts).mapNotNull { (w, acc) ->
            val token = acc?.let { TokenAccountData.decode(it.data) } ?: return@mapNotNull null
            w to Allowance(token.delegate == KinProgram.AUTOPAY, if (token.delegate == KinProgram.AUTOPAY) token.delegatedAmount else 0L)
        }.toMap()
    }

    /** The wallet's current allowance to Kin autopay on its token account, in base units. */
    suspend fun myAllowance(wallet: PublicKey, mint: PublicKey): Allowance =
        allowances(listOf(wallet), mint)[wallet] ?: Allowance(false, 0)

    /**
     * Compares the vaults' real on-chain token balances with what the program's accounting says they
     * should hold. If these ever differ, something is wrong. Anyone can check this without trusting Kin.
     */
    suspend fun proveFunds(circle: CircleData, members: List<MemberData>): FundsProof {
        val vault = KinProgram.vaultPda(circle.address)
        val bondVault = KinProgram.bondVaultPda(circle.address)
        val vaultBalance = rpc.tokenBalance(vault)
        val bondBalance = rpc.tokenBalance(bondVault)
        val bondsHeld = members.filter { !it.bondClaimed }.sumOf { it.bondLocked - it.bondUsed }
        return FundsProof(vaultBalance, circle.roundPot, bondBalance, bondsHeld)
    }

    /** Recent transactions on the circle with the program events decoded from their logs. */
    suspend fun activity(circle: PublicKey, limit: Int = 25): List<ActivityItem> {
        val refs = rpc.signaturesFor(circle, limit).filter { !it.failed }
        return refs.mapNotNull { ref ->
            val events = eventCache[ref.signature] ?: run {
                val logs = rpc.logsOf(ref.signature) ?: return@mapNotNull null
                KinEvents.fromLogs(logs).filter { it.circle == circle }.also { eventCache[ref.signature] = it }
            }
            if (events.isEmpty()) null else ActivityItem(ref.signature, ref.blockTime, events)
        }
    }

    /**
     * Finds a valid Seeker Genesis Token for [wallet]: a Token-2022 token account holding one token
     * whose mint has [authority] as its mint authority. Returns null if the wallet has none.
     */
    suspend fun findSeekerToken(wallet: PublicKey, authority: PublicKey): SgtProof? {
        val accounts = rpc.tokenAccountsByOwner(wallet, TokenInstructions.TOKEN_2022_PROGRAM)
        val candidates = accounts.mapNotNull { acc ->
            val token = TokenAccountData.decode(acc.data) ?: return@mapNotNull null
            if (token.amount == 1L) acc.address to token.mint else null
        }
        if (candidates.isEmpty()) return null
        val mints = rpc.multipleAccounts(candidates.map { it.second })
        candidates.zip(mints).forEach { (cand, mintAcc) ->
            if (mintAcc != null && mintAcc.owner == TokenInstructions.TOKEN_2022_PROGRAM && mintAcc.data.size >= 36) {
                val tag = BorshReader(mintAcc.data).u32()
                val mintAuthority = PublicKey(mintAcc.data.copyOfRange(4, 36))
                if (tag == 1L && mintAuthority == authority) return SgtProof(cand.first, cand.second)
            }
        }
        return null
    }

    suspend fun waitForConfirmation(signature: String, attempts: Int = 30): Boolean {
        repeat(attempts) {
            val status = rpc.signatureStatuses(listOf(signature)).firstOrNull()
            if (status == "failed") return false
            if (status == "confirmed" || status == "finalized") return true
            kotlinx.coroutines.delay(1000)
        }
        return false
    }
}
