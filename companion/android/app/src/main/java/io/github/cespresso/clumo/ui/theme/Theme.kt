package io.github.cespresso.clumo.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import io.github.cespresso.clumo.R

/** Design tokens from the CLumo visual spec. */
object ClumoColors {
    val Background = Color(0xFFFAF9F6)
    val Panel = Color(0xFFF4F2EE)
    val Text = Color(0xFF3E3A36)
    val Muted = Color(0xFF8C867E)
    val MutedLight = Color(0xFFA8A298)
    val Caption = Color(0xFFB4AFA5)
    val Sage = Color(0xFF7E9E7C)
    val Gray = Color(0xFFC7CCCA)
    val Coral = Color(0xFFE8907E)
    val LitDot = Color(0xFFF0A35E)
    val OffDot = Color(0xFFEAE6DE)
    val White = Color(0xFFFFFFFF)

    val CardBorder = Color(0xFFEDE9E1)
    val OutlineBorder = Color(0xFFDCD8D0)
    val ChipBorder = Color(0xFFE5E1D8)
    val DashedBorder = Color(0xFFD8D4CA)
    val Chevron = Color(0xFFC9C4BB)
    val SegBackground = Color(0xFFEFECE4)
    val SegInactiveText = Color(0xFF9A948A)
    val ErrorBg = Color(0xFFFBEBE6)
    val ErrorBorder = Color(0xFFF2CCC1)
    val ErrorText = Color(0xFFC06A57)
    val CoralChipBg = Color(0xFFFBEBE6)
    val CoralChipFg = Color(0xFFC06A57)
    val BreakChipBg = Color(0xFFDDE0DE)
    val BreakChipFg = Color(0xFF5B615E)
    val EditorOffCell = Color(0xFFEFEBE3)
    val Divider = Color(0xFFF1EDE5)
    val SwitchOff = Color(0xFFD8D4CC)
    val EmptyFaceBorder = Color(0xFFDDD9D0)
}

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
)

@Composable
fun ClumoTheme(content: @Composable () -> Unit) {
    // The design is a single warm light look regardless of the system theme.
    MaterialTheme(
        colorScheme = clumoColorScheme,
        typography = clumoTypography,
        content = content,
    )
}
