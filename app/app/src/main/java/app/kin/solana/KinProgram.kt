package app.kin.solana

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest

// Account models below mirror programs/kin/src/state.rs.

enum class CircleStatus { Open, Active, Completed }

data class CircleData(
    val address: PublicKey,
    val creator: PublicKey,
    val circleId: Long,
    val mint: PublicKey,
    val contribution: Long,
    val bond: Long,
    val periodSecs: Long,
    val graceSecs: Long,
    val maxMembers: Int,
    val memberCount: Int,
    val maxMissedAllowed: Long,
    val status: CircleStatus,
    val currentRound: Int,
    val roundStartTs: Long,
    val resolvedCount: Int,
    val roundPot: Long,
    val createdTs: Long,
    val name: String,
) {
    val roundEndTs get() = roundStartTs + periodSecs
    val graceEndTs get() = roundStartTs + periodSecs + graceSecs
}

data class MemberData(
    val address: PublicKey,
    val circle: PublicKey,
    val wallet: PublicKey,
    val index: Int,
    val bondLocked: Long,
    val bondUsed: Long,
    val roundsResolved: Int,
    val received: Boolean,
    val bondClaimed: Boolean,
    val onTime: Int,
    val late: Int,
    val missed: Int,
)

data class ScoreData(
    val wallet: PublicKey,
    val onTime: Long,
    val late: Long,
    val missed: Long,
    val circlesCompleted: Long,
    val streak: Long,
    val bestStreak: Long,
) {
    /** 0..100, or null when the wallet has no history yet. */
    val reliabilityPercent: Int?
        get() {
            val total = onTime + late + missed
            return if (total == 0L) null else ((onTime * 100) / total).toInt()
        }
}

/** Client for the Kin Anchor program: PDAs, instruction encoding and account decoding. */
object KinProgram {
    val PROGRAM_ID = PublicKey.fromBase58("7CGtKBZVMKgWJRg92SmRiQkeQe8hvTV3SWrHRfsdSMWe")
    val TOKEN_PROGRAM = PublicKey.fromBase58("TokenkegQfeZyiNwAJbNbGKPFXCWuBvf9Ss623VQ5DA")
    val ATA_PROGRAM = PublicKey.fromBase58("ATokenGPvbdGVxr1b2hvZbsiqW5xWH25efTNsLJA8knL")
    val SYSTEM_PROGRAM = PublicKey.fromBase58("11111111111111111111111111111111")

    private fun le64(v: Long) = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(v).array()
    private fun sha256(s: String) = MessageDigest.getInstance("SHA-256").digest(s.toByteArray())

    fun instructionDiscriminator(snakeName: String) = sha256("global:$snakeName").copyOf(8)
    fun accountDiscriminator(typeName: String) = sha256("account:$typeName").copyOf(8)

    fun circlePda(creator: PublicKey, circleId: Long) =
        Pda.find(listOf("circle".toByteArray(), creator.bytes, le64(circleId)), PROGRAM_ID).first

    fun vaultPda(circle: PublicKey) = Pda.find(listOf("vault".toByteArray(), circle.bytes), PROGRAM_ID).first
    fun bondVaultPda(circle: PublicKey) = Pda.find(listOf("bond_vault".toByteArray(), circle.bytes), PROGRAM_ID).first
    fun memberPda(circle: PublicKey, wallet: PublicKey) =
        Pda.find(listOf("member".toByteArray(), circle.bytes, wallet.bytes), PROGRAM_ID).first

    fun scorePda(wallet: PublicKey) = Pda.find(listOf("score".toByteArray(), wallet.bytes), PROGRAM_ID).first

    fun associatedTokenAddress(owner: PublicKey, mint: PublicKey) =
        Pda.find(listOf(owner.bytes, TOKEN_PROGRAM.bytes, mint.bytes), ATA_PROGRAM).first

    private fun meta(k: PublicKey, signer: Boolean = false, writable: Boolean = false) = AccountMeta(k, signer, writable)

    fun createCircle(
        creator: PublicKey,
        mint: PublicKey,
        circleId: Long,
        name: String,
        contribution: Long,
        bond: Long,
        periodSecs: Long,
        graceSecs: Long,
        maxMembers: Int,
        maxMissedAllowed: Long,
    ): Instruction {
        val circle = circlePda(creator, circleId)
        val data = BorshWriter()
            .bytes(instructionDiscriminator("create_circle"))
            .u64(circleId).string(name).u64(contribution).u64(bond)
            .i64(periodSecs).i64(graceSecs).u8(maxMembers).u32(maxMissedAllowed)
            .toByteArray()
        return Instruction(
            PROGRAM_ID,
            listOf(
                meta(creator, signer = true, writable = true),
                meta(mint),
                meta(circle, writable = true),
                meta(vaultPda(circle), writable = true),
                meta(bondVaultPda(circle), writable = true),
                meta(TOKEN_PROGRAM),
                meta(SYSTEM_PROGRAM),
            ),
            data,
        )
    }

