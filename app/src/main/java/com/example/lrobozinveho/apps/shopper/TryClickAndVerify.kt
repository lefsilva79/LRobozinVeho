package com.example.lrobozinveho.apps.shopper

import android.view.accessibility.AccessibilityNodeInfo
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.TimeZone

class TryClickAndVerify {
    companion object {
        private const val TAG = "TryClickAndVerify"
    }

    private var currentContainer: AccessibilityNodeInfo? = null

    fun clickClaimInCurrentContainer(container: AccessibilityNodeInfo?): Boolean {
        if (container == null) return false

        currentContainer = container

        Log.d(
            TAG, """
            ====== BUSCANDO BOTÃO NO CONTAINER VALIDADO =====
            Current Date and Time (UTC): ${getCurrentUTCDateTime()}
            =======================================
        """.trimIndent())

        try {
            return findClaimButtonInContainer(container)
        } catch (e: Exception) {
            Log.e(
                TAG, """
                ❌ ERRO AO BUSCAR BOTÃO
                Current Date and Time (UTC): ${getCurrentUTCDateTime()}
                Erro: ${e.message}
            """.trimIndent())
        }

        return false
    }

    private fun findClaimButtonInContainer(container: AccessibilityNodeInfo): Boolean {
        if (isClaimButton(container)) {
            Log.d(
                TAG, """
            ✅ BOTÃO CLAIM ENCONTRADO
            Current Date and Time (UTC): ${getCurrentUTCDateTime()}
            Texto: ${container.text}
            Descrição: ${container.contentDescription}
            Classe: ${container.className}
        """.trimIndent())

            if (container.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                Log.d(
                    TAG, """
                🎯 CLIQUE NO CLAIM EXECUTADO COM SUCESSO
                Current Date and Time (UTC): ${getCurrentUTCDateTime()}
            """.trimIndent())
                return true
            } else {
                Log.d(
                    TAG, """
                ❌ FALHA AO CLICAR NO CLAIM
                Current Date and Time (UTC): ${getCurrentUTCDateTime()}
            """.trimIndent())
                return false
            }
        }

        for (i in 0 until container.childCount) {
            val child = container.getChild(i) ?: continue
            if (findClaimButtonInContainer(child)) {
                return true
            }
            child.recycle()
        }

        return false
    }

    private fun findAndClickClaimButton(): Boolean {
        if (currentContainer == null) {
            Log.d(TAG, "Current Date and Time (UTC): ${getCurrentUTCDateTime()}")
            return false
        }

        val claimButton = findNodeByText(currentContainer!!, "Claim")
        if (claimButton == null) {
            Log.d(TAG, "Current Date and Time (UTC): ${getCurrentUTCDateTime()}")
            return false
        }

        if (!claimButton.isClickable) {
            var clickableParent = claimButton.parent
            while (clickableParent != null && !clickableParent.isClickable) {
                clickableParent = clickableParent.parent
            }

            if (clickableParent != null && clickableParent.isClickable) {
                Log.d(TAG, "Current Date and Time (UTC): ${getCurrentUTCDateTime()}")
                return clickableParent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
        } else {
            Log.d(TAG, "Current Date and Time (UTC): ${getCurrentUTCDateTime()}")
            return claimButton.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        }

        Log.d(TAG, "Current Date and Time (UTC): ${getCurrentUTCDateTime()}")
        return false
    }

    private fun findNodeByText(node: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        if (node.text?.toString() == text) {
            Log.d(TAG, "Current Date and Time (UTC): ${getCurrentUTCDateTime()}")
            return node
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findNodeByText(child, text)
            if (result != null) {
                Log.d(TAG, "Current Date and Time (UTC): ${getCurrentUTCDateTime()}")
                return result
            }
        }

        Log.d(TAG, "Current Date and Time (UTC): ${getCurrentUTCDateTime()}")
        return null
    }

    private fun isClaimButton(node: AccessibilityNodeInfo): Boolean {
        return node.isClickable && (
                node.text?.toString()?.contains("Claim", ignoreCase = true) == true ||
                        node.contentDescription?.toString()?.contains("Claim", ignoreCase = true) == true
                )
    }

    private fun getCurrentUTCDateTime(): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
        dateFormat.timeZone = TimeZone.getTimeZone("UTC")
        return dateFormat.format(Date())
    }

}