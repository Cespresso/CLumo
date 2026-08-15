package io.github.cespresso.clumo.ui.theme

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import io.github.cespresso.clumo.domain.DeviceAppearance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import io.github.cespresso.clumo.R
import io.github.cespresso.clumo.design.ClumoColors

/** M PLUS Rounded 1c, bundled in res/font. */
val RoundedFontFamily = FontFamily(
    Font(R.font.mplus_rounded1c_regular, FontWeight.Normal),
    Font(R.font.mplus_rounded1c_medium, FontWeight.Medium),
    Font(R.font.mplus_rounded1c_bold, FontWeight.Bold),
    Font(R.font.mplus_rounded1c_extrabold, FontWeight.ExtraBold),
)

private val baseTypography = Typography()

private fun TextStyle.rounded() = copy(fontFamily = RoundedFontFamily)

private val clumoTypography = Typography(
    displayLarge = baseTypography.displayLarge.rounded(),
    displayMedium = baseTypography.displayMedium.rounded(),
    displaySmall = baseTypography.displaySmall.rounded(),
    headlineLarge = baseTypography.headlineLarge.rounded(),
    headlineMedium = baseTypography.headlineMedium.rounded(),
    headlineSmall = baseTypography.headlineSmall.rounded(),
    titleLarge = baseTypography.titleLarge.rounded(),
    titleMedium = baseTypography.titleMedium.rounded(),
    titleSmall = baseTypography.titleSmall.rounded(),
    bodyLarge = baseTypography.bodyLarge.rounded(),
    bodyMedium = baseTypography.bodyMedium.rounded(),
    bodySmall = baseTypography.bodySmall.rounded(),
    labelLarge = baseTypography.labelLarge.rounded(),
    labelMedium = baseTypography.labelMedium.rounded(),
    labelSmall = baseTypography.labelSmall.rounded(),
)

private val clumoColorScheme = lightColorScheme(
    primary = ClumoColors.Sage,
    onPrimary = ClumoColors.White,
    secondary = ClumoColors.Coral,
    onSecondary = ClumoColors.White,
    background = ClumoColors.Background,
    onBackground = ClumoColors.Text,
    surface = ClumoColors.White,
    onSurface = ClumoColors.Text,
    surfaceVariant = ClumoColors.Panel,
    onSurfaceVariant = ClumoColors.Muted,
    outline = ClumoColors.OutlineBorder,
    error = ClumoColors.ErrorText,
    onError = ClumoColors.White,
    errorContainer = ClumoColors.ErrorBg,
    onErrorContainer = ClumoColors.ErrorText,
)

@Composable
private fun animatedAccents(target: ClumoAccents): ClumoAccents {
    val spec = tween<Color>(durationMillis = 450)
    return ClumoAccents(
        accent = animateColorAsState(target.accent, spec, label = "accent").value,
        onAccent = animateColorAsState(target.onAccent, spec, label = "onAccent").value,
        cta = animateColorAsState(target.cta, spec, label = "cta").value,
        onCta = animateColorAsState(target.onCta, spec, label = "onCta").value,
        knob = animateColorAsState(target.knob, spec, label = "knob").value,
        led = animateColorAsState(target.led, spec, label = "led").value,
    )
}

@Composable
fun ClumoTheme(
    appearance: DeviceAppearance = DeviceAppearance.DEFAULT,
    content: @Composable () -> Unit,
) {
    // The design is a single warm light look regardless of the system theme;
    // only the accent roles follow the primary device's appearance.
    val accents = animatedAccents(accentSpecFor(appearance).toClumoAccents())
    CompositionLocalProvider(LocalClumoAccents provides accents) {
        MaterialTheme(
            colorScheme = clumoColorScheme.copy(
                primary = accents.accent,
                onPrimary = accents.onAccent,
                secondary = accents.cta,
                onSecondary = accents.onCta,
            ),
            typography = clumoTypography,
            content = content,
        )
    }
}
