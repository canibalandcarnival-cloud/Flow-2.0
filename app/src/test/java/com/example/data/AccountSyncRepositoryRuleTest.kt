package com.example.data

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.UUID

class AccountSyncRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun ownerProfileAndBrowserCollections_crudAndRealtimeSnapshotsSucceed() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceUser = checkNotNull(auth.currentUser)
        val repository = AccountSyncRepository(firestore, auth)

        // 1. Upsert & read profile (both create and subsequent update path)
        withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.upsertUserProfile(aliceUser, selectedWallpaperId = "preset:savanna")
            repository.upsertUserProfile(aliceUser, selectedWallpaperId = "preset:coastal")
        }
        val profile = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.getUserProfile(aliceUid)
        }
        assertNotNull(profile)
        assertEquals(aliceUid, profile?.userId)
        assertEquals("preset:coastal", profile?.selectedWallpaperId)

        // 2. Bookmarks upsert + aligned query + realtime snapshots()
        val uniqueUrl = "https://www.dezeen.com/${UUID.randomUUID()}"
        val bmId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.upsertBookmark(
                url = uniqueUrl,
                title = "Dezeen Architecture",
                folder = "Design"
            )
        }
        val bookmarks = withTimeout(FLOW_TIMEOUT_MS) {
            repository.observeBookmarks(aliceUid).first { list -> list.any { it.id == bmId } }
        }
        assertTrue(bookmarks.any { it.url == uniqueUrl && it.userId == aliceUid })

        // 3. Favorites & Reading List upsert + aligned queries (including negative sortOrder clamping & isRead field)
        val favId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.upsertFavorite(
                title = "YouTube",
                url = "https://m.youtube.com/${UUID.randomUUID()}",
                accentHex = 0xFF111111L,
                position = -1
            )
        }
        val favs = withTimeout(DEFAULT_TIMEOUT_MS) { repository.getFavorites(aliceUid) }
        assertTrue(favs.any { it.id == favId && it.position == 0L })

        val rlId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.upsertReadingItem(
                url = "https://www.archdaily.com/${UUID.randomUUID()}",
                title = "Spatial Glass Architecture",
                excerpt = "Light and refraction in modern structures.",
                isRead = true
            )
        }
        val readingItems = withTimeout(DEFAULT_TIMEOUT_MS) { repository.getReadingList(aliceUid) }
        assertTrue(readingItems.any { it.id == rlId && it.isRead })
    }

    @Test
    fun crossUserAccess_bobCannotReadAliceProfileOrBookmarks() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceUser = checkNotNull(auth.currentUser)
        val repository = AccountSyncRepository(firestore, auth)

        withTimeout(DEFAULT_TIMEOUT_MS) {
            repository.upsertUserProfile(aliceUser, selectedWallpaperId = "preset:savanna")
            repository.upsertBookmark(
                url = "https://en.m.wikipedia.org/${UUID.randomUUID()}",
                title = "Alice Private Bookmark"
            )
        }

        // Switch authenticated user to Bob
        signInTestUser(BOB_EMAIL)

        try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                repository.getUserProfile(aliceUid)
            }
            fail("Expected PERMISSION_DENIED when Bob reads Alice's profile")
        } catch (e: Exception) {
            val fsEx = checkNotNull(e.unwrapFirestoreException()) {
                "Expected FirebaseFirestoreException, got $e"
            }
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx.code)
        }

        try {
            withTimeout(DEFAULT_TIMEOUT_MS) {
                repository.getBookmarks(aliceUid)
            }
            fail("Expected PERMISSION_DENIED when Bob lists Alice's bookmarks")
        } catch (e: Exception) {
            val fsEx = checkNotNull(e.unwrapFirestoreException()) {
                "Expected FirebaseFirestoreException, got $e"
            }
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx.code)
        }
    }

    @Test
    fun unauthenticatedCaller_observeBookmarksFailsWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        auth.signOut()
        val repository = AccountSyncRepository(firestore, auth)

        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repository.observeBookmarks(aliceUid).first()
            }
            fail("Expected PERMISSION_DENIED for signed-out caller")
        } catch (e: Exception) {
            val fsEx = checkNotNull(e.unwrapFirestoreException()) {
                "Expected nested FirebaseFirestoreException, got $e"
            }
            assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx.code)
        }
    }

    private companion object {
        const val ALICE_EMAIL = "alice.glassbrowser@test.com"
        const val BOB_EMAIL = "bob.glassbrowser@test.com"
        const val DEFAULT_TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 3500L
    }
}
