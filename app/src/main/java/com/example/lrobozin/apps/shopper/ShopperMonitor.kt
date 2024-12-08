package com.example.lrobozin.apps.shopper

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.lrobozin.MainActivity

class ShopperMonitor(private val service: AccessibilityService) {
    companion object {
        private const val TAG = "PriceMonitor"
        private const val SHOPPER_PACKAGE = "com.instacart.shopper"
    }

    private var isShopperApp = false
    private var targetPrice: String? = null

    fun onAccessibilityEvent(event: AccessibilityEvent) {
        try {
            // Atualiza o status se estamos no app do Shopper ou não
            val packageName = event.packageName?.toString()
            isShopperApp = packageName == SHOPPER_PACKAGE

            // Por enquanto, vamos processar eventos de qualquer app
            val rootNode = service.rootInActiveWindow ?: return
            searchForPrices(rootNode)
            rootNode.recycle()
        } catch (e: Exception) {
            Log.e(TAG, "Error processing accessibility event", e)
        }
    }

    fun setTargetPrice(price: String) {
        targetPrice = price
        Log.d(TAG, "Target price set to: $price")
    }

    private fun searchForPrices(node: AccessibilityNodeInfo) {
        try {
            // Verifica o texto do nó atual
            val nodeText = node.text?.toString() ?: ""
            if (nodeText.matches(MainActivity.PRICE_REGEX)) {
                handlePriceFound(nodeText, node)
            }

            // Busca recursivamente em todos os nós filhos
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                searchForPrices(child)
                child.recycle()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error processing node", e)
        }
    }

    private fun handlePriceFound(price: String, node: AccessibilityNodeInfo) {
        Log.d(TAG, "Found price: $price in ${if (isShopperApp) "Shopper" else "other app"}")

        // Se temos um preço alvo e ele corresponde ao encontrado
        if (targetPrice == price) {
            Log.d(TAG, "Target price found!")
            // Aqui você pode adicionar lógica específica quando encontrar o preço
            // Por exemplo, notificar o usuário ou realizar alguma ação
        }

        // Para desenvolvimento/debug
        logNodeInfo(node)
    }

    private fun logNodeInfo(node: AccessibilityNodeInfo) {
        try {
            val className = node.className?.toString() ?: "unknown"
            val viewId = node.viewIdResourceName ?: "no-id"
            Log.d(TAG, """
                Price node details:
                Class: $className
                ViewId: $viewId
                Clickable: ${node.isClickable}
                Enabled: ${node.isEnabled}
                Package: ${node.packageName}
            """.trimIndent())
        } catch (e: Exception) {
            Log.e(TAG, "Error logging node info", e)
        }
    }

    fun isTargetApp(): Boolean {
        return isShopperApp
    }

    fun clearTargetPrice() {
        targetPrice = null
        Log.d(TAG, "Target price cleared")
    }
}