package io.github.cespresso.clumo.ui.device

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.cespresso.clumo.R
import io.github.cespresso.clumo.data.ble.StandDisplaySettings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.io.ByteArrayInputStream

private class DisplayEditorBridge(
    private val onRequest: (Int, String, String) -> Unit,
    private val onDirty: (Boolean) -> Unit,
) {
    @JavascriptInterface fun request(id: Int, path: String, payload: String) = onRequest(id, path, payload)

    @JavascriptInterface fun dirtyState(dirty: Boolean) = onDirty(dirty)
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun StandDisplayEditor(
    readSettings: suspend () -> StandDisplaySettings,
    saveSettings: suspend (StandDisplaySettings) -> StandDisplaySettings,
    onClose: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var dirty by remember { mutableStateOf(false) }
    var confirmClose by remember { mutableStateOf(false) }
    val close = { if (dirty) confirmClose = true else onClose() }
    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            io.github.cespresso.clumo.ui.components.ScreenHeader(title = stringResource(R.string.stand_editor_title), onBack = close)
            AndroidView(
                modifier = Modifier.weight(1f),
                factory = { context ->
                    WebView(context).apply {
                        webChromeClient = android.webkit.WebChromeClient()
                        settings.javaScriptEnabled = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.blockNetworkLoads = true
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest) = true
                            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest) = WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(byteArrayOf()))
                        }
                        val web = this
                        addJavascriptInterface(
                            DisplayEditorBridge(
                                onRequest = { id, path, payload ->
                                    scope.launch {
                                        try {
                                            val result = when (path) {
                                                "/api/settings" -> readSettings().encode()
                                                "/api/save" -> {
                                                    val array = JSONArray(payload)
                                                    require(array.length() == StandDisplaySettings.SIZE)
                                                    val bytes = ByteArray(array.length()) { i ->
                                                        val value = array.getInt(i)
                                                        require(value in 0..255)
                                                        value.toByte()
                                                    }
                                                    saveSettings(requireNotNull(StandDisplaySettings.decode(bytes)))
                                                    byteArrayOf()
                                                }
                                                else -> error("Unsupported editor request")
                                            }
                                            val encoded = JSONArray(result.map { it.toInt() and 255 })
                                            web.evaluateJavascript("window.clumoReply($id,true,$encoded)", null)
                                        } catch (cancelled: CancellationException) {
                                            throw cancelled
                                        } catch (_: Exception) {
                                            web.evaluateJavascript("window.clumoReply($id,false,[])", null)
                                        }
                                    }
                                },
                                onDirty = { value -> scope.launch { dirty = value } },
                            ),
                            "ClumoNative",
                        )
                        loadDataWithBaseURL("https://clumo-editor.local/", context.assets.open("editor.html").bufferedReader().use { it.readText() }, "text/html", "UTF-8", null)
                    }
                },
                onRelease = {
                    it.removeJavascriptInterface("ClumoNative")
                    it.destroy()
                },
            )
        }
    }
    if (confirmClose) {
        AlertDialog(
            onDismissRequest = { confirmClose = false },
            title = { Text(stringResource(R.string.stand_editor_discard)) },
            confirmButton = { TextButton(onClick = onClose) { Text(stringResource(R.string.stand_editor_leave)) } },
            dismissButton = { TextButton(onClick = { confirmClose = false }) { Text(stringResource(R.string.dialog_cancel)) } },
        )
    }
}
