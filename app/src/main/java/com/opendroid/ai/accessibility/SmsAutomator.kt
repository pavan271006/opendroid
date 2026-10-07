package com.opendroid.ai.accessibility

/**
 * Universal SMS and messaging automator.
 * Dynamically clicks send or dispatches IME submit on Google Messages,
 * Samsung Messages, Nothing Messages, or any default SMS handler.
 */
object SmsAutomator {
    suspend fun automateSend(): Boolean {
        val service = OpenDroidAccessibilityService.getInstance() ?: return false
        return UniversalAppAutomator.automateInputAndSend("") || service.performImeEnter()
    }
}
