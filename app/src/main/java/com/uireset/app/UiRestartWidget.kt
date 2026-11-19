package com.uireset.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.Toast
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

class UiRestartWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { manager.updateAppWidget(it, buildViews(context)) }
    }

    private fun buildViews(context: Context): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_restart)
        val intent = Intent(context, RestartReceiver::class.java).setAction(RestartReceiver.ACTION)
        val pending = PendingIntent.getBroadcast(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        views.setOnClickPendingIntent(R.id.widget_root, pending)
        return views
    }
}

class RestartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        val app = context.applicationContext
        val pending = goAsync()
        MainScope().launch {
            Toast.makeText(app, "Reiniciando SystemUI…", Toast.LENGTH_SHORT).show()
            val result = ShizukuHelper.execute(app, RestartAction.SYSTEM_UI)
            result.onFailure {
                Toast.makeText(
                    app,
                    "No se pudo reiniciar: ${it.message}. Abre la app para revisar Shizuku.",
                    Toast.LENGTH_LONG
                ).show()
            }
            pending.finish()
        }
    }

    companion object {
        const val ACTION = "com.uireset.app.RESTART_SYSTEM_UI"
    }
}
