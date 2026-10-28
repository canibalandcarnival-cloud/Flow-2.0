package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.browserDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "glass_browser_prefs"
)

enum class GlassBackdropMode(val storageKey: String) {
    PRISM_AURA("prism_aura"),
    LIVE_SCENE("live_scene"),
    STUDIO_HALO("studio_halo"),
    DEEP_FROST("deep_frost");

    companion object {
        fun fromKey(key: String?): GlassBackdropMode =
            entries.firstOrNull { it.storageKey == key } ?: PRISM_AURA
    }
}

data class BrowserSettingsState(
    val isDarkMode: Boolean = true,
    val frostedGlassEnabled: Boolean = true,
    val glassBackdropMode: GlassBackdropMode = GlassBackdropMode.PRISM_AURA,
    val selectedWallpaperId: String = "preset:prism_waves",
    val sfxEnabled: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val highRefreshRateEnabled: Boolean = true,
    val disableGpuVsync: Boolean = true,
    val disableFrameRateLimit: Boolean = true,
    val forceGpuRendering: Boolean = true,
    val enableGpuRasterization: Boolean = true,
    val disableHardwareOverlays: Boolean = true,
    val immersiveOsMode: Boolean = true,
    val activeTabId: Long = -1L,
    val isPrivateMode: Boolean = false,
    val isGoogleSignedIn: Boolean = false,
    val googleAccountEmail: String = "",
    val googleAccountName: String = "",
    val googleAccountAvatarUrl: String = ""
)

class BrowserPreferences(private val context: Context) {

    private object Keys {
        val IS_DARK_MODE = booleanPreferencesKey("is_dark_mode")
        val FROSTED_GLASS_ENABLED = booleanPreferencesKey("frosted_glass_enabled")
        val GLASS_BACKDROP_MODE = stringPreferencesKey("glass_backdrop_mode")
        val SELECTED_WALLPAPER_ID = stringPreferencesKey("selected_wallpaper_id")
        val SFX_ENABLED = booleanPreferencesKey("sfx_enabled")
        val HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
        val HIGH_REFRESH_ENABLED = booleanPreferencesKey("high_refresh_enabled")
        val DISABLE_GPU_VSYNC = booleanPreferencesKey("disable_gpu_vsync")
        val DISABLE_FRAME_RATE_LIMIT = booleanPreferencesKey("disable_frame_rate_limit")
        val FORCE_GPU_RENDERING = booleanPreferencesKey("force_gpu_rendering")
        val ENABLE_GPU_RASTERIZATION = booleanPreferencesKey("enable_gpu_rasterization")
        val DISABLE_HW_OVERLAYS = booleanPreferencesKey("disable_hw_overlays")
        val IMMERSIVE_OS_MODE = booleanPreferencesKey("immersive_os_mode")
        val ACTIVE_TAB_ID = longPreferencesKey("active_tab_id")
        val IS_PRIVATE_MODE = booleanPreferencesKey("is_private_mode")
        val IS_GOOGLE_SIGNED_IN = booleanPreferencesKey("is_google_signed_in")
        val GOOGLE_ACCOUNT_EMAIL = stringPreferencesKey("google_account_email")
        val GOOGLE_ACCOUNT_NAME = stringPreferencesKey("google_account_name")
        val GOOGLE_ACCOUNT_AVATAR_URL = stringPreferencesKey("google_account_avatar_url")
    }

    val settingsFlow: Flow<BrowserSettingsState> = context.browserDataStore.data.map { prefs ->
        BrowserSettingsState(
            isDarkMode = prefs[Keys.IS_DARK_MODE] ?: true,
            frostedGlassEnabled = prefs[Keys.FROSTED_GLASS_ENABLED] ?: true,
            glassBackdropMode = GlassBackdropMode.fromKey(prefs[Keys.GLASS_BACKDROP_MODE]),
            selectedWallpaperId = prefs[Keys.SELECTED_WALLPAPER_ID] ?: "preset:prism_waves",
            sfxEnabled = prefs[Keys.SFX_ENABLED] ?: true,
            hapticsEnabled = prefs[Keys.HAPTICS_ENABLED] ?: true,
            highRefreshRateEnabled = prefs[Keys.HIGH_REFRESH_ENABLED] ?: true,
            disableGpuVsync = prefs[Keys.DISABLE_GPU_VSYNC] ?: true,
            disableFrameRateLimit = prefs[Keys.DISABLE_FRAME_RATE_LIMIT] ?: true,
            forceGpuRendering = prefs[Keys.FORCE_GPU_RENDERING] ?: true,
            enableGpuRasterization = prefs[Keys.ENABLE_GPU_RASTERIZATION] ?: true,
            disableHardwareOverlays = prefs[Keys.DISABLE_HW_OVERLAYS] ?: true,
            immersiveOsMode = prefs[Keys.IMMERSIVE_OS_MODE] ?: true,
            activeTabId = prefs[Keys.ACTIVE_TAB_ID] ?: -1L,
            isPrivateMode = prefs[Keys.IS_PRIVATE_MODE] ?: false,
            isGoogleSignedIn = prefs[Keys.IS_GOOGLE_SIGNED_IN] ?: false,
            googleAccountEmail = prefs[Keys.GOOGLE_ACCOUNT_EMAIL] ?: "",
            googleAccountName = prefs[Keys.GOOGLE_ACCOUNT_NAME] ?: "",
            googleAccountAvatarUrl = prefs[Keys.GOOGLE_ACCOUNT_AVATAR_URL] ?: ""
        )
    }

