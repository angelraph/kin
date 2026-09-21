package app.kin.solana

import java.io.ByteArrayOutputStream

class AccountMeta(val pubkey: PublicKey, val isSigner: Boolean, val isWritable: Boolean)

class Instruction(val programId: PublicKey, val accounts: List<AccountMeta>, val data: ByteArray)

/** Serializes legacy Solana transactions. The wallet (via Mobile Wallet Adapter) fills in signatures. */
object Transaction {
    fun buildUnsigned(feePayer: PublicKey, recentBlockhash: ByteArray, instructions: List<Instruction>): ByteArray {
        require(recentBlockhash.size == 32) { "Blockhash must be 32 bytes" }

        // Merge duplicate accounts, keeping the strongest flags; fee payer is always first.
        val order = LinkedHashMap<PublicKey, BooleanArray>() // [isSigner, isWritable]
        fun touch(k: PublicKey, signer: Boolean, writable: Boolean) {
            val f = order.getOrPut(k) { booleanArrayOf(false, false) }
            f[0] = f[0] || signer
            f[1] = f[1] || writable
        }
        touch(feePayer, signer = true, writable = true)
        instructions.forEach { ix ->
            ix.accounts.forEach { touch(it.pubkey, it.isSigner, it.isWritable) }
            touch(ix.programId, signer = false, writable = false)
        }

        val signerWritable = order.filter { it.value[0] && it.value[1] }.keys.toList()
        val signerReadonly = order.filter { it.value[0] && !it.value[1] }.keys.toList()
        val otherWritable = order.filter { !it.value[0] && it.value[1] }.keys.toList()
        val otherReadonly = order.filter { !it.value[0] && !it.value[1] }.keys.toList()
        val keys = signerWritable + signerReadonly + otherWritable + otherReadonly
        val index = keys.withIndex().associate { it.value to it.index }

        val msg = ByteArrayOutputStream()
        msg.write(signerWritable.size + signerReadonly.size)
        msg.write(signerReadonly.size)
        msg.write(otherReadonly.size)
        writeShortVec(msg, keys.size)
        keys.forEach { msg.write(it.bytes) }
        msg.write(recentBlockhash)
        writeShortVec(msg, instructions.size)
        instructions.forEach { ix ->
            msg.write(index.getValue(ix.programId))
            writeShortVec(msg, ix.accounts.size)
            ix.accounts.forEach { msg.write(index.getValue(it.pubkey)) }
            writeShortVec(msg, ix.data.size)
            msg.write(ix.data)
        }

        val numSigs = signerWritable.size + signerReadonly.size
        val tx = ByteArrayOutputStream()
        writeShortVec(tx, numSigs)
        tx.write(ByteArray(64 * numSigs))
        tx.write(msg.toByteArray())
        return tx.toByteArray()
    }

    private fun writeShortVec(out: ByteArrayOutputStream, value: Int) {
        var v = value
        while (true) {
            var elem = v and 0x7f
            v = v ushr 7
            if (v == 0) {
                out.write(elem)
                return
            }
            elem = elem or 0x80
            out.write(elem)
        }
    }
}
