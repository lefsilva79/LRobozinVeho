package com.example.lrobozinveho

import android.view.accessibility.AccessibilityNodeInfo
import android.util.Log
import java.lang.ref.WeakReference
import java.util.WeakHashMap
import java.util.ArrayDeque

class TryClickAndVerify {
    companion object {
        private const val TAG = "TryClickAndVerify"
        private const val CLAIM_BUTTON_ID = "claim-offer-button"
        private const val MAX_SEARCH_LEVELS = 3

        private val PRICE_CONTAINER_IDS = setOf(
            "price-container",
            "offer-price",
            "price-display"
        )

        private val nodeCache = WeakHashMap<String, WeakReference<AccessibilityNodeInfo>>()

        private fun getCachedNode(id: String): AccessibilityNodeInfo? {
            return nodeCache[id]?.get()
        }

        private fun cacheNode(id: String, node: AccessibilityNodeInfo) {
            nodeCache[id] = WeakReference(node)
        }
    }

    fun searchAndClickPrice(rootNode: AccessibilityNodeInfo?, targetValue: String): Boolean {
        if (rootNode == null) return false

        val startTime = System.currentTimeMillis()
        val targetNumber = targetValue.replace("$", "").toIntOrNull() ?: return false

        try {
            // Usando formato compatível com API 24
            val currentTime = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
                .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
                .format(java.util.Date())

            Log.d(TAG, """
            ====== PROCURANDO VALOR E BOTÃO CLAIM =====
            Valor procurado: $targetNumber
            Timestamp início: $startTime
            Data/Hora UTC: $currentTime
            =======================================
        """.trimIndent())

            val priceNodes = findNodesWithValidPrice(rootNode, targetNumber)
            val findTime = System.currentTimeMillis()
            Log.d(TAG, "⏱️ Tempo para encontrar nós: ${findTime - startTime}ms")

            for (priceNode in priceNodes) {
                val priceText = priceNode.text?.toString() ?: continue
                Log.d(TAG, "Encontrado texto com valor válido: $priceText")

                val claimButton = findClaimButton(priceNode)

                if (claimButton != null && claimButton.isClickable) {
                    val clickTime = System.currentTimeMillis()
                    Log.d(TAG, "✅ Botão Claim encontrado - MODO TESTE (SEM CLIQUE) (${clickTime - findTime}ms)")
                    // Comentado para testes - desabilita o clique real
                    //val clicked = claimButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    val clicked = true // Alterado para false para não disparar a notificação
                    val endTime = System.currentTimeMillis()

                    Log.d(TAG, """
        ⏱️ Tempos de execução (MODO TESTE):
        Busca: ${findTime - startTime}ms
        Tempo de verificação: ${endTime - clickTime}ms
        Total: ${endTime - startTime}ms
        ⚠️ CLIQUE DESABILITADO - Apenas monitorando
    """.trimIndent())

                    return clicked // Retornando false evita a notificação de "encontrado e clicado"
                }
            }

            Log.d(TAG, "❌ Nenhum botão Claim encontrado para valor >= $targetNumber")
            logFullTreeStructure(rootNode)
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao tentar encontrar e clicar", e)
        }

        return false
    }

    private fun findNodesWithValidPrice(root: AccessibilityNodeInfo, targetValue: Int): List<AccessibilityNodeInfo> {
        val nodes = mutableListOf<AccessibilityNodeInfo>()

        // Tenta encontrar diretamente pelo ID primeiro
        PRICE_CONTAINER_IDS.forEach { id ->
            getCachedNode(id)?.let { cachedNode ->
                if (processPrice(cachedNode.text?.toString() ?: "", targetValue, cachedNode, nodes)) {
                    return nodes
                }
            }

            findNodeById(root, id)?.let { priceNode ->
                priceNode.text?.toString()?.let { text ->
                    if (processPrice(text, targetValue, priceNode, nodes)) {
                        cacheNode(id, priceNode)
                        return nodes
                    }
                }
            }
        }

        // Se não encontrou pelo ID, faz busca mais ampla
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        val visited = HashSet<AccessibilityNodeInfo>()
        queue.add(root)

        while (queue.isNotEmpty() && nodes.isEmpty()) {
            val node = queue.removeFirst()

            if (!visited.add(node)) continue

            node.text?.toString()?.let { text ->
                if (text.contains("$")) {
                    processPrice(text, targetValue, node, nodes)
                }
            }

            if (nodes.isEmpty()) {
                for (i in 0 until node.childCount) {
                    node.getChild(i)?.let { queue.add(it) }
                }
            }
        }

        return nodes
    }

