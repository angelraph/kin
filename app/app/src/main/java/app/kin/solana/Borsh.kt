package app.kin.solana

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class BorshWriter {
    private val out = ByteArrayOutputStream()

    fun u8(v: Int) = apply { out.write(v and 0xff) }
    fun u32(v: Long) = apply { out.write(ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN).putInt(v.toInt()).array()) }
    fun u64(v: Long) = apply { out.write(ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(v).array()) }
    fun i64(v: Long) = u64(v)
    fun string(s: String) = apply {
        val b = s.toByteArray(Charsets.UTF_8)
        u32(b.size.toLong())
        out.write(b)
    }
    fun bytes(b: ByteArray) = apply { out.write(b) }
    fun toByteArray(): ByteArray = out.toByteArray()
}

class BorshReader(data: ByteArray) {
    private val buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN)

    fun skip(n: Int) = apply { buf.position(buf.position() + n) }
    fun u8(): Int = buf.get().toInt() and 0xff
    fun bool(): Boolean = u8() != 0
    fun u16(): Int = buf.short.toInt() and 0xffff
    fun u32(): Long = buf.int.toLong() and 0xffffffffL
    fun u64(): Long = buf.long
    fun i64(): Long = buf.long
    fun pubkey(): PublicKey = PublicKey(ByteArray(32).also { buf.get(it) })
    fun string(): String {
        val len = u32().toInt()
        val b = ByteArray(len)
        buf.get(b)
        return String(b, Charsets.UTF_8)
    }
}
