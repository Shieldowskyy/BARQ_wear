package yt.dsh.barq_wear.ui

import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.rotary.onPreRotaryScrollEvent
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.material.Colors
import androidx.wear.compose.material.MaterialTheme
import kotlinx.coroutines.launch

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

@Composable
fun Modifier.wearRotaryScroll(
    listState: ScalingLazyListState,
    speedMultiplier: Float = 1.8f
): Modifier {
    val focusRequester = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        repeat(5) {
            runCatching { focusRequester.requestFocus() }
            kotlinx.coroutines.delay(80)
        }
    }

    return this
        .onPreRotaryScrollEvent { event ->
            val delta = event.verticalScrollPixels * speedMultiplier
            coroutineScope.launch {
                listState.scrollBy(delta)
            }
            true
        }
        .onRotaryScrollEvent { event ->
            val delta = event.verticalScrollPixels * speedMultiplier
            coroutineScope.launch {
                listState.scrollBy(delta)
            }
            true
        }
        .focusRequester(focusRequester)
        .focusable()
}

