package app.kin.solana

import java.math.BigInteger

object Base58 {
    private const val ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
    private val BASE = BigInteger.valueOf(58)
    private val INDEXES = IntArray(128) { -1 }.also { t -> ALPHABET.forEachIndexed { i, c -> t[c.code] = i } }

    fun encode(input: ByteArray): String {
        if (input.isEmpty()) return ""
        val zeros = input.takeWhile { it == 0.toByte() }.size
        var n = BigInteger(1, input)
        val sb = StringBuilder()
        while (n.signum() > 0) {
            val qr = n.divideAndRemainder(BASE)
            sb.append(ALPHABET[qr[1].toInt()])
            n = qr[0]
        }
        repeat(zeros) { sb.append('1') }
        return sb.reverse().toString()
    }

    fun decode(input: String): ByteArray {
        if (input.isEmpty()) return ByteArray(0)
        var n = BigInteger.ZERO
        for (c in input) {
            val d = if (c.code < 128) INDEXES[c.code] else -1
            require(d >= 0) { "Invalid base58 character '$c'" }
            n = n.multiply(BASE).add(BigInteger.valueOf(d.toLong()))
        }
        val bytes = n.toByteArray().let { if (it.size > 1 && it[0] == 0.toByte()) it.copyOfRange(1, it.size) else it }
        val zeros = input.takeWhile { it == '1' }.length
        val body = if (n.signum() == 0) ByteArray(0) else bytes
        return ByteArray(zeros) + body
    }
}
