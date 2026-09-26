package app.kin.ui

/**
 * A scripted, local run of a five member circle so anyone can see every rule play out without a wallet
 * or test funds. Nothing here touches the network. The script mirrors what the on-chain program does.
 */
data class SimMember(
    val name: String,
    val onTime: Int = 0,
    val missed: Int = 0,
    val bondLeft: Int = BOND,
    val paidThisRound: Boolean = false,
    val received: Int = 0,
) {
    val scorePercent: Int? get() = (onTime + missed).takeIf { it > 0 }?.let { onTime * 100 / it }
}

data class SimEvent(val round: Int, val text: String, val tone: Tone = Tone.Normal) {
    enum class Tone { Normal, Good, Bad }
}

data class SimState(
    val round: Int = 0,
    val members: List<SimMember> = NAMES.map { SimMember(it) },
    val events: List<SimEvent> = emptyList(),
) {
    val done: Boolean get() = round >= ROUNDS
    val recipient: String? get() = if (done) null else NAMES[ORDER[round]]
    val pot: Int get() = PAY * ROUNDS
}

const val PAY = 20
const val BOND = 40
const val ROUNDS = 5
val NAMES = listOf("You", "Amara", "Diego", "Mei", "Sam")

/** Payout order, as it would be drawn on-chain. Index into [NAMES]. */
val ORDER = listOf(1, 0, 3, 4, 2)

object Simulation {
    /** Plays the next round in full and returns the new state. Does nothing once the circle is complete. */
    fun playRound(s: SimState): SimState {
        if (s.done) return s
        val r = s.round
        val events = mutableListOf<SimEvent>()
        var members = s.members.map { it.copy(paidThisRound = false) }

        val misser = if (r == 1) 2 else -1
        val autopayer = if (r == 2) 4 else -1

        members = members.mapIndexed { i, m ->
            when {
                i == misser -> m
                else -> {
                    events += SimEvent(
                        r,
                        if (i == autopayer) "${m.name} paid $PAY by autopay, without opening the app" else "${m.name} paid $PAY",
                        SimEvent.Tone.Good,
                    )
                    m.copy(onTime = m.onTime + 1, paidThisRound = true)
                }
            }
        }
        if (misser >= 0) {
            val m = members[misser]
            events += SimEvent(r, "${m.name} did not pay before the grace window closed", SimEvent.Tone.Bad)
            events += SimEvent(r, "Anyone can call cover. ${m.name}'s bond paid $PAY into the pot", SimEvent.Tone.Bad)
            members = members.mapIndexed { i, x ->
                if (i == misser) x.copy(missed = x.missed + 1, bondLeft = x.bondLeft - PAY, paidThisRound = true) else x
            }
            events += SimEvent(r, "${m.name}'s Kin Score dropped to ${members[misser].scorePercent}%", SimEvent.Tone.Bad)
        }

        val winner = ORDER[r]
        events += SimEvent(r, "${NAMES[winner]} received the pot of ${PAY * members.size}", SimEvent.Tone.Good)
        members = members.mapIndexed { i, x -> if (i == winner) x.copy(received = x.received + PAY * members.size) else x }

        val next = r + 1
        if (next == ROUNDS) {
            events += SimEvent(r, "Circle complete. Every member can now claim back what is left of their bond", SimEvent.Tone.Good)
        }
        return s.copy(round = next, members = members, events = s.events + events)
    }
}
