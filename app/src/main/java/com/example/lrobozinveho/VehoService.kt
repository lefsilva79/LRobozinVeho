package com.example.lrobozinveho

import android.app.*
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.content.Context
import android.os.Handler
import android.os.Looper

class VehoService : Service() {
    private val binder = LocalBinder()
    private lateinit var notificationHelper: NotificationHelper
    private var wakeLock: PowerManager.WakeLock? = null
    private var notificationCheckHandler: Handler? = null
    private var currentMessage = ""

    inner class LocalBinder : Binder() {
        fun getService(): VehoService = this@VehoService
    }

    override fun onCreate() {
        super.onCreate()
        notificationHelper = NotificationHelper(this)
        notificationCheckHandler = Handler(Looper.getMainLooper())

        // Configurar WakeLock para manter o serviço rodando
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "LRobozinVeho::ServiceWakeLock"
        ).apply {
            // Adquire WakeLock com timeout de 6 horas (em milissegundos)
            acquire(6 * 60 * 60 * 1000L)
        }

        startForeground(
            NotificationHelper.NOTIFICATION_ID,
            notificationHelper.createNotification(
                "LRobozin Veho está ativo",
                currentMessage
            )
        )

        // Solicitar ignorar otimização de bateria
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent().apply {
                action = android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                data = android.net.Uri.parse("package:$packageName")
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                startActivity(intent)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Recria a notificação se ela for removida
        startForeground(
            NotificationHelper.NOTIFICATION_ID,
            notificationHelper.createNotification(
                "LRobozin Veho está ativo",
                currentMessage
            )
        )

        // Inicia o monitoramento da notificação
        startNotificationMonitoring()

        return START_STICKY
    }

    private fun startNotificationMonitoring() {
        notificationCheckHandler?.postDelayed(object : Runnable {
            override fun run() {
                val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
                val notifications = notificationManager.activeNotifications

                if (notifications.none { it.id == NotificationHelper.NOTIFICATION_ID }) {
                    startForeground(
                        NotificationHelper.NOTIFICATION_ID,
                        notificationHelper.createNotification(
                            "LRobozin Veho está ativo",
                            currentMessage
                        )
                    )
                }

                notificationCheckHandler?.postDelayed(this, 1000)
            }
        }, 1000)
    }

    fun updateNotificationMessage(message: String) {
        try {
            currentMessage = message
            notificationHelper.updateNotification("LRobozin Veho está ativo", message)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Remove os callbacks do handler
        notificationCheckHandler?.removeCallbacksAndMessages(null)
        notificationCheckHandler = null

        // Libera o WakeLock quando o serviço é destruído
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        // Cancela a notificação quando o serviço é destruído
        notificationHelper.cancelNotification()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // Reiniciar o serviço se o app for fechado
        val restartServiceIntent = Intent(applicationContext, this.javaClass)
        val restartServicePendingIntent = PendingIntent.getService(
            applicationContext,
            1,
            restartServiceIntent,
            PendingIntent.FLAG_IMMUTABLE
        )
        val alarmService = applicationContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarmService.set(
            AlarmManager.ELAPSED_REALTIME,
            android.os.SystemClock.elapsedRealtime() + 1000,
            restartServicePendingIntent
        )
    }
}