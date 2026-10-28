package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface BrowserDao {

    // Favorites
    @Query("SELECT * FROM favorites ORDER BY sortOrder ASC, id ASC")
    fun observeFavorites(): Flow<List<FavoriteSiteEntity>>

    @Query("SELECT COUNT(*) FROM favorites")
    suspend fun countFavorites(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorites(items: List<FavoriteSiteEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFavorite(item: FavoriteSiteEntity): Long

    @Query("DELETE FROM favorites WHERE id = :id")
    suspend fun deleteFavoriteById(id: Long)

    // Reading List
    @Query("SELECT * FROM reading_list ORDER BY addedAt DESC")
    fun observeReadingList(): Flow<List<ReadingListItemEntity>>

    @Query("SELECT COUNT(*) FROM reading_list")
    suspend fun countReadingList(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReadingListItems(items: List<ReadingListItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReadingListItem(item: ReadingListItemEntity): Long

    @Query("DELETE FROM reading_list WHERE id = :id")
    suspend fun deleteReadingListById(id: Long)

    // Bookmarks
    @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC")
    fun observeBookmarks(): Flow<List<BookmarkEntity>>

    @Query("SELECT COUNT(*) FROM bookmarks")
    suspend fun countBookmarks(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmarks(items: List<BookmarkEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(item: BookmarkEntity): Long

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun deleteBookmarkById(id: Long)

    // History
    @Query("SELECT * FROM history_entries ORDER BY visitedAt DESC LIMIT 150")
    fun observeHistory(): Flow<List<HistoryEntryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistoryEntry(entry: HistoryEntryEntity): Long

    @Query("DELETE FROM history_entries WHERE id = :id")
    suspend fun deleteHistoryById(id: Long)

    @Query("DELETE FROM history_entries")
    suspend fun clearAllHistory()

    // Tabs
    @Query("SELECT * FROM browser_tabs ORDER BY lastAccessedAt DESC, id ASC")
    fun observeTabs(): Flow<List<BrowserTabEntity>>

    @Query("SELECT COUNT(*) FROM browser_tabs")
    suspend fun countTabs(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTab(tab: BrowserTabEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTabs(tabs: List<BrowserTabEntity>)

    @Update
    suspend fun updateTab(tab: BrowserTabEntity)

    @Query("SELECT * FROM browser_tabs WHERE id = :id LIMIT 1")
    suspend fun getTabById(id: Long): BrowserTabEntity?

    @Query("DELETE FROM browser_tabs WHERE id = :id")
    suspend fun deleteTabById(id: Long)

    @Query("DELETE FROM browser_tabs WHERE isPrivate = :isPrivate")
    suspend fun deleteAllTabsByMode(isPrivate: Boolean)

    // Custom Wallpapers
    @Query("SELECT * FROM custom_wallpapers ORDER BY addedAt DESC")
    fun observeCustomWallpapers(): Flow<List<CustomWallpaperEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomWallpaper(wallpaper: CustomWallpaperEntity): Long

    @Query("DELETE FROM custom_wallpapers WHERE id = :id")
    suspend fun deleteCustomWallpaperById(id: Long)
}
