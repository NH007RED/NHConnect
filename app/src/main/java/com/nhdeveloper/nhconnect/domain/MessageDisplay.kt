package com.nhdeveloper.nhconnect.domain

import com.nhdeveloper.nhconnect.data.ChatMessage
import com.nhdeveloper.nhconnect.data.TranslationStatus

data class DisplayDecision(
    val text: String,
    val showingTranslation: Boolean = false,
    /** a valid translation exists for the viewer's language, so a "view original/translation" toggle makes sense */
    val canToggle: Boolean = false,
    val translating: Boolean = false,
    val failed: Boolean = false,
    /** translation exists but for a different language than the viewer's current one */
    val needsRetranslate: Boolean = false,
    val deleted: Boolean = false
)

object MessageDisplay {
    private fun base(code: String?): String = (code ?: "").lowercase().substringBefore('-')

    /**
     * Decides what the viewer sees. The original text is never replaced in storage; this only picks what to render.
     * A failed or pending translation never shows fabricated text.
     */
    fun decide(
        m: ChatMessage,
        viewerId: String,
        viewerLanguage: String,
        autoTranslate: Boolean,
        showOriginalByDefault: Boolean,
        toggled: Boolean
    ): DisplayDecision {
        if (m.deleted) return DisplayDecision(text = "", deleted = true)
        if (m.senderId == viewerId) return DisplayDecision(text = m.originalText)

        val hasTranslation = m.translationStatus == TranslationStatus.COMPLETED && !m.translatedText.isNullOrBlank()
        val matchesViewer = base(m.targetLanguage) == base(viewerLanguage)
        val sourceLang = base(m.detectedLanguage ?: m.sourceLanguage)
        val alreadyViewerLanguage = sourceLang == base(viewerLanguage)

        return when {
            m.translationStatus == TranslationStatus.PENDING || m.translationStatus == TranslationStatus.TRANSLATING ->
                DisplayDecision(text = m.originalText, translating = autoTranslate && !alreadyViewerLanguage)

            hasTranslation && matchesViewer -> {
                val defaultShowsTranslation = autoTranslate && !showOriginalByDefault
                val showTranslation = defaultShowsTranslation != toggled
                DisplayDecision(
                    text = if (showTranslation) m.translatedText!! else m.originalText,
                    showingTranslation = showTranslation,
                    canToggle = true
                )
            }

            m.translationStatus == TranslationStatus.FAILED && autoTranslate && !alreadyViewerLanguage ->
                DisplayDecision(text = m.originalText, failed = true)

            !matchesViewer && autoTranslate && !alreadyViewerLanguage &&
                (m.translationStatus == TranslationStatus.COMPLETED || m.translationStatus == TranslationStatus.SKIPPED) ->
                DisplayDecision(text = m.originalText, needsRetranslate = true)

            else -> DisplayDecision(text = m.originalText)
        }
    }
}
