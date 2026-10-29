package com.example.core

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import android.util.Base64
import android.webkit.CookieManager
import android.webkit.MimeTypeMap
import android.webkit.URLUtil
import androidx.core.content.FileProvider
import com.example.data.BrowserRepository
import com.example.data.DownloadItemEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * Handles WebView & direct-URL downloads with zero broad storage permissions:
 * - HTTP/HTTPS files enqueue via Android [DownloadManager] into public `DIRECTORY_DOWNLOADS`
 *   (with automatic direct HTTP streaming fallback into app-scoped `filesDir/downloads` if
 *   DownloadManager is restricted).
 * - `data:` and `blob:` base64 payloads save directly into `filesDir/downloads`.
 * - Tracks live progress bytes & completion state into Room (`DownloadItemEntity`).
 */
class DownloadEngine(
    private val appContext: Context,
    private val repository: BrowserRepository,
    private val scope: CoroutineScope
) {

    @Volatile
    private var isPollingActive = false

    fun enqueueWebDownload(
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?,
        contentLength: Long,
        onStatusToast: (String) -> Unit
    ) {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return

        if (trimmed.startsWith("data:", ignoreCase = true)) {
            scope.launch {
                val savedName = saveDataUriDownload(trimmed, mimeType)
                if (savedName != null) {
                    onStatusToast("Downloaded $savedName")
                } else {
                    onStatusToast("Unable to decode data download")
                }
            }
            return
        }

        val resolvedMime = resolveMimeType(trimmed, mimeType)
        val fileName = sanitizeFileName(
            URLUtil.guessFileName(trimmed, contentDisposition, resolvedMime)
        )

        scope.launch(Dispatchers.IO) {
            var sysDownloadId = -1L
            val dm = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            if (dm != null && (trimmed.startsWith("http://", true) || trimmed.startsWith("https://", true))) {
                try {
                    val req = DownloadManager.Request(Uri.parse(trimmed)).apply {
                        setTitle(fileName)
                        setDescription("Downloading in Glass Browser")
                        setMimeType(resolvedMime)
                        setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                        setAllowedOverMetered(true)
                        setAllowedOverRoaming(true)
                        if (!userAgent.isNullOrBlank()) {
                            addRequestHeader("User-Agent", userAgent)
                        }
                        try {
                            val cookie = CookieManager.getInstance().getCookie(trimmed)
                            if (!cookie.isNullOrBlank()) {
                                addRequestHeader("Cookie", cookie)
                            }
                        } catch (_: Throwable) {}
                        setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                    }
                    sysDownloadId = dm.enqueue(req)
                } catch (_: Throwable) {
                    sysDownloadId = -1L
                }
            }

            if (sysDownloadId > 0L) {
                repository.insertDownload(
                    DownloadItemEntity(
                        systemDownloadId = sysDownloadId,
                        fileName = fileName,
                        url = trimmed,
                        mimeType = resolvedMime,
                        totalBytes = contentLength.coerceAtLeast(-1L),
                        downloadedBytes = 0L,
                        status = "RUNNING"
                    )
                )
                withContext(Dispatchers.Main) {
                    onStatusToast("Downloading $fileName…")
                }
                ensureProgressPollerRunning()
            } else {
                // Direct streaming fallback into app-scoped downloads folder
                val rowId = repository.insertDownload(
                    DownloadItemEntity(
                        systemDownloadId = -1L,
                        fileName = fileName,
                        url = trimmed,
                        mimeType = resolvedMime,
                        totalBytes = contentLength.coerceAtLeast(-1L),
                        downloadedBytes = 0L,
                        status = "RUNNING"
                    )
                )
                withContext(Dispatchers.Main) {
                    onStatusToast("Downloading $fileName…")
                }
                streamDirectHttpDownload(rowId, trimmed, fileName, resolvedMime, userAgent, onStatusToast)
            }
        }
    }

    suspend fun saveBase64BlobDownload(
        base64DataUrl: String,
        suggestedFileName: String?,
        mimeType: String?
    ): String? = withContext(Dispatchers.IO) {
        saveDataUriDownload(base64DataUrl, mimeType, suggestedFileName)
    }

    private suspend fun saveDataUriDownload(
        dataUri: String,
        fallbackMime: String?,
        suggestedName: String? = null
    ): String? = withContext(Dispatchers.IO) {
        try {
            val commaIndex = dataUri.indexOf(',')
            if (commaIndex <= 0) return@withContext null
            val header = dataUri.substring(5, commaIndex)
            val payload = dataUri.substring(commaIndex + 1)
            val isBase64 = header.contains(";base64", ignoreCase = true)
            val parsedMime = header.substringBefore(';').ifBlank {
                fallbackMime ?: "application/octet-stream"
            }
            val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(parsedMime) ?: "bin"
            val cleanName = sanitizeFileName(
                suggestedName?.takeIf { it.isNotBlank() }
                    ?: "download_${System.currentTimeMillis()}.$ext"
            )
            val bytes = if (isBase64) {
                Base64.decode(payload, Base64.DEFAULT)
            } else {
                Uri.decode(payload).toByteArray(Charsets.UTF_8)
            }
            val dir = File(appContext.filesDir, "downloads").apply { mkdirs() }
            val outFile = uniqueFile(dir, cleanName)
            FileOutputStream(outFile).use { it.write(bytes) }
            val uriString = FileProvider.getUriForFile(
                appContext,
                "${appContext.packageName}.fileprovider",
                outFile
            ).toString()

            repository.insertDownload(
                DownloadItemEntity(
                    systemDownloadId = -1L,
                    fileName = outFile.name,
                    url = dataUri.take(120),
                    mimeType = parsedMime,
                    localUri = uriString,
                    localFilePath = outFile.absolutePath,
                    totalBytes = bytes.size.toLong(),
                    downloadedBytes = bytes.size.toLong(),
                    status = "SUCCESSFUL"
                )
            )
            outFile.name
        } catch (_: Throwable) {
            null
        }
    }

    private suspend fun streamDirectHttpDownload(
        rowId: Long,
        urlString: String,
        fileName: String,
        mimeType: String,
        userAgent: String?,
        onStatusToast: (String) -> Unit
    ) {
        try {
            val dir = File(appContext.filesDir, "downloads").apply { mkdirs() }
            val destFile = uniqueFile(dir, fileName)
            val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15_000
                readTimeout = 30_000
                instanceFollowRedirects = true
                if (!userAgent.isNullOrBlank()) {
                    setRequestProperty("User-Agent", userAgent)
                }
                try {
                    val cookie = CookieManager.getInstance().getCookie(urlString)
                    if (!cookie.isNullOrBlank()) {
                        setRequestProperty("Cookie", cookie)
                    }
                } catch (_: Throwable) {}
            }
            conn.connect()
            if (conn.responseCode !in 200..299) {
                conn.disconnect()
                markFailed(rowId)
                return
            }
            val total = conn.contentLengthLong
            var downloaded = 0L
            var lastEmitMs = 0L

            conn.inputStream.use { input ->
                FileOutputStream(destFile).use { output ->
                    val buffer = ByteArray(16 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        val now = System.currentTimeMillis()
                        if (now - lastEmitMs >= 250L) {
                            lastEmitMs = now
                            repository.getDownloadById(rowId)?.let { current ->
                                repository.updateDownload(
                                    current.copy(
                                        totalBytes = total,
                                        downloadedBytes = downloaded,
                                        status = "RUNNING"
                                    )
                                )
                            }
                        }
                    }
                }
            }
            conn.disconnect()

            val contentUri = FileProvider.getUriForFile(
                appContext,
                "${appContext.packageName}.fileprovider",
                destFile
            ).toString()

            repository.getDownloadById(rowId)?.let { current ->
                repository.updateDownload(
                    current.copy(
                        fileName = destFile.name,
                        mimeType = mimeType,
                        localUri = contentUri,
                        localFilePath = destFile.absolutePath,
                        totalBytes = if (total > 0) total else downloaded,
                        downloadedBytes = downloaded,
                        status = "SUCCESSFUL"
                    )
                )
            }
            withContext(Dispatchers.Main) {
                onStatusToast("Download complete: ${destFile.name}")
            }
        } catch (_: Throwable) {
            markFailed(rowId)
            withContext(Dispatchers.Main) {
                onStatusToast("Download failed: $fileName")
            }
        }
    }

    private suspend fun markFailed(rowId: Long) {
        repository.getDownloadById(rowId)?.let { current ->
            repository.updateDownload(current.copy(status = "FAILED"))
        }
    }

    fun ensureProgressPollerRunning() {
        if (isPollingActive) return
        isPollingActive = true
        scope.launch(Dispatchers.IO) {
            try {
                val dm = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
                while (true) {
                    val active = repository.getActiveDownloads()
                        .filter { it.systemDownloadId > 0L }
                    if (active.isEmpty() || dm == null) break

                    for (item in active) {
                        querySystemDownload(dm, item)?.let { updated ->
                            if (updated != item) {
                                repository.updateDownload(updated)
                            }
                        }
                    }
                    delay(450L)
                }
            } finally {
                isPollingActive = false
            }
        }
    }

    private fun querySystemDownload(
        dm: DownloadManager,
        item: DownloadItemEntity
    ): DownloadItemEntity? {
        var cursor: Cursor? = null
        return try {
            cursor = dm.query(DownloadManager.Query().setFilterById(item.systemDownloadId))
            if (cursor != null && cursor.moveToFirst()) {
                val statusInt = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                val downloaded = cursor.getLong(
                    cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                )
                val total = cursor.getLong(
                    cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                )
                val localUriCol = cursor.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
                val rawLocalUri = if (localUriCol >= 0) cursor.getString(localUriCol).orEmpty() else ""

                val mappedStatus = when (statusInt) {
                    DownloadManager.STATUS_SUCCESSFUL -> "SUCCESSFUL"
                    DownloadManager.STATUS_FAILED -> "FAILED"
                    DownloadManager.STATUS_PAUSED -> "PAUSED"
                    else -> "RUNNING"
                }
                val contentUri = if (mappedStatus == "SUCCESSFUL") {
                    dm.getUriForDownloadedFile(item.systemDownloadId)?.toString()
                        ?: rawLocalUri
                } else {
                    item.localUri
                }

                item.copy(
                    downloadedBytes = downloaded.coerceAtLeast(0L),
                    totalBytes = if (total > 0L) total else item.totalBytes,
                    localUri = contentUri,
                    status = mappedStatus
                )
            } else {
                item.copy(status = "FAILED")
            }
        } catch (_: Throwable) {
            null
        } finally {
            cursor?.close()
        }
    }

    suspend fun removeDownload(item: DownloadItemEntity) = withContext(Dispatchers.IO) {
        if (item.systemDownloadId > 0L) {
            try {
                val dm = appContext.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
                dm?.remove(item.systemDownloadId)
            } catch (_: Throwable) {}
        }
        if (item.localFilePath.isNotBlank()) {
            try {
                File(item.localFilePath).delete()
            } catch (_: Throwable) {}
        }
        repository.deleteDownload(item.id)
    }

    fun openDownload(context: Context, item: DownloadItemEntity): Boolean {
        return try {
            val targetUri = resolveOpenableUri(context, item) ?: return false
            val viewIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(targetUri, item.mimeType.ifBlank { "*/*" })
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(viewIntent)
            true
        } catch (_: Throwable) {
            false
        }
    }

    fun shareDownload(context: Context, item: DownloadItemEntity): Boolean {
        return try {
            val targetUri = resolveOpenableUri(context, item)
            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                if (targetUri != null) {
                    type = item.mimeType.ifBlank { "*/*" }
                    putExtra(Intent.EXTRA_STREAM, targetUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                } else {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, item.url)
                }
            }
            val chooser = Intent.createChooser(sendIntent, item.fileName).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(chooser)
            true
        } catch (_: Throwable) {
            false
        }
    }

    private fun resolveOpenableUri(context: Context, item: DownloadItemEntity): Uri? {
        if (item.localFilePath.isNotBlank()) {
            val f = File(item.localFilePath)
            if (f.exists()) {
                return FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    f
                )
            }
        }
        if (item.systemDownloadId > 0L) {
            val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
            val dmUri = dm?.getUriForDownloadedFile(item.systemDownloadId)
            if (dmUri != null) return dmUri
        }
        if (item.localUri.isNotBlank()) {
            return Uri.parse(item.localUri)
        }
        return null
    }

    companion object {
        fun resolveMimeType(url: String, providedMime: String?): String {
            if (!providedMime.isNullOrBlank() && providedMime != "application/octet-stream") {
                return providedMime
            }
            val ext = MimeTypeMap.getFileExtensionFromUrl(url)
            if (!ext.isNullOrBlank()) {
                MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.lowercase(Locale.US))
                    ?.let { return it }
            }
            return providedMime?.ifBlank { "application/octet-stream" } ?: "application/octet-stream"
        }

        fun sanitizeFileName(raw: String): String {
            val cleaned = raw.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim()
            return cleaned.ifBlank { "download_${System.currentTimeMillis()}.bin" }
        }

        private fun uniqueFile(dir: File, name: String): File {
            var candidate = File(dir, name)
            if (!candidate.exists()) return candidate
            val base = name.substringBeforeLast('.', name)
            val ext = name.substringAfterLast('.', "")
            var idx = 1
            while (candidate.exists() && idx < 999) {
                val nextName = if (ext.isNotEmpty()) "${base}_$idx.$ext" else "${base}_$idx"
                candidate = File(dir, nextName)
                idx++
            }
            return candidate
        }

        fun formatBytes(bytes: Long): String {
            if (bytes < 0L) return "Unknown size"
            if (bytes < 1024L) return "$bytes B"
            val kb = bytes / 1024.0
            if (kb < 1024.0) return String.format(Locale.US, "%.1f KB", kb)
            val mb = kb / 1024.0
            if (mb < 1024.0) return String.format(Locale.US, "%.1f MB", mb)
            val gb = mb / 1024.0
            return String.format(Locale.US, "%.2f GB", gb)
        }
    }
}
