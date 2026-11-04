package yt.dsh.barq_wear.ui

import android.Manifest
import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Wc
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
import androidx.wear.compose.foundation.lazy.itemsIndexed
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Checkbox
import androidx.wear.compose.material.CheckboxDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Switch
import androidx.wear.compose.material.SwitchDefaults
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.ToggleChip
import androidx.wear.compose.material.ToggleChipDefaults
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import androidx.wear.input.RemoteInputIntentHelper
import yt.dsh.barq_wear.R
import yt.dsh.barq_wear.data.BarqFilterOptions
import yt.dsh.barq_wear.data.BarqSearchFilters
import yt.dsh.barq_wear.data.FilterOption
import yt.dsh.barq_wear.data.NewsItem
import yt.dsh.barq_wear.data.OverviewProfile
import yt.dsh.barq_wear.data.Place
import yt.dsh.barq_wear.data.RelationListType
import coil.compose.AsyncImage

@Composable
fun NearbyScreen(
    uiState: BarqUiState,
    onProfileClick: (String) -> Unit,
    onOpenFilters: () -> Unit,
    onOpenManualLocation: () -> Unit,
    onOpenMyAccount: () -> Unit,
    onRefresh: () -> Unit,
    onUpdateGps: () -> Unit,
    onLoadMore: () -> Unit,
    onClearSearch: () -> Unit
) {
    val listState = rememberScalingLazyListState()

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val granted = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            onUpdateGps()
        } else {
            onOpenManualLocation()
        }
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
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Pets,
                            contentDescription = null,
                            tint = BarqOrange,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (uiState.isAdMode) {
                                stringResource(R.string.nearby_title_ad)
                            } else {
                                stringResource(R.string.nearby_title)
                            },
                            color = BarqOrange,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp
                        )
                    }
                    Text(
                        text = stringResource(R.string.nearby_subtitle_closest),
                        color = BarqTextSecondary,
                        fontSize = 10.sp
                    )
                }
            }

            // Action bar: Filters/Search, GPS, Refresh, Account
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    val hasFilters = uiState.filters.hasActiveFilters
                    Box {
                        Button(
                            onClick = onOpenFilters,
                            modifier = Modifier.size(36.dp),
                            colors = ButtonDefaults.secondaryButtonColors(
                                backgroundColor = if (hasFilters) BarqOrange else BarqCardBg
                            )
                        ) {
                            Icon(
                                imageVector = if (hasFilters) Icons.Default.Tune else Icons.Default.Search,
                                contentDescription = stringResource(R.string.nearby_cd_filters),
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        if (hasFilters) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .background(BarqDarkBg)
                                    .border(1.dp, BarqOrange, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${uiState.filters.activeCount}",
                                    color = BarqOrangeLight,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }

                    Button(
                        onClick = {
                            locationPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.ACCESS_FINE_LOCATION,
                                    Manifest.permission.ACCESS_COARSE_LOCATION
                                )
                            )
                        },
                        modifier = Modifier.size(36.dp),
                        colors = ButtonDefaults.secondaryButtonColors(backgroundColor = BarqCardBg)
                    ) {
                        if (uiState.isUpdatingGps) {
                            CircularProgressIndicator(
                                indicatorColor = BarqOrange,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.MyLocation,
                                contentDescription = stringResource(R.string.nearby_cd_update_gps),
                                tint = BarqSky,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Button(
                        onClick = onRefresh,
                        modifier = Modifier.size(36.dp),
                        colors = ButtonDefaults.secondaryButtonColors(backgroundColor = BarqCardBg)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.nearby_cd_refresh),
                            tint = BarqOrangeLight,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    Button(
                        onClick = onOpenMyAccount,
                        modifier = Modifier.size(36.dp),
                        colors = ButtonDefaults.secondaryButtonColors(backgroundColor = BarqCardBg)
                    ) {
                        val myAvatar = uiState.currentUser?.profile?.avatarUrl(size = 128)
                        if (myAvatar != null) {
                            AsyncImage(
                                model = myAvatar,
                                contentDescription = stringResource(R.string.nearby_cd_my_profile),
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(CircleShape)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = stringResource(R.string.nearby_cd_my_profile),
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            // Location pill (shows current city or manual location button when GPS is missing)
            item {
                val activePlace = uiState.currentPlace ?: uiState.currentUser?.profile?.location?.place
                val unknownLoc = stringResource(R.string.location_unknown)
                val pillText = when {
                    uiState.gpsUnavailable -> stringResource(R.string.nearby_gps_missing_set_city)
                    activePlace != null -> stringResource(
                        R.string.nearby_city_change_fmt,
                        activePlace.formattedShort(unknownLoc)
                    )
                    !uiState.gpsStatusMessage.isNullOrBlank() -> uiState.gpsStatusMessage
                    else -> stringResource(R.string.nearby_set_location)
                }
                CompactChip(
                    onClick = onOpenManualLocation,
                    colors = ChipDefaults.secondaryChipColors(
                        backgroundColor = if (uiState.gpsUnavailable) Color(0xFF3B1219) else BarqCardBg
                    ),
                    icon = {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = if (uiState.gpsUnavailable) BarqOrangeLight else BarqSky,
                            modifier = Modifier.size(13.dp)
                        )
                    },
                    label = {
                        Text(
                            text = pillText,
                            color = if (uiState.gpsUnavailable) BarqOrangeLight else BarqTextPrimary,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                )
            }

            if (uiState.filters.hasActiveFilters) {
                item {
                    CompactChip(
                        onClick = onClearSearch,
                        colors = ChipDefaults.primaryChipColors(backgroundColor = BarqCardBg),
                        label = {
                            val labelText = if (uiState.searchQuery.isNotBlank() && uiState.filters.activeCount == 1) {
                                stringResource(R.string.nearby_searching_query_fmt, uiState.searchQuery)
                            } else {
                                stringResource(R.string.nearby_active_filters_fmt, uiState.filters.activeCount)
                            }
                            Text(
                                text = labelText,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = stringResource(R.string.nearby_clear_filters_cd),
                                tint = BarqOrange,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    )
                }
            }

            if (uiState.isLoadingProfiles && uiState.profiles.isEmpty()) {
                item {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(vertical = 20.dp)
                    ) {
                        CircularProgressIndicator(
                            indicatorColor = BarqOrange,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(34.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.nearby_loading_profiles),
                            color = BarqTextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            } else if (uiState.profilesError != null && uiState.profiles.isEmpty()) {
                item {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF3B1219))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = uiState.profilesError,
                            color = Color(0xFFFCA5A5),
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        CompactChip(
                            onClick = onRefresh,
                            label = { Text(stringResource(R.string.nearby_retry), fontSize = 11.sp) }
                        )
                    }
                }
            } else if (uiState.profiles.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.nearby_empty_results),
                        color = BarqTextSecondary,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 18.dp)
                    )
                }
            } else {
                itemsIndexed(
                    items = uiState.profiles,
                    key = { _, item -> item.uuid }
                ) { index, profile ->
                    if (index >= uiState.profiles.size - 5 && !uiState.hasReachedEnd && !uiState.isLoadingMore) {
                        LaunchedEffect(uiState.profiles.size) {
                            onLoadMore()
                        }
                    }
                    ProfileListCard(
                        profile = profile,
                        isAdMode = uiState.isAdMode || uiState.showNsfw,
                        onClick = { onProfileClick(profile.uuid) }
                    )
                }

                if (uiState.isLoadingMore) {
                    item {
                        CircularProgressIndicator(
                            indicatorColor = BarqOrange,
                            strokeWidth = 2.dp,
                            modifier = Modifier
                                .padding(vertical = 8.dp)
                                .size(24.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileListCard(
    profile: OverviewProfile,
    isAdMode: Boolean,
    onClick: () -> Unit
) {
    val roleBadge = profile.roleBadge()
    val borderColor = when {
        profile.relationType == "mutual" -> BarqPink
        profile.relationType == "likedBy" -> BarqOrange
        roleBadge == "TEAM" -> BarqSky
        roleBadge == "VIP" || roleBadge == "SUPPORTER" -> BarqGold
        else -> BarqCardBorder
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(BarqCardBg)
            .border(1.dp, borderColor, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF2B2B36)),
            contentAlignment = Alignment.Center
        ) {
            val avatar = profile.avatarUrl(isAd = isAdMode, size = 256)
            if (avatar != null) {
                AsyncImage(
                    model = avatar,
                    contentDescription = profile.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Pets,
                    contentDescription = null,
                    tint = BarqOrangeLight,
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = profile.displayName,
                    color = BarqTextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )

                if (roleBadge != null) {
                    Text(
                        text = roleBadge,
                        color = if (roleBadge == "TEAM") BarqSky else BarqGold,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF2D2D3A))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier.padding(top = 2.dp)
            ) {
                val distText = profile.location?.formattedDistanceKm() ?: "0 km"
                Text(
                    text = distText,
                    color = BarqOrangeLight,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )

                if (profile.location?.isTraveling == true) {
                    Icon(
                        imageVector = Icons.Default.Flight,
                        contentDescription = stringResource(R.string.nearby_traveling_cd),
                        tint = BarqSky,
                        modifier = Modifier.size(11.dp)
                    )
                }

                if (!profile.username.isNullOrBlank()) {
                    Text(
                        text = "@${profile.username}",
                        color = BarqTextSecondary,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Relation icon on the right
        when (profile.relationType) {
            "mutual" -> Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = "Mutual",
                tint = BarqPink,
                modifier = Modifier.size(16.dp)
            )
            "liked" -> Icon(
                imageVector = Icons.Default.Pets,
                contentDescription = "Liked",
                tint = BarqOrange,
                modifier = Modifier.size(15.dp)
            )
            "likedBy" -> Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = "Liked you",
                tint = BarqGold,
                modifier = Modifier.size(16.dp)
            )
            "friend" -> Icon(
                imageVector = Icons.Default.Star,
                contentDescription = "Friend",
                tint = BarqSky,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

private enum class FilterSubMenu {
    MAIN,
    AGE_MIN,
    AGE_MAX,
    GENDERS,
    RELATIONSHIP,
    ORIENTATION,
    SEX_POSITION,
    RELATIONS
}

@Composable
fun SearchFilterScreen(
    uiState: BarqUiState,
    onUpdateFilters: (BarqSearchFilters, Boolean) -> Unit,
    onClearFilters: () -> Unit,
    onOpenManualLocation: () -> Unit,
    onBack: () -> Unit
) {
    var subMenu by remember { mutableStateOf(FilterSubMenu.MAIN) }
    val filters = uiState.filters

    BackHandler(enabled = subMenu != FilterSubMenu.MAIN) {
        subMenu = FilterSubMenu.MAIN
    }

    when (subMenu) {
        FilterSubMenu.MAIN -> {
            MainFiltersMenu(
                uiState = uiState,
                onOpenSubMenu = { subMenu = it },
                onUpdateFilters = onUpdateFilters,
                onClearFilters = onClearFilters,
                onOpenManualLocation = onOpenManualLocation,
                onBack = onBack
            )
        }
        FilterSubMenu.AGE_MIN -> {
            AgePickerSubMenu(
                title = stringResource(R.string.filters_age_min_title),
                currentValue = filters.minAge,
                onSelectAge = { newMin ->
                    val adjustedMax = if (newMin != null && filters.maxAge != null && filters.maxAge < newMin) {
                        newMin
                    } else {
                        filters.maxAge
                    }
                    onUpdateFilters(
                        filters.copy(minAge = newMin, maxAge = adjustedMax),
                        uiState.isAdMode
                    )
                },
                onDone = { subMenu = FilterSubMenu.MAIN }
            )
        }
        FilterSubMenu.AGE_MAX -> {
            AgePickerSubMenu(
                title = stringResource(R.string.filters_age_max_title),
                currentValue = filters.maxAge,
                onSelectAge = { newMax ->
                    val adjustedMin = if (newMax != null && filters.minAge != null && filters.minAge > newMax) {
                        newMax
                    } else {
                        filters.minAge
                    }
                    onUpdateFilters(
                        filters.copy(minAge = adjustedMin, maxAge = newMax),
                        uiState.isAdMode
                    )
                },
                onDone = { subMenu = FilterSubMenu.MAIN }
            )
        }
        FilterSubMenu.GENDERS -> {
            MultiSelectFilterSubMenu(
                title = stringResource(R.string.filters_genders),
                options = BarqFilterOptions.GENDERS,
                selectedIds = filters.genders,
                onToggleOption = { id ->
                    val next = if (id in filters.genders) filters.genders - id else filters.genders + id
                    onUpdateFilters(filters.copy(genders = next), uiState.isAdMode)
                },
                onClearSection = {
                    onUpdateFilters(filters.copy(genders = emptySet()), uiState.isAdMode)
                },
                onDone = { subMenu = FilterSubMenu.MAIN }
            )
        }
        FilterSubMenu.RELATIONSHIP -> {
            MultiSelectFilterSubMenu(
                title = stringResource(R.string.filters_relationship_status),
                options = BarqFilterOptions.RELATIONSHIP_STATUSES,
                selectedIds = filters.relationshipStatus,
                onToggleOption = { id ->
                    val next = if (id in filters.relationshipStatus) {
                        filters.relationshipStatus - id
                    } else {
                        filters.relationshipStatus + id
                    }
                    onUpdateFilters(filters.copy(relationshipStatus = next), uiState.isAdMode)
                },
                onClearSection = {
                    onUpdateFilters(filters.copy(relationshipStatus = emptySet()), uiState.isAdMode)
                },
                onDone = { subMenu = FilterSubMenu.MAIN }
            )
        }
        FilterSubMenu.ORIENTATION -> {
            MultiSelectFilterSubMenu(
                title = stringResource(R.string.filters_sexual_orientation),
                options = BarqFilterOptions.SEXUAL_ORIENTATIONS,
                selectedIds = filters.sexualOrientation,
                onToggleOption = { id ->
                    val next = if (id in filters.sexualOrientation) {
                        filters.sexualOrientation - id
                    } else {
                        filters.sexualOrientation + id
                    }
                    onUpdateFilters(filters.copy(sexualOrientation = next), uiState.isAdMode)
                },
                onClearSection = {
                    onUpdateFilters(filters.copy(sexualOrientation = emptySet()), uiState.isAdMode)
                },
                onDone = { subMenu = FilterSubMenu.MAIN }
            )
        }
        FilterSubMenu.SEX_POSITION -> {
            MultiSelectFilterSubMenu(
                title = stringResource(R.string.filters_sexual_position),
                options = BarqFilterOptions.SEX_POSITIONS,
                selectedIds = filters.sexPositions,
                onToggleOption = { id ->
                    val next = if (id in filters.sexPositions) {
                        filters.sexPositions - id
                    } else {
                        filters.sexPositions + id
                    }
                    onUpdateFilters(filters.copy(sexPositions = next), uiState.isAdMode)
                },
                onClearSection = {
                    onUpdateFilters(filters.copy(sexPositions = emptySet()), uiState.isAdMode)
                },
                onDone = { subMenu = FilterSubMenu.MAIN }
            )
        }
        FilterSubMenu.RELATIONS -> {
            MultiSelectFilterSubMenu(
                title = stringResource(R.string.filters_relation_type),
                options = BarqFilterOptions.RELATION_TYPES,
                selectedIds = filters.relationTypes,
                onToggleOption = { id ->
                    val next = if (id in filters.relationTypes) {
                        filters.relationTypes - id
                    } else {
                        filters.relationTypes + id
                    }
                    onUpdateFilters(filters.copy(relationTypes = next), uiState.isAdMode)
                },
                onClearSection = {
                    onUpdateFilters(filters.copy(relationTypes = emptySet()), uiState.isAdMode)
                },
                onDone = { subMenu = FilterSubMenu.MAIN }
            )
        }
    }
}

@Composable
private fun MainFiltersMenu(
    uiState: BarqUiState,
    onOpenSubMenu: (FilterSubMenu) -> Unit,
    onUpdateFilters: (BarqSearchFilters, Boolean) -> Unit,
    onClearFilters: () -> Unit,
    onOpenManualLocation: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val listState = rememberScalingLazyListState()
    val filters = uiState.filters
    val searchKey = "barq_search_query"

    val searchLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data: Intent? = result.data
        if (data != null) {
            val results: Bundle? = RemoteInput.getResultsFromIntent(data)
            val query = results?.getCharSequence(searchKey)?.toString().orEmpty()
            onUpdateFilters(filters.copy(displayName = query), uiState.isAdMode)
        }
    }

    fun launchSearchKeyboard() {
        val remoteInputs = listOf(
            RemoteInput.Builder(searchKey)
                .setLabel(context.getString(R.string.filters_name_input_label))
                .build()
        )
        val intent = RemoteInputIntentHelper.createActionRemoteInputIntent()
        RemoteInputIntentHelper.putRemoteInputsExtra(intent, remoteInputs)
        searchLauncher.launch(intent)
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
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.filters_title),
                        color = BarqOrange,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp
                    )
                    if (filters.hasActiveFilters) {
                        Text(
                            text = stringResource(R.string.filters_active_count_fmt, filters.activeCount),
                            color = BarqOrangeLight,
                            fontSize = 10.sp
                        )
                    }
                }
            }

            // Location / Manual City Selection
            item {
                val activePlace = uiState.currentPlace ?: uiState.currentUser?.profile?.location?.place
                val unknownLoc = stringResource(R.string.location_unknown)
                FilterMenuItemCard(
                    icon = Icons.Default.LocationCity,
                    title = stringResource(R.string.filters_location_title),
                    subtitle = activePlace?.formattedShort(unknownLoc)
                        ?: stringResource(R.string.filters_location_set_manual),
                    isSelected = activePlace != null,
                    accentColor = BarqSky,
                    onClick = onOpenManualLocation
                )
            }

            // Name search
            item {
                Chip(
                    onClick = { launchSearchKeyboard() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ChipDefaults.primaryChipColors(
                        backgroundColor = if (filters.displayName.isNotBlank()) BarqOrange else BarqCardBg
                    ),
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    label = {
                        Text(
                            text = if (filters.displayName.isBlank()) {
                                stringResource(R.string.filters_search_by_name)
                            } else {
                                stringResource(R.string.filters_name_fmt, filters.displayName)
                            },
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                )
            }

            if (filters.displayName.isNotBlank()) {
                item {
                    CompactChip(
                        onClick = {
                            onUpdateFilters(filters.copy(displayName = ""), uiState.isAdMode)
                        },
                        colors = ChipDefaults.secondaryChipColors(backgroundColor = BarqCardBg),
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = null,
                                tint = BarqOrangeLight,
                                modifier = Modifier.size(14.dp)
                            )
                        },
                        label = {
                            Text(stringResource(R.string.filters_clear_name), fontSize = 10.sp)
                        }
                    )
                }
            }

            // Group: Age
            item {
                FilterGroupHeader(title = stringResource(R.string.filters_group_age))
            }

            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AgeBoxButton(
                        label = stringResource(R.string.filters_age_min_short),
                        value = filters.minAge,
                        onClick = { onOpenSubMenu(FilterSubMenu.AGE_MIN) },
                        modifier = Modifier.weight(1f)
                    )
                    AgeBoxButton(
                        label = stringResource(R.string.filters_age_max_short),
                        value = filters.maxAge,
                        onClick = { onOpenSubMenu(FilterSubMenu.AGE_MAX) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Group: Profile
            item {
                FilterGroupHeader(title = stringResource(R.string.filters_group_profile))
            }

            item {
                FilterMenuItemCard(
                    icon = Icons.Default.Wc,
                    title = stringResource(R.string.filters_genders),
                    subtitle = BarqFilterOptions.formatSelected(
                        context,
                        filters.genders,
                        BarqFilterOptions.GENDERS
                    ),
                    isSelected = filters.genders.isNotEmpty(),
                    onClick = { onOpenSubMenu(FilterSubMenu.GENDERS) }
                )
            }

            item {
                FilterMenuItemCard(
                    icon = Icons.Default.Favorite,
                    title = stringResource(R.string.filters_relationship_status),
                    subtitle = BarqFilterOptions.formatSelected(
                        context,
                        filters.relationshipStatus,
                        BarqFilterOptions.RELATIONSHIP_STATUSES
                    ),
                    isSelected = filters.relationshipStatus.isNotEmpty(),
                    onClick = { onOpenSubMenu(FilterSubMenu.RELATIONSHIP) }
                )
            }

            item {
                FilterMenuItemCard(
                    icon = Icons.Default.Star,
                    title = stringResource(R.string.filters_sexual_orientation),
                    subtitle = BarqFilterOptions.formatSelected(
                        context,
                        filters.sexualOrientation,
                        BarqFilterOptions.SEXUAL_ORIENTATIONS
                    ),
                    isSelected = filters.sexualOrientation.isNotEmpty(),
                    onClick = { onOpenSubMenu(FilterSubMenu.ORIENTATION) }
                )
            }

            item {
                FilterMenuItemCard(
                    icon = Icons.Default.Favorite,
                    title = stringResource(R.string.filters_relation_type),
                    subtitle = BarqFilterOptions.formatSelected(
                        context,
                        filters.relationTypes,
                        BarqFilterOptions.RELATION_TYPES
                    ),
                    isSelected = filters.relationTypes.isNotEmpty(),
                    accentColor = BarqPink,
                    onClick = { onOpenSubMenu(FilterSubMenu.RELATIONS) }
                )
            }

            // Group: Search Options
            item {
                FilterGroupHeader(title = stringResource(R.string.filters_group_options))
            }

            item {
                ToggleChip(
                    checked = filters.requireProfileImage,
                    onCheckedChange = { checked ->
                        onUpdateFilters(filters.copy(requireProfileImage = checked), uiState.isAdMode)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ToggleChipDefaults.toggleChipColors(
                        checkedStartBackgroundColor = BarqCardBg,
                        checkedEndBackgroundColor = BarqOrange.copy(alpha = 0.35f),
                        uncheckedStartBackgroundColor = BarqCardBg,
                        uncheckedEndBackgroundColor = BarqCardBg
                    ),
                    appIcon = {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = if (filters.requireProfileImage) BarqOrangeLight else BarqTextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    label = {
                        Text(
                            text = stringResource(R.string.filters_require_profile_image),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    secondaryLabel = {
                        Text(
                            text = stringResource(R.string.filters_require_profile_image_sub),
                            fontSize = 9.sp,
                            color = BarqTextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    toggleControl = {
                        Checkbox(
                            checked = filters.requireProfileImage,
                            colors = CheckboxDefaults.colors(
                                checkedBoxColor = BarqOrange,
                                checkedCheckmarkColor = Color.White
                            )
                        )
                    }
                )
            }

            // Group: After Dark (AD)
            item {
                FilterGroupHeader(title = stringResource(R.string.filters_group_after_dark))
            }

            item {
                ToggleChip(
                    checked = uiState.isAdMode,
                    onCheckedChange = { checked ->
                        onUpdateFilters(filters, checked)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ToggleChipDefaults.toggleChipColors(
                        checkedStartBackgroundColor = BarqCardBg,
                        checkedEndBackgroundColor = BarqPink.copy(alpha = 0.35f),
                        uncheckedStartBackgroundColor = BarqCardBg,
                        uncheckedEndBackgroundColor = BarqCardBg
                    ),
                    label = {
                        Text(stringResource(R.string.filters_after_dark_mode), fontSize = 11.sp)
                    },
                    toggleControl = {
                        Switch(
                            checked = uiState.isAdMode,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = BarqPink,
                                checkedTrackColor = BarqPink.copy(alpha = 0.5f)
                            )
                        )
                    }
                )
            }

            item {
                FilterMenuItemCard(
                    icon = Icons.Default.SwapVert,
                    title = stringResource(R.string.filters_sexual_position),
                    subtitle = BarqFilterOptions.formatSelected(
                        context,
                        filters.sexPositions,
                        BarqFilterOptions.SEX_POSITIONS
                    ),
                    isSelected = filters.sexPositions.isNotEmpty(),
                    accentColor = BarqPink,
                    onClick = { onOpenSubMenu(FilterSubMenu.SEX_POSITION) }
                )
            }

            // Apply / Back button
            item {
                Chip(
                    onClick = onBack,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    colors = ChipDefaults.primaryChipColors(backgroundColor = BarqOrange),
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    label = {
                        Text(
                            text = stringResource(R.string.filters_show_results),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                )
            }

            // Footer: Clear filters
            item {
                Chip(
                    onClick = onClearFilters,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ChipDefaults.secondaryChipColors(backgroundColor = Color(0xFF3B1219)),
                    icon = {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = null,
                            tint = Color(0xFFFCA5A5),
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    label = {
                        Text(
                            text = stringResource(R.string.filters_clear_all),
                            color = Color(0xFFFCA5A5),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun FilterGroupHeader(title: String) {
    Text(
        text = title,
        color = BarqOrangeLight,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
    )
}

@Composable
private fun AgeBoxButton(
    label: String,
    value: Int?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasVal = value != null && value > 0
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(BarqCardBg)
            .border(
                width = 1.dp,
                color = if (hasVal) BarqOrange else BarqCardBorder,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 6.dp)
    ) {
        Text(
            text = label,
            color = BarqTextSecondary,
            fontSize = 10.sp
        )
        Text(
            text = if (hasVal) {
                stringResource(R.string.filters_age_years_fmt, value ?: 0)
            } else {
                stringResource(R.string.filters_age_any)
            },
            color = if (hasVal) BarqOrangeLight else BarqTextPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
private fun FilterMenuItemCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isSelected: Boolean,
    accentColor: Color = BarqOrange,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(BarqCardBg)
            .border(
                width = 1.dp,
                color = if (isSelected) accentColor else BarqCardBorder,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isSelected) accentColor else BarqTextSecondary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = BarqTextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                color = if (isSelected) accentColor else BarqTextSecondary,
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = BarqTextSecondary,
            modifier = Modifier.size(16.dp)
        )
    }
}

@Composable
private fun AgePickerSubMenu(
    title: String,
    currentValue: Int?,
    onSelectAge: (Int?) -> Unit,
    onDone: () -> Unit
) {
    val listState = rememberScalingLazyListState()
    val ageInputKey = "barq_age_input"
    val presets = listOf(18, 20, 22, 25, 30, 35, 40, 50)

    val ageKeyboardLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data: Intent? = result.data
        if (data != null) {
            val results: Bundle? = RemoteInput.getResultsFromIntent(data)
            val raw = results?.getCharSequence(ageInputKey)?.toString()?.trim().orEmpty()
            val parsed = raw.toIntOrNull()
            if (parsed != null && parsed > 0) {
                onSelectAge(parsed.coerceIn(18, 120))
            } else if (raw.isEmpty() || parsed == 0) {
                onSelectAge(null)
            }
        }
    }

    fun launchAgeKeyboard() {
        val remoteInputs = listOf(
            RemoteInput.Builder(ageInputKey)
                .setLabel(title)
                .build()
        )
        val intent = RemoteInputIntentHelper.createActionRemoteInputIntent()
        RemoteInputIntentHelper.putRemoteInputsExtra(intent, remoteInputs)
        ageKeyboardLauncher.launch(intent)
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
            item {
                Text(
                    text = title,
                    color = BarqOrange,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center
                )
            }

            // Stepper - / value / +
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    Button(
                        onClick = {
                            val cur = currentValue ?: 18
                            if (cur <= 18) onSelectAge(null) else onSelectAge(cur - 1)
                        },
                        modifier = Modifier.size(34.dp),
                        colors = ButtonDefaults.secondaryButtonColors(backgroundColor = BarqCardBg)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Remove,
                            contentDescription = stringResource(R.string.filters_age_less),
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Text(
                        text = if (currentValue != null && currentValue > 0) {
                            stringResource(R.string.filters_age_years_fmt, currentValue)
                        } else {
                            stringResource(R.string.filters_age_any)
                        },
                        color = BarqTextPrimary,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.width(72.dp)
                    )

                    Button(
                        onClick = {
                            val next = (currentValue ?: 17) + 1
                            onSelectAge(next.coerceIn(18, 99))
                        },
                        modifier = Modifier.size(34.dp),
                        colors = ButtonDefaults.secondaryButtonColors(backgroundColor = BarqCardBg)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = stringResource(R.string.filters_age_more),
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            item {
                CompactChip(
                    onClick = { launchAgeKeyboard() },
                    colors = ChipDefaults.secondaryChipColors(backgroundColor = BarqCardBg),
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            tint = BarqOrangeLight,
                            modifier = Modifier.size(14.dp)
                        )
                    },
                    label = {
                        Text(stringResource(R.string.filters_age_enter_manually), fontSize = 11.sp)
                    }
                )
            }

            item {
                Chip(
                    onClick = {
                        onSelectAge(null)
                        onDone()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ChipDefaults.secondaryChipColors(
                        backgroundColor = if (currentValue == null) BarqOrange else BarqCardBg
                    ),
                    label = {
                        Text(stringResource(R.string.filters_age_no_limit), fontSize = 11.sp)
                    }
                )
            }

            items(presets) { preset ->
                val isSelected = currentValue == preset
                Chip(
                    onClick = {
                        onSelectAge(preset)
                        onDone()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ChipDefaults.secondaryChipColors(
                        backgroundColor = if (isSelected) BarqOrange else BarqCardBg
                    ),
                    label = {
                        Text(
                            text = stringResource(R.string.filters_age_years_fmt, preset),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                )
            }

            item {
                Chip(
                    onClick = onDone,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ChipDefaults.primaryChipColors(backgroundColor = BarqOrange),
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    label = {
                        Text(stringResource(R.string.filters_done), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                )
            }
        }
    }
}

@Composable
private fun MultiSelectFilterSubMenu(
    title: String,
    options: List<FilterOption>,
    selectedIds: Set<String>,
    onToggleOption: (String) -> Unit,
    onClearSection: () -> Unit,
    onDone: () -> Unit
) {
    val listState = rememberScalingLazyListState()

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
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = title,
                        color = BarqOrange,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = if (selectedIds.isEmpty()) {
                            stringResource(R.string.filters_none_selected)
                        } else {
                            stringResource(R.string.filters_selected_count_fmt, selectedIds.size)
                        },
                        color = BarqTextSecondary,
                        fontSize = 10.sp
                    )
                }
            }

            items(options, key = { it.id }) { option ->
                val checked = option.id in selectedIds
                val localizedLabel = stringResource(option.labelRes)
                ToggleChip(
                    checked = checked,
                    onCheckedChange = { onToggleOption(option.id) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ToggleChipDefaults.toggleChipColors(
                        checkedStartBackgroundColor = BarqCardBg,
                        checkedEndBackgroundColor = BarqOrange.copy(alpha = 0.35f),
                        uncheckedStartBackgroundColor = BarqCardBg,
                        uncheckedEndBackgroundColor = BarqCardBg
                    ),
                    label = {
                        Text(
                            text = localizedLabel,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    secondaryLabel = if (!localizedLabel.equals(option.labelEn, ignoreCase = true)) {
                        {
                            Text(
                                text = option.labelEn,
                                fontSize = 9.sp,
                                color = BarqTextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    } else null,
                    toggleControl = {
                        Checkbox(
                            checked = checked,
                            colors = CheckboxDefaults.colors(
                                checkedBoxColor = BarqOrange,
                                checkedCheckmarkColor = Color.White
                            )
                        )
                    }
                )
            }

            if (selectedIds.isNotEmpty()) {
                item {
                    CompactChip(
                        onClick = onClearSection,
                        colors = ChipDefaults.secondaryChipColors(backgroundColor = Color(0xFF3B1219)),
                        icon = {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = null,
                                tint = Color(0xFFFCA5A5),
                                modifier = Modifier.size(14.dp)
                            )
                        },
                        label = {
                            Text(
                                text = stringResource(R.string.filters_clear_selection),
                                color = Color(0xFFFCA5A5),
                                fontSize = 10.sp
                            )
                        }
                    )
                }
            }

            item {
                Chip(
                    onClick = onDone,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ChipDefaults.primaryChipColors(backgroundColor = BarqOrange),
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    label = {
                        Text(stringResource(R.string.filters_done), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                )
            }
        }
    }
}

@Composable
fun MyAccountScreen(
    uiState: BarqUiState,
    onEnsureUserLoaded: () -> Unit,
    onOpenMyFullProfile: () -> Unit,
    onOpenRelationList: (RelationListType) -> Unit,
    onOpenNews: () -> Unit,
    onOpenSettings: () -> Unit,
    onLogout: () -> Unit
) {
    val listState = rememberScalingLazyListState()
    val user = uiState.currentUser
    val profile = user?.profile

    LaunchedEffect(user) {
        if (user == null && !uiState.isLoadingCurrentUser) {
            onEnsureUserLoaded()
        }
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
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                val avatar = profile?.avatarUrl(isAd = uiState.isAdMode || uiState.showNsfw, size = 256)
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(BarqCardBg)
                        .border(2.dp, BarqOrange, CircleShape)
                        .clickable { onOpenMyFullProfile() },
                    contentAlignment = Alignment.Center
                ) {
                    if (avatar != null) {
                        AsyncImage(
                            model = avatar,
                            contentDescription = profile.displayName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else if (uiState.isLoadingCurrentUser) {
                        CircularProgressIndicator(
                            indicatorColor = BarqOrange,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(26.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Pets,
                            contentDescription = null,
                            tint = BarqOrange,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
            }

            item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(vertical = 4.dp)
                ) {
                    Text(
                        text = profile?.displayName ?: stringResource(R.string.account_default_title),
                        color = BarqTextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center
                    )
                    if (!profile?.username.isNullOrBlank()) {
                        Text(
                            text = "@${profile?.username}",
                            color = BarqTextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }
            }

            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(BarqCardBg)
                            .border(1.dp, BarqOrange.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                            .clickable { onOpenRelationList(RelationListType.LIKES) }
                            .padding(vertical = 8.dp, horizontal = 6.dp)
                    ) {
                        Text(
                            text = "${user?.likeCount ?: 0}",
                            color = BarqOrange,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = stringResource(R.string.account_likes),
                            color = BarqTextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(BarqCardBg)
                            .border(1.dp, BarqPink.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                            .clickable { onOpenRelationList(RelationListType.MUTUALS) }
                            .padding(vertical = 8.dp, horizontal = 6.dp)
                    ) {
                        Text(
                            text = "${user?.mutualCount ?: 0}",
                            color = BarqPink,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = stringResource(R.string.account_mutuals),
                            color = BarqTextPrimary,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            item {
                Chip(
                    onClick = { onOpenRelationList(RelationListType.FRIENDS) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ChipDefaults.secondaryChipColors(backgroundColor = BarqCardBg),
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = BarqSky,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    label = {
                        Text(
                            text = stringResource(R.string.account_friends_btn),
                            color = BarqTextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                )
            }

            item {
                Chip(
                    onClick = onOpenNews,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ChipDefaults.secondaryChipColors(backgroundColor = BarqCardBg),
                    icon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Article,
                            contentDescription = null,
                            tint = BarqGold,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    label = {
                        Text(
                            text = stringResource(R.string.account_news_btn),
                            color = BarqTextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                )
            }

            item {
                Chip(
                    onClick = onOpenMyFullProfile,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ChipDefaults.primaryChipColors(backgroundColor = BarqOrange),
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    label = {
                        Text(
                            text = stringResource(R.string.account_my_profile_btn),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                )
            }

            item {
                Chip(
                    onClick = onOpenSettings,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ChipDefaults.secondaryChipColors(backgroundColor = BarqCardBg),
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = BarqOrange,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    label = {
                        Text(
                            text = stringResource(R.string.account_settings_btn),
                            color = BarqTextPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    secondaryLabel = {
                        Text(
                            text = stringResource(R.string.account_cache_fmt, uiState.formattedCacheSize()),
                            color = BarqTextSecondary,
                            fontSize = 10.sp
                        )
                    }
                )
            }

            item {
                Chip(
                    onClick = onLogout,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ChipDefaults.secondaryChipColors(backgroundColor = Color(0xFF3B1219)),
                    label = {
                        Text(
                            text = stringResource(R.string.account_logout_btn),
                            color = Color(0xFFFCA5A5),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                )
            }
        }
    }
}

@Composable
fun ManualLocationScreen(
    uiState: BarqUiState,
    onSearchPlaces: (String) -> Unit,
    onSelectPlace: (Place) -> Unit,
    onRetryGps: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val listState = rememberScalingLazyListState()
    val cityQueryKey = "barq_city_query"
    val activePlace = uiState.currentPlace ?: uiState.currentUser?.profile?.location?.place

    val cityKeyboardLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data: Intent? = result.data
        if (data != null) {
            val results: Bundle? = RemoteInput.getResultsFromIntent(data)
            val query = results?.getCharSequence(cityQueryKey)?.toString()?.trim().orEmpty()
            if (query.isNotBlank()) {
                onSearchPlaces(query)
            }
        }
    }

    fun launchCityKeyboard() {
        val remoteInputs = listOf(
            RemoteInput.Builder(cityQueryKey)
                .setLabel(context.getString(R.string.location_input_label))
                .build()
        )
        val intent = RemoteInputIntentHelper.createActionRemoteInputIntent()
        RemoteInputIntentHelper.putRemoteInputsExtra(intent, remoteInputs)
        cityKeyboardLauncher.launch(intent)
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val granted = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            perms[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (granted) {
            onRetryGps()
        }
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
            item {
                val unknownLoc = stringResource(R.string.location_unknown)
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.location_title),
                        color = BarqOrange,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                    if (activePlace != null) {
                        Text(
                            text = stringResource(
                                R.string.location_current_fmt,
                                activePlace.formattedShort(unknownLoc)
                            ),
                            color = BarqSky,
                            fontSize = 10.sp,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }

            item {
                Chip(
                    onClick = { launchCityKeyboard() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ChipDefaults.primaryChipColors(backgroundColor = BarqOrange),
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    label = {
                        Text(
                            text = if (uiState.placeSearchQuery.isBlank()) {
                                stringResource(R.string.location_search_placeholder)
                            } else {
                                stringResource(R.string.location_searching_fmt, uiState.placeSearchQuery)
                            },
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                )
            }

            item {
                Chip(
                    onClick = {
                        locationPermissionLauncher.launch(
                            arrayOf(
                                Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION
                            )
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ChipDefaults.secondaryChipColors(backgroundColor = BarqCardBg),
                    icon = {
                        if (uiState.isUpdatingGps) {
                            CircularProgressIndicator(
                                indicatorColor = BarqSky,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(15.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.MyLocation,
                                contentDescription = null,
                                tint = BarqSky,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    },
                    label = {
                        Text(
                            text = stringResource(R.string.location_use_watch_gps),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                )
            }

            if (uiState.isSearchingPlaces) {
                item {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(vertical = 12.dp)
                    ) {
                        CircularProgressIndicator(
                            indicatorColor = BarqOrange,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.location_searching_cities),
                            color = BarqTextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }
            } else if (!uiState.placeSearchError.isNullOrBlank()) {
                item {
                    Text(
                        text = uiState.placeSearchError,
                        color = Color(0xFFFCA5A5),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
            } else if (uiState.placeSearchResults.isNotEmpty()) {
                item {
                    FilterGroupHeader(
                        title = stringResource(
                            R.string.location_found_cities_fmt,
                            uiState.placeSearchResults.size
                        )
                    )
                }
                items(
                    items = uiState.placeSearchResults,
                    key = { it.id ?: "${it.place}_${it.region}_${it.country}" }
                ) { placeItem ->
                    val isCurrent = activePlace != null && (
                        (activePlace.id != null && activePlace.id == placeItem.id) ||
                            (activePlace.place == placeItem.place && activePlace.region == placeItem.region)
                        )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(BarqCardBg)
                            .border(
                                width = 1.dp,
                                color = if (isCurrent) BarqOrange else BarqCardBorder,
                                shape = RoundedCornerShape(14.dp)
                            )
                            .clickable {
                                onSelectPlace(placeItem)
                                onBack()
                            }
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = if (isCurrent) BarqOrange else BarqSky,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = placeItem.place ?: stringResource(R.string.location_unknown_city),
                                color = BarqTextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            val subText = listOfNotNull(
                                placeItem.region?.takeIf { it.isNotBlank() },
                                placeItem.country?.takeIf { it.isNotBlank() } ?: placeItem.countryCode
                            ).joinToString(", ")
                            if (subText.isNotBlank()) {
                                Text(
                                    text = subText,
                                    color = BarqTextSecondary,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        if (isCurrent) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = BarqOrange,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                    }
                }
            } else if (uiState.placeSearchQuery.isNotBlank()) {
                item {
                    Text(
                        text = stringResource(R.string.location_no_cities_for_fmt, uiState.placeSearchQuery),
                        color = BarqTextSecondary,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 10.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun AdNsfwOnboardingScreen(
    onComplete: (Boolean) -> Unit
) {
    val listState = rememberScalingLazyListState()

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
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.onboarding_ad_badge),
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(BarqPink)
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.onboarding_ad_title),
                        color = BarqTextPrimary,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }

            item {
                Text(
                    text = stringResource(R.string.onboarding_ad_desc),
                    color = BarqTextSecondary,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            item {
                Chip(
                    onClick = { onComplete(true) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ChipDefaults.primaryChipColors(backgroundColor = BarqPink),
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    label = {
                        Text(
                            text = stringResource(R.string.onboarding_ad_enable_btn),
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                )
            }

            item {
                Chip(
                    onClick = { onComplete(false) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ChipDefaults.secondaryChipColors(backgroundColor = BarqCardBg),
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = BarqGreen,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    label = {
                        Text(
                            text = stringResource(R.string.onboarding_ad_disable_btn),
                            color = BarqTextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                )
            }
        }
    }
}

@Composable
fun SettingsScreen(
    uiState: BarqUiState,
    onToggleAdMode: (Boolean) -> Unit,
    onToggleNsfw: (Boolean) -> Unit,
    onSelectLanguage: (AppLanguage) -> Unit,
    onRefreshCacheSize: () -> Unit,
    onClearCache: () -> Unit,
    onOpenManualLocation: () -> Unit
) {
    val listState = rememberScalingLazyListState()

    LaunchedEffect(Unit) {
        onRefreshCacheSize()
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
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        tint = BarqOrange,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = stringResource(R.string.settings_title),
                        color = BarqOrange,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }

            // After Dark & NSFW (18+) section
            item {
                FilterGroupHeader(title = stringResource(R.string.settings_ad_nsfw_section))
            }

            item {
                ToggleChip(
                    checked = uiState.isAdMode,
                    onCheckedChange = onToggleAdMode,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ToggleChipDefaults.toggleChipColors(
                        checkedStartBackgroundColor = BarqCardBg,
                        checkedEndBackgroundColor = BarqPink.copy(alpha = 0.35f),
                        uncheckedStartBackgroundColor = BarqCardBg,
                        uncheckedEndBackgroundColor = BarqCardBg
                    ),
                    label = {
                        Text(
                            text = stringResource(R.string.settings_ad_mode_title),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    secondaryLabel = {
                        Text(
                            text = stringResource(R.string.settings_ad_mode_sub),
                            fontSize = 9.sp,
                            color = BarqTextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    toggleControl = {
                        Switch(
                            checked = uiState.isAdMode,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = BarqPink,
                                checkedTrackColor = BarqPink.copy(alpha = 0.5f)
                            )
                        )
                    }
                )
            }

            item {
                ToggleChip(
                    checked = uiState.showNsfw,
                    onCheckedChange = onToggleNsfw,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ToggleChipDefaults.toggleChipColors(
                        checkedStartBackgroundColor = BarqCardBg,
                        checkedEndBackgroundColor = BarqPink.copy(alpha = 0.35f),
                        uncheckedStartBackgroundColor = BarqCardBg,
                        uncheckedEndBackgroundColor = BarqCardBg
                    ),
                    label = {
                        Text(
                            text = stringResource(R.string.settings_nsfw_title),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    secondaryLabel = {
                        Text(
                            text = stringResource(R.string.settings_nsfw_sub),
                            fontSize = 9.sp,
                            color = BarqTextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    toggleControl = {
                        Switch(
                            checked = uiState.showNsfw,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = BarqPink,
                                checkedTrackColor = BarqPink.copy(alpha = 0.5f)
                            )
                        )
                    }
                )
            }

            // Language selector section
            item {
                FilterGroupHeader(title = stringResource(R.string.settings_language_title))
            }

            items(AppLanguage.entries, key = { it.code }) { lang ->
                val isSelected = uiState.appLanguage == lang
                val labelText = if (lang == AppLanguage.SYSTEM) {
                    stringResource(R.string.settings_language_system)
                } else {
                    lang.nativeLabel
                }
                Chip(
                    onClick = { onSelectLanguage(lang) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ChipDefaults.secondaryChipColors(
                        backgroundColor = if (isSelected) BarqOrange else BarqCardBg
                    ),
                    icon = {
                        Icon(
                            imageVector = if (isSelected) Icons.Default.Check else Icons.Default.Language,
                            contentDescription = null,
                            tint = if (isSelected) Color.White else BarqOrange,
                            modifier = Modifier.size(15.dp)
                        )
                    },
                    label = {
                        Text(
                            text = labelText,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                )
            }

            // Image Cache section
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(BarqCardBg)
                        .padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.settings_image_cache_title),
                        color = BarqTextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = uiState.formattedCacheSize(),
                        color = BarqOrange,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 18.sp
                    )
                    Text(
                        text = stringResource(R.string.settings_image_cache_sub),
                        color = BarqTextSecondary,
                        fontSize = 9.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }

            item {
                Chip(
                    onClick = {
                        if (!uiState.isClearingCache) {
                            onClearCache()
                        }
                    },
                    enabled = !uiState.isClearingCache,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ChipDefaults.secondaryChipColors(backgroundColor = Color(0xFF3B1219)),
                    icon = {
                        if (uiState.isClearingCache) {
                            CircularProgressIndicator(
                                indicatorColor = Color(0xFFFCA5A5),
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(15.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = null,
                                tint = Color(0xFFFCA5A5),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    },
                    label = {
                        Text(
                            text = if (uiState.isClearingCache) {
                                stringResource(R.string.settings_clearing_cache)
                            } else {
                                stringResource(R.string.settings_clear_cache)
                            },
                            color = Color(0xFFFCA5A5),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                )
            }

            item {
                CompactChip(
                    onClick = onRefreshCacheSize,
                    colors = ChipDefaults.secondaryChipColors(backgroundColor = BarqCardBg),
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            tint = BarqOrange,
                            modifier = Modifier.size(14.dp)
                        )
                    },
                    label = {
                        Text(
                            text = stringResource(R.string.settings_refresh_size),
                            color = BarqTextPrimary,
                            fontSize = 11.sp
                        )
                    }
                )
            }

            // Saved location section
            item {
                val unknownLoc = stringResource(R.string.location_unknown)
                Chip(
                    onClick = onOpenManualLocation,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    colors = ChipDefaults.secondaryChipColors(backgroundColor = BarqCardBg),
                    icon = {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = BarqOrange,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    label = {
                        Text(
                            text = stringResource(R.string.settings_saved_city),
                            color = BarqTextPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    },
                    secondaryLabel = {
                        Text(
                            text = uiState.currentPlace?.formattedShort(unknownLoc)
                                ?: stringResource(R.string.settings_no_saved_city),
                            color = BarqOrange,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                )
            }
        }
    }
}

@Composable
fun RelationProfilesScreen(
    uiState: BarqUiState,
    onSelectType: (RelationListType) -> Unit,
    onProfileClick: (String) -> Unit,
    onRefresh: () -> Unit
) {
    val listState = rememberScalingLazyListState()
    val currentType = uiState.relationListType
    val titleRes = when (currentType) {
        RelationListType.MUTUALS -> R.string.relations_title_mutuals
        RelationListType.LIKES -> R.string.relations_title_likes
        RelationListType.FRIENDS -> R.string.relations_title_friends
    }
    val accentColor = when (currentType) {
        RelationListType.MUTUALS -> BarqPink
        RelationListType.LIKES -> BarqOrange
        RelationListType.FRIENDS -> BarqSky
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
            item {
                Text(
                    text = stringResource(titleRes),
                    color = accentColor,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center
                )
            }

            // Quick switch tabs
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val tabs = listOf(
                        Triple(RelationListType.MUTUALS, R.string.relations_tab_mutuals, BarqPink),
                        Triple(RelationListType.LIKES, R.string.relations_tab_likes, BarqOrange),
                        Triple(RelationListType.FRIENDS, R.string.relations_tab_friends, BarqSky)
                    )
                    for ((tabType, labelRes, tabColor) in tabs) {
                        val selected = currentType == tabType
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selected) tabColor else BarqCardBg)
                                .clickable { onSelectType(tabType) }
                                .padding(vertical = 6.dp, horizontal = 4.dp)
                        ) {
                            Text(
                                text = stringResource(labelRes),
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            if (uiState.isLoadingRelationProfiles) {
                item {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(vertical = 18.dp)
                    ) {
                        CircularProgressIndicator(
                            indicatorColor = accentColor,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.nearby_loading_profiles),
                            color = BarqTextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }
            } else if (!uiState.relationProfilesError.isNullOrBlank()) {
                item {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF3B1219))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = uiState.relationProfilesError,
                            color = Color(0xFFFCA5A5),
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        CompactChip(
                            onClick = onRefresh,
                            label = { Text(stringResource(R.string.nearby_retry), fontSize = 11.sp) }
                        )
                    }
                }
            } else if (uiState.relationProfiles.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.relations_empty),
                        color = BarqTextSecondary,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 18.dp)
                    )
                }
            } else {
                items(
                    items = uiState.relationProfiles,
                    key = { it.uuid }
                ) { profile ->
                    ProfileListCard(
                        profile = profile,
                        isAdMode = uiState.isAdMode || uiState.showNsfw,
                        onClick = { onProfileClick(profile.uuid) }
                    )
                }
            }
        }
    }
}

@Composable
fun NewsListScreen(
    uiState: BarqUiState,
    onNewsClick: (NewsItem) -> Unit,
    onRefresh: () -> Unit
) {
    val listState = rememberScalingLazyListState()

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
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Article,
                        contentDescription = null,
                        tint = BarqGold,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = stringResource(R.string.news_title),
                        color = BarqGold,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp
                    )
                }
            }

            if (uiState.isLoadingNews && uiState.newsItems.isEmpty()) {
                item {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(vertical = 18.dp)
                    ) {
                        CircularProgressIndicator(
                            indicatorColor = BarqGold,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.news_loading),
                            color = BarqTextSecondary,
                            fontSize = 11.sp
                        )
                    }
                }
            } else if (!uiState.newsError.isNullOrBlank() && uiState.newsItems.isEmpty()) {
                item {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF3B1219))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = uiState.newsError,
                            color = Color(0xFFFCA5A5),
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        CompactChip(
                            onClick = onRefresh,
                            label = { Text(stringResource(R.string.nearby_retry), fontSize = 11.sp) }
                        )
                    }
                }
            } else if (uiState.newsItems.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.news_empty),
                        color = BarqTextSecondary,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(vertical = 18.dp)
                    )
                }
            } else {
                items(
                    items = uiState.newsItems,
                    key = { it.id ?: it.slug }
                ) { news ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(BarqCardBg)
                            .border(1.dp, BarqCardBorder, RoundedCornerShape(16.dp))
                            .clickable { onNewsClick(news) }
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                if (!news.category.isNullOrBlank()) {
                                    Text(
                                        text = news.category,
                                        color = BarqGold,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 9.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                val dateStr = news.formattedDate()
                                if (dateStr.isNotBlank()) {
                                    Text(
                                        text = dateStr,
                                        color = BarqTextSecondary,
                                        fontSize = 9.sp
                                    )
                                }
                            }
                            Text(
                                text = news.title,
                                color = BarqTextPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = null,
                            tint = BarqTextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NewsDetailScreen(
    uiState: BarqUiState
) {
    val listState = rememberScalingLazyListState()
    val news = uiState.selectedNewsItem

    Scaffold(
        timeText = { TimeText() },
        vignette = { Vignette(vignettePosition = VignettePosition.TopAndBottom) },
        positionIndicator = { PositionIndicator(scalingLazyListState = listState) }
    ) {
        if (news == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(BarqDarkBg),
                contentAlignment = Alignment.Center
            ) {
                Text(stringResource(R.string.news_empty), color = BarqTextSecondary, fontSize = 11.sp)
            }
            return@Scaffold
        }

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
                        .border(1.dp, BarqGold.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                        .padding(10.dp)
                ) {
                    if (!news.category.isNullOrBlank()) {
                        Text(
                            text = news.category,
                            color = BarqGold,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                    }
                    Text(
                        text = news.title,
                        color = BarqTextPrimary,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                    val dateStr = news.formattedDate()
                    if (dateStr.isNotBlank()) {
                        Text(
                            text = dateStr,
                            color = BarqTextSecondary,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
            }

            if (uiState.isLoadingNewsDetail && news.content.isNullOrBlank()) {
                item {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(vertical = 16.dp)
                    ) {
                        CircularProgressIndicator(
                            indicatorColor = BarqGold,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.news_loading_article),
                            color = BarqTextSecondary,
                            fontSize = 10.sp
                        )
                    }
                }
            } else if (!news.content.isNullOrBlank()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(BarqCardBg)
                            .padding(10.dp)
                    ) {
                        Text(
                            text = news.content.trim(),
                            color = BarqTextPrimary,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }
}





