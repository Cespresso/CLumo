package io.github.cespresso.clumo.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.cespresso.clumo.BuildConfig
import io.github.cespresso.clumo.R
import io.github.cespresso.clumo.design.ClumoColors
import io.github.cespresso.clumo.ui.theme.LocalClumoAccents
import io.github.cespresso.clumo.ui.theme.RoundedFontFamily

private const val GITHUB_URL = "https://github.com/Cespresso/CLumo"

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenLicenses: () -> Unit,
) {
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(title = stringResource(R.string.settings_title), onBack = onBack)

        Column(
            modifier = Modifier
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                    ),
                )
                .padding(horizontal = 22.dp, vertical = 12.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(ClumoColors.White)
                    .border(1.5.dp, ClumoColors.CardBorder, RoundedCornerShape(24.dp)),
            ) {
                SettingsRow(
                    label = stringResource(R.string.settings_version),
                    trailing = BuildConfig.VERSION_NAME,
                )
                SettingsDivider()
                SettingsRow(
                    label = stringResource(R.string.settings_licenses),
                    chevron = true,
                    onClick = onOpenLicenses,
                )
                SettingsDivider()
                SettingsRow(
                    label = stringResource(R.string.settings_github),
                    chevron = true,
                    onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_URL)))
                    },
                )
            }
        }
    }
}

@Composable
fun ScreenHeader(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(
                    WindowInsetsSides.Top + WindowInsetsSides.Horizontal,
                ),
            )
            .padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "‹",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = RoundedFontFamily,
            color = ClumoColors.Muted,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onBack)
                .padding(horizontal = 10.dp, vertical = 6.dp),
        )
        Text(
            text = title,
            fontSize = 17.sp,
            fontWeight = FontWeight.ExtraBold,
            fontFamily = RoundedFontFamily,
            color = ClumoColors.Text,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SettingsRow(
    label: String,
    trailing: String? = null,
    chevron: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 20.dp, vertical = 17.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = label,
            fontSize = 14.5.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = RoundedFontFamily,
            color = ClumoColors.Text,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) {
            Text(
                text = trailing,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = RoundedFontFamily,
                color = ClumoColors.Muted,
            )
        }
        if (chevron) {
            Text(
                text = "›",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = RoundedFontFamily,
                color = ClumoColors.Chevron,
            )
        }
    }
}

@Composable
private fun SettingsDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .height(1.5.dp)
            .background(ClumoColors.Divider),
    )
}

// ---------------------------------------------------------------------------
// Open source licenses (static, hand-maintained against gradle dependencies)
// ---------------------------------------------------------------------------

private data class LicenseEntry(
    val name: String,
    val copyright: String,
    val license: String,
)

private val LICENSE_ENTRIES = listOf(
    LicenseEntry(
        name = "AndroidX Jetpack Compose (ui, material3, activity-compose, ui-tooling)",
        copyright = "Copyright The Android Open Source Project",
        license = "Apache License 2.0",
    ),
    LicenseEntry(
        name = "AndroidX Core KTX",
        copyright = "Copyright The Android Open Source Project",
        license = "Apache License 2.0",
    ),
    LicenseEntry(
        name = "AndroidX Lifecycle (ViewModel Compose / Runtime KTX)",
        copyright = "Copyright The Android Open Source Project",
        license = "Apache License 2.0",
    ),
    LicenseEntry(
        name = "AndroidX DataStore Preferences",
        copyright = "Copyright The Android Open Source Project",
        license = "Apache License 2.0",
    ),
    LicenseEntry(
        name = "Kotlin Standard Library",
        copyright = "Copyright 2010-2024 JetBrains s.r.o.",
        license = "Apache License 2.0",
    ),
    LicenseEntry(
        name = "kotlinx.coroutines",
        copyright = "Copyright 2016-2024 JetBrains s.r.o.",
        license = "Apache License 2.0",
    ),
    LicenseEntry(
        name = "M PLUS Rounded 1c (bundled font)",
        copyright = "Copyright 2021 The M+ FONTS Project Authors",
        license = "SIL Open Font License 1.1",
    ),
)

@Composable
fun LicensesScreen(onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        ScreenHeader(title = stringResource(R.string.licenses_title), onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                    ),
                )
                .padding(horizontal = 22.dp, vertical = 12.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(ClumoColors.White)
                    .border(1.5.dp, ClumoColors.CardBorder, RoundedCornerShape(24.dp)),
            ) {
                LICENSE_ENTRIES.forEachIndexed { index, entry ->
                    if (index > 0) SettingsDivider()
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 15.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = entry.name,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = RoundedFontFamily,
                            color = ClumoColors.Text,
                        )
                        Text(
                            text = entry.copyright,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = RoundedFontFamily,
                            color = ClumoColors.Muted,
                        )
                        Text(
                            text = entry.license,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = RoundedFontFamily,
                            color = LocalClumoAccents.current.accent,
                        )
                    }
                }
            }
        }
    }
}
