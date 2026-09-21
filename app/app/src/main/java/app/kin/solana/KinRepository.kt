package app.kin.solana

/** Reads Kin state from chain. All methods are read-only. */
class KinRepository(private val rpc: SolanaRpc) {

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
}
