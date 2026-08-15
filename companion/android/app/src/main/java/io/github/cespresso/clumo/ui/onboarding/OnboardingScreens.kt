package io.github.cespresso.clumo.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.cespresso.clumo.R
import io.github.cespresso.clumo.data.AppPreferences
import io.github.cespresso.clumo.ui.components.BrandCorner
import io.github.cespresso.clumo.ui.components.CtaPillButton
import io.github.cespresso.clumo.ui.components.DeviceFace
import io.github.cespresso.clumo.ui.components.FaceBits
import io.github.cespresso.clumo.ui.theme.ClumoColors
import io.github.cespresso.clumo.ui.theme.LocalClumoAccents
import io.github.cespresso.clumo.ui.theme.RoundedFontFamily
import kotlinx.coroutines.launch

/** Bluetooth mark drawn as an 8x8 dot pattern for the explainer face. */
private val BT_FACE_BITS = FaceBits.fromBitsString(
    listOf(
        "00010000", "00011000", "01010100", "00111000",
        "00111000", "01010100", "00011000", "00010000",
    ).joinToString("")
)

@Composable
fun OnboardingWelcomeScreen(onStart: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 26.dp)
            .padding(top = 18.dp, bottom = 30.dp),
    ) {
        BrandCorner(
            size = 52.dp,
            stroke = 14.dp,
            modifier = Modifier.padding(top = 4.dp, bottom = 20.dp),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(16.dp, RoundedCornerShape(28.dp))
                .clip(RoundedCornerShape(28.dp)),
        ) {
            Image(
                painter = painterResource(R.drawable.clumo_hero),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(252.dp),
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = stringResource(R.string.ob1_title),
            fontSize = 27.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = RoundedFontFamily,
            color = ClumoColors.Text,
            lineHeight = 38.sp,
        )
        Text(
            text = stringResource(R.string.ob1_subtitle),
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = RoundedFontFamily,
            color = ClumoColors.Muted,
            lineHeight = 27.sp,
            modifier = Modifier.padding(top = 10.dp),
        )
        Spacer(modifier = Modifier.weight(1f))
        CtaPillButton(
            text = stringResource(R.string.ob1_cta),
            onClick = onStart,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
fun OnboardingBluetoothScreen(
    preferences: AppPreferences,
    onDone: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var permissionDenied by remember { mutableStateOf(false) }

    fun finish() {
        scope.launch {
            preferences.setOnboardingDone()
            onDone()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        if (results.values.all { it }) finish() else permissionDenied = true
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(horizontal = 26.dp)
            .padding(top = 18.dp, bottom = 30.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BrandCorner(
            size = 52.dp,
            stroke = 14.dp,
            modifier = Modifier
                .padding(top = 4.dp)
                .align(Alignment.Start),
        )
        Spacer(modifier = Modifier.weight(1f))

        // Device face inside two soft signal rings.
        val accents = LocalClumoAccents.current
        Box(
            modifier = Modifier.size(250.dp),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    color = accents.accent.copy(alpha = 0.25f),
                    radius = size.minDimension / 2f - 1.dp.toPx(),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()),
                )
                drawCircle(
                    color = accents.accent.copy(alpha = 0.45f),
                    radius = size.minDimension * 190f / 250f / 2f,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.dp.toPx()),
                )
            }
            DeviceFace(
                bits = BT_FACE_BITS,
                frameColor = accents.accent,
                size = 118.dp,
                frameCorner = 28.dp,
                framePadding = 15.dp,
                innerCorner = 14.dp,
                gridPadding = 9.dp,
                glow = false,
                shadowElevation = 10.dp,
            )
        }

        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = stringResource(R.string.ob2_title),
            fontSize = 21.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = RoundedFontFamily,
            color = ClumoColors.Text,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.ob2_body).replace("\\n", "\n"),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = RoundedFontFamily,
            color = ClumoColors.Muted,
            textAlign = TextAlign.Center,
            lineHeight = 27.sp,
            modifier = Modifier.padding(top = 12.dp),
        )
        if (permissionDenied) {
            Text(
                text = stringResource(R.string.ob2_permission_denied),
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = RoundedFontFamily,
                color = ClumoColors.Coral,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        CtaPillButton(
            text = stringResource(R.string.ob2_allow),
            onClick = { permissionLauncher.launch(bluetoothPermissions()) },
            modifier = Modifier.fillMaxWidth(),
        )
        Box(
            modifier = Modifier
                .padding(top = 4.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(999.dp))
                .clickable { finish() }
                .padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.ob2_later),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = RoundedFontFamily,
                color = ClumoColors.Muted,
            )
        }
    }
}

/** Runtime permissions needed for scanning/connecting, per SDK level. */
fun bluetoothPermissions(): Array<String> =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
        )
    } else {
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }
