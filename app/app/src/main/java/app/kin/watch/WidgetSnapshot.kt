package app.kin.watch

import android.content.Context
import app.kin.Config
import app.kin.solana.Allowance
import app.kin.solana.CircleData
import app.kin.solana.CircleStatus
import app.kin.solana.MemberData
import app.kin.solana.PublicKey
import app.kin.ui.formatAmount
import app.kin.ui.formatDuration

/**
 * What the home-screen widget shows: one circle, the thing most worth knowing about it right now,
 * and a paid/total count for the progress bar. Kept tiny and string-only so it survives a trip
 * through SharedPreferences into a process that may not even have the app's classes warmed up.
 */
data class WidgetSnapshot(
    val circleAddress: String? = null,
    val circleName: String = "Kin",
    val headline: String = "Open Kin to start a circle",
    val detail: String = "",
    val resolved: Int = 0,
    val total: Int = 0,
    val showProgress: Boolean = false,
) {
    companion object {
        val EMPTY = WidgetSnapshot()

        /** Shown while a wallet is connected but nothing else is known yet, e.g. right after a fresh install. */
        fun connected() = WidgetSnapshot(headline = "Loading your circles")
    }
}

/**
 * Picks the single most useful thing to show, from just the circle list. Used for an immediate update
 * whenever the app itself has fresh data, without needing every member loaded.
 */
fun aggregateSnapshot(circles: List<CircleData>, now: Long): WidgetSnapshot {
    val active = circles.filter { it.status == CircleStatus.Active }
    val featured = active.minByOrNull { it.roundEndTs } ?: circles.firstOrNull { it.status == CircleStatus.Open }
    if (featured == null) {
        if (circles.isEmpty()) return WidgetSnapshot(headline = "No circles yet", detail = "Open Kin to start one")
        return WidgetSnapshot(headline = "All circles settled", detail = "Nothing due right now")
    }
    val symbol = Config.tokenSymbol(featured.mint)
    val name = featured.name.ifBlank { "Circle" }
    return when {
        featured.status == CircleStatus.Open -> WidgetSnapshot(
            featured.address.toBase58(), name, "Waiting for members",
            "${featured.memberCount} of ${featured.maxMembers} joined", featured.memberCount, featured.maxMembers, true,
        )
        featured.resolvedCount == featured.memberCount && now >= featured.roundEndTs -> WidgetSnapshot(
            featured.address.toBase58(), name, "Payout is ready",
            "${formatAmount(featured.roundPot, symbol = symbol)} can be sent", featured.resolvedCount, featured.memberCount, true,
        )
        now > featured.graceEndTs -> WidgetSnapshot(
            featured.address.toBase58(), name, "Grace window is over",
            "${featured.resolvedCount} of ${featured.memberCount} paid, a bond can cover the rest", featured.resolvedCount, featured.memberCount, true,
        )
        else -> WidgetSnapshot(
            featured.address.toBase58(), name, "Round ${featured.currentRound + 1} of ${featured.maxMembers}",
            "${featured.resolvedCount} of ${featured.memberCount} paid, closes in ${formatDuration(featured.roundEndTs - now)}",
            featured.resolvedCount, featured.memberCount, true,
        )
    }
}

/**
 * The richer, personal version for one circle: what this wallet specifically should do. Returns null
 * when there is nothing to say about this circle, so the caller can fall back to another one.
 */
fun personalSnapshot(
    me: PublicKey,
    circle: CircleData,
    members: List<MemberData>,
    allowances: Map<PublicKey, Allowance>,
    now: Long,
): WidgetSnapshot? {
    if (circle.status != CircleStatus.Active) return null
    val mine = members.firstOrNull { it.wallet == me } ?: return null
    val name = circle.name.ifBlank { "Circle" }
    val symbol = Config.tokenSymbol(circle.mint)
    val address = circle.address.toBase58()
    val round = circle.currentRound
    val iOwe = mine.roundsResolved <= round
    val myAllowance = allowances[me]
    val onAutopay = myAllowance != null && myAllowance.delegatedToKin && myAllowance.amount >= circle.contribution

    if (circle.resolvedCount == circle.memberCount && now >= circle.roundEndTs) {
        val recipient = members.firstOrNull { it.index == circle.payoutOrder[round] }
        if (recipient?.wallet == me) {
            return WidgetSnapshot(address, name, "Your pot is ready", "${formatAmount(circle.roundPot, symbol = symbol)} is waiting", circle.resolvedCount, circle.memberCount, true)
        }
    }
    if (iOwe && !onAutopay && now <= circle.graceEndTs) {
        return WidgetSnapshot(
            address, name, "Pay ${formatAmount(circle.contribution, symbol = symbol)}",
            "${formatDuration(circle.roundEndTs.coerceAtLeast(now) - now)} left this round", circle.resolvedCount, circle.memberCount, true,
        )
    }
    if (AlertRules.dueForAutopay(circle, members, allowances, now).isNotEmpty()) {
        return WidgetSnapshot(address, name, "Autopay is ready", "Collect this round's dues in one tap", circle.resolvedCount, circle.memberCount, true)
    }
    return WidgetSnapshot(
        address, name, "Round ${round + 1} of ${circle.maxMembers}",
        "${circle.resolvedCount} of ${circle.memberCount} paid", circle.resolvedCount, circle.memberCount, true,
    )
}

/** Ranks personal snapshots so the most actionable one wins when a wallet is in several circles. */
private fun WidgetSnapshot.urgency(): Int = when {
    headline.startsWith("Pay") -> 0
    headline.startsWith("Your pot") -> 1
    headline.startsWith("Autopay") -> 2
    else -> 3
}

fun bestOf(snapshots: List<WidgetSnapshot>): WidgetSnapshot? = snapshots.minByOrNull { it.urgency() }

/** Reads and writes the one snapshot the widget renders. Plain strings and ints, no serialization needed. */
object KinWidgetStore {
    private const val PREFS = "kin_widget"
    private const val ADDR = "address"
    private const val CIRCLE = "circle"
    private const val HEADLINE = "headline"
    private const val DETAIL = "detail"
    private const val RESOLVED = "resolved"
    private const val TOTAL = "total"
    private const val SHOW_PROGRESS = "show_progress"

    fun save(context: Context, snapshot: WidgetSnapshot) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(ADDR, snapshot.circleAddress)
            .putString(CIRCLE, snapshot.circleName)
            .putString(HEADLINE, snapshot.headline)
            .putString(DETAIL, snapshot.detail)
            .putInt(RESOLVED, snapshot.resolved)
            .putInt(TOTAL, snapshot.total)
            .putBoolean(SHOW_PROGRESS, snapshot.showProgress)
            .apply()
    }

    fun load(context: Context): WidgetSnapshot {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!p.contains(HEADLINE)) return WidgetSnapshot.EMPTY
        return WidgetSnapshot(
            circleAddress = p.getString(ADDR, null),
            circleName = p.getString(CIRCLE, "Kin") ?: "Kin",
            headline = p.getString(HEADLINE, "") ?: "",
            detail = p.getString(DETAIL, "") ?: "",
            resolved = p.getInt(RESOLVED, 0),
            total = p.getInt(TOTAL, 0),
            showProgress = p.getBoolean(SHOW_PROGRESS, false),
        )
    }

    fun clear(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
