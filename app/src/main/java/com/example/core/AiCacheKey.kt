package com.example.core

import java.security.MessageDigest

/**
 * Cache key of one model request: a SHA-256 over the model name and the exact prompt (system instruction and
 * delimited user input). Because the whole prompt is hashed, changing the prompt wording, the language, the
 * mode, the specialty or the input text automatically produces a different key, so a stale answer can never
 * be served for a different question.
 */
object AiCacheKey {
    fun of(model: String, systemInstruction: String, userContent: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(model.trim().toByteArray(Charsets.UTF_8))
        digest.update(0)
        digest.update(systemInstruction.toByteArray(Charsets.UTF_8))
        digest.update(0)
        digest.update(userContent.toByteArray(Charsets.UTF_8))
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
