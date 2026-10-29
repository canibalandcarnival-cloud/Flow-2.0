package com.example.core

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.compose.runtime.Immutable
import com.example.BuildConfig
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.recaptcha.RecaptchaAppCheckProviderFactory
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject

private const val TAG = "BrowserAiService"
private const val TOKEN_CACHE_STORE_PREFIX = "com.google.firebase.appcheck.store."
private const val RECAPTCHA_SITE_KEY_RES = "recaptcha_site_key"

@Immutable
data class WebPageAiContext(
    val url: String = "",
    val title: String = "",
    val selectedText: String = "",
    val bodySnippet: String = ""
)

@Immutable
data class SmartPageDigest(
    val headline: String,
    val tldr: String,
    val keyPoints: List<String>,
    val suggestedFollowUps: List<String>,
    val suggestedActionUrl: String?
)

@Immutable
data class SiriChatTurn(
    val id: Long = System.nanoTime(),
    val userPrompt: String,
    val modelReply: String,
    val usedScreenVision: Boolean = false
)

sealed interface SiriAiUiState {
    data object Idle : SiriAiUiState
    data class Loading(val activePrompt: String, val isVision: Boolean = false) : SiriAiUiState
    data class Streaming(
        val activePrompt: String,
        val partialText: String,
        val isVision: Boolean = false
    ) : SiriAiUiState
    data class Success(
        val prompt: String,
        val text: String,
        val suggestedNavigateUrl: String? = null,
        val isVision: Boolean = false
    ) : SiriAiUiState
    data class DigestSuccess(val digest: SmartPageDigest) : SiriAiUiState
    data class Error(
        val message: String,
        val retryPrompt: String? = null,
        val retrySmartDigest: Boolean = false
    ) : SiriAiUiState
}

/**
 * Real Firebase AI Logic integration powered by `gemini-3.8-flash` for Glass Browser's
 * iOS 26 Siri Intelligence assistant.
 *
 * Strictly initializes `Firebase.ai.generativeModel(...)` lazily via `by lazy`, wraps every
 * model request in a 60-second coroutine timeout, preserves `CancellationException`, refreshes
 * stale App Check tokens automatically if needed, and surfaces authentic errors directly to UI state.
 */
class BrowserAiService {

