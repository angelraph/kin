package app.kin.ui

import app.kin.solana.CircleData
import app.kin.solana.CircleStatus
import app.kin.solana.MemberData

enum class RoundState { Done, Current, Upcoming }

/** One line of the payout calendar. [memberIndex] is null while the order is not drawn yet. */
data class RoundRow(
    val round: Int,
    val memberIndex: Int?,
    val state: RoundState,
    /** Earliest time the round can close, in unix seconds. Null before the circle starts. */
    val opensAt: Long?,
)

object Schedule {
    /**
     * The full payout calendar. A round starts when the previous payout is sent, so dates for future rounds
     * are the earliest possible ones and can only move later.
     */
    fun rows(c: CircleData): List<RoundRow> {
        val total = c.maxMembers
        return (0 until total).map { r ->
            val order = if (c.status == CircleStatus.Open) null else c.payoutOrder.getOrNull(r)
            val state = when {
                c.status == CircleStatus.Completed || (c.status == CircleStatus.Active && r < c.currentRound) -> RoundState.Done
                c.status == CircleStatus.Active && r == c.currentRound -> RoundState.Current
                else -> RoundState.Upcoming
            }
            val opens = if (c.status == CircleStatus.Active && r >= c.currentRound) c.roundEndTs + (r - c.currentRound) * c.periodSecs else null
            RoundRow(r, order, state, opens)
        }
    }

    /** What a member's position in the order means for them, in plain words. Null until the order is set. */
    fun positionNote(c: CircleData, me: MemberData): String? {
        val round = c.roundOf(me.index) ?: return null
        val n = c.maxMembers
        val paidByThen = c.contribution * (round + 1)
        val pot = c.contribution * n
        val advance = pot - paidByThen
        return when {
            n < 2 -> null
            round == 0 -> "You are paid first, in round 1. You collect ${formatAmount(pot)} after paying ${formatAmount(c.contribution)}, an interest free advance of ${formatAmount(advance)} that you repay over the next ${n - 1} rounds."
            round == n - 1 -> "You are paid last, in round $n. Every payment you make builds up to one lump sum of ${formatAmount(pot)}. It works as a savings goal."
            else -> "You are paid in round ${round + 1}. By then you will have paid ${formatAmount(paidByThen)} and you collect ${formatAmount(pot)}, an advance of ${formatAmount(advance)} that you repay afterwards."
        }
    }
}
