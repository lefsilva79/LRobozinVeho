package com.example.lrobozinveho

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import android.content.ComponentName
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder

@Composable
fun MainScreen(coroutineScope: CoroutineScope, initialSwitchState: Boolean = false) {
    val context = LocalContext.current
    var priceInput by remember { mutableStateOf("") }
    var deliveryAreaInput by remember { mutableStateOf("") }
    var startTimeInput by remember { mutableStateOf("") }
    var hoursInput by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    var onlyVehoApp by rememberSaveable { mutableStateOf(initialSwitchState) }

    // Adicionando controle do serviço
    var vehoService by remember { mutableStateOf<VehoService?>(null) }
    val serviceConnection = remember {
        object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                vehoService = (service as VehoService.LocalBinder).getService()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                vehoService = null
            }
        }
    }

    DisposableEffect(Unit) {
        // Iniciar o serviço de notificação
        val intent = Intent(context, VehoService::class.java)
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }

        onDispose {
            context.unbindService(serviceConnection)
        }
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
                value = priceInput,
                onValueChange = { newValue ->
                    if (newValue.isEmpty() || (newValue.all { it.isDigit() } && newValue.length <= 3)) {
                        val numericValue = newValue.toIntOrNull()
                        if (numericValue == null || numericValue <= 999) {
                            priceInput = newValue
                        }
                    }
                },
                label = { Text("Preço mínimo") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                enabled = !isSearching,
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        OutlinedTextField(
            value = deliveryAreaInput,
            onValueChange = { newValue ->
                if (newValue.isEmpty() || (newValue.all { it.isDigit() } && newValue.length <= 1)) {
                    val numericValue = newValue.toIntOrNull()
                    if (numericValue == null || numericValue <= 9) {
                        deliveryAreaInput = newValue
                    }
                }
            },
            label = { Text("Delivery Area") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            enabled = !isSearching,
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = startTimeInput,
            onValueChange = { newValue ->
                if (newValue.isEmpty() || (newValue.all { it.isDigit() } && newValue.length <= 2)) {
                    val numericValue = newValue.toIntOrNull()
                    if (numericValue == null || numericValue <= 12) {
                        startTimeInput = newValue
                    }
                }
            },
            label = { Text("Start Time") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            enabled = !isSearching,
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = hoursInput,
            onValueChange = { newValue ->
                if (newValue.isEmpty() || (newValue.all { it.isDigit() } && newValue.length <= 1)) {
                    val numericValue = newValue.toIntOrNull()
                    if (numericValue == null || numericValue <= 9) {
                        hoursInput = newValue
                    }
                }
            },
            label = { Text("Hours") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            enabled = !isSearching,
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    if (priceInput.isNotEmpty() && !isSearching) {
                        isSearching = true
                        vehoService?.startSearching() // Nova linha adicionada
                        vehoService?.updateNotificationMessage(
                            "Buscando com preço mínimo: $ $priceInput" +
                                    (if (deliveryAreaInput.isNotEmpty()) ", Área: $deliveryAreaInput" else "") +
                                    (if (startTimeInput.isNotEmpty()) ", Início: $startTimeInput" else "") +
                                    (if (hoursInput.isNotEmpty()) ", Horas: $hoursInput" else "")
                        )
                        searchJob = MainActivity.startSearch(
                            context = context,
                            price = priceInput,
                            deliveryArea = deliveryAreaInput,
                            startTime = startTimeInput,
                            hours = hoursInput,
                            scope = coroutineScope
                        ) {
                            isSearching = false
                            searchJob = null
                            vehoService?.stopSearching() // Nova linha adicionada
                            vehoService?.updateNotificationMessage("Serviço em espera")
                        }
                    }
                },
                enabled = priceInput.isNotEmpty() && !isSearching,
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
                        vehoService?.stopSearching() // Nova linha adicionada
                        vehoService?.updateNotificationMessage("Busca interrompida")
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