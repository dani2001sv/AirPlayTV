package com.airplaytv

import android.app.*
import android.content.Context
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*

class AirPlayService : Service() {

    companion object {
        const val ACTION_STATUS_UPDATE = "com.airplaytv.STATUS_UPDATE"
        const val EXTRA_STATUS = "status"
        const val EXTRA_CLIENT_NAME = "client_name"

        const val CHANNEL_ID = "airplay_service"
        const val NOTIFICATION_ID = 1

        var isRunning = false
            private set
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Referencia al servidor nativo UxPlay (JNI)
    private var nativeServerPtr: Long = 0

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireLocks()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification(getString(R.string.status_waiting)))
        isRunning = true

        val deviceName = AirPlayPreferences.getDeviceName(this)
        startNativeServer(deviceName)

        return START_STICKY // Reiniciar si el sistema lo mata
    }

    private fun startNativeServer(deviceName: String) {
        serviceScope.launch {
            try {
                broadcastStatus(AirPlayStatusState.STARTING)

                // Iniciar servidor UxPlay nativo
                nativeServerPtr = UxPlayBridge.startServer(
                    deviceName = deviceName,
                    port = 7000,
                    onClientConnected = { clientName ->
                        broadcastStatus(AirPlayStatusState.CONNECTED, clientName)
                        updateNotification(getString(R.string.status_connected, clientName))
                    },
                    onClientDisconnected = {
                        broadcastStatus(AirPlayStatusState.WAITING)
                        updateNotification(getString(R.string.status_waiting))
                    },
                    onError = { error ->
                        broadcastStatus(AirPlayStatusState.ERROR)
                        updateNotification(getString(R.string.status_error))
                    }
                )

                if (nativeServerPtr != 0L) {
                    broadcastStatus(AirPlayStatusState.WAITING)
                } else {
                    broadcastStatus(AirPlayStatusState.ERROR)
                }

            } catch (e: Exception) {
                broadcastStatus(AirPlayStatusState.ERROR)
            }
        }
    }

    fun broadcastStatus(status: String, clientName: String? = null) {
        val intent = Intent(ACTION_STATUS_UPDATE).apply {
            putExtra(EXTRA_STATUS, status)
            clientName?.let { putExtra(EXTRA_CLIENT_NAME, it) }
            setPackage(packageName)
        }
        sendBroadcast(intent)
    }

    private fun buildNotification(statusText: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(statusText)
            .setSmallIcon(R.drawable.ic_airplay_notification)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    private fun updateNotification(statusText: String) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(NOTIFICATION_ID, buildNotification(statusText))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
            }
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    private fun acquireLocks() {
        // Wake lock: CPU activo aunque la pantalla se apague
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "AirPlayTV::ServerWakeLock"
        ).apply { acquire() }

        // WiFi Multicast lock: necesario para mDNS (descubrimiento de dispositivos)
        val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        wifiLock = wm.createWifiLock(
            WifiManager.WIFI_MODE_FULL_HIGH_PERF,
            "AirPlayTV::WiFiLock"
        ).apply { acquire() }
    }

    private fun releaseLocks() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wifiLock?.takeIf { it.isHeld }?.release()
    }

    override fun onDestroy() {
        isRunning = false
        serviceScope.cancel()

        if (nativeServerPtr != 0L) {
            UxPlayBridge.stopServer(nativeServerPtr)
            nativeServerPtr = 0
        }

        releaseLocks()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
