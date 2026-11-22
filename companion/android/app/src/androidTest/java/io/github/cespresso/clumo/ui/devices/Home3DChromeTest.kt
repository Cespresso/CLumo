package io.github.cespresso.clumo.ui.devices

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import io.github.cespresso.clumo.ui.theme.ClumoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class Home3DChromeTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun normalHomeShowsBrandWithoutCountAndHintCanBeDismissed() {
        val hint = mutableStateOf(true)
        compose.setContent {
            ClumoTheme { Home3DChrome(hint.value, { hint.value = false }, null, deviceCount = 3, selectedIndex = 0, onResolveIssue = {}) }
        }
        compose.onNodeWithText("clumo").assertExists()
        compose.onNodeWithText("01 / 03").assertDoesNotExist()
        compose.onNodeWithTag("home-status").assertDoesNotExist()
        compose.onNodeWithTag("home-gesture-hint").assertExists()
        compose.onNodeWithTag("dismiss-home-hint").performClick()
        compose.onNodeWithTag("home-gesture-hint").assertDoesNotExist()
    }

    @Test
    fun actionableStatusDisappearsWhenBluetoothRecovers() {
        val issue = mutableStateOf<HomeBluetoothIssue?>(HomeBluetoothIssue.Off)
        var actions = 0
        compose.setContent {
            ClumoTheme { Home3DChrome(false, {}, issue.value, onResolveIssue = { actions++ }) }
        }
        compose.onNodeWithText("Bluetoothがオフです").assertExists()
        compose.onNodeWithText("Bluetoothを設定").performClick()
        compose.runOnIdle {
            assertEquals(1, actions)
            issue.value = null
        }
        compose.onNodeWithTag("home-status").assertDoesNotExist()
        compose.runOnIdle { issue.value = HomeBluetoothIssue.Permission }
        compose.onNodeWithText("アクセス許可を設定").assertExists()
    }
}