    private fun processPrice(text: String, targetValue: Int, node: AccessibilityNodeInfo, nodes: MutableList<AccessibilityNodeInfo>): Boolean {
        if (text.contains("$")) {
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
                    Classe: ${node.className}
                    Parent ID: ${node.parent?.viewIdResourceName}
                """.trimIndent())
                return true
            }
        }
        return false
    }

    private fun findNodeById(root: AccessibilityNodeInfo, targetId: String): AccessibilityNodeInfo? {
        getCachedNode(targetId)?.let { return it }

        if (root.viewIdResourceName?.endsWith(targetId) == true) {
            cacheNode(targetId, root)
            return root
        }

        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)

        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()

            if (node.viewIdResourceName?.endsWith(targetId) == true) {
                cacheNode(targetId, node)
                return node
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }

        return null
    }

    private fun findClaimButton(startNode: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        Log.d(TAG, "🔍 Iniciando busca por botão Claim em: ${startNode.viewIdResourceName}")

        // 1. Tentar cache primeiro
        getCachedNode(CLAIM_BUTTON_ID)?.let {
            if (it.isClickable) {
                Log.d(TAG, "🎯 Botão Claim encontrado no cache")
                return it
            }
        }

        // 2. Buscar na raiz da árvore
        val rootNode = getRootNode(startNode)

        // 3. Buscar por ID exato
        findNodeById(rootNode, CLAIM_BUTTON_ID)?.let { button ->
            if (button.isClickable) {
                Log.d(TAG, "🎯 Botão Claim encontrado por ID exato")
                cacheNode(CLAIM_BUTTON_ID, button)
                return button
            }
        }

        // 4. Buscar por ID parcial
        findButtonByPartialId(rootNode, "claim")?.let { button ->
            if (button.isClickable) {
                Log.d(TAG, "🎯 Botão Claim encontrado por ID parcial")
                return button
            }
        }

        // 5. Buscar por texto/descrição
        findClickableWithText(rootNode, "claim")?.let { button ->
            Log.d(TAG, "🎯 Botão Claim encontrado por texto")
            return button
        }

        // 6. Busca na hierarquia próxima
        var currentNode = startNode
        var searchLevel = 0

        while (currentNode.parent != null && searchLevel < MAX_SEARCH_LEVELS) {
            currentNode = currentNode.parent
            logNodeStructure(currentNode, searchLevel)

            for (i in 0 until currentNode.childCount) {
                currentNode.getChild(i)?.let { sibling ->
                    if (sibling != startNode) {
                        findClaimButtonQuick(sibling)?.let {
                            Log.d(TAG, "🎯 Botão Claim encontrado próximo ao valor (nível: $searchLevel)")
                            return it
                        }
                    }
                }
            }
            searchLevel++
        }

        Log.d(TAG, "❌ Busca por botão Claim falhou")
        return null
    }

    private fun findClaimButtonQuick(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.viewIdResourceName?.endsWith(CLAIM_BUTTON_ID) == true && node.isClickable) {
            logClaimButtonFound(node)
            return node
        }

        if (node.className?.contains("Button") == true &&
            node.isClickable &&
            (node.text?.toString()?.equals("Claim", ignoreCase = true) == true ||
                    node.contentDescription?.toString()?.equals("Claim", ignoreCase = true) == true)) {
            logClaimButtonFound(node)
            return node
        }

        return null
    }

    private fun getRootNode(node: AccessibilityNodeInfo): AccessibilityNodeInfo {
        var current = node
        while (current.parent != null) {
            current = current.parent
        }
        return current
    }

    private fun findButtonByPartialId(root: AccessibilityNodeInfo, partialId: String): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)

        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()

            if (node.viewIdResourceName?.contains(partialId, ignoreCase = true) == true) {
                return node
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }

    private fun findClickableWithText(root: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)

        while (queue.isNotEmpty()) {
            val node = queue.removeFirst()

            if (node.isClickable &&
                (node.text?.toString()?.contains(text, ignoreCase = true) == true ||
                        node.contentDescription?.toString()?.contains(text, ignoreCase = true) == true)) {
                return node
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return null
    }

    private fun logNodeStructure(node: AccessibilityNodeInfo, level: Int) {
        Log.d(TAG, """
            🌳 Nível $level:
            ID: ${node.viewIdResourceName}
            Texto: ${node.text}
            Descrição: ${node.contentDescription}
            Clicável: ${node.isClickable}
            Classe: ${node.className}
            Filhos: ${node.childCount}
        """.trimIndent())
    }

    private fun logFullTreeStructure(root: AccessibilityNodeInfo, level: Int = 0) {
        val indent = "  ".repeat(level)
        Log.d(TAG, "$indent📍 Nó: ${node2String(root)}")

        for (i in 0 until root.childCount) {
            root.getChild(i)?.let { logFullTreeStructure(it, level + 1) }
        }
    }

    private fun node2String(node: AccessibilityNodeInfo): String {
        return """
            {
                id: ${node.viewIdResourceName},
                text: ${node.text},
                desc: ${node.contentDescription},
                click: ${node.isClickable},
                class: ${node.className},
                child: ${node.childCount}
            }
        """.trimIndent()
    }

    private fun logClaimButtonFound(node: AccessibilityNodeInfo) {
        Log.d(TAG, """
            🎯 Botão Claim identificado:
            Texto: ${node.text}
            Descrição: ${node.contentDescription}
            ID: ${node.viewIdResourceName}
            Classe: ${node.className}
            Clicável: ${node.isClickable}
            Parent: ${node.parent?.viewIdResourceName}
        """.trimIndent())
    }

    private fun recycleNodes(nodes: List<AccessibilityNodeInfo>) {
        nodes.forEach { it.recycle() }
    }
}