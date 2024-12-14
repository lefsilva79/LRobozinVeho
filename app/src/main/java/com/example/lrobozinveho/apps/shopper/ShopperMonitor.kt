package com.example.lrobozinveho.apps.shopper

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.lrobozinveho.TryClickAndVerify
import java.text.SimpleDateFormat
import java.util.*

class ShopperMonitor(private val service: AccessibilityService) {
    companion object {
        private const val TAG = "ShopperMonitor"
        private const val SHOPPER_PACKAGE = "com.vehotechnologies.Driver"
        private const val PREFS_NAME = "ShopperMonitorPrefs"
        private const val PREF_ONLY_VEHO = "only_veho_enabled"
    }

    private var isShopperApp = false
    private var targetPrice: String? = null
    private var targetDeliveryArea: String? = null
    private var targetStartTime: Int? = null
    private var targetHours: Int? = null
    private var lastDollarSign: AccessibilityNodeInfo? = null
    private var priceFound = false
    private var deliveryAreaMatched = false
    private var startTimeMatched = false
    private var hoursMatched = false
    private var lastProcessedNode: AccessibilityNodeInfo? = null
    private var onlyCheckVehoApp: Boolean
    private val clickVerifier = TryClickAndVerify()
    private var lastProcessedEventTime = 0L
    private var isProcessingEvent = false
    private val processedNodeTexts = mutableSetOf<String>()

    init {
        val prefs = service.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        onlyCheckVehoApp = prefs.getBoolean(PREF_ONLY_VEHO, false)
        Log.d(TAG, "Estado inicial do switch: $onlyCheckVehoApp")
    }

