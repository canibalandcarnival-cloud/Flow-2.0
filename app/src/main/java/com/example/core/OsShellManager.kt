package com.example.core

import android.app.Activity
import android.app.SearchManager
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Integrates the app with Android OS Shell / Home Launcher (ROLE_HOME),
 * Default System Browser (ROLE_BROWSER), system search/link intents,
 * and full-screen immersive OS display mode.
 */
object OsShellManager {

    data class OsRoleStatus(
        val isDefaultHomeShell: Boolean = false,
        val isDefaultBrowser: Boolean = false
    )

    sealed interface SystemShellIntentAction {
        data object GoHome : SystemShellIntentAction
        data class OpenUrlOrQuery(val rawInput: String) : SystemShellIntentAction
    }

    fun queryRoleStatus(context: Context): OsRoleStatus {
        var isHome = false
        var isBrowser = false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            if (roleManager != null) {
                if (roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) {
                    isHome = roleManager.isRoleHeld(RoleManager.ROLE_HOME)
                }
                if (roleManager.isRoleAvailable(RoleManager.ROLE_BROWSER)) {
                    isBrowser = roleManager.isRoleHeld(RoleManager.ROLE_BROWSER)
                }
            }
        }

        if (!isHome) {
            val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val resolved = context.packageManager.resolveActivity(
                homeIntent,
                PackageManager.MATCH_DEFAULT_ONLY
            )
            isHome = resolved?.activityInfo?.packageName == context.packageName
        }

        return OsRoleStatus(
            isDefaultHomeShell = isHome,
            isDefaultBrowser = isBrowser
        )
    }

    fun createRequestHomeRoleIntent(context: Context): Intent {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            if (roleManager != null &&
                roleManager.isRoleAvailable(RoleManager.ROLE_HOME) &&
                !roleManager.isRoleHeld(RoleManager.ROLE_HOME)
            ) {
                return roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)
            }
        }
        return Intent(Settings.ACTION_HOME_SETTINGS)
    }

    fun createRequestBrowserRoleIntent(context: Context): Intent {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            if (roleManager != null &&
                roleManager.isRoleAvailable(RoleManager.ROLE_BROWSER) &&
                !roleManager.isRoleHeld(RoleManager.ROLE_BROWSER)
            ) {
                return roleManager.createRequestRoleIntent(RoleManager.ROLE_BROWSER)
            }
        }
        return Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
    }

    fun applyImmersiveOsMode(activity: Activity, immersiveEnabled: Boolean) {
        val window = activity.window ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }

        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (immersiveEnabled) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    fun parseIncomingSystemIntent(intent: Intent?): SystemShellIntentAction? {
        if (intent == null) return null
        return when (intent.action) {
            Intent.ACTION_VIEW -> {
                val url = intent.dataString?.trim().orEmpty()
                if (url.isNotEmpty()) SystemShellIntentAction.OpenUrlOrQuery(url) else null
            }
            Intent.ACTION_WEB_SEARCH -> {
                val query = intent.getStringExtra(SearchManager.QUERY)?.trim().orEmpty()
                if (query.isNotEmpty()) SystemShellIntentAction.OpenUrlOrQuery(query) else null
            }
            Intent.ACTION_MAIN -> {
                if (intent.hasCategory(Intent.CATEGORY_HOME)) {
                    SystemShellIntentAction.GoHome
                } else {
                    null
                }
            }
            else -> null
        }
    }
}
