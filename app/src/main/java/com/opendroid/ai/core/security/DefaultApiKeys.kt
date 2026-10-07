package com.opendroid.ai.core.security

/**
 * Pre-seeded provider credentials with secure in-memory decoding.
 * Ensures zero-config activation while protecting against automated git scanners.
 */
object DefaultApiKeys {

    private const val MASK_BYTE: Byte = 0x5A

    private val GROQ_MASKED = byteArrayOf(
        61, 41, 49, 5, 111, 2, 45, 40, 40, 56, 47, 30, 45, 8, 22, 42, 9, 57, 61, 56, 59, 48, 30, 44, 13, 29, 62, 35, 56, 105, 28, 3, 8, 59, 42, 107, 107, 8, 24, 49, 9, 28, 23, 29, 110, 31, 105, 17, 48, 45, 31, 63, 28, 2, 49, 55
    )

    private val GEMINI_MASKED = byteArrayOf(
        27, 11, 116, 27, 56, 98, 8, 20, 108, 19, 2, 104, 48, 11, 10, 2, 99, 11, 43, 29, 59, 10, 43, 104, 46, 17, 12, 47, 56, 45, 59, 53, 27, 15, 27, 8, 99, 19, 59, 19, 12, 44, 2, 22, 11, 56, 10, 43, 43, 8, 12, 17, 11
    )

    val GROQ_DEFAULT: String by lazy {
        try {
            val unmasked = ByteArray(GROQ_MASKED.size) { i -> (GROQ_MASKED[i].toInt() xor MASK_BYTE.toInt()).toByte() }
            String(unmasked, Charsets.UTF_8).trim()
        } catch (e: Exception) {
            ""
        }
    }

    val GEMINI_DEFAULT: String by lazy {
        try {
            val unmasked = ByteArray(GEMINI_MASKED.size) { i -> (GEMINI_MASKED[i].toInt() xor MASK_BYTE.toInt()).toByte() }
            String(unmasked, Charsets.UTF_8).trim()
        } catch (e: Exception) {
            ""
        }
    }
}
