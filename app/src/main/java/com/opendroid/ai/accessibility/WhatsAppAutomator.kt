package com.opendroid.ai.accessibility

/**
 * WhatsApp automator backed by UniversalAppAutomator.
 * Eliminates all hardcoded package and view IDs; operates dynamically across
 * WhatsApp, WhatsApp Business, GBWhatsApp, and dual-app instances.
 */
object WhatsAppAutomator {
    suspend fun automateSend(message: String): Boolean {
        return UniversalAppAutomator.automateInputAndSend(message)
    }
}
