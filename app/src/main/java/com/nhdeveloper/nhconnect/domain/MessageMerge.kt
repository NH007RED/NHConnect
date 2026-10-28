package com.nhdeveloper.nhconnect.domain

import com.nhdeveloper.nhconnect.data.ChatMessage
import com.nhdeveloper.nhconnect.data.SendState

object MessageMerge {
    /**
     * Merges a message snapshot from the server into the locally cached row.
     * - Receipts live in a separate node, so they are carried over from the cache.
     * - Our own message stays SENDING until the write is acknowledged (RTDB raises local events early).
     * - Writing the same id twice is idempotent: the merge simply overwrites the row.
     */
    fun merge(existing: ChatMessage?, remote: ChatMessage, receipt: Pair<Long?, Long?>?): ChatMessage {
        val delivered = receipt?.first ?: existing?.deliveredAt
        val read = receipt?.second ?: existing?.readAt
        val state = if (existing?.sendState == SendState.SENDING) SendState.SENDING else SendState.SENT
        return remote.copy(deliveredAt = delivered, readAt = read, sendState = state)
    }
}

object Reactions {
    private const val PAIR = '\u0001'
    private const val KV = '\u0002'

    fun encode(map: Map<String, String>): String = map.entries.joinToString(PAIR.toString()) { "${it.key}$KV${it.value}" }

    fun decode(s: String): Map<String, String> {
        if (s.isEmpty()) return emptyMap()
        return s.split(PAIR).mapNotNull {
            val i = it.indexOf(KV)
            if (i <= 0) null else it.substring(0, i) to it.substring(i + 1)
        }.toMap()
    }
}
