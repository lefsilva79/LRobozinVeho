package com.example.lrobozinveho

import android.view.accessibility.AccessibilityNodeInfo
import android.util.Log

class TryClickAndVerify {
    companion object {
        private const val TAG = "TryClickAndVerify"

        // Cache de IDs conhecidos para otimizar a busca
        private val PRICE_CONTAINER_IDS = setOf(
            "price-container",
            "offer-price",
            "price-display"
        )

        private const val CLAIM_BUTTON_ID = "claim-offer-button"
        private const val MAX_SEARCH_LEVELS = 3 // Reduzido de 5 para 3
    }

    fun searchAndClickPrice(rootNode: AccessibilityNodeInfo?, targetValue: String): Boolean {
        if (rootNode == null) return false

        val startTime = System.currentTimeMillis()

        // Converte o valor alvo para número
        val targetNumber = targetValue.replace("$", "").toIntOrNull() ?: return false

        try {
            Log.d(TAG, """
                ====== PROCURANDO VALOR E BOTÃO CLAIM =====
                Valor procurado: $targetNumber
                Timestamp início: $startTime
                =======================================
            """.trimIndent())

            // Procura por qualquer texto que contenha um valor adequado
            val priceNodes = findNodesWithValidPrice(rootNode, targetNumber)

            val findTime = System.currentTimeMillis()
            Log.d(TAG, "⏱️ Tempo para encontrar nós: ${findTime - startTime}ms")

            for (priceNode in priceNodes) {
                val priceText = priceNode.text?.toString() ?: continue
                Log.d(TAG, "Encontrado texto com valor válido: $priceText")

                // Tenta encontrar o botão Claim primeiro pelo ID
                var claimButton = findNodeById(priceNode, CLAIM_BUTTON_ID)

                // Se não encontrou pelo ID, procura na hierarquia
                if (claimButton == null || !claimButton.isClickable) {
                    claimButton = findClaimButton(priceNode)
                }

                if (claimButton != null && claimButton.isClickable) {
                    val clickTime = System.currentTimeMillis()
                    Log.d(TAG, "✅ Botão Claim encontrado - Tentando clicar (${clickTime - findTime}ms)")
                    val clicked = claimButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    val endTime = System.currentTimeMillis()

                    Log.d(TAG, """
                        ⏱️ Tempos de execução:
                        Busca: ${findTime - startTime}ms
                        Clique: ${endTime - clickTime}ms
                        Total: ${endTime - startTime}ms
                    """.trimIndent())

                    return clicked
                }
            }

            Log.d(TAG, "❌ Nenhum botão Claim encontrado para valor >= $targetNumber")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao tentar encontrar e clicar", e)
        }

        return false
    }

    private fun findNodesWithValidPrice(root: AccessibilityNodeInfo, targetValue: Int): List<AccessibilityNodeInfo> {
        val nodes = mutableListOf<AccessibilityNodeInfo>()
        val queue = ArrayList<AccessibilityNodeInfo>()
        val visited = HashSet<AccessibilityNodeInfo>()
        queue.add(root)

        while (queue.isNotEmpty() && nodes.isEmpty()) { // Para assim que encontrar um match
            val node = queue.removeAt(0)

            if (!visited.add(node)) continue // Evita loops

            // Verifica se é um container de preço conhecido
            val isRelevantContainer = node.viewIdResourceName?.let { id ->
                PRICE_CONTAINER_IDS.any { id.contains(it, ignoreCase = true) }
            } ?: false

            // Só processa se for um container relevante ou tiver "$"
            if (isRelevantContainer || node.text?.toString()?.contains("$") == true) {
                val text = node.text?.toString()

                if (text != null && text.contains("$")) {
                    val firstNumber = text.replace("$", "")
                        .split("-")[0]
                        .trim()
                        .toIntOrNull()

                    if (firstNumber != null && firstNumber >= targetValue) {
                        nodes.add(node)
                        Log.d(TAG, """
                            💲 Encontrado valor válido:
                            Texto completo: $text
                            Primeiro valor: $firstNumber
                            Valor procurado: $targetValue
                            ID: ${node.viewIdResourceName}
                        """.trimIndent())
                    }
                }
            }

            // Só adiciona filhos se ainda não encontrou nada
            if (nodes.isEmpty()) {
                for (i in 0 until node.childCount) {
                    node.getChild(i)?.let { queue.add(it) }
                }
            }
        }

        return nodes
    }

    private fun findNodeById(root: AccessibilityNodeInfo, targetId: String): AccessibilityNodeInfo? {
        if (root.viewIdResourceName?.endsWith(targetId) == true) {
            return root
        }

        for (i in 0 until root.childCount) {
            root.getChild(i)?.let { child ->
                findNodeById(child, targetId)?.let { return it }
            }
        }

        return null
    }

    private fun findClaimButton(startNode: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var parent = startNode.parent
        var searchLevel = 0

        while (parent != null && searchLevel < MAX_SEARCH_LEVELS) {
            val claimButton = searchForClaimInNode(parent)
            if (claimButton != null) {
                Log.d(TAG, "🎯 Botão Claim encontrado próximo ao valor (nível: $searchLevel)")
                return claimButton
            }
            parent = parent.parent
            searchLevel++
        }

        return searchForClaimInNode(startNode)
    }

    private fun searchForClaimInNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val queue = ArrayList<AccessibilityNodeInfo>()
        val visited = HashSet<AccessibilityNodeInfo>()
        queue.add(node)

        while (queue.isNotEmpty()) {
            val current = queue.removeAt(0)

            if (visited.add(current)) {
                if (isClaimButton(current)) {
                    Log.d(TAG, "🔍 Encontrado botão Claim: ${current.text}")
                    return current
                }

                for (i in 0 until current.childCount) {
                    current.getChild(i)?.let { queue.add(it) }
                }
            }
        }
        return null
    }

    private fun isClaimButton(node: AccessibilityNodeInfo): Boolean {
        val isButton = node.className?.contains("Button") == true
        val isClickable = node.isClickable
        val hasClaimText = node.text?.toString() == "Claim"
        val hasClaimDescription = node.contentDescription?.toString() == "Claim"
        val hasClaimId = node.viewIdResourceName?.endsWith(CLAIM_BUTTON_ID) == true

        if (isButton && isClickable && (hasClaimText || hasClaimDescription || hasClaimId)) {
            Log.d(TAG, """
                🎯 Botão Claim identificado:
                Texto: ${node.text}
                Descrição: ${node.contentDescription}
                ID: ${node.viewIdResourceName}
                Clicável: $isClickable
            """.trimIndent())
            return true
        }
        return false
    }

    private fun recycleNodes(nodes: List<AccessibilityNodeInfo>) {
        nodes.forEach { it.recycle() }
    }
}