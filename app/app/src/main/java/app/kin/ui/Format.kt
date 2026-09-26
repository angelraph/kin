package app.kin.ui

import app.kin.Config
import app.kin.solana.PublicKey
import java.math.BigDecimal
import java.math.RoundingMode

/** Base units -> "12.5 USDC". Amounts under 0.01 keep full precision so they never read as zero. */
fun formatAmount(baseUnits: Long, withSymbol: Boolean = true): String {
    val exact = BigDecimal(baseUnits).movePointLeft(Config.TOKEN_DECIMALS)
    val scale = if (baseUnits > 0 && exact < BigDecimal("0.01")) Config.TOKEN_DECIMALS else 2
    val v = exact.setScale(scale, RoundingMode.DOWN)
    val s = v.stripTrailingZeros().let { if (it.scale() < 0) it.setScale(0) else it }.toPlainString()
    return if (withSymbol) "$s ${Config.TOKEN_SYMBOL}" else s
}

/** "12.5" -> base units, or null if it is not a positive number. */
fun parseAmount(text: String): Long? = runCatching {
    val v = BigDecimal(text.trim()).movePointRight(Config.TOKEN_DECIMALS)
    if (v.signum() <= 0) null else v.setScale(0, RoundingMode.DOWN).longValueExact()
}.getOrNull()

fun shortKey(k: PublicKey): String = k.toBase58().let { "${it.take(4)}…${it.takeLast(4)}" }

fun formatDuration(totalSeconds: Long): String {
    var s = totalSeconds.coerceAtLeast(0)
    val d = s / 86400; s %= 86400
    val h = s / 3600; s %= 3600
    val m = s / 60; s %= 60
    return when {
        d > 0 -> "${d}d ${h}h"
        h > 0 -> "${h}h ${m}m"
        m > 0 -> "${m}m ${s}s"
        else -> "${s}s"
    }
}

/** How long a whole circle runs, e.g. "5 weeks", given how many rounds it has and how long each lasts. */
fun spanLabel(rounds: Int, periodSecs: Long): String {
    val unit = when {
        periodSecs % 2_592_000L == 0L -> "month"
        periodSecs % 604_800L == 0L -> "week"
        periodSecs % 86_400L == 0L -> "day"
        periodSecs % 60L == 0L -> "minute"
        else -> null
    }
    if (unit == null) return formatDuration(rounds * periodSecs)
    val divisor = when (unit) {
        "month" -> 2_592_000L
        "week" -> 604_800L
        "day" -> 86_400L
        else -> 60L
    }
    val count = rounds * (periodSecs / divisor)
    return if (count == 1L) "1 $unit" else "$count ${unit}s"
}

fun periodLabel(seconds: Long): String = when {
    seconds % 2_592_000L == 0L -> "monthly"
    seconds % 604_800L == 0L -> "weekly"
    seconds % 86_400L == 0L -> "daily"
    seconds < 3600 -> "every ${seconds / 60}m"
    else -> "every ${formatDuration(seconds)}"
}
