package com.nhdeveloper.nhconnect.domain

object ChatIds {
    /** Deterministic chat id for two users: sorted UIDs joined by "_". The security rules rely on this layout. */
    fun chatId(a: String, b: String): String = listOf(a, b).sorted().joinToString("_")

    fun otherUid(chatId: String, me: String): String? = when {
        chatId.startsWith(me + "_") -> chatId.removePrefix(me + "_")
        chatId.endsWith("_" + me) -> chatId.removeSuffix("_" + me)
        else -> null
    }
}

object Backoff {
    /** Exponential backoff: 1s, 2s, 4s ... capped. */
    fun delayMs(attempt: Int, baseMs: Long = 1_000, maxMs: Long = 60_000): Long =
        minOf(maxMs, baseMs * (1L shl attempt.coerceIn(0, 20)))
}

object Validators {
    private val username = Regex("^[A-Za-z0-9_]{3,20}$")
    private val email = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

    fun username(s: String) = username.matches(s)
    fun email(s: String) = email.matches(s.trim())
    fun password(s: String) = s.length >= 8
    fun displayName(s: String) = s.trim().length in 1..50
    fun messageText(s: String) = s.trim().isNotEmpty() && s.trim().length <= 4000
}
