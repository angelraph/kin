package app.kin.solana

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

/**
 * Expected values were produced independently with @solana/web3.js and @solana/spl-token
 * against the deployed program ID, so these tests check the Kotlin client against a second implementation.
 */
class KinProgramTest {
    private val creator = PublicKey.fromBase58("3LpS3ZUAS1xuQxhLygtGgti6cB7uj4dQQrYnyjg3WuwB")
    private val other = PublicKey.fromBase58("GmaDrppBC7P5ARKV8g3djiwP89vz1jLK23V2GBjuAEGB")
    private val usdc = PublicKey.fromBase58("EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v")

    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }

    private fun circleBytes(randomize: Boolean, seeker: Boolean, order: List<Int>, seed: ByteArray = ByteArray(32)): ByteArray =
        BorshWriter()
            .bytes(KinProgram.accountDiscriminator("Circle"))
            .bytes(creator.bytes).u64(7).bytes(usdc.bytes)
            .u64(100).u64(200).i64(60).i64(30)
            .u8(3).u8(3).u32(0).u8(1) // maxMembers, memberCount, maxMissed, status=Active
            .u8(1).i64(1_700_000_000).u8(2).u64(200).i64(1_699_999_000)
            .u8(255).u8(254).u8(253)
            .u8(if (randomize) 1 else 0).u8(if (seeker) 1 else 0).bytes(other.bytes)
            .u64(if (randomize) 812345 else 0).bytes(seed)
            .also { w -> repeat(12) { i -> w.u8(order.getOrElse(i) { 0 }) } }
            .string("Family")
            .toByteArray()

    @Test
    fun base58RoundTrips() {
        val samples = listOf(ByteArray(32), ByteArray(32) { 0xff.toByte() }, ByteArray(32) { it.toByte() }, byteArrayOf(0, 0, 1, 2))
        samples.forEach { assertArrayEquals(it, Base58.decode(Base58.encode(it))) }
        assertEquals(usdc.toBase58(), Base58.encode(usdc.bytes))
    }

    @Test
    fun onCurveCheckAgreesWithKnownKeys() {
        assertTrue("wallet keys are on the curve", Pda.isOnCurve(creator.bytes))
        assertTrue(Pda.isOnCurve(other.bytes))
        val vote = PublicKey.fromBase58("Vote111111111111111111111111111111111111111")
        assertFalse("the vote program address is off the curve", Pda.isOnCurve(vote.bytes))
    }

    @Test
    fun pdasMatchTheJavascriptClient() {
        val circle = KinProgram.circlePda(creator, 1)
        assertEquals("9aGLiqBgmVMB7BMfozp1h8UUSL3LRvdGDuRh7tSAJssT", circle.toBase58())
        assertEquals("BaNKqrDxRQ4PaTUf7WpY53HcUYBEk2a9V17WGP2LeXzm", KinProgram.vaultPda(circle).toBase58())
        assertEquals("DqbvVyWVvML2JgKiDzJVix4ib5LrcXWmUqLdb9BKMLZ", KinProgram.bondVaultPda(circle).toBase58())
        assertEquals("BKji1ajDJi3WpuZ7LChSAkeK2ERzMZtr3PoakWHabgaN", KinProgram.memberPda(circle, other).toBase58())
        assertEquals("Dg8i857QoU4BQWWE1RoAPqZ6NqUFNcEiBpzHf7zVByNf", KinProgram.scorePda(other).toBase58())
    }

    @Test
    fun associatedTokenAddressMatches() {
        assertEquals("7woc3ajaGMMXczFYjxon4aQoHH3j126fMUR9c58eHRsK", KinProgram.associatedTokenAddress(other, usdc).toBase58())
    }

    @Test
    fun discriminatorsMatchAnchor() {
        assertEquals("ba6331831f330dc6", hex(KinProgram.instructionDiscriminator("create_circle")))
        assertEquals("1b3b08753ec7defc", hex(KinProgram.accountDiscriminator("Circle")))
    }

    @Test
    fun autopayDelegateIsTheProgramPda() {
        val expected = Pda.find(listOf("autopay".toByteArray()), KinProgram.PROGRAM_ID).first
        assertEquals(expected, KinProgram.AUTOPAY)
        assertFalse("a PDA must be off the curve", Pda.isOnCurve(KinProgram.AUTOPAY.bytes))
    }

    @Test
    fun createCircleInstructionLayout() {
        val ix = KinProgram.createCircle(creator, usdc, 1, "Family", 100, 200, 60, 30, 3, 0, true, false, PublicKey.DEFAULT)
        assertEquals(7, ix.accounts.size)
        assertTrue(ix.accounts[0].isSigner && ix.accounts[0].isWritable)
        // 8 disc + 8 id + (4+6) name + 8 + 8 + 8 + 8 + 1 + 4 + 1 + 1 + 32
        assertEquals(8 + 8 + 10 + 8 + 8 + 8 + 8 + 1 + 4 + 1 + 1 + 32, ix.data.size)
    }

    @Test
    fun joinPassesTheProgramIdForMissingSeekerAccounts() {
        val circle = KinProgram.decodeCircle(creator, circleBytes(false, false, listOf(0, 1, 2)))
        val plain = KinProgram.joinCircle(other, circle)
        assertEquals(11, plain.accounts.size)
        assertEquals(TokenInstructions.SLOT_HASHES_SYSVAR, plain.accounts[6].pubkey)
        assertEquals(KinProgram.PROGRAM_ID, plain.accounts[7].pubkey)
        assertEquals(KinProgram.PROGRAM_ID, plain.accounts[8].pubkey)

        val sgt = SgtProof(usdc, creator)
        val seeker = KinProgram.joinCircle(other, circle, sgt)
        assertEquals(usdc, seeker.accounts[7].pubkey)
        assertEquals(creator, seeker.accounts[8].pubkey)
    }

    @Test
    fun collectInstructionOnlyTargetsTheCirclesOwnAccounts() {
        val circle = KinProgram.decodeCircle(creator, circleBytes(false, false, listOf(0, 1, 2)))
        val ix = KinProgram.collect(creator, circle, other)
        assertEquals(8, ix.accounts.size)
        assertTrue("the caller only signs", ix.accounts[0].isSigner && !ix.accounts[0].isWritable)
        assertEquals(KinProgram.memberPda(circle.address, other), ix.accounts[2].pubkey)
        assertEquals(KinProgram.associatedTokenAddress(other, circle.mint), ix.accounts[4].pubkey)
        assertEquals(KinProgram.vaultPda(circle.address), ix.accounts[5].pubkey)
        assertEquals(KinProgram.AUTOPAY, ix.accounts[6].pubkey)
        assertTrue("the delegate is never a signer", !ix.accounts[6].isSigner)
    }

    @Test
    fun transactionMessageLayout() {
        val ix = KinProgram.createCircle(creator, usdc, 1, "Family", 100, 200, 60, 30, 3, 0, false, false, PublicKey.DEFAULT)
        val tx = Transaction.buildUnsigned(creator, ByteArray(32) { 1 }, listOf(ix))
        assertEquals("one signer slot", 1, tx[0].toInt())
        assertTrue("signature slot is empty", tx.copyOfRange(1, 65).all { it == 0.toByte() })
        assertEquals("header: 1 required signature", 1, tx[65].toInt())
        assertEquals("header: 0 readonly signed", 0, tx[66].toInt())
        assertEquals("fee payer is the first account key", creator, PublicKey(tx.copyOfRange(69, 101)))
    }

    @Test
    fun budgetInstructionsHaveTheRightLayout() {
        val limit = TokenInstructions.computeUnitLimit(300_000)
        assertEquals(2, limit.data[0].toInt())
        assertEquals(5, limit.data.size)
        val price = TokenInstructions.computeUnitPrice(2_000)
        assertEquals(3, price.data[0].toInt())
        assertEquals(9, price.data.size)
        assertTrue(limit.accounts.isEmpty() && price.accounts.isEmpty())
    }

    @Test
    fun approveAndRevokeMatchTheSplTokenLayout() {
        val approve = TokenInstructions.approve(other, KinProgram.AUTOPAY, creator, 250)
        assertEquals(4, approve.data[0].toInt())
        assertEquals(250, approve.data[1].toInt() and 0xff)
        assertEquals(9, approve.data.size)
        assertTrue(approve.accounts[0].isWritable && approve.accounts[2].isSigner)
        val revoke = TokenInstructions.revoke(other, creator)
        assertEquals(5, revoke.data[0].toInt())
        assertEquals(2, revoke.accounts.size)
    }

    @Test
    fun decodesTokenAccountDelegation() {
        val w = BorshWriter().bytes(usdc.bytes).bytes(other.bytes).u64(5_000)
            .u32(1).bytes(KinProgram.AUTOPAY.bytes) // delegate
            .u8(1) // state
            .u32(0).u64(0) // is_native
            .u64(300) // delegated amount
            .bytes(ByteArray(36)) // close authority
        val t = TokenAccountData.decode(w.toByteArray())!!
        assertEquals(other, t.owner)
        assertEquals(5_000L, t.amount)
        assertEquals(KinProgram.AUTOPAY, t.delegate)
        assertEquals(300L, t.delegatedAmount)

        val none = BorshWriter().bytes(usdc.bytes).bytes(other.bytes).u64(1).u32(0).bytes(ByteArray(32)).u8(1)
            .u32(0).u64(0).u64(0).bytes(ByteArray(36))
        assertNull(TokenAccountData.decode(none.toByteArray())!!.delegate)
        assertNull(TokenAccountData.decode(ByteArray(10)))
    }

    @Test
    fun decodesACircleAccountWithItsOrderAndGates() {
        val c = KinProgram.decodeCircle(creator, circleBytes(true, true, listOf(2, 0, 1), ByteArray(32) { 3 }))
        assertEquals(7L, c.circleId)
        assertEquals(CircleStatus.Active, c.status)
        assertEquals(3, c.memberCount)
        assertEquals(200L, c.roundPot)
        assertEquals("Family", c.name)
        assertEquals(1_700_000_060L, c.roundEndTs)
        assertEquals(1_700_000_090L, c.graceEndTs)
        assertTrue(c.randomize && c.seekerOnly)
        assertEquals(other, c.seekerAuthority)
        assertEquals(812345L, c.orderSlot)
        assertEquals(listOf(2, 0, 1), c.payoutOrder.take(3))
        assertEquals("member 2 is paid first", 0, c.roundOf(2))
        assertEquals(2, c.roundOf(1))
    }

    @Test
    fun shuffleMatchesTheJavascriptReference() {
        assertEquals(listOf(5, 2, 0, 6, 3, 1, 4, 7), OrderProof.drawOrder(ByteArray(32) { 1 }, 8))
        assertEquals(listOf(8, 10, 1, 4, 11, 7, 5, 3, 6, 2, 9, 0), OrderProof.drawOrder(ByteArray(32) { it.toByte() }, 12))
        val kin = MessageDigest.getInstance("SHA-256").digest("kin".toByteArray())
        assertEquals("c1d6b0ba57d78fec8689f0877d8053556112c2277d3896f34eb5f6c3e5f230d5", hex(kin))
        assertEquals(listOf(3, 2, 1, 0, 4), OrderProof.drawOrder(kin, 5))
        assertEquals(listOf(0, 1), OrderProof.drawOrder(kin, 2))
    }

    @Test
    fun orderProofAcceptsTheRealOrderAndRejectsATamperedOne() {
        val seed = ByteArray(32) { 1 }
        val real = OrderProof.drawOrder(seed, 8) + List(4) { 0 }
        val honest = KinProgram.decodeCircle(creator, circleBytes(true, false, real, seed).let { patchMembers(it, 8) })
        assertTrue(OrderProof.verifies(honest))

        val tampered = real.toMutableList().also { val t = it[0]; it[0] = it[1]; it[1] = t }
        val forged = KinProgram.decodeCircle(creator, circleBytes(true, false, tampered, seed).let { patchMembers(it, 8) })
        assertFalse("a swapped order must not verify", OrderProof.verifies(forged))
    }

    /** Rewrites memberCount in the encoded circle so the order length matches the test. */
    private fun patchMembers(bytes: ByteArray, n: Int): ByteArray {
        // Layout offsets: 8 disc + 32 + 8 + 32 + 8 + 8 + 8 + 8 = 112 -> maxMembers, memberCount
        val copy = bytes.copyOf()
        copy[112] = n.toByte()
        copy[113] = n.toByte()
        return copy
    }

    @Test
    fun decodesProgramEventsFromLogs() {
        val disc = MessageDigest.getInstance("SHA-256").digest("event:PaidOut".toByteArray()).copyOf(8)
        val payload = BorshWriter().bytes(disc).bytes(creator.bytes).bytes(other.bytes).u8(2).u64(300).u8(1).toByteArray()
        val log = "Program data: " + java.util.Base64.getEncoder().encodeToString(payload)
        val events = KinEvents.fromLogs(listOf("Program log: Instruction: Payout", log, "Program data: AAAA"))
        assertEquals(1, events.size)
        val e = events.single() as KinEvent.PaidOut
        assertEquals(creator, e.circle)
        assertEquals(other, e.recipient)
        assertEquals(2, e.round)
        assertEquals(300L, e.amount)
        assertTrue(e.completed)
    }

    @Test
    fun fundsProofPassesWithASurplusButNotAShortfall() {
        assertTrue(FundsProof(300, 300, 200, 200).ok)
        assertTrue("extra tokens sent to a vault are harmless", FundsProof(350, 300, 200, 200).ok)
        assertFalse(FundsProof(299, 300, 200, 200).ok)
        assertFalse(FundsProof(300, 300, 199, 200).ok)
    }

    @Test
    fun reliabilityScore() {
        assertNull(ScoreData(PublicKey.DEFAULT, 0, 0, 0, 0, 0, 0).reliabilityPercent)
        assertEquals(75, ScoreData(PublicKey.DEFAULT, 3, 0, 1, 0, 0, 0).reliabilityPercent)
        assertEquals(100, ScoreData(PublicKey.DEFAULT, 5, 0, 0, 0, 0, 0).reliabilityPercent)
    }
}
