package com.example.lrobozinveho.apps.shopper

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class ShopperMonitor(private val service: AccessibilityService) {
    companion object {
        private const val TAG = "ShopperMonitor"
        private const val SHOPPER_PACKAGE = "com.vehotechnologies.Driver"
    }

    private var isShopperApp = false
    private var targetPrice: String? = null
    private var lastDollarSign: AccessibilityNodeInfo? = null
    private var priceFound = false
    private var lastProcessedNode: AccessibilityNodeInfo? = null

    fun onAccessibilityEvent(event: AccessibilityEvent) {
        try {
            val packageName = event.packageName?.toString()
            isShopperApp = packageName == SHOPPER_PACKAGE

            val rootNode = service.rootInActiveWindow ?: return
            priceFound = false
            lastDollarSign = null
            lastProcessedNode = null

            Log.d(TAG, "Iniciando processamento de evento. Package: $packageName")
            processNode(rootNode)
            rootNode.recycle()
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao processar evento de acessibilidade", e)
        }
    }

    private fun processNode(node: AccessibilityNodeInfo) {
        try {
            val nodeText = node.text?.toString() ?: ""

            if (nodeText.isNotEmpty()) {
                Log.v(TAG, "Processando nó com texto: '$nodeText'")

                when {
                    nodeText == "$" -> {
                        Log.d(TAG, "Símbolo $ encontrado")
                        lastDollarSign = node
                        lastProcessedNode = node
                    }
                    lastDollarSign != null && nodeText.all { it.isDigit() || it == '.' } -> {
                        val combinedText = "$$nodeText"
                        Log.d(TAG, "Combinando $ com número: $combinedText")
                        handlePrice(combinedText, node)
                        lastDollarSign = null
                        lastProcessedNode = node
                    }
                    nodeText.startsWith("$") -> {
                        Log.d(TAG, "Texto já começa com $: $nodeText")
                        handlePrice(nodeText, node)
                        lastProcessedNode = node
                    }
                    else -> {
                        if (nodeText.contains("$")) {
                            Log.d(TAG, "Texto contém $ em algum lugar: $nodeText")
                        }
                        handlePrice(nodeText, node)
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

    private fun isPriceText(text: String): Boolean {
        return text.matches(Regex("""\$\d+(\.\d{0,2})?"""))
    }

    private fun handlePrice(price: String, node: AccessibilityNodeInfo) {
        if (isPriceText(price)) {
            Log.d(
                TAG, """
                Preço válido encontrado:
                Valor: $price
                App: ${if (isShopperApp) "Shopper" else "outro"}
                Preço alvo: $targetPrice
            """.trimIndent())

            if (targetPrice == price) {
                Log.d(TAG, "🎯 PREÇO ALVO ENCONTRADO! 🎯")
                priceFound = true
                logNodeDetails(node)
            }
        } else if (price.contains("$")) {
            Log.v(TAG, "Texto com $ encontrado, mas não é um preço válido: $price")
        }
    }

    private fun logNodeDetails(node: AccessibilityNodeInfo) {
        try {
            Log.d(
                TAG, """
                Detalhes do nó:
                Texto: ${node.text}
                Classe: ${node.className}
                ID: ${node.viewIdResourceName ?: "sem-id"}
                Clicável: ${node.isClickable}
                Habilitado: ${node.isEnabled}
                Pacote: ${node.packageName}
                Parent: ${node.parent?.className}
                Bounds: ${node.getBoundsInScreen(android.graphics.Rect())}
            """.trimIndent())
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao registrar detalhes do nó", e)
        }
    }

    fun setTargetPrice(price: String) {
        targetPrice = price
        priceFound = false
        Log.d(TAG, "💲 Novo preço alvo definido: $price")
    }

    fun clearTargetPrice() {
        val oldPrice = targetPrice
        targetPrice = null
        priceFound = false
        Log.d(TAG, "🚫 Preço alvo limpo. Valor anterior: $oldPrice")
    }

    fun isPriceFound(): Boolean {
        return priceFound
    }

    fun isTargetApp(): Boolean {
        return isShopperApp
    }

    fun getTargetPrice(): String? {
        return targetPrice
    }
}