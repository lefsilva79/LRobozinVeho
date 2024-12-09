package com.example.lrobozin

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.example.lrobozin.apps.shopper.ShopperMonitor

class ShopperAccessibility : AccessibilityService() {
    companion object {
        private const val TAG = "ShopperAccessibility"
        private var instance: ShopperAccessibility? = null

        fun getInstance(): ShopperAccessibility? {
            return instance
        }
    }

    lateinit var priceMonitor: ShopperMonitor
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        priceMonitor = ShopperMonitor(this)
        Log.d(TAG, "Service created")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        Log.d(TAG, "Service destroyed")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        try {
            priceMonitor.onAccessibilityEvent(event)
        } catch (e: Exception) {
            Log.e(TAG, "Error processing accessibility event", e)
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility Service interrupted")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Accessibility Service connected")
        try {
            val config = AccessibilityServiceInfo().apply {
                eventTypes = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                        AccessibilityEvent.TYPE_VIEW_CLICKED or
                        AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
                flags = AccessibilityServiceInfo.FLAG_REQUEST_ENHANCED_WEB_ACCESSIBILITY or
                        AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                        AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
                notificationTimeout = 100
            }
            serviceInfo = config
            Log.d(TAG, "Service configuration applied successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error configuring service", e)
        }
    }

    fun getRootNodeInActiveWindow() = try {
        rootInActiveWindow
    } catch (e: Exception) {
        Log.e(TAG, "Error getting root node", e)
        null
    }
}