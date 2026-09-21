package app.kin.watch

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.kin.Config
import app.kin.solana.CircleStatus
import app.kin.solana.KinRepository
import app.kin.solana.SolanaRpc
import java.util.concurrent.TimeUnit
import kotlin.coroutines.cancellation.CancellationException

/**
 * Checks the chain in the background and tells the member when something needs them.
 * It only reads. Signing always happens in the wallet, on the member's tap.
 */
class CircleWatchWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val prefs = KinPrefs(applicationContext)
        val wallet = prefs.wallet ?: return Result.success()
        if (!prefs.remindersEnabled || !Notifier.canPost(applicationContext)) return Result.success()

        return try {
            val repo = KinRepository(SolanaRpc(Config.RPC_URL))
            val now = System.currentTimeMillis() / 1000
            repo.circlesFor(wallet).filter { it.status == CircleStatus.Active }.forEach { circle ->
                val members = repo.members(circle.address)
                val allowances = repo.allowances(members.map { it.wallet }, circle.mint)
                AlertRules.evaluate(wallet, circle, members, allowances, now)
                    .filterNot { prefs.wasShown(it.key) }
                    .forEach { alert -> if (Notifier.post(applicationContext, alert)) prefs.markShown(alert.key) }
            }
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Network or RPC trouble is normal on a phone. Try again later.
            Result.retry()
        }
    }

    companion object {
        private const val WORK_NAME = "kin-circle-watch"

        /** WorkManager's minimum period is 15 minutes. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<CircleWatchWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }
    }
}
