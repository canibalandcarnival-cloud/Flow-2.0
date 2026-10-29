package com.example.core

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.recaptcha.RecaptchaAppCheckProviderFactory

private const val TAG = "AppCheckInit"

// App Check contract keys. These strings are defined by the Firebase SDK and
// the runtime harness, so do not rename them.
private const val DEBUG_TOKEN_EXTRA = "FIREBASE_APPCHECK_DEBUG_TOKEN"
private const val DEBUG_STORE_PREFIX = "com.google.firebase.appcheck.debug.store."
private const val TOKEN_CACHE_STORE_PREFIX = "com.google.firebase.appcheck.store."
private const val DEBUG_SECRET_KEY = "com.google.firebase.appcheck.debug.DEBUG_SECRET"
private const val BACKUP_STORE_NAME = "com.example.appcheck.backup"
private const val BACKUP_SECRET_KEY = "last_registered_debug_token"
private const val RECAPTCHA_SITE_KEY_RES = "recaptcha_site_key"

@Volatile
private var isProviderInstalled = false

fun configureAppCheck(context: Context, intent: Intent?) {
    try {
        // 1. Ensure FirebaseApp is initialized before configuring App Check
        val appContext = context.applicationContext ?: context
        if (FirebaseApp.getApps(appContext).isEmpty()) {
            FirebaseApp.initializeApp(appContext)
        }

        if (BuildConfig.DEBUG) {
            // 2. Debug: Extract debug token from launch Intent extra (or Bundle fallback)
            val intentDebugToken = (
                intent?.getStringExtra(DEBUG_TOKEN_EXTRA)
                    ?: intent?.extras?.getString(DEBUG_TOKEN_EXTRA)
                )?.trim()?.takeIf { it.isNotEmpty() }

            val persistenceKey = FirebaseApp.getInstance().persistenceKey
            val debugPrefs = appContext.getSharedPreferences(
                "$DEBUG_STORE_PREFIX$persistenceKey",
                Context.MODE_PRIVATE
            )
            val backupPrefs = appContext.getSharedPreferences(
                BACKUP_STORE_NAME,
                Context.MODE_PRIVATE
            )

            if (intentDebugToken != null) {
                // CRITICAL: MUST use synchronous commit() so the secret is persisted on disk
                // before DebugAppCheckProviderFactory reads it.
                debugPrefs.edit()
                    .putString(DEBUG_SECRET_KEY, intentDebugToken)
                    .commit()
                backupPrefs.edit()
                    .putString(BACKUP_SECRET_KEY, intentDebugToken)
                    .commit()

                // Always clear any previously cached App Check JWT (e.g. from pre-provisioning
                // launches) so the next Siri AI call exchanges a fresh valid backend token.
                appContext.getSharedPreferences(
                    "$TOKEN_CACHE_STORE_PREFIX$persistenceKey",
                    Context.MODE_PRIVATE
                ).edit().clear().commit()

                FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                    DebugAppCheckProviderFactory.getInstance()
                )
                isProviderInstalled = true
                // Proactively warm a fresh in-memory App Check token asynchronously
                FirebaseAppCheck.getInstance().getAppCheckToken(true)
                Log.d(TAG, "App Check debug secret configured and token cache refreshed.")
            } else {
                // Launched without intent extra (e.g. tapping the Home Screen launcher icon or CATEGORY_HOME).
                // Restore last known harness debug token if debugPrefs has none so DebugAppCheckProvider
                // never generates an unregistered random UUID.
                val existingDebugSecret = debugPrefs.getString(DEBUG_SECRET_KEY, null)
                val backupSecret = backupPrefs.getString(BACKUP_SECRET_KEY, null)
                if (existingDebugSecret.isNullOrBlank() && !backupSecret.isNullOrBlank()) {
                    debugPrefs.edit()
                        .putString(DEBUG_SECRET_KEY, backupSecret)
                        .commit()
                } else {
                    Log.w(TAG, "No debug token found in Intent extra.")
                }

                if (!isProviderInstalled) {
                    FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                        DebugAppCheckProviderFactory.getInstance()
                    )
                    isProviderInstalled = true
                    Log.d(TAG, "DebugAppCheckProviderFactory installed.")
                }
            }
        } else {
            if (!isProviderInstalled) {
                // 3. Release: Look up recaptcha_site_key safely without throwing Resources.NotFoundException
                val resId = appContext.resources.getIdentifier(
                    RECAPTCHA_SITE_KEY_RES,
                    "string",
                    appContext.packageName
                )
                if (resId != 0) {
                    val siteKey = appContext.getString(resId)
                    if (siteKey.isNotBlank()) {
                        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                            RecaptchaAppCheckProviderFactory.getInstance(siteKey)
                        )
                        isProviderInstalled = true
                        Log.d(TAG, "RecaptchaAppCheckProviderFactory installed.")
                    } else {
                        Log.e(TAG, "recaptcha_site_key resource string is blank.")
                    }
                } else {
                    Log.e(TAG, "recaptcha_site_key string resource not found in package.")
                }
            }
        }
    } catch (e: Exception) {
        // Log explicitly to keep App Check attestation failures visible
        Log.e(TAG, "Failed to initialize Firebase App Check", e)
    }
}
