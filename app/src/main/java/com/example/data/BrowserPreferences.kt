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

data class BrowserSettingsState(
    val isDarkMode: Boolean = true,
    val selectedWallpaperId: String = "preset:forest",
    val sfxEnabled: Boolean = true,
    val hapticsEnabled: Boolean = true,
    val highRefreshRateEnabled: Boolean = true,
    val forceGpuRendering: Boolean = true,
    val enableGpuRasterization: Boolean = true,
    val disableHardwareOverlays: Boolean = true,
    val immersiveOsMode: Boolean = true,
    val replaceSiteNavBar: Boolean = true,
    val activeTabId: Long = -1L,
    val isPrivateMode: Boolean = false
)

class BrowserPreferences(private val context: Context) {

    private object Keys {
        val IS_DARK_MODE = booleanPreferencesKey("is_dark_mode")
        val SELECTED_WALLPAPER_ID = stringPreferencesKey("selected_wallpaper_id")
        val SFX_ENABLED = booleanPreferencesKey("sfx_enabled")
        val HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
        val HIGH_REFRESH_ENABLED = booleanPreferencesKey("high_refresh_enabled")
        val FORCE_GPU_RENDERING = booleanPreferencesKey("force_gpu_rendering")
        val ENABLE_GPU_RASTERIZATION = booleanPreferencesKey("enable_gpu_rasterization")
        val DISABLE_HW_OVERLAYS = booleanPreferencesKey("disable_hw_overlays")
        val IMMERSIVE_OS_MODE = booleanPreferencesKey("immersive_os_mode")
        val REPLACE_SITE_NAV_BAR = booleanPreferencesKey("replace_site_nav_bar")
        val ACTIVE_TAB_ID = longPreferencesKey("active_tab_id")
        val IS_PRIVATE_MODE = booleanPreferencesKey("is_private_mode")
    }

    val settingsFlow: Flow<BrowserSettingsState> = context.browserDataStore.data.map { prefs ->
        BrowserSettingsState(
            isDarkMode = prefs[Keys.IS_DARK_MODE] ?: true,
            selectedWallpaperId = prefs[Keys.SELECTED_WALLPAPER_ID] ?: "preset:forest",
            sfxEnabled = prefs[Keys.SFX_ENABLED] ?: true,
            hapticsEnabled = prefs[Keys.HAPTICS_ENABLED] ?: true,
            highRefreshRateEnabled = prefs[Keys.HIGH_REFRESH_ENABLED] ?: true,
            forceGpuRendering = prefs[Keys.FORCE_GPU_RENDERING] ?: true,
            enableGpuRasterization = prefs[Keys.ENABLE_GPU_RASTERIZATION] ?: true,
            disableHardwareOverlays = prefs[Keys.DISABLE_HW_OVERLAYS] ?: true,
            immersiveOsMode = prefs[Keys.IMMERSIVE_OS_MODE] ?: true,
            replaceSiteNavBar = prefs[Keys.REPLACE_SITE_NAV_BAR] ?: true,
            activeTabId = prefs[Keys.ACTIVE_TAB_ID] ?: -1L,
            isPrivateMode = prefs[Keys.IS_PRIVATE_MODE] ?: false
        )
    }

    suspend fun setDarkMode(dark: Boolean) {
        context.browserDataStore.edit { it[Keys.IS_DARK_MODE] = dark }
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

    suspend fun setReplaceSiteNavBar(enabled: Boolean) {
        context.browserDataStore.edit { it[Keys.REPLACE_SITE_NAV_BAR] = enabled }
    }

    suspend fun setActiveTab(tabId: Long, isPrivate: Boolean) {
        context.browserDataStore.edit {
            it[Keys.ACTIVE_TAB_ID] = tabId
            it[Keys.IS_PRIVATE_MODE] = isPrivate
        }
    }
}