    suspend fun setDarkMode(dark: Boolean) {
        context.browserDataStore.edit { it[Keys.IS_DARK_MODE] = dark }
    }

    suspend fun setFrostedGlassEnabled(enabled: Boolean) {
        context.browserDataStore.edit { it[Keys.FROSTED_GLASS_ENABLED] = enabled }
    }

    suspend fun setGlassBackdropMode(mode: GlassBackdropMode) {
        context.browserDataStore.edit { it[Keys.GLASS_BACKDROP_MODE] = mode.storageKey }
    }

    suspend fun setSelectedWallpaper(wallpaperId: String) {
        context.browserDataStore.edit { it[Keys.SELECTED_WALLPAPER_ID] = wallpaperId }
    }

    suspend fun setSfxEnabled(enabled: Boolean) {
        context.browserDataStore.edit { it[Keys.SFX_ENABLED] = enabled }
    }

    suspend fun setHapticsEnabled(enabled: Boolean) {
        context.browserDataStore.edit { it[Keys.HAPTICS_ENABLED] = enabled }
    }

    suspend fun setHighRefreshRateEnabled(enabled: Boolean) {
        context.browserDataStore.edit { it[Keys.HIGH_REFRESH_ENABLED] = enabled }
    }

    suspend fun setDisableGpuVsync(enabled: Boolean) {
        context.browserDataStore.edit { it[Keys.DISABLE_GPU_VSYNC] = enabled }
    }

    suspend fun setDisableFrameRateLimit(enabled: Boolean) {
        context.browserDataStore.edit { it[Keys.DISABLE_FRAME_RATE_LIMIT] = enabled }
    }

    suspend fun setForceGpuRendering(enabled: Boolean) {
        context.browserDataStore.edit { it[Keys.FORCE_GPU_RENDERING] = enabled }
    }

    suspend fun setEnableGpuRasterization(enabled: Boolean) {
        context.browserDataStore.edit { it[Keys.ENABLE_GPU_RASTERIZATION] = enabled }
    }

    suspend fun setDisableHardwareOverlays(enabled: Boolean) {
        context.browserDataStore.edit { it[Keys.DISABLE_HW_OVERLAYS] = enabled }
    }

    suspend fun setImmersiveOsMode(enabled: Boolean) {
        context.browserDataStore.edit { it[Keys.IMMERSIVE_OS_MODE] = enabled }
    }

    suspend fun setActiveTab(tabId: Long, isPrivate: Boolean) {
        context.browserDataStore.edit {
            it[Keys.ACTIVE_TAB_ID] = tabId
            it[Keys.IS_PRIVATE_MODE] = isPrivate
        }
    }

    suspend fun setGoogleAccountSession(
        signedIn: Boolean,
        email: String = "",
        name: String = "",
        avatarUrl: String = ""
    ) {
        context.browserDataStore.edit { prefs ->
            prefs[Keys.IS_GOOGLE_SIGNED_IN] = signedIn
            if (!signedIn) {
                prefs[Keys.GOOGLE_ACCOUNT_EMAIL] = ""
                prefs[Keys.GOOGLE_ACCOUNT_NAME] = ""
                prefs[Keys.GOOGLE_ACCOUNT_AVATAR_URL] = ""
            } else {
                if (email.isNotBlank()) prefs[Keys.GOOGLE_ACCOUNT_EMAIL] = email
                if (name.isNotBlank()) prefs[Keys.GOOGLE_ACCOUNT_NAME] = name
                if (avatarUrl.isNotBlank()) prefs[Keys.GOOGLE_ACCOUNT_AVATAR_URL] = avatarUrl
            }
        }
    }
}