    private fun getCurrentUTCDateTime(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date())
    }

    fun onAccessibilityEvent(event: AccessibilityEvent) {
        try {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastProcessedEventTime < 500) {
                Log.d(TAG, "⏭️ Ignorando evento muito próximo")
                return
            }
            lastProcessedEventTime = currentTime

            val packageName = event.packageName?.toString()
            isShopperApp = packageName == SHOPPER_PACKAGE

            // Se nenhum alvo foi definido, não processa
            if (targetPrice == null && targetDeliveryArea == null &&
                targetStartTime == null && targetHours == null) {
                return
            }

            Log.d(
                TAG, """
            ====== NOVO EVENTO =====
            Data/Hora (UTC): ${getCurrentUTCDateTime()}
            Package: $packageName
            É Veho? $isShopperApp
            Modo apenas Veho? $onlyCheckVehoApp
            =======================
            """.trimIndent()
            )

            if (onlyCheckVehoApp && !isShopperApp) {
                Log.d(TAG, "🚫 IGNORANDO evento - não é o app Veho")
                clearNodes()
                return
            }

            val rootNode = service.rootInActiveWindow ?: return
            clearNodes()

            targetPrice?.let { price ->
                if (clickVerifier.searchAndClickPrice(rootNode, price)) {
                    Log.d(
                        TAG, """
                    ✅ MATCH ENCONTRADO!
                    Data/Hora (UTC): ${getCurrentUTCDateTime()}
                    Preço alvo encontrado e validado!
                """.trimIndent()
                    )
                    priceFound = true
                    if (areAllConditionsMet()) {
                        clearTargetPrice()
                        return
                    }
                }
            }

            processNode(rootNode)
            rootNode.recycle()

        } catch (e: Exception) {
            Log.e(TAG, "Erro ao processar evento de acessibilidade", e)
        }
    }

    private fun clearNodes() {
        priceFound = false
        deliveryAreaMatched = false
        startTimeMatched = false
        hoursMatched = false
        lastDollarSign = null
        lastProcessedNode = null
        processedNodeTexts.clear() // Adicione apenas esta linha
    }

    fun setOnlyCheckVehoApp(enabled: Boolean) {
        onlyCheckVehoApp = enabled
        service.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PREF_ONLY_VEHO, enabled)
            .apply()

        clearNodes()
        Log.d(
            TAG, """
            ====== ALTERAÇÃO DE MODO ======
            Data/Hora (UTC): ${getCurrentUTCDateTime()}
            Modo apenas Veho: ${if (enabled) "ATIVADO" else "DESATIVADO"}
            Estado salvo com sucesso
            ==============================
            """.trimIndent()
        )
    }

    private fun processNode(node: AccessibilityNodeInfo) {
        try {
            if (!isShopperApp) return

            val nodeText = node.text?.toString() ?: ""

            // Log de diagnóstico - todos os textos
            if (nodeText.isNotEmpty()) {
                Log.d(TAG, """
                📝 TEXTO NA TELA:
                Texto: '$nodeText'
                Classe: ${node.className}
                ID: ${node.viewIdResourceName ?: "sem-id"}
                Parent: ${node.parent?.className}
                ====================
            """.trimIndent())
            }

            // Se já processamos este texto, pula
            if (nodeText.isNotEmpty() && !processedNodeTexts.contains(nodeText)) {
                processedNodeTexts.add(nodeText)

                // Se encontrou um preço válido, procura os outros dados ao redor
                if (nodeText.startsWith("$") || nodeText.contains("$")) {
                    val price = extractFirstPrice(nodeText)
                    if (isPriceText(price)) {
                        val foundValue = extractNumericValue(price)
                        val targetValue = targetPrice?.let { extractNumericValue(it) } ?: 0

                        if (foundValue >= targetValue) {
                            Log.d(TAG, "💲 Preço elegível encontrado: $price >= $targetPrice")

                            // Procura nos nós irmãos e pai
                            searchSiblingNodes(node)
                            searchParentNode(node.parent)

                            // Verifica condições e loga detalhes
                            Log.d(TAG, """
                            🔍 CONDIÇÕES APÓS BUSCA EXPANDIDA:
                            Preço encontrado: $priceFound ($price)
                            Delivery Area ok: $deliveryAreaMatched (Alvo: $targetDeliveryArea)
                            Start Time ok: $startTimeMatched (Alvo: $targetStartTime)
                            Hours ok: $hoursMatched (Alvo: $targetHours)
                            ====================
                        """.trimIndent())

                            if (areAllConditionsMet()) {
                                Log.d(TAG, "🎯 TODAS CONDIÇÕES ATENDIDAS! Clicando...")
                                clickVerifier.searchAndClickPrice(node, targetPrice!!)
                                clearTargetPrice()
                                return
                            }
                        }
                    }
                }

                // Processa o nó com a lógica detalhada original
                processSingleNode(node)
            }

            // Continua nos filhos se não encontrou match completo
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                processNode(child)
                if (areAllConditionsMet()) {
                    child.recycle()
                    return
                }
                child.recycle()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Erro ao processar nó", e)
        }
    }

    // NOVA FUNÇÃO: Procura nos nós irmãos
    private fun searchSiblingNodes(node: AccessibilityNodeInfo) {
        val parent = node.parent ?: return
        for (i in 0 until parent.childCount) {
            val sibling = parent.getChild(i) ?: continue
            if (sibling != node) {
                Log.d(TAG, "👥 Verificando nó irmão")
                processSingleNode(sibling)
            }
            sibling.recycle()
        }
    }

    // NOVA FUNÇÃO: Procura no nó pai
    private fun searchParentNode(parent: AccessibilityNodeInfo?) {
        parent?.let {
            Log.d(TAG, "👆 Verificando nó pai")
            processSingleNode(it)
        }
    }


    // Nova função que contém a lógica original de processamento de um único nó
    private fun processSingleNode(node: AccessibilityNodeInfo) {
        val nodeText = node.text?.toString() ?: ""
        if (nodeText.isEmpty()) return

        // Verifica Delivery Area
        if (nodeText.contains("Delivery Area")) {
            val areaNumber = nodeText.filter { it.isDigit() }
            targetDeliveryArea?.let { target ->
                if (areaNumber == target) {
                    Log.d(TAG, """
                    ✅ DELIVERY AREA MATCH
                    Data/Hora (UTC): ${getCurrentUTCDateTime()}
                    Área encontrada: $areaNumber
                    Área alvo: $target
                    """.trimIndent()
                    )
                    deliveryAreaMatched = true
                    logNodeDetails(node)
                }
            }
        }

        // Verifica Start Time
        if (nodeText.contains(":00") || nodeText.contains(":30")) {
            val timeRegex = """(\d{1,2}:\d{2}\s*(?:AM|PM))""".toRegex()
            val matchResult = timeRegex.find(nodeText)
            val fullTime = matchResult?.groupValues?.get(1)

            val hourRegex = """(\d{1,2})[:.]\d{2}""".toRegex()
            val hourMatch = hourRegex.find(nodeText)
            val hour = hourMatch?.groupValues?.get(1)?.toIntOrNull()

            targetStartTime?.let { target ->
                if (hour != null && hour >= target) {
                    Log.d(TAG, """
                    ✅ START TIME MATCH
                    Data/Hora (UTC): ${getCurrentUTCDateTime()}
                    Horário encontrado: $fullTime
                    Horário numérico: $hour
                    Horário mínimo: $target
                    Texto original: $nodeText
                    """.trimIndent()
                    )
                    startTimeMatched = true
                    logNodeDetails(node)
                }
            }
        }

        // Verifica Hours
        if (nodeText.contains("hrs") || nodeText.contains("hour")) {
            val firstDigit = extractFirstDigit(nodeText)
            targetHours?.let { target ->
                if (firstDigit != null && firstDigit <= target) {
                    Log.d(TAG, """
                    ✅ HOURS MATCH
                    Data/Hora (UTC): ${getCurrentUTCDateTime()}
                    Duração encontrada: $firstDigit
                    Duração máxima: $target
                    Texto original: $nodeText
                    """.trimIndent()
                    )
                    hoursMatched = true
                    logNodeDetails(node)
                }
            }
        }

        // Verifica Preço
        when {
            nodeText == "$" -> {
                Log.d(TAG, "💲 Símbolo $ encontrado")
                lastDollarSign = node
                lastProcessedNode = node
            }
            lastDollarSign != null && nodeText.all { it.isDigit() || it == '.' } -> {
                val combinedText = "$$nodeText"
                Log.d(TAG, "🔄 Combinando $ com número: $combinedText")
                handlePrice(combinedText, node)
                lastDollarSign = null
                lastProcessedNode = node
            }
            nodeText.startsWith("$") -> {
                Log.d(TAG, "💲 Texto já começa com $: $nodeText")
                handlePrice(nodeText, node)
                lastProcessedNode = node
            }
            else -> {
                if (nodeText.contains("$")) {
                    Log.d(TAG, "💲 Texto contém $ em algum lugar: $nodeText")
                    handlePrice(nodeText, node)
                }
                lastProcessedNode = node
            }
        }

        checkAllConditions()
    }

    // Função auxiliar para verificar se todas as condições foram atendidas
    private fun areAllConditionsMet(): Boolean {
        val allMet = priceFound &&
                (targetDeliveryArea == null || deliveryAreaMatched) &&
                (targetStartTime == null || startTimeMatched) &&
                (targetHours == null || hoursMatched)

        if (allMet) {
            Log.d(TAG, """
            ✅ TODAS AS CONDIÇÕES ATENDIDAS
            Data/Hora (UTC): ${getCurrentUTCDateTime()}
            Preço encontrado: $priceFound
            Delivery Area ok: ${targetDeliveryArea == null || deliveryAreaMatched}
            Start Time ok: ${targetStartTime == null || startTimeMatched}
            Hours ok: ${targetHours == null || hoursMatched}
        """.trimIndent())
        }

        return allMet
    }


    private fun extractFirstDigit(text: String): Int? {
        return text.firstOrNull { it.isDigit() }?.toString()?.toIntOrNull()
    }

    private fun extractFirstPrice(text: String): String {
        val regex = """\$(\d+)""".toRegex()
        return regex.find(text)?.groupValues?.get(1)?.let { "$$it" } ?: text
    }

    private fun isPriceText(text: String): Boolean {
        val firstPrice = extractFirstPrice(text)
        return firstPrice.matches(Regex("""\$\d+(\.\d{0,2})?"""))
    }

    private fun extractNumericValue(price: String): Int {
        return price.replace("$", "").split("-")[0].toIntOrNull() ?: 0
    }

    private fun handlePrice(price: String, node: AccessibilityNodeInfo) {
        val firstPrice = extractFirstPrice(price)
        if (isPriceText(firstPrice)) {
            val foundValue = extractNumericValue(firstPrice)
            val targetValue = targetPrice?.let { extractNumericValue(it) } ?: 0

            Log.d(
                TAG, """
            ### PREÇO ENCONTRADO ###
            Data/Hora (UTC): ${getCurrentUTCDateTime()}
            Valor original: $price
            Primeiro valor: $firstPrice
            Valor numérico: $foundValue
            Valor alvo: $targetValue
            App: ${if (isShopperApp) "Veho" else "outro"}
            Modo apenas Veho: $onlyCheckVehoApp
            ######################
            """.trimIndent()
            )

            if (targetValue > 0 && foundValue >= targetValue) {
                Log.d(
                    TAG, """
                ### PREÇO VÁLIDO ENCONTRADO ###
                Data/Hora (UTC): ${getCurrentUTCDateTime()}
                Encontrado: $$foundValue >= Alvo: $$targetValue
                #############################
                """.trimIndent()
                )
                priceFound = true
                logNodeDetails(node)
            }
        }
    }

    private fun checkAllConditions() {
        val allConditionsMet = priceFound &&
                (targetDeliveryArea == null || deliveryAreaMatched) &&
                (targetStartTime == null || startTimeMatched) &&
                (targetHours == null || hoursMatched)

        Log.d(
            TAG, """
            ====== VERIFICAÇÃO DE CONDIÇÕES ======
            Data/Hora (UTC): ${getCurrentUTCDateTime()}
            Preço encontrado: $priceFound
            Delivery Area ok: ${targetDeliveryArea == null || deliveryAreaMatched}
            Start Time ok: ${targetStartTime == null || startTimeMatched}
            Hours ok: ${targetHours == null || hoursMatched}
            TODAS CONDIÇÕES ATENDIDAS: $allConditionsMet
            ===================================
            """.trimIndent()
        )
    }

    private fun logNodeDetails(node: AccessibilityNodeInfo) {
        try {
            Log.d(
                TAG, """
                ====== DETALHES DO NÓ ======
                Data/Hora (UTC): ${getCurrentUTCDateTime()}
                Texto: ${node.text}
                Classe: ${node.className}
                ID: ${node.viewIdResourceName ?: "sem-id"}
                Clicável: ${node.isClickable}
                Habilitado: ${node.isEnabled}
                Pacote: ${node.packageName}
                Parent: ${node.parent?.className}
                Bounds: ${node.getBoundsInScreen(android.graphics.Rect())}
                ===========================
                """.trimIndent()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao registrar detalhes do nó", e)
        }
    }

    fun setTargetPrice(price: String) {
        targetPrice = price
        priceFound = false
        Log.d(
            TAG, """
            ====== NOVO ALVO: PREÇO ======
            Data/Hora (UTC): ${getCurrentUTCDateTime()}
            Valor definido: $price
            ===========================
            """.trimIndent()
        )
    }

    fun setTargetDeliveryArea(area: String) {
        targetDeliveryArea = area
        deliveryAreaMatched = false
        Log.d(
            TAG, """
            ====== NOVO ALVO: ÁREA ======
            Data/Hora (UTC): ${getCurrentUTCDateTime()}
            Área definida: $area
            ===========================
            """.trimIndent()
        )
    }

    fun setTargetStartTime(time: String) {
        // Converte a string completa para número
        targetStartTime = time.toIntOrNull()
        startTimeMatched = false
        Log.d(
            TAG, """
        ====== NOVO ALVO: HORÁRIO ======
        Data/Hora (UTC): ${getCurrentUTCDateTime()}
        Horário definido: $targetStartTime
        ===========================
        """.trimIndent()
        )
    }

    fun setTargetHours(hours: String) {
        targetHours = hours.firstOrNull()?.toString()?.toIntOrNull()
        hoursMatched = false
        Log.d(
            TAG, """
            ====== NOVO ALVO: DURAÇÃO ======
            Data/Hora (UTC): ${getCurrentUTCDateTime()}
            Duração definida: $targetHours
            ===========================
            """.trimIndent()
        )
    }

    fun clearTargetPrice() {
        targetPrice = null
        targetDeliveryArea = null
        targetStartTime = null
        targetHours = null
        priceFound = false
        deliveryAreaMatched = false
        startTimeMatched = false
        hoursMatched = false
        Log.d(
            TAG, """
            ====== LIMPEZA DE ALVOS ======
            Data/Hora (UTC): ${getCurrentUTCDateTime()}
            Todos os alvos foram limpos
            ===========================
            """.trimIndent()
        )
    }

    fun isPriceFound(): Boolean = priceFound
    fun isTargetApp(): Boolean = isShopperApp
    fun getTargetPrice(): String? = targetPrice
}