    private val conversationalModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            systemInstruction = content {
                text(
                    "You are Siri Intelligence inside Glass Browser (iOS 26 Liquid Glass edition). " +
                        "Provide crisp, high-signal, well-structured answers. When webpage context or " +
                        "a live webpage screenshot is provided, ground your response in the page's " +
                        "actual content. Keep formatting clean and scannable with short bullet points " +
                        "when helpful. If the user explicitly asks to open a website, include the full " +
                        "https:// URL on the final line prefixed with OPEN_URL: https://..."
                )
            }
        )
    }

    private val digestSchema by lazy {
        Schema.obj(
            properties = mapOf(
                "headline" to Schema.string(description = "Concise 5-10 word headline for the page or topic"),
                "tldr" to Schema.string(description = "Clear 1-2 sentence executive summary"),
                "keyPoints" to Schema.array(
                    items = Schema.string(),
                    description = "3 to 5 crisp bullet takeaways"
                ),
                "suggestedFollowUps" to Schema.array(
                    items = Schema.string(),
                    description = "2 or 3 short follow-up questions the user can tap"
                ),
                "suggestedActionUrl" to Schema.string(
                    description = "Optional relevant https:// URL if appropriate, or empty string"
                )
            ),
            optionalProperties = listOf("suggestedActionUrl")
        )
    }

    private val structuredDigestModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = digestSchema
            }
        )
    }

    /**
     * Streams a conversational or multimodal (live WebView screenshot + DOM context) response
     * from `gemini-3.8-flash` with low Time-to-First-Token.
     */
    fun streamAssistantResponse(
        userPrompt: String,
        pageContext: WebPageAiContext?,
        screenBitmap: Bitmap?,
        recentTurns: List<SiriChatTurn>
    ): Flow<SiriAiUiState> = flow {
        val isVision = screenBitmap != null
        emit(SiriAiUiState.Loading(activePrompt = userPrompt, isVision = isVision))

        val fullPrompt = buildContextualPrompt(userPrompt, pageContext, recentTurns)
        val scaledBitmap = screenBitmap?.let { downscaleForInlineVision(it) }
        val accumulated = StringBuilder()

        try {
            withTimeout(60_000L) {
                val requestContent = content {
                    if (scaledBitmap != null) {
                        image(scaledBitmap)
                    }
                    text(fullPrompt)
                }

                suspend fun collectStream() {
                    conversationalModel.generateContentStream(requestContent).collect { chunk ->
                        val piece = chunk.text.orEmpty()
                        if (piece.isNotEmpty()) {
                            accumulated.append(piece)
                            emit(
                                SiriAiUiState.Streaming(
                                    activePrompt = userPrompt,
                                    partialText = stripOpenUrlDirective(accumulated.toString()),
                                    isVision = isVision
                                )
                            )
                        }
                    }
                }

                try {
                    collectStream()
                } catch (firstErr: Exception) {
                    if (firstErr is CancellationException) throw firstErr
                    if (accumulated.isEmpty()) {
                        if (isAppCheckTokenError(firstErr)) {
                            Log.w(TAG, "Evicting stale App Check cache & retrying Siri stream", firstErr)
                            forceRefreshAppCheckToken()
                        }
                        try {
                            collectStream()
                        } catch (secondErr: Exception) {
                            if (secondErr is CancellationException) throw secondErr
                            if (accumulated.isEmpty()) {
                                // Unary fallback if SSE stream framing encountered a transport error
                                val unary = conversationalModel.generateContent(requestContent)
                                val unaryText = unary.text.orEmpty()
                                if (unaryText.isNotBlank()) {
                                    accumulated.append(unaryText)
                                } else {
                                    throw secondErr
                                }
                            } else {
                                throw secondErr
                            }
                        }
                    } else {
                        throw firstErr
                    }
                }
            }

            val rawFinal = accumulated.toString().trim()
            if (rawFinal.isNotBlank()) {
                val navUrl = extractOpenUrlDirective(rawFinal)
                val cleanDisplay = stripOpenUrlDirective(rawFinal).trim()
                emit(
                    SiriAiUiState.Success(
                        prompt = userPrompt,
                        text = cleanDisplay.ifBlank { rawFinal },
                        suggestedNavigateUrl = navUrl,
                        isVision = isVision
                    )
                )
            } else {
                emit(
                    SiriAiUiState.Error(
                        message = "Model returned an empty response.",
                        retryPrompt = userPrompt
                    )
                )
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Gemini streaming timed out", e)
            emit(
                SiriAiUiState.Error(
                    message = "Generation timed out after 60 seconds. Please try again.",
                    retryPrompt = userPrompt
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Firebase AI streaming failed", e)
            emit(
                SiriAiUiState.Error(
                    message = formatReadableAiError(e),
                    retryPrompt = userPrompt
                )
            )
        }
    }

    /**
     * Generates a structured JSON `SmartPageDigest` using `Schema.obj(...)` and parses raw JSON directly.
     */
    suspend fun generateStructuredDigest(
        pageContext: WebPageAiContext?,
        fallbackTopic: String
    ): SiriAiUiState {
        return try {
            val promptText = buildString {
                append("Create a structured Smart Digest for the user's current browser view.\n")
                if (pageContext != null && (pageContext.url.isNotBlank() || pageContext.bodySnippet.isNotBlank())) {
                    append("Page Title: ${pageContext.title}\n")
                    append("Page URL: ${pageContext.url}\n")
                    if (pageContext.selectedText.isNotBlank()) {
                        append("Selected Text: ${pageContext.selectedText.take(1500)}\n")
                    }
                    if (pageContext.bodySnippet.isNotBlank()) {
                        append("Extracted Page Content:\n${pageContext.bodySnippet.take(10_000)}\n")
                    }
                } else {
                    append("Topic / Context: ${fallbackTopic.ifBlank { "Modern spatial web browsing & productivity tips" }}\n")
                }
            }

            val response = withTimeout(60_000L) {
                try {
                    structuredDigestModel.generateContent(promptText)
                } catch (firstErr: Exception) {
                    if (firstErr is CancellationException) throw firstErr
                    if (isAppCheckTokenError(firstErr)) {
                        Log.w(TAG, "Evicting stale App Check cache & retrying Smart Digest", firstErr)
                        forceRefreshAppCheckToken()
                        structuredDigestModel.generateContent(promptText)
                    } else {
                        throw firstErr
                    }
                }
            }
            val jsonString = response.text
                ?: return SiriAiUiState.Error(
                    message = "Model returned an empty digest response.",
                    retrySmartDigest = true
                )

            val json = JSONObject(jsonString)
            val headline = json.optString("headline", "Smart Page Digest")
            val tldr = json.optString("tldr", "")
            val pointsJson = json.optJSONArray("keyPoints")
            val points = buildList {
                if (pointsJson != null) {
                    for (i in 0 until pointsJson.length()) {
                        val item = pointsJson.optString(i).trim()
                        if (item.isNotEmpty()) add(item)
                    }
                }
            }
            val followUpsJson = json.optJSONArray("suggestedFollowUps")
            val followUps = buildList {
                if (followUpsJson != null) {
                    for (i in 0 until followUpsJson.length()) {
                        val item = followUpsJson.optString(i).trim()
                        if (item.isNotEmpty()) add(item)
                    }
                }
            }
            val actionUrl = json.optString("suggestedActionUrl", "")
                .trim()
                .takeIf { it.startsWith("http://") || it.startsWith("https://") }

            SiriAiUiState.DigestSuccess(
                SmartPageDigest(
                    headline = headline,
                    tldr = tldr,
                    keyPoints = points,
                    suggestedFollowUps = followUps,
                    suggestedActionUrl = actionUrl
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Smart Digest generation timed out", e)
            SiriAiUiState.Error(
                message = "Smart Digest timed out after 60 seconds. Please try again.",
                retrySmartDigest = true
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Smart Digest generation failed", e)
            SiriAiUiState.Error(
                message = formatReadableAiError(e),
                retrySmartDigest = true
            )
        }
    }

    private fun buildContextualPrompt(
        userPrompt: String,
        pageContext: WebPageAiContext?,
        recentTurns: List<SiriChatTurn>
    ): String = buildString {
        if (recentTurns.isNotEmpty()) {
            append("Recent Conversation Context:\n")
            recentTurns.takeLast(3).forEach { turn ->
                append("User: ${turn.userPrompt}\n")
                append("Siri: ${turn.modelReply.take(500)}\n")
            }
            append("\n")
        }
        if (pageContext != null && (pageContext.url.isNotBlank() || pageContext.bodySnippet.isNotBlank())) {
            append("Active Browser Webpage Context:\n")
            if (pageContext.title.isNotBlank()) append("- Title: ${pageContext.title}\n")
            if (pageContext.url.isNotBlank()) append("- URL: ${pageContext.url}\n")
            if (pageContext.selectedText.isNotBlank()) {
                append("- Highlighted Selection: ${pageContext.selectedText.take(1500)}\n")
            }
            if (pageContext.bodySnippet.isNotBlank()) {
                append("- Visible Page Text:\n${pageContext.bodySnippet.take(10_500)}\n")
            }
            append("\n")
        }
        append("User Request: $userPrompt")
    }

    /**
     * Ensures inline vision bitmaps stay crisp yet well under 2MB for fast multimodal upload.
     */
    private fun downscaleForInlineVision(bitmap: Bitmap): Bitmap {
        val maxSide = maxOf(bitmap.width, bitmap.height)
        if (maxSide <= 1024) return bitmap
        val ratio = 1024f / maxSide.toFloat()
        val w = (bitmap.width * ratio).toInt().coerceAtLeast(64)
        val h = (bitmap.height * ratio).toInt().coerceAtLeast(64)
        return Bitmap.createScaledBitmap(bitmap, w, h, true)
    }

    private suspend fun forceRefreshAppCheckToken() {
        try {
            val firebaseApp = FirebaseApp.getInstance()
            val appContext = firebaseApp.applicationContext
            // Clear any stale on-disk cached JWT synchronously before requesting a fresh token
            appContext.getSharedPreferences(
                "$TOKEN_CACHE_STORE_PREFIX${firebaseApp.persistenceKey}",
                Context.MODE_PRIVATE
            ).edit().clear().commit()

            if (BuildConfig.DEBUG) {
                FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
                    DebugAppCheckProviderFactory.getInstance()
                )
            } else {
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
                    }
                }
            }

            withTimeoutOrNull(8_000L) {
                val task = FirebaseAppCheck.getInstance().getAppCheckToken(true)
                suspendCancellableCoroutine<Unit> { cont ->
                    task.addOnCompleteListener {
                        if (cont.isActive) {
                            cont.resume(Unit)
                        }
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "App Check force refresh completed with warning", e)
        }
    }

    private fun isAppCheckTokenError(error: Throwable): Boolean {
        val combined = generateSequence(error) { it.cause }
            .take(6)
            .joinToString(" ") { "${it.javaClass.simpleName}: ${it.message.orEmpty()} ${it.localizedMessage.orEmpty()}" }
        return combined.contains("app check", ignoreCase = true) ||
            combined.contains("appcheck", ignoreCase = true) ||
            combined.contains("attestation", ignoreCase = true) ||
            combined.contains("403", ignoreCase = true) ||
            combined.contains("401", ignoreCase = true) ||
            combined.contains("unauthenticated", ignoreCase = true) ||
            combined.contains("permission_denied", ignoreCase = true)
    }

    private fun formatReadableAiError(error: Throwable): String {
        val raw = error.localizedMessage ?: error.message ?: "AI generation failed."
        return raw.trim().ifBlank { "AI generation failed." }
    }

    companion object {
        fun extractOpenUrlDirective(raw: String): String? {
            val line = raw.lineSequence()
                .map { it.trim() }
                .lastOrNull { it.startsWith("OPEN_URL:", ignoreCase = true) }
                ?: return null
            val candidate = line.substringAfter(':').trim()
            return candidate.takeIf {
                it.startsWith("http://", ignoreCase = true) ||
                    it.startsWith("https://", ignoreCase = true)
            }
        }

        fun stripOpenUrlDirective(raw: String): String {
            return raw.lineSequence()
                .filterNot { it.trim().startsWith("OPEN_URL:", ignoreCase = true) }
                .joinToString("\n")
        }
    }
}
