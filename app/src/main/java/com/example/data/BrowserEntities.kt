package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorites")
data class FavoriteSiteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String,
    val iconType: String, // "GEOMETRIC_A", "CC_SLASH", "BOLD_S", "MONO_M", "DROPLET", "PIN_P", "MONO_K", "EC_MARK", "MONOGRAM"
    val monogram: String,
    val bgStyle: String, // "DARK_ESPRESSO", "SOFT_PRISM", "PURE_BLACK", "SLATE_GRAY", "BLUSH_LIGHT", "PURE_WHITE", "SILVER_GRAY"
    val sortOrder: Int = 0
)

@Entity(tableName = "reading_list")
data class ReadingListItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val excerpt: String,
    val domain: String,
    val url: String,
    val artworkStyle: String, // "CERAMIC_CUBE", "BOTANICAL_FLORAL", "BOLD_S_MONO", "GLASS_ARCH", "TYPO_GRID"
    val isUnread: Boolean = true,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String,
    val folder: String = "Bookmarks",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "history_entries")
data class HistoryEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String,
    val domain: String,
    val visitedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "browser_tabs")
data class BrowserTabEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String, // Empty string "" represents the Start Page
    val isPrivate: Boolean = false,
    val groupName: String? = null,
    val previewHeadline: String = "",
    val previewSubhead: String = "",
    val lastAccessedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "custom_wallpapers")
data class CustomWallpaperEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val filePath: String,
    val isLight: Boolean,
    val addedAt: Long = System.currentTimeMillis()
)
