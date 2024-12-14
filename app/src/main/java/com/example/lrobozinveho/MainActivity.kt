package com.example.lrobozinveho

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*

class MainActivity : ComponentActivity() {
    companion object {
        const val CHANNEL_ID = "search_notification"
        const val NOTIFICATION_ID = 1
        const val NOTIFICATION_PERMISSION_REQUEST_CODE = 123
        val PRICE_REGEX = Regex("""\$\d+(\.\d{0,2})?""")
        const val PREF_ONLY_VEHO = "only_veho_enabled"

        fun isAccessibilityServiceEnabled(context: Context): Boolean {
            val accessibilityManager =
                context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
            return accessibilityManager.isEnabled
        }

        fun showNotification(context: Context, title: String, content: String) {
            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(content)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)

            with(NotificationManagerCompat.from(context)) {
                if (ActivityCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
                ) {
                    notify(NOTIFICATION_ID, builder.build())
                }
            }
        }

        fun startSearch(
            context: Context,
            price: String,
            deliveryArea: String = "",
            startTime: String = "",
            hours: String = "",
            scope: CoroutineScope,
            onSearchComplete: () -> Unit
        ): Job {
            return scope.launch {
                try {
                    val searchValue = "$$price"
                    VehoAcessibility.getInstance()?.priceMonitor?.apply {
                        setTargetPrice(searchValue)
                        if (deliveryArea.isNotEmpty()) setTargetDeliveryArea(deliveryArea)
                        if (startTime.isNotEmpty()) setTargetStartTime(startTime)
                        if (hours.isNotEmpty()) setTargetHours(hours)
                    }

                    showNotification(
                        context,
                        "Iniciando busca",
                        buildString {
                            append("Procurando: ")
                            append("Preço >=$searchValue")
                            if (deliveryArea.isNotEmpty()) append(", Area $deliveryArea")
                            if (startTime.isNotEmpty()) append(", Horário >=$startTime")
                            if (hours.isNotEmpty()) append(", Duração <=$hours")
                        }
                    )

                    var searching = true
                    while (searching) {
                        delay(1000)

                        if (!isAccessibilityServiceEnabled(context)) {
                            showNotification(
                                context,
                                "Serviço Desativado",
                                "Por favor, ative o serviço de acessibilidade"
                            )
                            break
                        }

                        VehoAcessibility.getInstance()?.priceMonitor?.let { monitor ->
                            if (monitor.isPriceFound()) {
                                searching = false
                                showNotification(
                                    context,
                                    "Correspondência Encontrada!",
                                    buildString {
                                        append("Encontrado: ")
                                        append("Preço >=$searchValue")
                                        if (deliveryArea.isNotEmpty()) append(", Area $deliveryArea")
                                        if (startTime.isNotEmpty()) append(", Horário >=$startTime")
                                        if (hours.isNotEmpty()) append(", Duração <=$hours")
                                    }
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    showNotification(
                        context,
                        "Busca cancelada",
                        "A busca foi interrompida"
                    )
                } finally {
                    VehoAcessibility.getInstance()?.priceMonitor?.clearTargetPrice()
                    onSearchComplete()
                }
            }
        }
    }

    private val searchScope = CoroutineScope(Dispatchers.Main)
    private val sharedPreferences by lazy {
        getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createNotificationChannel()
        requestNotificationPermission()

        val savedSwitchState = sharedPreferences.getBoolean(PREF_ONLY_VEHO, false)
        VehoAcessibility.getInstance()?.priceMonitor?.setOnlyCheckVehoApp(savedSwitchState)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(lifecycleScope, savedSwitchState)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        searchScope.cancel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Search Notifications"
            val descriptionText = "Notifications for price search"
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    NOTIFICATION_PERMISSION_REQUEST_CODE
                )
            }
        }
    }
}