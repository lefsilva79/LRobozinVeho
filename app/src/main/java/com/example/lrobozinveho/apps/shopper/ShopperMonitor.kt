package com.example.lrobozinveho.apps.shopper

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.lrobozinveho.TryClickAndVerify

class ShopperMonitor(private val service: AccessibilityService) {
    companion object {
        private const val TAG = "ShopperMonitor"
        private const val SHOPPER_PACKAGE = "com.vehotechnologies.Driver"
        private const val PREFS_NAME = "ShopperMonitorPrefs"
        private const val PREF_ONLY_VEHO = "only_veho_enabled"
    }

    private var isShopperApp = false
    private var targetPrice: String? = null
    private var lastDollarSign: AccessibilityNodeInfo? = null
    private var priceFound = false
    private var lastProcessedNode: AccessibilityNodeInfo? = null
    private var onlyCheckVehoApp: Boolean
    private val clickVerifier = TryClickAndVerify()

    init {
        // Carrega o estado salvo do switch
        val prefs = service.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        onlyCheckVehoApp = prefs.getBoolean(PREF_ONLY_VEHO, false)
        Log.d(TAG, "Estado inicial do switch: $onlyCheckVehoApp")
    }

    fun onAccessibilityEvent(event: AccessibilityEvent) {
        try {
            val packageName = event.packageName?.toString()
            isShopperApp = packageName == SHOPPER_PACKAGE

            Log.d(TAG, """
            ====== NOVO EVENTO =====
            Package: $packageName
            É Veho? $isShopperApp
            Modo apenas Veho? $onlyCheckVehoApp
            =======================
        """.trimIndent())

            // Verifica se deve processar apenas eventos do Veho
            if (onlyCheckVehoApp && !isShopperApp) {
                Log.d(TAG, "🚫 IGNORANDO evento - não é o app Veho")
                clearNodes()
                return
            }

            val rootNode = service.rootInActiveWindow ?: return

            // Tenta encontrar e clicar no preço alvo
            targetPrice?.let { price ->
                if (clickVerifier.searchAndClickPrice(rootNode, price)) {
                    Log.d(TAG, "✅ Preço encontrado e botão Claim clicado!")
                    priceFound = true
                }
            }

            // Mantém a lógica existente
            clearNodes()
            processNode(rootNode)
            rootNode.recycle()

        } catch (e: Exception) {
            Log.e(TAG, "Erro ao processar evento de acessibilidade", e)
        }
    }

    private fun clearNodes() {
        priceFound = false
        lastDollarSign = null
        lastProcessedNode = null
    }

    fun setOnlyCheckVehoApp(enabled: Boolean) {
        onlyCheckVehoApp = enabled
        // Salva o novo estado do switch
        service.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(PREF_ONLY_VEHO, enabled)
            .apply()

        clearNodes()
        Log.d(TAG, """
            ====== ALTERAÇÃO DE MODO ======
            Modo apenas Veho: ${if(enabled) "ATIVADO" else "DESATIVADO"}
            Estado salvo com sucesso
            ==============================
        """.trimIndent())
    }

    private fun processNode(node: AccessibilityNodeInfo) {
        try {
            val nodeText = node.text?.toString() ?: ""

            if (nodeText.isNotEmpty()) {
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
            }

            // Processa os nós filhos
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                if (child != lastProcessedNode) {
                    processNode(child)
                }
                child.recycle()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao processar nó", e)
        }
    }

    private fun extractFirstPrice(text: String): String {
        // Extrai o primeiro número após o $
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
            // Extrai os valores numéricos para comparação
            val foundValue = extractNumericValue(firstPrice)
            val targetValue = targetPrice?.let { extractNumericValue(it) } ?: 0

            Log.d(TAG, """
            ====== PREÇO ENCONTRADO ======
            Valor original: $price
            Primeiro valor: $firstPrice
            Valor numérico: $foundValue
            Valor alvo: $targetValue
            App: ${if (isShopperApp) "Veho" else "outro"}
            Modo apenas Veho: $onlyCheckVehoApp
            ============================
        """.trimIndent())

            // Só notifica se o valor alvo não for 0 (não inicializado)
            if (targetValue > 0 && foundValue > targetValue) {
                Log.d(TAG, "🎯 VALOR MAIOR ENCONTRADO! 🎯")
                Log.d(TAG, "Encontrado: $$foundValue > Alvo: $$targetValue")
                priceFound = true
                logNodeDetails(node)
            }
        }
    }

    private fun logNodeDetails(node: AccessibilityNodeInfo) {
        try {
            Log.d(TAG, """
                ====== DETALHES DO NÓ ======
                Texto: ${node.text}
                Classe: ${node.className}
                ID: ${node.viewIdResourceName ?: "sem-id"}
                Clicável: ${node.isClickable}
                Habilitado: ${node.isEnabled}
                Pacote: ${node.packageName}
                Parent: ${node.parent?.className}
                Bounds: ${node.getBoundsInScreen(android.graphics.Rect())}
                ===========================
            """.trimIndent())
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao registrar detalhes do nó", e)
        }
    }

    fun setTargetPrice(price: String) {
        targetPrice = price
        priceFound = false
        Log.d(TAG, """
            ====== NOVO PREÇO ALVO ======
            Valor: $price
            Modo apenas Veho: $onlyCheckVehoApp
            ===========================
        """.trimIndent())
    }

    fun clearTargetPrice() {
        val oldPrice = targetPrice
        targetPrice = null
        priceFound = false
        Log.d(TAG, """
            ====== LIMPEZA DE PREÇO ======
            Valor anterior: $oldPrice
            Modo apenas Veho: $onlyCheckVehoApp
            ============================
        """.trimIndent())
    }

    fun isPriceFound(): Boolean = priceFound

    fun isTargetApp(): Boolean = isShopperApp

    fun getTargetPrice(): String? = targetPrice
}