package app.kin.solana

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** SPL Token and Compute Budget instructions the app builds itself. */
object TokenInstructions {
    val COMPUTE_BUDGET_PROGRAM = PublicKey.fromBase58("ComputeBudget111111111111111111111111111111")
    val SLOT_HASHES_SYSVAR = PublicKey.fromBase58("SysvarS1otHashes111111111111111111111111111")
    val TOKEN_2022_PROGRAM = PublicKey.fromBase58("TokenzQdBNbLqP5VEhdkAS6EPFLC1PHnBqCXEpPxuEb")

    private fun le64(v: Long) = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(v).array()
    private fun le32(v: Int) = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v).array()

    /** SPL Token `Approve`: lets [delegate] move up to [amount] from [source] on the owner's behalf. */
    fun approve(source: PublicKey, delegate: PublicKey, owner: PublicKey, amount: Long) = Instruction(
        KinProgram.TOKEN_PROGRAM,
        listOf(
            AccountMeta(source, isSigner = false, isWritable = true),
            AccountMeta(delegate, isSigner = false, isWritable = false),
            AccountMeta(owner, isSigner = true, isWritable = false),
        ),
        byteArrayOf(4) + le64(amount),
    )

    /** SPL Token `Revoke`: removes any delegate from [source]. */
    fun revoke(source: PublicKey, owner: PublicKey) = Instruction(
        KinProgram.TOKEN_PROGRAM,
        listOf(
            AccountMeta(source, isSigner = false, isWritable = true),
            AccountMeta(owner, isSigner = true, isWritable = false),
        ),
        byteArrayOf(5),
    )

    fun computeUnitLimit(units: Int) = Instruction(COMPUTE_BUDGET_PROGRAM, emptyList(), byteArrayOf(2) + le32(units))

    fun computeUnitPrice(microLamports: Long) = Instruction(COMPUTE_BUDGET_PROGRAM, emptyList(), byteArrayOf(3) + le64(microLamports))
}

/** Fields of a classic SPL token account that Kin reads. */
data class TokenAccountData(
    val mint: PublicKey,
    val owner: PublicKey,
    val amount: Long,
    val delegate: PublicKey?,
    val delegatedAmount: Long,
) {
    companion object {
        /** Base layout: mint(32) owner(32) amount(8) delegate(4+32) state(1) native(4+8) delegated(8) ... */
        fun decode(data: ByteArray): TokenAccountData? {
            if (data.size < 129) return null
            val r = BorshReader(data)
            val mint = r.pubkey()
            val owner = r.pubkey()
            val amount = r.u64()
            val hasDelegate = r.u32() == 1L
            val delegateKey = r.pubkey()
            r.skip(1 + 4 + 8) // state, is_native option
            val delegated = r.u64()
            return TokenAccountData(mint, owner, amount, if (hasDelegate) delegateKey else null, delegated)
        }
    }
}

/**
 * Recomputes a circle's payout order from the seed stored on-chain. This mirrors `draw_order`
 * in programs/kin/src/logic.rs: a Fisher-Yates shuffle driven by SHA-256, so anyone can check it.
 */
object OrderProof {
    fun drawOrder(seed: ByteArray, n: Int): List<Int> {
        require(seed.size == 32) { "Seed must be 32 bytes" }
        val order = MutableList(n) { it }
        for (i in n - 1 downTo 1) {
            val md = java.security.MessageDigest.getInstance("SHA-256")
            md.update(seed)
            md.update(byteArrayOf(i.toByte()))
            val h = md.digest()
            val r = ByteBuffer.wrap(h, 0, 8).order(ByteOrder.LITTLE_ENDIAN).long
            val j = java.lang.Long.remainderUnsigned(r, (i + 1).toLong()).toInt()
            val tmp = order[i]; order[i] = order[j]; order[j] = tmp
        }
        return order
    }

    /** True when the on-chain order equals the order recomputed from the seed. */
    fun verifies(circle: CircleData): Boolean =
        circle.randomize && circle.orderSlot != 0L &&
            drawOrder(circle.orderSeed, circle.memberCount) == circle.payoutOrder.take(circle.memberCount)
}
