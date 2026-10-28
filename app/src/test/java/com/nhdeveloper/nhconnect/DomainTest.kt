package com.nhdeveloper.nhconnect

import com.nhdeveloper.nhconnect.data.ChatMessage
import com.nhdeveloper.nhconnect.data.SendState
import com.nhdeveloper.nhconnect.domain.Backoff
import com.nhdeveloper.nhconnect.domain.ChatIds
import com.nhdeveloper.nhconnect.domain.MessageMerge
import com.nhdeveloper.nhconnect.domain.Reactions
import com.nhdeveloper.nhconnect.domain.Validators
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DomainTest {
    private fun m(state: SendState = SendState.SENT, d: Long? = null, r: Long? = null) = ChatMessage(
        id = "x", chatId = "A_B", senderId = "A", recipientId = "B", originalText = "hi",
        sourceLanguage = "en", targetLanguage = "bn", createdAt = 5L, sendState = state, deliveredAt = d, readAt = r
    )

    @Test fun chatIdIsSymmetric() {
        assertEquals(ChatIds.chatId("b", "a"), ChatIds.chatId("a", "b"))
        assertEquals("a_b", ChatIds.chatId("b", "a"))
    }

    @Test fun otherUidResolvesBothSides() {
        assertEquals("b", ChatIds.otherUid("a_b", "a"))
        assertEquals("a", ChatIds.otherUid("a_b", "b"))
        assertNull(ChatIds.otherUid("a_b", "c"))
    }

    @Test fun backoffDoublesAndCaps() {
        assertEquals(1_000, Backoff.delayMs(0)); assertEquals(2_000, Backoff.delayMs(1)); assertEquals(8_000, Backoff.delayMs(3))
        assertEquals(60_000, Backoff.delayMs(30))
    }

    @Test fun mergeKeepsReceiptsAndPendingState() {
        val existing = m(SendState.SENDING, d = 10L, r = null)
        val merged = MessageMerge.merge(existing, m(), null)
        assertEquals(SendState.SENDING, merged.sendState); assertEquals(10L, merged.deliveredAt)
    }

    @Test fun mergeIsIdempotent() {
        val once = MessageMerge.merge(null, m(), 1L to 2L)
        val twice = MessageMerge.merge(once, m(), 1L to 2L)
        assertEquals(once, twice)
    }

    @Test fun receiptFromServerWins() {
        val merged = MessageMerge.merge(m(d = 1L), m(), 5L to 6L)
        assertEquals(5L, merged.deliveredAt); assertEquals(6L, merged.readAt)
    }

    @Test fun reactionsRoundTrip() {
        val map = mapOf("u1" to "👍", "u2" to "❤️")
        assertEquals(map, Reactions.decode(Reactions.encode(map)))
        assertTrue(Reactions.decode("").isEmpty())
    }

    @Test fun validators() {
        assertTrue(Validators.username("coumi_01")); assertFalse(Validators.username("a b")); assertFalse(Validators.username("ab"))
        assertTrue(Validators.email("a@b.co")); assertFalse(Validators.email("nope"))
        assertFalse(Validators.password("short")); assertTrue(Validators.messageText("hi"))
        assertFalse(Validators.messageText("   ")); assertFalse(Validators.messageText("x".repeat(4001)))
    }
}
