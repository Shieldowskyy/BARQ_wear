package yt.dsh.barq_wear.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import yt.dsh.barq_wear.R
import yt.dsh.barq_wear.data.ActivityItem

@Composable
fun ActivitiesScreen(
    uiState: BarqUiState,
    onLoadActivities: () -> Unit,
    onMarkActivitiesRead: () -> Unit,
    onProfileClick: (String) -> Unit
) {
    val listState = rememberScalingLazyListState()

    LaunchedEffect(Unit) {
        onLoadActivities()
        // Wait briefly so user sees unread highlights, then mark read
        delay(1500)
        onMarkActivitiesRead()
    }

    Scaffold(
        timeText = { TimeText() },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) }
    ) {
        ScalingLazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .background(BarqDarkBg)
                .wearRotaryScroll(listState),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(bottom = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = null,
                            tint = BarqOrange,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = stringResource(R.string.activities_title),
                            color = BarqOrange,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp
                        )
                    }
                    Text(
                        text = stringResource(R.string.activities_subtitle),
                        color = BarqTextSecondary,
                        fontSize = 10.sp
                    )
                }
            }

            // Loading state
            if (uiState.isLoadingActivities && uiState.activities.isEmpty()) {
                item {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(vertical = 24.dp)
                    ) {
                        CircularProgressIndicator(
                            indicatorColor = BarqOrange,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.activities_loading),
                            color = BarqTextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            // Error state
            if (uiState.activitiesError != null && uiState.activities.isEmpty()) {
                item {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(vertical = 12.dp)
                    ) {
                        Text(
                            text = uiState.activitiesError,
                            color = Color(0xFFFCA5A5),
                            fontSize = 10.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        CompactChip(
                            onClick = onLoadActivities,
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp)
                                )
                            },
                            label = { Text(stringResource(R.string.profile_retry), fontSize = 10.sp) }
                        )
                    }
                }
            }

            // Empty state
            if (!uiState.isLoadingActivities && uiState.activities.isEmpty() && uiState.activitiesError == null) {
                item {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(vertical = 16.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.activities_empty),
                            color = BarqTextSecondary,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        CompactChip(
                            onClick = onLoadActivities,
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp)
                                )
                            },
                            label = { Text(stringResource(R.string.nearby_cd_refresh), fontSize = 10.sp) }
                        )
                    }
                }
            }

            // Activities list
            items(uiState.activities, key = { it.id }) { activity ->
                ActivityCard(
                    activity = activity,
                    onClick = {
                        val uuid = activity.sourceProfile?.uuid
                        if (!uuid.isNullOrBlank()) {
                            onProfileClick(uuid)
                        }
                    }
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            // Mark as read button at bottom if activities exist
            if (uiState.activities.isNotEmpty()) {
                item {
                    CompactChip(
                        onClick = onMarkActivitiesRead,
                        icon = {
                            Icon(
                                imageVector = Icons.Default.DoneAll,
                                contentDescription = null,
                                tint = BarqOrangeLight,
                                modifier = Modifier.size(12.dp)
                            )
                        },
                        label = {
                            Text(
                                text = stringResource(R.string.activities_mark_read),
                                fontSize = 10.sp,
                                color = BarqTextSecondary
                            )
                        },
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ActivityCard(
    activity: ActivityItem,
    onClick: () -> Unit
) {
    val actor = activity.sourceProfile
    val actorName = actor?.displayName?.takeIf { it.isNotBlank() } ?: "Someone"
    val avatarUrl = actor?.primaryImage?.resolvedUrl(128)

    val (iconVector, iconTint, actionText) = when (activity.type) {
        "PROFILE_BOOPED" -> {
            val count = activity.boopCount
            val txt = if (count > 1) "${count}x Boop! 🐾" else stringResource(R.string.activity_type_booped)
            Triple(Icons.Default.Pets, BarqOrange, txt)
        }
        "PROFILE_LIKED" -> {
            Triple(Icons.Default.Favorite, BarqPink, stringResource(R.string.activity_type_liked))
        }
        "PROFILE_MATCHED" -> {
            Triple(Icons.Default.Celebration, BarqOrangeLight, stringResource(R.string.activity_type_matched))
        }
        "PROFILE_IMAGE_LIKED" -> {
            Triple(Icons.Default.PhotoCamera, BarqSky, stringResource(R.string.activity_type_image_liked))
        }
        else -> {
            Triple(Icons.Default.Notifications, BarqOrangeLight, activity.type)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(BarqCardBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar or Activity Type Icon
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(Color(0xFF2A2A38)),
            contentAlignment = Alignment.Center
        ) {
            if (avatarUrl != null) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = actorName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = iconVector,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(16.dp)
                )
            }

            // Small badge for activity type in corner if avatar is present
            if (avatarUrl != null) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(BarqDarkBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = iconVector,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(9.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Text content
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = actorName,
                    color = BarqTextPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                val timeStr = activity.formattedTime()
                if (timeStr.isNotBlank()) {
                    Text(
                        text = timeStr,
                        color = BarqTextSecondary,
                        fontSize = 8.sp,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }

            Text(
                text = actionText,
                color = iconTint,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Unread indicator dot
        if (activity.isUnread) {
            Spacer(modifier = Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(BarqOrange)
            )
        }
    }
}
