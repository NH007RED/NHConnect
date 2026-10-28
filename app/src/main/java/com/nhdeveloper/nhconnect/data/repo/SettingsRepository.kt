package com.nhdeveloper.nhconnect.data.repo

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.google.firebase.database.FirebaseDatabase
import com.nhdeveloper.nhconnect.data.AppSettings
import com.nhdeveloper.nhconnect.data.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "nh_settings")

class SettingsRepository(private val context: Context, private val db: FirebaseDatabase) {
    private object K {
        val theme = stringPreferencesKey("theme")
        val auto = booleanPreferencesKey("auto_translate")
        val showOrig = booleanPreferencesKey("show_original_default")
        val notif = booleanPreferencesKey("notifications")
        val preview = booleanPreferencesKey("notification_preview")
        val presence = booleanPreferencesKey("share_presence")
        val onboarded = booleanPreferencesKey("onboarding_done")
        val draftLang = stringPreferencesKey("draft_language")
        val draftCountry = stringPreferencesKey("draft_country")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            themeMode = runCatching { ThemeMode.valueOf(p[K.theme] ?: "SYSTEM") }.getOrDefault(ThemeMode.SYSTEM),
            autoTranslate = p[K.auto] ?: true,
            showOriginalByDefault = p[K.showOrig] ?: false,
            notificationsEnabled = p[K.notif] ?: true,
            notificationPreview = p[K.preview] ?: true,
            sharePresence = p[K.presence] ?: true,
            onboardingDone = p[K.onboarded] ?: false,
            draftLanguage = p[K.draftLang] ?: "en",
            draftCountry = p[K.draftCountry] ?: "IN"
        )
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.dataStore.edit { block(it) }
    }

    suspend fun setTheme(m: ThemeMode) = edit { it[K.theme] = m.name }
    suspend fun setAutoTranslate(v: Boolean) = edit { it[K.auto] = v }
    suspend fun setShowOriginalDefault(v: Boolean) = edit { it[K.showOrig] = v }
    suspend fun setSharePresence(v: Boolean) = edit { it[K.presence] = v }
    suspend fun setOnboardingDone() = edit { it[K.onboarded] = true }
    suspend fun setDraft(language: String?, country: String?) = edit {
        if (language != null) it[K.draftLang] = language
        if (country != null) it[K.draftCountry] = country
    }

    suspend fun setNotifications(uid: String?, enabled: Boolean) {
        edit { it[K.notif] = enabled }
        pushNotificationPrefs(uid)
    }

    suspend fun setNotificationPreview(uid: String?, v: Boolean) {
        edit { it[K.preview] = v }
        pushNotificationPrefs(uid)
    }

    /** The Cloud Function reads these two flags before sending a push. */
    suspend fun pushNotificationPrefs(uid: String?) {
        if (uid == null) return
        val s = settings.first()
        db.getReference("userSettings/$uid").setValue(
            mapOf("notificationsEnabled" to s.notificationsEnabled, "notificationPreview" to s.notificationPreview)
        )
    }
}
