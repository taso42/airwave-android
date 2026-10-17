package com.vibex.airwave

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder

class StreamingService : Service() {
    companion object {
        private var nextToken = 0L
        private var wantedToken: Long? = null
        private var onStopped: (() -> Unit)? = null
        private var instance: StreamingService? = null
        fun start(context: Context, simulation: Boolean, stopped: () -> Unit) {
            val token = ++nextToken
            wantedToken = token
            onStopped = stopped
            val intent = Intent(context, StreamingService::class.java)
                .putExtra("simulation", simulation).putExtra("token", token)
            try {
                if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(intent)
                else context.startService(intent)
            } catch (e: Exception) {
                wantedToken = null; onStopped = null
                throw e
            }
        }
        fun stop() {
            val cancelled = wantedToken
            wantedToken = null
            onStopped = null
            // A pending start MUST reach startForeground before it can be stopped.
            instance?.let { if (it.promoted && it.token == cancelled) it.stopSelf() }
        }
        private const val CHANNEL = "live_stream"
    }
    private var token = 0L
    private var promoted = false
    override fun onCreate() { super.onCreate();instance = this }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        token = intent?.getLongExtra("token", 0L) ?: 0L
        val simulation = intent?.getBooleanExtra("simulation", false) == true
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Live streaming", NotificationManager.IMPORTANCE_LOW))
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL) else Notification.Builder(this)
        val notification = builder.setSmallIcon(R.drawable.ic_notification_airwave)
            .setContentTitle(if (simulation) "Airwave simulation active" else "Airwave Bluetooth active")
            .setContentText(if (simulation) "Generating simulated waveforms. Tap to open Airwave." else "Keeping your live connection available. Tap to open Airwave.")
            .setContentIntent(open).setOngoing(true).build()
        if (Build.VERSION.SDK_INT >= 34 && simulation) startForeground(1, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else if (Build.VERSION.SDK_INT >= 29) startForeground(1, notification, if (simulation) 0 else android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        else startForeground(1, notification)
        promoted = true
        if (wantedToken != token) stopSelf(startId)
        return START_NOT_STICKY
    }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() {
        if (instance === this) instance = null
        if (wantedToken == token) {
            val callback = onStopped
            wantedToken = null; onStopped = null
            callback?.invoke()
        }
        super.onDestroy()
    }
}
