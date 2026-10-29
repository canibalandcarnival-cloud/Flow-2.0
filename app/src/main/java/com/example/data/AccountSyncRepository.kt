package com.example.data

import android.content.Context
import com.example.R
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.security.MessageDigest

@IgnoreExtraProperties
data class CloudUserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String? = null,
    val photoUrl: String? = null,
    val selectedWallpaperId: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class CloudBookmark(
    @DocumentId val id: String = "",
    val userId: String = "",
    val url: String = "",
    val title: String = "",
    val folder: String = "Bookmarks",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class CloudFavorite(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val url: String = "",
    val accentHex: Long = 0xFF111111L,
    val position: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class CloudReadingItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val url: String = "",
    val title: String = "",
    val excerpt: String = "",
    @get:PropertyName("isRead")
    @set:PropertyName("isRead")
    var isRead: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

class AccountSyncRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    // CRITICAL: Always initialize Firestore with R.string.firestore_database_id
    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        auth = Firebase.auth
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    suspend fun upsertUserProfile(
        user: FirebaseUser,
        selectedWallpaperId: String?
    ) {
        val uid = requireUserId()
        require(uid == user.uid) { "Authenticated UID mismatch" }
        val docRef = db.collection("users").document(uid)
        val safeName = user.displayName?.trim()?.takeIf { it.isNotEmpty() }?.take(120)
            ?: user.email?.substringBefore('@')?.trim()?.takeIf { it.isNotEmpty() }?.take(120)
            ?: "Glass Browser User"
        val safeEmail = user.email?.trim()?.takeIf { it.isNotEmpty() }?.take(254)
        val safePhoto = user.photoUrl?.toString()?.trim()?.takeIf { it.isNotEmpty() }?.take(1024)
        val safeWallpaper = selectedWallpaperId?.trim()?.takeIf { it.isNotEmpty() }?.take(120)

        try {
            val existingSnap = docRef.get().await()
            if (!existingSnap.exists()) {
                val createPayload = buildMap<String, Any> {
                    put("userId", uid)
                    put("displayName", safeName)
                    if (safeEmail != null) put("email", safeEmail)
                    if (safePhoto != null) put("photoUrl", safePhoto)
                    if (safeWallpaper != null) put("selectedWallpaperId", safeWallpaper)
                    put("createdAt", FieldValue.serverTimestamp())
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                docRef.set(createPayload).await()
            } else {
                val updatePayload = buildMap<String, Any> {
                    put("displayName", safeName)
                    if (safeEmail != null) put("email", safeEmail)
                    if (safePhoto != null) put("photoUrl", safePhoto)
                    if (safeWallpaper != null) put("selectedWallpaperId", safeWallpaper)
                    put("updatedAt", FieldValue.serverTimestamp())
                }
                docRef.update(updatePayload).await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    suspend fun getUserProfile(targetUserId: String): CloudUserProfile? {
        val docRef = db.collection("users").document(targetUserId)
        return try {
            val snap = docRef.get().await()
            if (snap.exists()) {
                snap.toObject(CloudUserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            } else {
                null
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            throw e
        }
    }

    suspend fun upsertBookmark(
        url: String,
        title: String,
        folder: String = "Bookmarks",
        customDocId: String? = null
    ): String {
        val uid = requireUserId()
        val safeUrl = url.trim().take(2048)
        require(safeUrl.isNotEmpty()) { "Bookmark URL must not be empty" }
        val safeTitle = title.trim().ifEmpty { BrowserRepository.extractDomain(safeUrl) }.take(300)
        val safeFolder = folder.trim().ifEmpty { "Bookmarks" }.take(100)
        val docId = customDocId ?: stableIdForUrl("bm", safeUrl)
        val docRef = db.collection("users").document(uid).collection("bookmarks").document(docId)

        try {
            val existing = docRef.get().await()
            if (!existing.exists()) {
                val payload = mapOf(
                    "userId" to uid,
                    "url" to safeUrl,
                    "title" to safeTitle,
                    "folder" to safeFolder,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            } else {
                val payload = mapOf(
                    "url" to safeUrl,
                    "title" to safeTitle,
                    "folder" to safeFolder,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.update(payload).await()
            }
            return docId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    suspend fun deleteBookmarkByUrl(url: String) {
        val uid = requireUserId()
        val safeUrl = url.trim().take(2048)
        if (safeUrl.isEmpty()) return
        val docId = stableIdForUrl("bm", safeUrl)
        val docRef = db.collection("users").document(uid).collection("bookmarks").document(docId)
        try {
            docRef.delete().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            throw e
        }
    }

    suspend fun getBookmarks(userId: String): List<CloudBookmark> {
        val colRef = db.collection("users").document(userId).collection("bookmarks")
        return try {
            val snap = colRef.whereEqualTo("userId", userId).get().await()
            snap.toObjects(CloudBookmark::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, colRef.path)
            throw e
        }
    }

    fun observeBookmarks(userId: String): Flow<List<CloudBookmark>> {
        val colRef = db.collection("users").document(userId).collection("bookmarks")
        return colRef
            .whereEqualTo("userId", userId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(CloudBookmark::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                if (error is Exception) {
                    handleFirestoreError(error, OperationType.LIST, colRef.path)
                }
                throw error
            }
    }

    suspend fun upsertFavorite(
        title: String,
        url: String,
        accentHex: Long = 0xFF111111L,
        position: Int = 0,
        customDocId: String? = null
    ): String {
        val uid = requireUserId()
        val safeUrl = url.trim().take(2048)
        require(safeUrl.isNotEmpty()) { "Favorite URL must not be empty" }
        val safeTitle = title.trim().ifEmpty { BrowserRepository.extractDomain(safeUrl) }.take(200)
        val safePos = position.coerceIn(0, 10000).toLong()
        val docId = customDocId ?: stableIdForUrl("fav", safeUrl)
        val docRef = db.collection("users").document(uid).collection("favorites").document(docId)

        try {
            val existing = docRef.get().await()
            if (!existing.exists()) {
                val payload = mapOf(
                    "userId" to uid,
                    "title" to safeTitle,
                    "url" to safeUrl,
                    "accentHex" to accentHex,
                    "position" to safePos,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            } else {
                val payload = mapOf(
                    "title" to safeTitle,
                    "url" to safeUrl,
                    "accentHex" to accentHex,
                    "position" to safePos,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.update(payload).await()
            }
            return docId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    suspend fun getFavorites(userId: String): List<CloudFavorite> {
        val colRef = db.collection("users").document(userId).collection("favorites")
        return try {
            val snap = colRef.whereEqualTo("userId", userId).get().await()
            snap.toObjects(CloudFavorite::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, colRef.path)
            throw e
        }
    }

    fun observeFavorites(userId: String): Flow<List<CloudFavorite>> {
        val colRef = db.collection("users").document(userId).collection("favorites")
        return colRef
            .whereEqualTo("userId", userId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(CloudFavorite::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                if (error is Exception) {
                    handleFirestoreError(error, OperationType.LIST, colRef.path)
                }
                throw error
            }
    }

    suspend fun upsertReadingItem(
        url: String,
        title: String,
        excerpt: String,
        isRead: Boolean = false,
        customDocId: String? = null
    ): String {
        val uid = requireUserId()
        val safeUrl = url.trim().take(2048)
        require(safeUrl.isNotEmpty()) { "Reading item URL must not be empty" }
        val safeTitle = title.trim().ifEmpty { BrowserRepository.extractDomain(safeUrl) }.take(300)
        val safeExcerpt = excerpt.trim().take(2000)
        val docId = customDocId ?: stableIdForUrl("rl", safeUrl)
        val docRef = db.collection("users").document(uid).collection("readingList").document(docId)

        try {
            val existing = docRef.get().await()
            if (!existing.exists()) {
                val payload = mapOf(
                    "userId" to uid,
                    "url" to safeUrl,
                    "title" to safeTitle,
                    "excerpt" to safeExcerpt,
                    "isRead" to isRead,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.set(payload).await()
            } else {
                val payload = mapOf(
                    "url" to safeUrl,
                    "title" to safeTitle,
                    "excerpt" to safeExcerpt,
                    "isRead" to isRead,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                docRef.update(payload).await()
            }
            return docId
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docRef.path)
            throw e
        }
    }

    suspend fun getReadingList(userId: String): List<CloudReadingItem> {
        val colRef = db.collection("users").document(userId).collection("readingList")
        return try {
            val snap = colRef.whereEqualTo("userId", userId).get().await()
            snap.toObjects(CloudReadingItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, colRef.path)
            throw e
        }
    }

    fun observeReadingList(userId: String): Flow<List<CloudReadingItem>> {
        val colRef = db.collection("users").document(userId).collection("readingList")
        return colRef
            .whereEqualTo("userId", userId)
            .snapshots()
            .map { snapshot ->
                snapshot.toObjects(CloudReadingItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch { error ->
                if (error is Exception) {
                    handleFirestoreError(error, OperationType.LIST, colRef.path)
                }
                throw error
            }
    }

    companion object {
        fun stableIdForUrl(prefix: String, url: String): String {
            val digest = MessageDigest.getInstance("SHA-256")
                .digest(url.trim().lowercase().toByteArray(Charsets.UTF_8))
            val hex = digest.take(12).joinToString("") { "%02x".format(it) }
            return "${prefix}_$hex"
        }
    }
}
