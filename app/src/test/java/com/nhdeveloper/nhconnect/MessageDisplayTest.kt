package com.nhdeveloper.nhconnect

import com.nhdeveloper.nhconnect.data.ChatMessage
import com.nhdeveloper.nhconnect.data.TranslationStatus
import com.nhdeveloper.nhconnect.domain.MessageDisplay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageDisplayTest {
    private fun msg(
        status: TranslationStatus, translated: String? = null, target: String = "bn",
        source: String = "en", detected: String? = null, sender: String = "A", deleted: Boolean = false
    ) = ChatMessage(
        id = "m1", chatId = "A_B", senderId = sender, recipientId = if (sender == "A") "B" else "A",
        originalText = "Hello, how are you?", sourceLanguage = source, targetLanguage = target,
        translatedText = translated, translationStatus = status, detectedLanguage = detected,
        createdAt = 1L, deleted = deleted
    )

    private fun decide(m: ChatMessage, lang: String = "bn", auto: Boolean = true, showOrig: Boolean = false, toggled: Boolean = false, viewer: String = "B") =
        MessageDisplay.decide(m, viewer, lang, auto, showOrig, toggled)

    @Test fun completedTranslationIsShownToRecipient() {
        val d = decide(msg(TranslationStatus.COMPLETED, "হ্যালো, তুমি কেমন আছো?"))
        assertEquals("হ্যালো, তুমি কেমন আছো?", d.text)
        assertTrue(d.showingTranslation); assertTrue(d.canToggle)
    }

    @Test fun toggleShowsOriginal() {
        val d = decide(msg(TranslationStatus.COMPLETED, "অনুবাদ"), toggled = true)
        assertEquals("Hello, how are you?", d.text); assertFalse(d.showingTranslation)
    }

    @Test fun showOriginalByDefaultStartsWithOriginal() {
        assertEquals("Hello, how are you?", decide(msg(TranslationStatus.COMPLETED, "অনুবাদ"), showOrig = true).text)
    }

    @Test fun autoTranslateOffShowsOriginalButAllowsToggle() {
        val d = decide(msg(TranslationStatus.COMPLETED, "অনুবাদ"), auto = false)
        assertEquals("Hello, how are you?", d.text); assertTrue(d.canToggle)
    }

    @Test fun senderAlwaysSeesOriginal() {
        val d = decide(msg(TranslationStatus.COMPLETED, "অনুবাদ", sender = "A"), viewer = "A", lang = "en")
        assertEquals("Hello, how are you?", d.text); assertFalse(d.canToggle)
    }

    @Test fun pendingShowsOriginalWithIndicator() {
        val d = decide(msg(TranslationStatus.PENDING))
        assertEquals("Hello, how are you?", d.text); assertTrue(d.translating)
    }

    @Test fun failedKeepsOriginalReadableAndOffersRetry() {
        val d = decide(msg(TranslationStatus.FAILED))
        assertEquals("Hello, how are you?", d.text); assertTrue(d.failed); assertFalse(d.showingTranslation)
    }

    @Test fun completedWithoutTextIsNeverShownAsTranslation() {
        val d = decide(msg(TranslationStatus.COMPLETED, translated = null))
        assertEquals("Hello, how are you?", d.text); assertFalse(d.showingTranslation)
    }

    @Test fun messageAlreadyInViewerLanguageIsNotTranslated() {
        val d = decide(msg(TranslationStatus.SKIPPED, source = "bn", detected = "bn"))
        assertFalse(d.translating); assertFalse(d.failed); assertFalse(d.needsRetranslate)
    }

    @Test fun afterLanguageSwitchOldTranslationNeedsRetranslate() {
        val d = decide(msg(TranslationStatus.COMPLETED, "অনুবাদ", target = "bn", detected = "en"), lang = "hi")
        assertEquals("Hello, how are you?", d.text); assertTrue(d.needsRetranslate); assertFalse(d.canToggle)
    }

    @Test fun deletedMessage() {
        assertTrue(decide(msg(TranslationStatus.COMPLETED, "x", deleted = true)).deleted)
    }
}
