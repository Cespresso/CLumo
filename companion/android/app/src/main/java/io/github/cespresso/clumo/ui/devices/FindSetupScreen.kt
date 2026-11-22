package io.github.cespresso.clumo.ui.devices

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.cespresso.clumo.R
import io.github.cespresso.clumo.domain.ConnectionState
import io.github.cespresso.clumo.domain.DeviceAppearance
import io.github.cespresso.clumo.domain.DeviceKind
import io.github.cespresso.clumo.domain.FaceBits
import io.github.cespresso.clumo.ui.components.ScreenHeader
import io.github.cespresso.clumo.ui.components.product.GalleryDevice
import io.github.cespresso.clumo.ui.components.product.ProductDisplayState
import io.github.cespresso.clumo.ui.theme.LocalClumoAccents
import kotlinx.coroutines.delay

enum class SetupPage { Welcome, Products, Classic, Stand }

private data class PairingGuide(
    val page: SetupPage,
    val name: String,
    val description: String,
    val image: Int,
    val instruction: String,
    val detail: String,
    val action: String,
)
private val guides = listOf(
    PairingGuide(
        SetupPage.Classic,
        "CLumo",
        "8×8 LED matrix",
        R.drawable.pairing_cover_clumo,
        "Power on your CLumo",
        "Use the companion firmware. If Android asks for a pairing code, enter 123456.",
        "Find my CLumo",
    ),
    PairingGuide(
        SetupPage.Stand,
        "CLumo Headphone Stand",
        "Stand with rotary knob",
        R.drawable.pairing_cover_stand,
        "Hold the knob for 3 seconds",
        "On your stand, hold until the Bluetooth symbol blinks.",
        "It’s blinking · Find device",
    ),
)

private val pairingSymbol = FaceBits.fromRowBytes(byteArrayOf(0x18, 0x14, 0x52, 0x34, 0x34, 0x52, 0x14, 0x18))

/** Guide models are illustrations with no transport address. */
@Composable
internal fun setupModel(page: SetupPage, standAppearance: DeviceAppearance): GalleryDevice? {
    val demoStart = remember(page) { android.os.SystemClock.uptimeMillis() }
    val bits by produceState(0L, page) {
        value = 0L
        if (page == SetupPage.Stand) {
            while (true) {
                delay(3000)
                repeat(3) {
                    value = pairingSymbol
                    delay(400)
                    value = 0L
                    delay(400)
                }
            }
        }
    }
    return when (page) {
        SetupPage.Products -> null
        SetupPage.Welcome -> GalleryDevice("setup-tag", "", DeviceKind.CLUMO, "", unidentified = true)
        SetupPage.Classic -> GalleryDevice("setup-classic", "", DeviceKind.CLUMO, "", display = ProductDisplayState())
        SetupPage.Stand -> GalleryDevice(
            "setup-stand",
            "",
            DeviceKind.HEADPHONE_STAND,
            "",
            pairingDemoStartMs = demoStart,
            display = ProductDisplayState(
                standAppearance,
                ConnectionState.Ready,
                bits = bits,
            ),
        )
    }
}

@Composable
internal fun FindSetupScreen(
    page: SetupPage,
    onPage: (SetupPage) -> Unit,
    onSearch: () -> Unit,
    onBack: () -> Unit,
) {
    val guide = guides.firstOrNull { it.page == page }
    val accents = LocalClumoAccents.current
    val base = Modifier.fillMaxSize()
    BoxWithConstraints(if (page == SetupPage.Products) base.background(Color(0xFFE3DACB)) else base) {
        val wide = maxWidth > maxHeight
        ScreenHeader(guide?.name?.plus(" guide") ?: if (page == SetupPage.Products) "Pairing guide" else "Find devices", onBack)
        if (page == SetupPage.Products) {
            Column(
                Modifier.safeDrawingPadding().padding(start = 20.dp, end = 20.dp, top = 68.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Which CLumo do you have?", style = MaterialTheme.typography.headlineMedium)
                guides.forEach { item ->
                    Card(onClick = { onPage(item.page) }, modifier = Modifier.fillMaxWidth()) {
                        Row(
                            Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            Image(painterResource(item.image), null, Modifier.width(112.dp).aspectRatio(4f / 3f))
                            Column(Modifier.weight(1f)) {
                                Text(item.name, style = MaterialTheme.typography.titleMedium)
                                Text(item.description, style = MaterialTheme.typography.bodyMedium)
                            }
                            Text("›")
                        }
                    }
                }
            }
        } else {
            Column(
                Modifier.align(if (wide) Alignment.CenterEnd else Alignment.BottomCenter)
                    .fillMaxWidth(if (wide) .48f else 1f).safeDrawingPadding()
                    .verticalScroll(rememberScrollState()).padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(guide?.instruction ?: "Set up your CLumo", textAlign = TextAlign.Center, style = MaterialTheme.typography.headlineMedium)
                if (guide != null) Text(guide.detail, textAlign = TextAlign.Center)
                Button(
                    onClick = onSearch,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = accents.cta, contentColor = accents.onCta),
                ) {
                    Text(guide?.action ?: "Find my CLumo")
                }
                if (page == SetupPage.Welcome) TextButton(onClick = { onPage(SetupPage.Products) }) { Text("Show me how to pair") }
            }
        }
    }
}