    fun joinCircle(wallet: PublicKey, circle: CircleData): Instruction = Instruction(
        PROGRAM_ID,
        listOf(
            meta(wallet, signer = true, writable = true),
            meta(circle.address, writable = true),
            meta(memberPda(circle.address, wallet), writable = true),
            meta(scorePda(wallet), writable = true),
            meta(associatedTokenAddress(wallet, circle.mint), writable = true),
            meta(bondVaultPda(circle.address), writable = true),
            meta(TOKEN_PROGRAM),
            meta(SYSTEM_PROGRAM),
        ),
        instructionDiscriminator("join_circle"),
    )

    fun contribute(wallet: PublicKey, circle: CircleData): Instruction = Instruction(
        PROGRAM_ID,
        listOf(
            meta(wallet, signer = true, writable = true),
            meta(circle.address, writable = true),
            meta(memberPda(circle.address, wallet), writable = true),
            meta(scorePda(wallet), writable = true),
            meta(associatedTokenAddress(wallet, circle.mint), writable = true),
            meta(vaultPda(circle.address), writable = true),
            meta(TOKEN_PROGRAM),
        ),
        instructionDiscriminator("contribute"),
    )

    fun coverMissed(caller: PublicKey, circle: CircleData, target: PublicKey): Instruction = Instruction(
        PROGRAM_ID,
        listOf(
            meta(caller, signer = true),
            meta(circle.address, writable = true),
            meta(memberPda(circle.address, target), writable = true),
            meta(scorePda(target), writable = true),
            meta(vaultPda(circle.address), writable = true),
            meta(bondVaultPda(circle.address), writable = true),
            meta(TOKEN_PROGRAM),
        ),
        instructionDiscriminator("cover_missed"),
    )

    fun payout(caller: PublicKey, circle: CircleData, recipient: PublicKey): Instruction = Instruction(
        PROGRAM_ID,
        listOf(
            meta(caller, signer = true, writable = true),
            meta(circle.address, writable = true),
            meta(circle.mint),
            meta(memberPda(circle.address, recipient), writable = true),
            meta(recipient),
            meta(associatedTokenAddress(recipient, circle.mint), writable = true),
            meta(vaultPda(circle.address), writable = true),
            meta(TOKEN_PROGRAM),
            meta(ATA_PROGRAM),
            meta(SYSTEM_PROGRAM),
        ),
        instructionDiscriminator("payout"),
    )

    fun claimBond(wallet: PublicKey, circle: CircleData): Instruction = Instruction(
        PROGRAM_ID,
        listOf(
            meta(wallet, signer = true, writable = true),
            meta(circle.address),
            meta(memberPda(circle.address, wallet), writable = true),
            meta(scorePda(wallet), writable = true),
            meta(associatedTokenAddress(wallet, circle.mint), writable = true),
            meta(bondVaultPda(circle.address), writable = true),
            meta(TOKEN_PROGRAM),
        ),
        instructionDiscriminator("claim_bond"),
    )

    /** Anchor accounts are an 8-byte discriminator followed by Borsh fields. */
    fun decodeCircle(address: PublicKey, data: ByteArray): CircleData {
        val r = BorshReader(data).skip(8)
        val creator = r.pubkey()
        val circleId = r.u64()
        val mint = r.pubkey()
        val contribution = r.u64()
        val bond = r.u64()
        val period = r.i64()
        val grace = r.i64()
        val maxMembers = r.u8()
        val memberCount = r.u8()
        val maxMissed = r.u32()
        val status = CircleStatus.entries[r.u8()]
        val currentRound = r.u8()
        val roundStart = r.i64()
        val resolved = r.u8()
        val pot = r.u64()
        val created = r.i64()
        r.skip(3) // bumps
        val name = r.string()
        return CircleData(
            address, creator, circleId, mint, contribution, bond, period, grace, maxMembers, memberCount,
            maxMissed, status, currentRound, roundStart, resolved, pot, created, name,
        )
    }

    fun decodeMember(address: PublicKey, data: ByteArray): MemberData {
        val r = BorshReader(data).skip(8)
        return MemberData(
            address = address, circle = r.pubkey(), wallet = r.pubkey(), index = r.u8(),
            bondLocked = r.u64(), bondUsed = r.u64(), roundsResolved = r.u8(),
            received = r.bool(), bondClaimed = r.bool(),
            onTime = r.u16(), late = r.u16(), missed = r.u16(),
        )
    }

    fun decodeScore(data: ByteArray): ScoreData {
        val r = BorshReader(data).skip(8)
        return ScoreData(
            wallet = r.pubkey(), onTime = r.u32(), late = r.u32(), missed = r.u32(),
            circlesCompleted = r.u32(), streak = r.u32(), bestStreak = r.u32(),
        )
    }
}
