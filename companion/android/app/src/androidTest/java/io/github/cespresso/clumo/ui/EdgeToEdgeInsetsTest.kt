package io.github.cespresso.clumo.ui

import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performSemanticsAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.cespresso.clumo.MainActivity
import io.github.cespresso.clumo.design.ClumoColors
import io.github.cespresso.clumo.ui.settings.LicensesScreen
import io.github.cespresso.clumo.ui.theme.ClumoTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EdgeToEdgeInsetsTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun mainContentDrawsBehindSystemBars() {
        val scenario = androidx.test.core.app.ActivityScenario.launch(MainActivity::class.java)
        try {
            scenario.onActivity { activity ->
                val composeView = activity.window.decorView.findComposeView()
                requireNotNull(composeView) { "MainActivity must host Compose content" }
                val location = IntArray(2)
                composeView.getLocationOnScreen(location)

                assertEquals(0, location[1])
                assertEquals(activity.window.decorView.height, composeView.height)
            }
        } finally {
            scenario.close()
        }
    }

    @Test
    fun licensesKeepVisualSpacingAboveNavigationBar() {
        compose.runOnUiThread { compose.activity.enableEdgeToEdge() }
        var navigationBarBottomPx = 0
        var density = 1f
        compose.setContent {
            density = LocalDensity.current.density
            navigationBarBottomPx = WindowInsets.navigationBars.getBottom(LocalDensity.current)
            ClumoTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(ClumoColors.Background),
                ) {
                    LicensesScreen(onBack = {})
                }
            }
        }

        val lastLicense = compose.onNodeWithText("SIL Open Font License 1.1")
        compose.onNode(hasScrollAction()).performSemanticsAction(SemanticsActions.ScrollBy) { scrollBy ->
            scrollBy(0f, Float.MAX_VALUE)
        }
        compose.waitForIdle()

        val lastLicenseBounds = lastLicense.fetchSemanticsNode().boundsInRoot
        val rootBounds = compose.onRoot().fetchSemanticsNode().boundsInRoot
        val visualSpacingPx = 27f * density
        val navigationBarTop = rootBounds.bottom - navigationBarBottomPx
        val expectedMaximumBottom = navigationBarTop - visualSpacingPx

        assertTrue(
            "Last license bottom=${lastLicenseBounds.bottom}, expected <= $expectedMaximumBottom " +
                "(root=${rootBounds.bottom}, nav=$navigationBarBottomPx, density=$density)",
            lastLicenseBounds.bottom <= expectedMaximumBottom,
        )
    }
}

private fun android.view.View.findComposeView(): android.view.View? {
    if (javaClass.name == "androidx.compose.ui.platform.AndroidComposeView") return this
    if (this !is android.view.ViewGroup) return null
    for (index in 0 until childCount) {
        getChildAt(index).findComposeView()?.let { return it }
    }
    return null
}
