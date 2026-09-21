package app.kin.solana

import java.math.BigInteger
import java.security.MessageDigest

/** A 32-byte Solana public key / address. */
class PublicKey(val bytes: ByteArray) {
    init {
        require(bytes.size == 32) { "Public key must be 32 bytes, was ${bytes.size}" }
    }

    fun toBase58(): String = Base58.encode(bytes)

    override fun equals(other: Any?) = other is PublicKey && bytes.contentEquals(other.bytes)
    override fun hashCode() = bytes.contentHashCode()
    override fun toString() = toBase58()

    companion object {
        fun fromBase58(s: String) = PublicKey(Base58.decode(s))
        val DEFAULT = PublicKey(ByteArray(32))
    }
}

/** Program-derived address search, matching Solana's `find_program_address`. */
object Pda {
    private val P: BigInteger = BigInteger.valueOf(2).pow(255).subtract(BigInteger.valueOf(19))
    private val D: BigInteger =
        BigInteger.valueOf(-121665).multiply(BigInteger.valueOf(121666).modInverse(P)).mod(P)

    fun find(seeds: List<ByteArray>, programId: PublicKey): Pair<PublicKey, Int> {
        require(seeds.all { it.size <= 32 }) { "Seed longer than 32 bytes" }
        for (bump in 255 downTo 0) {
            val sha = MessageDigest.getInstance("SHA-256")
            seeds.forEach { sha.update(it) }
            sha.update(byteArrayOf(bump.toByte()))
            sha.update(programId.bytes)
            sha.update("ProgramDerivedAddress".toByteArray())
            val hash = sha.digest()
            if (!isOnCurve(hash)) return PublicKey(hash) to bump
        }
        error("No viable bump seed")
    }

    /** True if the 32 bytes decode to a valid ed25519 point (so cannot be a PDA). */
    fun isOnCurve(bytes: ByteArray): Boolean {
        val le = bytes.copyOf()
        le[31] = (le[31].toInt() and 0x7f).toByte() // drop the sign bit
        val y = BigInteger(1, le.reversedArray()).mod(P)
        val y2 = y.multiply(y).mod(P)
        val u = y2.subtract(BigInteger.ONE).mod(P)
        val v = D.multiply(y2).add(BigInteger.ONE).mod(P)
        val x2 = u.multiply(v.modInverse(P)).mod(P)
        if (x2.signum() == 0) return true
        return x2.modPow(P.subtract(BigInteger.ONE).shiftRight(1), P) == BigInteger.ONE
    }
}
