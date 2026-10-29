package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CloudDone
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.Wallpaper
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import coil.compose.AsyncImage
import com.example.R
import com.example.core.DeviceOptimizationManager
import com.example.core.GoogleAuthManager
import com.example.core.SignedInAccountSummary
import com.example.ui.components.EyeglassesIcon
import com.example.ui.components.FrostedGlassBox
import com.example.ui.components.GlassElevation
import com.example.ui.components.bouncyClickable
import com.example.ui.theme.LocalGlassStyle

/**
 * Frosted-glass Google Account Sign-In & Cloud Sync modal sheet powered by
 * Jetpack Credential Manager (`GetSignInWithGoogleOption`) and Firebase Firestore.
 */
@Composable
fun AccountAuthGlassSheet(
    signedInAccount: SignedInAccountSummary?,
    isSyncingCloud: Boolean,
    cloudSyncStatus: String?,
    bookmarksCount: Int,
    favoritesCount: Int,
    readingListCount: Int,
    onAuthSucceeded: () -> Unit,
    onSyncNow: () -> Unit,
    onSignedOut: () -> Unit,
    onDismiss: () -> Unit
) {
    val style = LocalGlassStyle.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var isSigningIn by remember { mutableStateOf(false) }
    var authError by remember { mutableStateOf<String?>(null) }

    Box(
        contentAlignment = Alignment.BottomCenter,
        modifier = Modifier
            .fillMaxSize()
            .background(style.glassBaseColor.copy(alpha = 0.28f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss
            )
    ) {
        BoxWithConstraints(
            contentAlignment = Alignment.BottomCenter,
            modifier = Modifier.fillMaxSize()
        ) {
            val metrics = DeviceOptimizationManager.computeAdaptiveMetrics(maxWidth, maxHeight)
            FrostedGlassBox(
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                elevation = GlassElevation.MODAL_OVERLAY,
                useLiveSceneBackdrop = true,
                modifier = Modifier
                    .widthIn(max = metrics.sheetMaxWidth)
                    .fillMaxWidth()
                    .testTag("account_auth_sheet")
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}
                    )
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 26.dp)
                ) {
                    // Grab handle + Header
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .width(40.dp)
                                .height(4.dp)
                                .clip(RoundedCornerShape(50))
                                .background(style.contentMuted)
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = stringResource(R.string.account_section_title),
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = style.contentPrimary
                            )
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(style.activeSegmentFill)
                                    .testTag("close_account_sheet_button")
                                    .bouncyClickable(
                                        pressedScale = 0.86f,
                                        contentDescriptionText = "Close account sheet",
                                        onClick = onDismiss
                                    )
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = "Close account sheet",
                                    tint = style.contentPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    if (signedInAccount == null) {
                        // Unauthenticated Google Sign-In state
                        FrostedGlassBox(
                            shape = RoundedCornerShape(24.dp),
                            elevation = GlassElevation.CARD,
                            useLiveSceneBackdrop = false,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(22.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(60.dp)
                                        .clip(CircleShape)
                                        .background(style.activeSegmentFill)
                                        .border(1.dp, style.rimTopLeft, CircleShape)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.AccountCircle,
                                        contentDescription = null,
                                        tint = style.contentPrimary,
                                        modifier = Modifier.size(34.dp)
                                    )
                                }

                                Text(
                                    text = "Sign in to Glass Browser",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = style.contentPrimary,
                                    textAlign = TextAlign.Center
                                )

                                Text(
                                    text = stringResource(R.string.account_sync_subtitle),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = style.contentSecondary,
                                    textAlign = TextAlign.Center
                                )

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    SyncFeatureBadge(
                                        icon = Icons.Outlined.BookmarkBorder,
                                        label = "$bookmarksCount Bookmarks",
                                        modifier = Modifier.weight(1f)
                                    )
                                    SyncFeatureBadge(
                                        icon = Icons.Outlined.StarOutline,
                                        label = "$favoritesCount Favorites",
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    FrostedGlassBox(
                                        shape = RoundedCornerShape(14.dp),
                                        elevation = GlassElevation.CARD,
                                        useLiveSceneBackdrop = false,
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(40.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center,
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(horizontal = 8.dp)
                                        ) {
                                            EyeglassesIcon(
                                                tint = style.contentSecondary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "$readingListCount Reading",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = style.contentPrimary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }

                                    SyncFeatureBadge(
                                        icon = Icons.Outlined.Wallpaper,
                                        label = "Wallpaper",
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }

                        if (!authError.isNullOrBlank()) {
                            FrostedGlassBox(
                                shape = RoundedCornerShape(16.dp),
                                elevation = GlassElevation.CARD,
                                useLiveSceneBackdrop = false,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.ErrorOutline,
                                        contentDescription = null,
                                        tint = Color(0xFFFF6B6B),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Text(
                                        text = authError.orEmpty(),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = style.contentPrimary,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }

                        // Interactive Google Sign-In Button
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .clip(RoundedCornerShape(50))
                                .background(style.solidActionFill)
                                .testTag("google_sign_in_button")
                                .bouncyClickable(
                                    pressedScale = 0.95f,
                                    enabled = !isSigningIn,
                                    contentDescriptionText = stringResource(R.string.sign_in_with_google),
                                    onClick = {
                                        authError = null
                                        isSigningIn = true
                                        GoogleAuthManager.signInWithGoogleInteractive(
                                            context = context,
                                            credentialManager = credentialManager,
                                            scope = scope,
                                            onAuthSuccess = {
                                                isSigningIn = false
                                                authError = null
                                                onAuthSucceeded()
                                            },
                                            onAuthCancelled = {
                                                isSigningIn = false
                                            },
                                            onAuthError = { err ->
                                                isSigningIn = false
                                                authError = err
                                            }
                                        )
                                    }
                                )
                        ) {
                            if (isSigningIn) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    color = style.solidActionContent,
                                    strokeWidth = 2.2.dp
                                )
                            } else {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.AccountCircle,
                                        contentDescription = null,
                                        tint = style.solidActionContent,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Text(
                                        text = stringResource(R.string.sign_in_with_google),
                                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                        color = style.solidActionContent
                                    )
                                }
                            }
                        }
                    } else {
                        // Signed-in Profile & Cloud Sync Controls
                        FrostedGlassBox(
                            shape = RoundedCornerShape(24.dp),
                            elevation = GlassElevation.CARD,
                            useLiveSceneBackdrop = false,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                verticalArrangement = Arrangement.spacedBy(14.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(18.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    AccountAvatarBadge(
                                        account = signedInAccount,
                                        sizeDp = 54
                                    )

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = signedInAccount.displayName,
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                            color = style.contentPrimary,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (signedInAccount.email.isNotBlank()) {
                                            Text(
                                                text = signedInAccount.email,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = style.contentSecondary,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }

                                // Cloud Sync Status Pill
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(style.activeSegmentFill)
                                        .padding(horizontal = 12.dp, vertical = 10.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isSyncingCloud) Icons.Outlined.CloudSync else Icons.Outlined.CloudDone,
                                        contentDescription = null,
                                        tint = Color(0xFF4ADE80),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = cloudSyncStatus ?: "Cloud Sync active • $bookmarksCount Bookmarks • $favoritesCount Favorites",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = style.contentPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Sync Now Button
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp)
                                    .clip(RoundedCornerShape(50))
                                    .background(style.solidActionFill)
                                    .testTag("sync_now_button")
                                    .bouncyClickable(
                                        pressedScale = 0.94f,
                                        enabled = !isSyncingCloud,
                                        contentDescriptionText = stringResource(R.string.sync_now_label),
                                        onClick = onSyncNow
                                    )
                            ) {
                                if (isSyncingCloud) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = style.solidActionContent,
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.CloudSync,
                                            contentDescription = null,
                                            tint = style.solidActionContent,
                                            modifier = Modifier.size(19.dp)
                                        )
                                        Text(
                                            text = stringResource(R.string.sync_now_label),
                                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                            color = style.solidActionContent
                                        )
                                    }
                                }
                            }

                            // Sign Out Button
                            FrostedGlassBox(
                                shape = RoundedCornerShape(50),
                                elevation = GlassElevation.CARD,
                                useLiveSceneBackdrop = false,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(50.dp)
                                    .testTag("sign_out_button")
                                    .bouncyClickable(
                                        pressedScale = 0.94f,
                                        contentDescriptionText = stringResource(R.string.sign_out_label),
                                        onClick = {
                                            GoogleAuthManager.signOut(
                                                credentialManager = credentialManager,
                                                scope = scope,
                                                onSignOutComplete = onSignedOut
                                            )
                                        }
                                    )
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Outlined.Logout,
                                        contentDescription = null,
                                        tint = style.contentPrimary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Text(
                                        text = stringResource(R.string.sign_out_label),
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                        color = style.contentPrimary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun AccountAvatarBadge(
    account: SignedInAccountSummary,
    sizeDp: Int,
    modifier: Modifier = Modifier
) {
    val style = LocalGlassStyle.current
    val initial = account.displayName.trim().take(1).uppercase().ifBlank { "G" }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(sizeDp.dp)
            .clip(CircleShape)
            .background(style.solidActionFill)
            .border(1.dp, style.rimTopLeft, CircleShape)
    ) {
        Text(
            text = initial,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = style.solidActionContent
        )
        if (!account.photoUrl.isNullOrBlank()) {
            AsyncImage(
                model = account.photoUrl,
                contentDescription = account.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
            )
        }
    }
}

@Composable
private fun SyncFeatureBadge(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier
) {
    val style = LocalGlassStyle.current
    FrostedGlassBox(
        shape = RoundedCornerShape(14.dp),
        elevation = GlassElevation.CARD,
        useLiveSceneBackdrop = false,
        modifier = modifier.height(40.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = style.contentSecondary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = style.contentPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
