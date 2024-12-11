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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.material3.Switch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable

class MainActivity : ComponentActivity() {
    companion object {
        const val CHANNEL_ID = "search_notification"
        const val NOTIFICATION_ID = 1
        const val NOTIFICATION_PERMISSION_REQUEST_CODE = 123
        val PRICE_REGEX = Regex("""\$\d+(\.\d{0,2})?""")
        const val PREF_ONLY_VEHO = "only_veho_enabled" // Removido o private
    }

    private val searchScope = CoroutineScope(Dispatchers.Main)
    private val sharedPreferences by lazy {
        getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        createNotificationChannel()
        requestNotificationPermission()

        // Restaura a configuração do Switch ao iniciar o app
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

@Composable
fun MainScreen(coroutineScope: CoroutineScope, initialSwitchState: Boolean = false) {
    val context = LocalContext.current
    var numberInput by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    var onlyVehoApp by rememberSaveable { mutableStateOf(initialSwitchState) }

    // Efeito para persistir a mudança do Switch
    DisposableEffect(onlyVehoApp) {
        onDispose { }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$",
                style = MaterialTheme.typography.headlineMedium
            )
            OutlinedTextField(
                value = numberInput,
                onValueChange = { newValue ->
                    if (newValue.isEmpty() || newValue.all { it.isDigit() }) {
                        numberInput = newValue
                    }
                },
                label = { Text("Digite um número") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number
                ),
                enabled = !isSearching,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    if (numberInput.isNotEmpty() && !isSearching) {
                        isSearching = true
                        searchJob = startSearch(context, numberInput, coroutineScope) {
                            isSearching = false
                            searchJob = null
                        }
                    }
                },
                enabled = numberInput.isNotEmpty() && !isSearching,
                modifier = Modifier.weight(1f)
            ) {
                Text(if (isSearching) "Buscando..." else "Buscar")
            }

            if (isSearching) {
                Button(
                    onClick = {
                        searchJob?.cancel()
                        isSearching = false
                        searchJob = null
                        VehoAcessibility.getInstance()?.priceMonitor?.clearTargetPrice()
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Stop")
                }
            }
        }

        Button(
            onClick = {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                context.startActivity(intent)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Verificar Serviço de Acessibilidade")
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Apenas detectar no app Veho",
                modifier = Modifier.weight(1f)
            )
            Switch(
                checked = onlyVehoApp,
                onCheckedChange = { checked ->
                    onlyVehoApp = checked
                    // Salva o estado do switch nas preferências
                    context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
                        .edit()
                        .putBoolean(MainActivity.PREF_ONLY_VEHO, checked)
                        .apply()
                    VehoAcessibility.getInstance()?.priceMonitor?.setOnlyCheckVehoApp(checked)
                }
            )
        }
    }
}

private fun startSearch(
    context: Context,
    number: String,
    scope: CoroutineScope,
    onSearchComplete: () -> Unit
): Job {
    return scope.launch {
        try {
            val searchValue = "$$number"
            VehoAcessibility.getInstance()?.priceMonitor?.setTargetPrice(searchValue)

            showNotification(
                context,
                "Iniciando busca",
                "Procurando pelo valor $searchValue na tela"
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
                            "Valor Encontrado!",
                            "O valor $searchValue foi encontrado na tela!"
                        )
                    }
                }
            }
        } catch (e: Exception) {
            showNotification(
                context,
                "Busca cancelada",
                "A busca por $number foi interrompida"
            )
        } finally {
            VehoAcessibility.getInstance()?.priceMonitor?.clearTargetPrice()
            onSearchComplete()
        }
    }
}

private fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val accessibilityManager =
        context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
    return accessibilityManager.isEnabled
}

private fun showNotification(context: Context, title: String, content: String) {
    val builder = NotificationCompat.Builder(context, MainActivity.CHANNEL_ID)
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
            notify(MainActivity.NOTIFICATION_ID, builder.build())
        }
    }
}