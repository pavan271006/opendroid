package com.opendroid.ai.accessibility

/**
 * Telegram automator backed by UniversalAppAutomator.
 * Eliminates all hardcoded package and view IDs; operates dynamically across
 * official Telegram, Telegram X, Nekox, Plus Messenger, and all forks.
 */
object TelegramAutomator {
    suspend fun automateSend(message: String): Boolean {
        return UniversalAppAutomator.automateInputAndSend(message)
    }
}
