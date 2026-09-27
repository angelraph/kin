package app.kin.watch

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import app.kin.MainActivity
import app.kin.R

/**
 * The Kin home-screen widget: one circle, glanceable, updated by whichever of the app or the background
 * watcher last looked at the chain. It never signs anything; tapping it just opens the app.
 */
class KinWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val snapshot = KinWidgetStore.load(context)
        ids.forEach { id -> manager.updateAppWidget(id, buildViews(context, snapshot)) }
    }

    companion object {
        /** Called after the app or the watcher saves a fresh snapshot, to push it onto any placed widgets. */
        fun refresh(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, KinWidgetProvider::class.java))
            if (ids.isEmpty()) return
            val snapshot = KinWidgetStore.load(context)
            ids.forEach { id -> manager.updateAppWidget(id, buildViews(context, snapshot)) }
        }

        private fun buildViews(context: Context, s: WidgetSnapshot): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_kin)
            views.setTextViewText(R.id.widget_circle, s.circleName)
            views.setTextViewText(R.id.widget_headline, s.headline)
            views.setTextViewText(R.id.widget_detail, s.detail)
            if (s.showProgress && s.total > 0) {
                views.setViewVisibility(R.id.widget_progress, android.view.View.VISIBLE)
                views.setProgressBar(R.id.widget_progress, s.total, s.resolved, false)
            } else {
                views.setViewVisibility(R.id.widget_progress, android.view.View.GONE)
            }
            val uri = s.circleAddress?.let { "kin://circle/$it" } ?: "kin://join/"
            val open = Intent(Intent.ACTION_VIEW, Uri.parse(uri), context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            val pending = PendingIntent.getActivity(context, 0, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widget_root, pending)
            return views
        }
    }
}
