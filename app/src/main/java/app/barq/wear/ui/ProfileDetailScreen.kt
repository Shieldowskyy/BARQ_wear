package app.barq.wear.ui

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
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import app.barq.wear.R
import app.barq.wear.data.ProfileDetail
import app.barq.wear.data.ProfileImageItem
import app.barq.wear.data.Sona
import coil.compose.AsyncImage

@Composable
fun ProfileDetailScreen(
    uiState: BarqUiState,
    onRetry: () -> Unit,
    onToggleLikeProfile: () -> Unit,
    onToggleLikeImage: (String, Boolean) -> Unit,
    onSonaClick: (Sona) -> Unit
) {
    val context = LocalContext.current
    val listState = rememberScalingLazyListState()
    val detail: ProfileDetail? = uiState.selectedProfile
    val isMyOwnProfile = detail != null && detail.uuid == uiState.currentUser?.profile?.uuid

    Scaffold(
        timeText = { TimeText() },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) }
    ) {
        if (uiState.isLoadingProfileDetail && detail == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(BarqDarkBg),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        indicatorColor = BarqOrange,
                        strokeWidth = 3.dp,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.profile_loading),
                        color = BarqTextSecondary,
                        fontSize = 12.sp
                    )
                }
            }
            return@Scaffold
        }

        if (uiState.profileDetailError != null && detail == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(BarqDarkBg)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = uiState.profileDetailError,
                        color = Color(0xFFFCA5A5),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    CompactChip(
                        onClick = onRetry,
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                        },
                        label = { Text(stringResource(R.string.profile_retry), fontSize = 11.sp) }
                    )
                }
            }
            return@Scaffold
        }

        if (detail == null) return@Scaffold

        ScalingLazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .background(BarqDarkBg)
                .wearRotaryScroll(listState),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Hero Banner + Avatar
            item {
                ProfileHeroHeader(detail = detail, isAdMode = uiState.isAdMode)
            }

            // Like / Boop Button (if not own profile)
            if (!isMyOwnProfile) {
                item {
                    val isLiked = detail.isLikedOrMutual
                    val btnColor = when (detail.relationType) {
                        "mutual" -> BarqPink
                        "liked" -> Color(0xFF3F3F4E)
                        "likedBy" -> BarqOrange
                        else -> BarqOrange
                    }
                    Chip(
                        onClick = onToggleLikeProfile,
                        enabled = !uiState.isLikingProfile,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ChipDefaults.primaryChipColors(backgroundColor = btnColor),
                        icon = {
                            Icon(
                                imageVector = if (isLiked) Icons.Default.Favorite else Icons.Default.Pets,
                                contentDescription = null,
                                tint = Color.White
                            )
                        },
                        label = {
                            Text(
                                text = stringResource(detail.relationButtonTextRes()),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    )
                }
            }

            // Location card
            if (detail.location != null) {
                item {
                    val loc = detail.location
                    val unknownLoc = stringResource(R.string.location_unknown)
                    val placeName = loc.place?.formattedFull(unknownLoc)
                    val homeName = loc.homePlace?.formattedFull(unknownLoc)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(BarqCardBg)
                            .padding(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                tint = BarqOrangeLight,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = loc.formattedDistanceKm(),
                                color = BarqOrangeLight,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                        if (!placeName.isNullOrBlank()) {
                            Text(
                                text = placeName,
                                color = BarqTextPrimary,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                        if (loc.isTraveling && !homeName.isNullOrBlank()) {
                            Text(
                                text = stringResource(R.string.profile_travel_home_fmt, homeName),
                                color = BarqSky,
                                fontSize = 10.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }
            }

            // Fursonas section
            val sonas = detail.sonas.orEmpty()
            if (sonas.isNotEmpty()) {
                item {
                    SectionHeader(title = stringResource(R.string.profile_section_sonas_fmt, sonas.size))
                }
                items(sonas, key = { it.id }) { sona ->
                    SonaCard(sona = sona, onClick = { onSonaClick(sona) })
                }
            }

            // Biography & Details
            val bio = detail.bio
            if (bio != null) {
                item {
                    SectionHeader(title = stringResource(R.string.profile_section_about))
                }
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(BarqCardBg)
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        if (!bio.biography.isNullOrBlank()) {
                            Text(
                                text = bio.biography.trim(),
                                color = BarqTextPrimary,
                                fontSize = 11.sp
                            )
                        }

                        val metaLines = buildList {
                            if (!bio.genders.isNullOrEmpty()) {
                                add(
                                    context.getString(
                                        R.string.profile_meta_gender_fmt,
                                        bio.genders.joinToString(", ") { formatGender(context, it) }
                                    )
                                )
                            }
                            if (!bio.relationshipStatus.isNullOrBlank()) {
                                add(
                                    context.getString(
                                        R.string.profile_meta_status_fmt,
                                        formatRelationship(context, bio.relationshipStatus)
                                    )
                                )
                            }
                            if (!bio.sexualOrientation.isNullOrBlank()) {
                                add(
                                    context.getString(
                                        R.string.profile_meta_orientation_fmt,
                                        formatOrientation(context, bio.sexualOrientation)
                                    )
                                )
                            }
                            if (!bio.languages.isNullOrEmpty()) {
                                add(
                                    context.getString(
                                        R.string.profile_meta_languages_fmt,
                                        bio.languages.joinToString(", ").uppercase()
                                    )
                                )
                            }
                        }

                        for (line in metaLines) {
                            Text(
                                text = line,
                                color = BarqTextSecondary,
                                fontSize = 10.sp
                            )
                        }

                        val hobbyNames = bio.hobbies?.mapNotNull { it.interest }?.filter { it.isNotBlank() }.orEmpty()
                        if (hobbyNames.isNotEmpty()) {
                            Text(
                                text = stringResource(
                                    R.string.profile_meta_interests_fmt,
                                    hobbyNames.joinToString(" • ")
                                ),
                                color = BarqOrangeLight,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }
            }

            // Profile Gallery Images
            val gallery = detail.images.orEmpty().filter { it.image?.resolvedUrl() != null }
            if (gallery.isNotEmpty()) {
                item {
                    SectionHeader(title = stringResource(R.string.profile_section_photos_fmt, gallery.size))
                }
                items(gallery, key = { it.id }) { imgItem ->
                    ProfileGalleryCard(
                        item = imgItem,
                        onToggleLike = { onToggleLikeImage(imgItem.id, imgItem.hasLiked) }
                    )
                }
            }

            // Social Accounts
            val socials = detail.socialAccounts.orEmpty()
            if (socials.isNotEmpty()) {
                item {
                    SectionHeader(title = stringResource(R.string.profile_section_socials))
                }
                items(socials, key = { it.id ?: (it.socialNetwork + it.value) }) { acc ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(BarqCardBg)
                            .padding(horizontal = 10.dp, vertical = 7.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                            tint = BarqSky,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = formatEnum(acc.socialNetwork ?: stringResource(R.string.profile_social_default)),
                                color = BarqTextSecondary,
                                fontSize = 9.sp
                            )
                            Text(
                                text = acc.displayName?.takeIf { it.isNotBlank() } ?: acc.value.orEmpty(),
                                color = BarqTextPrimary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // Groups
            val groups = detail.groups.orEmpty().mapNotNull { it.group }
            if (groups.isNotEmpty()) {
                item {
                    SectionHeader(title = stringResource(R.string.profile_section_groups_fmt, groups.size))
                }
                items(groups, key = { it.uuid }) { grp ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(BarqCardBg)
                            .padding(horizontal = 10.dp, vertical = 7.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Groups,
                            contentDescription = null,
                            tint = BarqOrangeLight,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = grp.displayName,
                            color = BarqTextPrimary,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Events / Conventions
            val events = detail.events.orEmpty().mapNotNull { it.event }
            if (events.isNotEmpty()) {
                item {
                    SectionHeader(title = stringResource(R.string.profile_section_events_fmt, events.size))
                }
                items(events, key = { it.uuid }) { ev ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(BarqCardBg)
                            .padding(horizontal = 10.dp, vertical = 7.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Event,
                            contentDescription = null,
                            tint = BarqGold,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = ev.displayName,
                            color = BarqTextPrimary,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileHeroHeader(detail: ProfileDetail, isAdMode: Boolean) {
    val bannerUrl = detail.bannerUrl(isAd = isAdMode, size = 512)
    val avatarUrl = detail.avatarUrl(isAd = isAdMode, size = 400)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(BarqCardBg)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(84.dp),
            contentAlignment = Alignment.Center
        ) {
            if (bannerUrl != null) {
                AsyncImage(
                    model = bannerUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, BarqCardBg)
                            )
                        )
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(BarqOrange.copy(alpha = 0.35f), BarqCardBg)
                            )
                        )
                )
            }

            Box(
                modifier = Modifier
                    .size(62.dp)
                    .clip(CircleShape)
                    .background(BarqDarkBg)
                    .border(2.dp, BarqOrange, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (avatarUrl != null) {
                    AsyncImage(
                        model = avatarUrl,
                        contentDescription = detail.displayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Pets,
                        contentDescription = null,
                        tint = BarqOrange,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = detail.displayName,
                    color = BarqTextPrimary,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center
                )
                if (detail.age != null) {
                    Text(
                        text = "• ${detail.age}",
                        color = BarqOrangeLight,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
                if (detail.isBirthday) {
                    Icon(
                        imageVector = Icons.Default.Cake,
                        contentDescription = stringResource(R.string.profile_birthday_cd),
                        tint = BarqGold,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            if (!detail.username.isNullOrBlank()) {
                Text(
                    text = "@${detail.username}",
                    color = BarqTextSecondary,
                    fontSize = 11.sp
                )
            }

            val roles = detail.roles.orEmpty()
            if (roles.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(top = 4.dp)
                ) {
                    for (role in roles) {
                        val label = when (role) {
                            "team" -> "BARQ! TEAM"
                            "supporter_vip" -> "VIP SUPPORTER"
                            "supporter" -> "SUPPORTER"
                            else -> role.uppercase()
                        }
                        Text(
                            text = label,
                            color = if (role == "team") BarqSky else BarqGold,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF2B2B38))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SonaCard(sona: Sona, onClick: () -> Unit) {
    val imgUrl = sona.mainImageUrl(size = 256)
    val imageCount = sona.images?.size ?: 0

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(BarqCardBg)
            .border(1.dp, BarqOrange.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF2B2B38)),
            contentAlignment = Alignment.Center
        ) {
            if (imgUrl != null) {
                AsyncImage(
                    model = imgUrl,
                    contentDescription = sona.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Pets,
                    contentDescription = null,
                    tint = BarqOrange,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = sona.displayName,
                color = BarqTextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = sona.species?.displayName ?: stringResource(R.string.sona_unknown_species),
                color = BarqOrangeLight,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 2.dp)
            ) {
                if (sona.hasFursuit) {
                    Text(
                        text = stringResource(R.string.sona_fursuit_badge),
                        color = BarqGreen,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (imageCount > 0) {
                    Text(
                        text = stringResource(R.string.sona_photos_count_fmt, imageCount),
                        color = BarqTextSecondary,
                        fontSize = 9.sp
                    )
                }
            }
        }

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = stringResource(R.string.sona_view_cd),
            tint = BarqTextSecondary,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun ProfileGalleryCard(
    item: ProfileImageItem,
    onToggleLike: () -> Unit
) {
    val imgUrl = item.image?.resolvedUrl(size = 512) ?: return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(BarqCardBg)
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AsyncImage(
            model = imgUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
                .clip(RoundedCornerShape(12.dp))
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 4.dp)
        ) {
            Text(
                text = if (item.isAd) "AD 18+" else "SFW",
                color = if (item.isAd) BarqPink else BarqTextSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (item.hasLiked) BarqPink.copy(alpha = 0.25f) else Color(0xFF2B2B38))
                    .clickable(onClick = onToggleLike)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Icon(
                    imageVector = if (item.hasLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = stringResource(R.string.profile_like_photo_cd),
                    tint = if (item.hasLiked) BarqPink else BarqTextPrimary,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = "${item.likeCount}",
                    color = if (item.hasLiked) BarqPink else BarqTextPrimary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun SonaDetailScreen(sona: Sona?) {
    val listState = rememberScalingLazyListState()

    Scaffold(
        timeText = { TimeText() },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) }
    ) {
        if (sona == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(BarqDarkBg),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(R.string.sona_none_selected), color = BarqTextSecondary, fontSize = 12.sp)
            }
            return@Scaffold
        }

        val images = sona.images.orEmpty().mapNotNull { it.image?.resolvedUrl(size = 600) }

        ScalingLazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .background(BarqDarkBg)
                .wearRotaryScroll(listState),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(BarqCardBg)
                        .padding(10.dp)
                ) {
                    Text(
                        text = sona.displayName,
                        color = BarqOrange,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = sona.species?.displayName ?: stringResource(R.string.sona_unknown_species),
                        color = BarqTextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                    if (sona.hasFursuit) {
                        Text(
                            text = stringResource(R.string.sona_has_fursuit),
                            color = BarqGreen,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            if (images.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.sona_no_images),
                        color = BarqTextSecondary,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                }
            } else {
                items(images) { url ->
                    AsyncImage(
                        model = url,
                        contentDescription = sona.displayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(145.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .border(1.dp, BarqCardBorder, RoundedCornerShape(14.dp))
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        color = BarqOrangeLight,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
    )
}

private fun formatEnum(raw: String): String {
    return raw.replace("_", " ").replaceFirstChar { it.uppercase() }
}

private fun formatGender(context: android.content.Context, raw: String): String = when (raw) {
    "Female" -> context.getString(R.string.gender_female)
    "Male" -> context.getString(R.string.gender_male)
    "Transgender Female" -> context.getString(R.string.gender_trans_female)
    "Transgender Male" -> context.getString(R.string.gender_trans_male)
    "Non-Binary" -> context.getString(R.string.gender_non_binary)
    else -> formatEnum(raw)
}

private fun formatOrientation(context: android.content.Context, raw: String): String = when (raw) {
    "straight" -> context.getString(R.string.orient_straight)
    "bisexual" -> context.getString(R.string.orient_bisexual)
    "gay" -> context.getString(R.string.orient_gay)
    "lesbian" -> context.getString(R.string.orient_lesbian)
    "pansexual" -> context.getString(R.string.orient_pansexual)
    "asexual" -> context.getString(R.string.orient_asexual)
    "questioning" -> context.getString(R.string.orient_questioning)
    "other" -> context.getString(R.string.orient_other)
    else -> formatEnum(raw)
}

private fun formatRelationship(context: android.content.Context, raw: String): String = when (raw) {
    "single" -> context.getString(R.string.rel_single)
    "dating" -> context.getString(R.string.rel_dating)
    "relationship" -> context.getString(R.string.rel_relationship)
    "open_relationship" -> context.getString(R.string.rel_open_relationship)
    "polyamorous" -> context.getString(R.string.rel_polyamorous)
    "engaged_married" -> context.getString(R.string.rel_engaged_married)
    "domestic_partnership" -> context.getString(R.string.rel_domestic_partnership)
    "other" -> context.getString(R.string.rel_other)
    else -> formatEnum(raw)
}

