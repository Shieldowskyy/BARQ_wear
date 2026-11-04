package app.barq.wear.ui

import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CircularProgressIndicator
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText
import androidx.wear.compose.material.Vignette
import androidx.wear.compose.material.VignettePosition
import androidx.wear.input.RemoteInputIntentHelper
import app.barq.wear.R

@Composable
fun LoginScreen(
    uiState: BarqUiState,
    onRefreshQr: () -> Unit,
    onSubmitManualToken: (String) -> Unit
) {
    val context = LocalContext.current
    val listState = rememberScalingLazyListState()
    var fullScreenQr by remember { mutableStateOf(false) }

    val tokenInputKey = "barq_manual_token"
    val remoteInputLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data: Intent? = result.data
        if (data != null) {
            val results: Bundle? = RemoteInput.getResultsFromIntent(data)
            val tokenStr = results?.getCharSequence(tokenInputKey)?.toString()
            if (!tokenStr.isNullOrBlank()) {
                onSubmitManualToken(tokenStr)
            }
        }
    }

    fun launchTokenInput() {
        val remoteInputs = listOf(
            RemoteInput.Builder(tokenInputKey)
                .setLabel(context.getString(R.string.login_token_input_label))
                .build()
        )
        val intent = RemoteInputIntentHelper.createActionRemoteInputIntent()
        RemoteInputIntentHelper.putRemoteInputsExtra(intent, remoteInputs)
        remoteInputLauncher.launch(intent)
    }

    if (fullScreenQr && uiState.qrSession != null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.White)
                .clickable { fullScreenQr = false },
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(14.dp)
            ) {
                Image(
                    bitmap = uiState.qrSession.qrBitmap.asImageBitmap(),
                    contentDescription = stringResource(R.string.login_qr_content_desc),
                    modifier = Modifier.size(148.dp)
                )
                Text(
                    text = stringResource(R.string.login_tap_to_shrink),
                    color = Color.Black,
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
        return
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
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(BarqOrange)
                    )
                    Text(
                        text = stringResource(R.string.app_title_wear),
                        color = BarqOrange,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp
                    )
                }
            }

            item {
                Text(
                    text = uiState.qrStatusMessage.ifBlank { stringResource(R.string.qr_status_init) },
                    color = BarqTextSecondary,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }

            if (uiState.authError != null) {
                item {
                    Text(
                        text = uiState.authError,
                        color = Color(0xFFF87171),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF3B1219))
                            .padding(8.dp)
                    )
                }
            }

            item {
                Box(
                    modifier = Modifier
                        .padding(vertical = 6.dp)
                        .size(132.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White)
                        .border(2.dp, BarqOrange, RoundedCornerShape(14.dp))
                        .clickable(enabled = uiState.qrSession != null) {
                            fullScreenQr = true
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (uiState.isLoadingQr) {
                        CircularProgressIndicator(
                            indicatorColor = BarqOrange,
                            trackColor = Color.LightGray,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(38.dp)
                        )
                    } else if (uiState.qrSession != null) {
                        Image(
                            bitmap = uiState.qrSession.qrBitmap.asImageBitmap(),
                            contentDescription = stringResource(R.string.login_qr_content_desc),
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(6.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.QrCode2,
                            contentDescription = null,
                            tint = Color.DarkGray,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }
            }

            if (uiState.qrSession != null) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .clickable { fullScreenQr = true }
                            .padding(bottom = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ZoomIn,
                            contentDescription = null,
                            tint = BarqOrangeLight,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = stringResource(R.string.login_tap_to_enlarge),
                            color = BarqOrangeLight,
                            fontSize = 10.sp
                        )
                    }
                }
            }

            item {
                Chip(
                    onClick = onRefreshQr,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ChipDefaults.secondaryChipColors(backgroundColor = BarqCardBg),
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.login_refresh_qr),
                            tint = BarqOrange
                        )
                    },
                    label = {
                        Text(
                            text = stringResource(R.string.login_refresh_qr),
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                    }
                )
            }

            item {
                Chip(
                    onClick = { launchTokenInput() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ChipDefaults.secondaryChipColors(backgroundColor = BarqCardBg),
                    icon = {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = stringResource(R.string.login_paste_token),
                            tint = BarqSky
                        )
                    },
                    label = {
                        Text(
                            text = stringResource(R.string.login_paste_token),
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                    }
                )
            }

            item {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "ADB: adb shell am start -a android.intent.action.VIEW -d \"barq://token?access_token=ory_at_...\"",
                    color = BarqTextSecondary.copy(alpha = 0.65f),
                    fontSize = 9.sp,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

