package app.kin.watch

import android.content.Context
import app.kin.solana.PublicKey

/**
 * Small local settings. Holds only public information: the connected wallet address, whether reminders
 * are on, and which alerts were already shown. No keys, no secrets.
 */
class KinPrefs(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("kin", Context.MODE_PRIVATE)

    var wallet: PublicKey?
        get() = prefs.getString(WALLET, null)?.let { runCatching { PublicKey.fromBase58(it) }.getOrNull() }
        set(value) = prefs.edit().apply { if (value == null) remove(WALLET) else putString(WALLET, value.toBase58()) }.apply()

    var remindersEnabled: Boolean
        get() = prefs.getBoolean(REMINDERS, true)
        set(value) = prefs.edit().putBoolean(REMINDERS, value).apply()

    var askedForNotifications: Boolean
        get() = prefs.getBoolean(ASKED, false)
        set(value) = prefs.edit().putBoolean(ASKED, value).apply()

    private fun shown(): List<String> = prefs.getString(SHOWN, "").orEmpty().split('\n').filter { it.isNotEmpty() }

    fun wasShown(key: String): Boolean = shown().contains(key)

    /**
     * Remembers an alert, oldest first, and keeps only the most recent entries so this never grows
     * without bound. An ordered string is used because a StringSet would lose the order.
     */
    fun markShown(key: String) {
        val kept = (shown() - key + key).takeLast(MAX_REMEMBERED)
        prefs.edit().putString(SHOWN, kept.joinToString("\n")).apply()
    }

    fun clearShown() = prefs.edit().remove(SHOWN).apply()

    private companion object {
        const val WALLET = "wallet"
        const val REMINDERS = "reminders"
        const val ASKED = "asked_notifications"
        const val SHOWN = "shown_alerts"
        const val MAX_REMEMBERED = 200
    }
}
