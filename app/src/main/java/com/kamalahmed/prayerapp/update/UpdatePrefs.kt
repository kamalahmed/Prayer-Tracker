package com.kamalahmed.prayerapp.update

import android.content.Context
import androidx.core.content.edit
import com.kamalahmed.prayerapp.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Updater preferences and the cached GitHub response (SharedPreferences; small and synchronous). */
@Singleton
class UpdatePrefs @Inject constructor(@ApplicationContext ctx: Context) {
    private val sp = ctx.getSharedPreferences("updater", Context.MODE_PRIVATE)

    init {
        // One-time beta.9 migration, including previously disabled installs. Subsequent opt-out is respected.
        if (!sp.getBoolean("auto_migrated_beta9",false)) sp.edit {
            putBoolean(K_AUTO,true); putBoolean("auto_migrated_beta9",true); remove("notified_tag")
        }
    }
    private val _autoCheck = MutableStateFlow(sp.getBoolean(K_AUTO, true))
    val autoCheck: StateFlow<Boolean> = _autoCheck
    private val _wifiOnly = MutableStateFlow(sp.getBoolean(K_WIFI, false))
    val wifiOnly: StateFlow<Boolean> = _wifiOnly
    private val _includeBeta = MutableStateFlow(sp.getBoolean(K_BETA, SemVer.parse(BuildConfig.VERSION_NAME)?.isPreRelease ?: true))
    val includeBeta: StateFlow<Boolean> = _includeBeta
    private val _promptShown = MutableStateFlow(sp.getBoolean(K_PROMPT, false))
    val installPromptShown: StateFlow<Boolean> = _promptShown

    fun setAutoCheck(v: Boolean) { sp.edit { putBoolean(K_AUTO, v) }; _autoCheck.value = v }
    fun setWifiOnly(v: Boolean) { sp.edit { putBoolean(K_WIFI, v) }; _wifiOnly.value = v }
    fun setIncludeBeta(v: Boolean) { sp.edit { putBoolean(K_BETA, v) }; _includeBeta.value = v }
    fun markInstallPromptShown() { sp.edit { putBoolean(K_PROMPT, true) }; _promptShown.value = true }

    var lastAttempt: Long
        get() = sp.getLong("last_attempt",0)
        set(v) = sp.edit { putLong("last_attempt",v) }
    private val _accepted = MutableStateFlow(sp.getString("accepted_release",null))
    val accepted: StateFlow<String?> = _accepted
    var acceptedJson: String?
        get() = _accepted.value
        set(v) { sp.edit { putString("accepted_release",v) }; _accepted.value = v }
    fun acceptedRelease(): ReleaseInfo? = acceptedJson?.let { runCatching { ReleaseParser.parseRelease(org.json.JSONObject(it)) }.getOrNull() }
    var verifiedHash: String?
        get() = sp.getString("verified_hash",null)
        set(v) = sp.edit { putString("verified_hash",v) }
    var verifiedKey: String?
        get() = sp.getString("verified_key",null)
        set(v) = sp.edit { putString("verified_key",v) }
    private val _later = MutableStateFlow(sp.getString("later_key",null))
    val later: StateFlow<String?> = _later
    fun defer(key: String) {
        sp.edit { putString("later_key",key); putLong("later_until",System.currentTimeMillis()+60*60_000L) }; _later.value = key
    }
    fun isDeferred(key: String): Boolean = _later.value == key && System.currentTimeMillis() < sp.getLong("later_until",0)
    fun clearExpiredDeferral() { if (System.currentTimeMillis() >= sp.getLong("later_until",0)) _later.value = null }

    var lastCheck: Long
        get() = sp.getLong("last_check", 0)
        set(v) = sp.edit { putLong("last_check", v) }
    var notifiedTag: String?
        get() = sp.getString("notified_tag", null)
        set(v) = sp.edit { putString("notified_tag", v) }
    var verifiedApk: String?
        get() = sp.getString("verified_apk", null)
        set(v) = sp.edit { putString("verified_apk", v) }
    val etag: String? get() = sp.getString("etag", null)

    fun cachedReleasesJson(): String? = sp.getString("releases_json", null)
    fun saveReleases(json: String, etag: String?) = sp.edit { putString("releases_json", json); putString("etag", etag) }

    private companion object {
        const val K_AUTO = "auto_check"; const val K_WIFI = "wifi_only"; const val K_BETA = "include_beta"; const val K_PROMPT = "install_prompt_shown"
    }
}
