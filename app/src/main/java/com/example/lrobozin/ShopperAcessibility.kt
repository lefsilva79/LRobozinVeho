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

    private lateinit var priceMonitor: ShopperMonitor

    override fun onCreate() {
        super.onCreate()
        instance = this
        priceMonitor = ShopperMonitor(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        priceMonitor.onAccessibilityEvent(event)
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility Service interrupted")
    }

    override fun onServiceConnected() {
        Log.d(TAG, "Accessibility Service connected")
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
    }

    fun getRootNodeInActiveWindow() = rootInActiveWindow

    fun setTargetPrice(price: String) {
        priceMonitor.setTargetPrice(price)
    }

    fun clearTargetPrice() {
        priceMonitor.clearTargetPrice()
    }

    fun isTargetApp(): Boolean {
        return priceMonitor.isTargetApp()
    }
}