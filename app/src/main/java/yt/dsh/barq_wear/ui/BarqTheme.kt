package yt.dsh.barq_wear.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.foundation.ExperimentalWearFoundationApi
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.foundation.rotary.rotaryScrollable
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme

val BarqOrange = Color(0xFFF76808)
val BarqOrangeLight = Color(0xFFFF8B3D)
val BarqPink = Color(0xFFEC4899)
val BarqSky = Color(0xFF38BDF8)
val BarqGold = Color(0xFFFACC15)
val BarqGreen = Color(0xFF22C55E)
val BarqDarkBg = Color(0xFF0E0E12)
val BarqCardBg = Color(0xFF1C1C24)
val BarqCardBorder = Color(0xFF2E2E3A)
val BarqTextPrimary = Color(0xFFF4F4F6)
val BarqTextSecondary = Color(0xFFA1A1AA)

private val BarqWearColorPalette = Colors(
    primary = BarqOrange,
    primaryVariant = BarqOrangeLight,
    secondary = BarqPink,
    secondaryVariant = BarqSky,
    background = BarqDarkBg,
    surface = BarqCardBg,
    error = Color(0xFFEF4444),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = BarqTextPrimary,
    onSurface = BarqTextPrimary,
    onSurfaceVariant = BarqTextSecondary,
    onError = Color.White
)

@Composable
fun BarqWearTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colors = BarqWearColorPalette,
        content = content
    )
}

@OptIn(ExperimentalWearFoundationApi::class)
@Composable
fun Modifier.wearRotaryScroll(
    listState: ScalingLazyListState
): Modifier {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        repeat(5) {
            runCatching { focusRequester.requestFocus() }
            kotlinx.coroutines.delay(80)
        }
    }

    return this.rotaryScrollable(
        behavior = RotaryScrollableDefaults.behavior(
            scrollableState = listState,
            hapticFeedbackEnabled = true
        ),
        focusRequester = focusRequester
    )
}

