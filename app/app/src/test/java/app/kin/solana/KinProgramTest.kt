package app.kin.solana

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Expected values were produced independently with @solana/web3.js and @solana/spl-token
 * against the deployed program ID, so these tests check the Kotlin client against a second implementation.
 */
class KinProgramTest {
    private val creator = PublicKey.fromBase58("3LpS3ZUAS1xuQxhLygtGgti6cB7uj4dQQrYnyjg3WuwB")
    private val other = PublicKey.fromBase58("GmaDrppBC7P5ARKV8g3djiwP89vz1jLK23V2GBjuAEGB")
    private val usdc = PublicKey.fromBase58("EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v")

    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }

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
    fun createCircleInstructionLayout() {
        val ix = KinProgram.createCircle(creator, usdc, 1, "Family", 100, 200, 60, 30, 3, 0)
        assertEquals(7, ix.accounts.size)
        assertTrue(ix.accounts[0].isSigner && ix.accounts[0].isWritable)
        // 8 disc + 8 id + (4+6) name + 8 + 8 + 8 + 8 + 1 + 4
        assertEquals(8 + 8 + 10 + 8 + 8 + 8 + 8 + 1 + 4, ix.data.size)
    }

    @Test
    fun transactionMessageLayout() {
        val ix = KinProgram.createCircle(creator, usdc, 1, "Family", 100, 200, 60, 30, 3, 0)
        val tx = Transaction.buildUnsigned(creator, ByteArray(32) { 1 }, listOf(ix))
        assertEquals("one signer slot", 1, tx[0].toInt())
        assertTrue("signature slot is empty", tx.copyOfRange(1, 65).all { it == 0.toByte() })
        assertEquals("header: 1 required signature", 1, tx[65].toInt())
        assertEquals("header: 0 readonly signed", 0, tx[66].toInt())
        assertEquals("fee payer is the first account key", creator, PublicKey(tx.copyOfRange(69, 101)))
    }

    @Test
    fun decodesACircleAccount() {
        val w = BorshWriter()
            .bytes(KinProgram.accountDiscriminator("Circle"))
            .bytes(creator.bytes).u64(7).bytes(usdc.bytes)
            .u64(100).u64(200).i64(60).i64(30)
            .u8(3).u8(2).u32(0).u8(1) // maxMembers, memberCount, maxMissed, status=Active
            .u8(1).i64(1_700_000_000).u8(2).u64(200).i64(1_699_999_000)
            .u8(255).u8(254).u8(253).string("Family")
        val c = KinProgram.decodeCircle(creator, w.toByteArray())
        assertEquals(7L, c.circleId)
        assertEquals(CircleStatus.Active, c.status)
        assertEquals(2, c.memberCount)
        assertEquals(200L, c.roundPot)
        assertEquals("Family", c.name)
        assertEquals(1_700_000_060L, c.roundEndTs)
        assertEquals(1_700_000_090L, c.graceEndTs)
    }
}
