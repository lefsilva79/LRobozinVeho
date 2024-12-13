package com.example.lrobozinveho

import android.view.accessibility.AccessibilityNodeInfo
import android.util.Log

class TryClickAndVerify {
    companion object {
        private const val TAG = "TryClickAndVerify"
    }

    fun searchAndClickPrice(rootNode: AccessibilityNodeInfo?, targetValue: String): Boolean {
        if (rootNode == null) return false

        // Converte o valor alvo para número
        val targetNumber = targetValue.replace("$", "").toIntOrNull() ?: return false

        try {
            Log.d(TAG, """
                ====== PROCURANDO VALOR E BOTÃO CLAIM =====
                Valor procurado: $targetNumber
                =======================================
            """.trimIndent())

            // Procura por qualquer texto que contenha um valor adequado
            val priceNodes = findNodesWithValidPrice(rootNode, targetNumber)

            for (priceNode in priceNodes) {
                val priceText = priceNode.text?.toString() ?: continue
                Log.d(TAG, "Encontrado texto com valor válido: $priceText")

                // Procura o botão Claim mais próximo
                val claimButton = findClaimButton(priceNode)
                if (claimButton != null && claimButton.isClickable) {
                    Log.d(TAG, "✅ Botão Claim encontrado - Tentando clicar")
                    return claimButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)
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
        queue.add(root)

        while (queue.isNotEmpty()) {
            val node = queue.removeAt(0)
            val text = node.text?.toString()

            if (text != null && text.contains("$")) {
                // Extrai o primeiro número do texto (assumindo formato $XX-$YY)
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
                    """.trimIndent())
                }
            }

            // Continua procurando nos filhos
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }

        return nodes
    }

    private fun findClaimButton(startNode: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        // Procura no mesmo nível e acima
        var parent = startNode.parent
        var searchLevel = 0
        val maxSearchLevels = 5 // Limite de níveis para procurar acima

        while (parent != null && searchLevel < maxSearchLevels) {
            val claimButton = searchForClaimInNode(parent)
            if (claimButton != null) {
                Log.d(TAG, "🎯 Botão Claim encontrado próximo ao valor (nível: $searchLevel)")
                return claimButton
            }
            parent = parent.parent
            searchLevel++
        }

        // Se não encontrou subindo, tenta procurar descendo
        return searchForClaimInNode(startNode)
    }

    private fun searchForClaimInNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        val queue = ArrayList<AccessibilityNodeInfo>()
        queue.add(node)
        val visited = HashSet<AccessibilityNodeInfo>()

        while (queue.isNotEmpty()) {
            val current = queue.removeAt(0)

            if (visited.add(current)) { // Evita loops infinitos
                if (isClaimButton(current)) {
                    Log.d(TAG, "🔍 Encontrado botão Claim: ${current.text}")
                    return current
                }

                // Adiciona os filhos à fila
                for (i in 0 until current.childCount) {
                    current.getChild(i)?.let { child ->
                        queue.add(child)
                    }
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
        val hasClaimId = node.viewIdResourceName?.endsWith("claim-offer-button") == true

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