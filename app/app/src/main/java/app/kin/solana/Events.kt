package app.kin.solana

import java.util.Base64

/** Events the Kin program emits. Decoded from transaction logs, so history is verifiable from chain data alone. */
sealed interface KinEvent {
    val circle: PublicKey

    data class CircleCreated(override val circle: PublicKey, val creator: PublicKey, val contribution: Long, val maxMembers: Int) : KinEvent
    data class MemberJoined(override val circle: PublicKey, val wallet: PublicKey, val index: Int, val bond: Long, val started: Boolean) : KinEvent
    data class OrderDrawn(override val circle: PublicKey, val randomized: Boolean, val slot: Long, val order: List<Int>) : KinEvent
    data class Contributed(override val circle: PublicKey, val wallet: PublicKey, val round: Int, val amount: Long, val onTime: Boolean, val autopay: Boolean) : KinEvent
    data class MissCovered(override val circle: PublicKey, val wallet: PublicKey, val round: Int, val covered: Long, val shortfall: Long) : KinEvent
    data class PaidOut(override val circle: PublicKey, val recipient: PublicKey, val round: Int, val amount: Long, val completed: Boolean) : KinEvent
    data class BondReturned(override val circle: PublicKey, val wallet: PublicKey, val amount: Long, val refundedOpen: Boolean) : KinEvent
}

object KinEvents {
    private const val PREFIX = "Program data: "

    private fun disc(name: String) =
        java.security.MessageDigest.getInstance("SHA-256").digest("event:$name".toByteArray()).copyOf(8).toList()

    private val CREATED = disc("CircleCreated")
    private val JOINED = disc("MemberJoined")
    private val DRAWN = disc("OrderDrawn")
    private val CONTRIBUTED = disc("Contributed")
    private val COVERED = disc("MissCovered")
    private val PAID = disc("PaidOut")
    private val RETURNED = disc("BondReturned")

    /** Extracts every Kin event from a transaction's log messages. */
    fun fromLogs(logs: List<String>): List<KinEvent> =
        logs.mapNotNull { line ->
            if (!line.startsWith(PREFIX)) return@mapNotNull null
            val bytes = runCatching { Base64.getDecoder().decode(line.removePrefix(PREFIX).trim()) }.getOrNull()
            bytes?.let { runCatching { decode(it) }.getOrNull() }
        }

    fun decode(data: ByteArray): KinEvent? {
        if (data.size < 8) return null
        val head = data.copyOf(8).toList()
        val r = BorshReader(data).skip(8)
        return when (head) {
            CREATED -> {
                val circle = r.pubkey(); val creator = r.pubkey(); r.pubkey()
                val contribution = r.u64(); r.u64(); r.i64(); r.i64()
                KinEvent.CircleCreated(circle, creator, contribution, r.u8())
            }
            JOINED -> KinEvent.MemberJoined(r.pubkey(), r.pubkey(), r.u8(), r.u64(), r.bool())
            DRAWN -> {
                val circle = r.pubkey(); val randomized = r.bool(); val slot = r.u64()
                r.skip(32) // seed
                KinEvent.OrderDrawn(circle, randomized, slot, List(12) { r.u8() })
            }
            CONTRIBUTED -> KinEvent.Contributed(r.pubkey(), r.pubkey(), r.u8(), r.u64(), r.bool(), r.bool())
            COVERED -> KinEvent.MissCovered(r.pubkey(), r.pubkey(), r.u8(), r.u64(), r.u64())
            PAID -> KinEvent.PaidOut(r.pubkey(), r.pubkey(), r.u8(), r.u64(), r.bool())
            RETURNED -> KinEvent.BondReturned(r.pubkey(), r.pubkey(), r.u64(), r.bool())
            else -> null
        }
    }
}
