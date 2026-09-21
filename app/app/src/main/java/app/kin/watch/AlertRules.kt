package app.kin.watch

import app.kin.solana.Allowance
import app.kin.solana.CircleData
import app.kin.solana.CircleStatus
import app.kin.solana.MemberData
import app.kin.solana.PublicKey
import app.kin.ui.formatAmount
import app.kin.ui.formatDuration
import app.kin.ui.shortKey

enum class AlertKind { PayOpen, PayClosing, AutopayDue, CoverNeeded, PayoutReady }

/** Something worth telling the user about. [key] is stable, so the same thing is never announced twice. */
data class Alert(
    val key: String,
    val circle: PublicKey,
    val kind: AlertKind,
    val title: String,
    val body: String,
)

/**
 * Decides what a member should be told about right now. Pure and read-only: it looks at chain state
 * and the clock, and never touches keys or money.
 */
object AlertRules {
    /** Once this share of the period has passed, a reminder becomes "closing soon". */
    private const val CLOSING_SHARE = 0.75

    /** Members who owe this round, approved enough autopay, and can still be collected from. */
    fun dueForAutopay(
        circle: CircleData,
        members: List<MemberData>,
        allowances: Map<PublicKey, Allowance>,
        now: Long,
    ): List<MemberData> {
        if (circle.status != CircleStatus.Active || now > circle.graceEndTs) return emptyList()
        return members.filter { m ->
            val a = allowances[m.wallet]
            m.roundsResolved <= circle.currentRound && a != null && a.delegatedToKin && a.amount >= circle.contribution
        }
    }

    fun evaluate(
        me: PublicKey,
        circle: CircleData,
        members: List<MemberData>,
        allowances: Map<PublicKey, Allowance>,
        now: Long,
    ): List<Alert> {
        if (circle.status != CircleStatus.Active) return emptyList()
        val mine = members.firstOrNull { it.wallet == me } ?: return emptyList()
        val name = circle.name.ifBlank { "Your circle" }
        val round = circle.currentRound
        val alerts = mutableListOf<Alert>()
        fun alert(kind: AlertKind, title: String, body: String) =
            alerts.add(Alert("${circle.address}:$round:${kind.name}", circle.address, kind, title, body))

        val iOwe = mine.roundsResolved <= round
        val myAllowance = allowances[me]
        val onAutopay = myAllowance != null && myAllowance.delegatedToKin && myAllowance.amount >= circle.contribution

        if (iOwe && !onAutopay && now <= circle.graceEndTs) {
            val closingAt = circle.roundStartTs + (circle.periodSecs * CLOSING_SHARE).toLong()
            if (now < closingAt) {
                alert(
                    AlertKind.PayOpen,
                    "Round ${round + 1} is open",
                    "$name: pay ${formatAmount(circle.contribution)}. ${formatDuration(circle.roundEndTs - now)} left.",
                )
            } else {
                val left = if (now <= circle.roundEndTs) circle.roundEndTs - now else circle.graceEndTs - now
                alert(
                    AlertKind.PayClosing,
                    "Payment closing soon",
                    "$name: ${formatDuration(left)} left to pay ${formatAmount(circle.contribution)} before bonds step in.",
                )
            }
        }

        val due = dueForAutopay(circle, members, allowances, now)
        if (due.isNotEmpty()) {
            val n = due.size
            alert(
                AlertKind.AutopayDue,
                "Autopay is ready",
                "$name: $n payment${if (n > 1) "s" else ""} can be collected now. One tap covers everyone.",
            )
        }

        val late = members.filter { it.roundsResolved <= round }
        if (now > circle.graceEndTs && late.isNotEmpty()) {
            val who = late.first().wallet
            alert(
                AlertKind.CoverNeeded,
                "A payment was missed",
                "$name: ${if (who == me) "your" else shortKey(who) + "'s"} bond can cover the missing share so the round can finish.",
            )
        }

        if (circle.resolvedCount == circle.memberCount && now >= circle.roundEndTs) {
            val recipientIndex = circle.payoutOrder[round]
            val recipient = members.firstOrNull { it.index == recipientIndex }
            if (recipient != null) {
                if (recipient.wallet == me) {
                    alert(AlertKind.PayoutReady, "Your pot is ready", "$name: ${formatAmount(circle.roundPot)} is waiting. Tap to send it to your wallet.")
                } else {
                    alert(AlertKind.PayoutReady, "Payout is ready", "$name: the pot can be sent to ${shortKey(recipient.wallet)}.")
                }
            }
        }
        return alerts
    }
}
