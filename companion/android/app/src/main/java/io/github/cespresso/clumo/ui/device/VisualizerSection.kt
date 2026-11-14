package io.github.cespresso.clumo.ui.device

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import io.github.cespresso.clumo.R
import io.github.cespresso.clumo.design.ClumoColors
import io.github.cespresso.clumo.ui.components.ClumoSlider
import io.github.cespresso.clumo.ui.components.ClumoToggleSwitch
import io.github.cespresso.clumo.ui.components.CtaPillButton
import io.github.cespresso.clumo.ui.components.OutlinePillButton
import io.github.cespresso.clumo.ui.theme.RoundedFontFamily
import kotlinx.coroutines.awaitCancellation

// onVisualizerSensitivityChangeFinished carries the name of the Material3 Slider
// callback it is handed to, so the correspondence survives a reader jumping between them.
@Suppress("ktlint:compose:parameter-naming")
@Composable
internal fun VisualizerSection(
    visualizerActive: Boolean,
    visualizerSensitivity: Float,
    automaticLowVolumeBoost: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onVisualizerSensitivityChange: (Float) -> Unit,
    onVisualizerSensitivityChangeFinished: (Float) -> Unit,
    onAutomaticLowVolumeBoostChange: (Boolean) -> Unit,
    hardwareMediaControls: Boolean = false,
    ready: Boolean = true,
) {
    val context = LocalContext.current
    var autoStartAllowed by rememberSaveable { mutableStateOf(true) }
    var sensitivity by remember(visualizerSensitivity) {
        mutableFloatStateOf(visualizerSensitivity * 100f)
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { results ->
        if (results[Manifest.permission.RECORD_AUDIO] == true) {
            onStart()
        }
    }

    fun toggle() {
        if (visualizerActive) {
            autoStartAllowed = false
            onStop()
            return
        }
        autoStartAllowed = true
        val needed = buildList {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED
            ) {
                add(Manifest.permission.RECORD_AUDIO)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (needed.isNotEmpty()) {
            audioPermissionLauncher.launch(needed.toTypedArray())
        } else {
            onStart()
        }
    }

    val currentOnStart by rememberUpdatedState(onStart)
    val currentActive by rememberUpdatedState(visualizerActive)
    val currentAutoStartAllowed by rememberUpdatedState(autoStartAllowed)
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(hardwareMediaControls, ready, lifecycle) {
        if (hardwareMediaControls && ready) {
            lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                if (currentAutoStartAllowed && !currentActive && ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    currentOnStart()
                }
                awaitCancellation()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(6.dp, RoundedCornerShape(28.dp))
            .clip(RoundedCornerShape(28.dp))
            .background(ClumoColors.White)
            .border(1.5.dp, ClumoColors.CardBorder, RoundedCornerShape(28.dp))
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        if (hardwareMediaControls) {
            StandHelp(title = stringResource(R.string.stand_visualizer_title), help = stringResource(R.string.stand_visualizer_background))
        }
        if (visualizerActive) {
            OutlinePillButton(
                text = stringResource(R.string.viz_stop),
                onClick = { toggle() },
                fontSize = 16.sp,
                verticalPadding = 17.dp,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            CtaPillButton(
                text = stringResource(R.string.viz_start),
                onClick = { toggle() },
                fontSize = 16.sp,
                verticalPadding = 17.dp,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.viz_sensitivity),
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = RoundedFontFamily,
                color = ClumoColors.Muted,
            )
            ClumoSlider(
                value = sensitivity,
                onValueChange = {
                    sensitivity = it
                    onVisualizerSensitivityChange(it / 100f)
                },
                onValueChangeFinished = {
                    onVisualizerSensitivityChangeFinished(sensitivity / 100f)
                },
                modifier = Modifier.weight(1f),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.viz_auto_low_volume_boost),
                fontSize = 13.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = RoundedFontFamily,
                color = ClumoColors.Muted,
                modifier = Modifier.weight(1f),
            )
            ClumoToggleSwitch(
                checked = automaticLowVolumeBoost,
                onCheckedChange = onAutomaticLowVolumeBoostChange,
            )
        }

        if (!hardwareMediaControls) {
            Text(
                text = stringResource(R.string.viz_caption),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                fontFamily = RoundedFontFamily,
                color = ClumoColors.Caption,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
