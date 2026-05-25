package com.p2pchat.app

import android.content.Intent
import android.net.Uri
import android.webkit.JavascriptInterface
import android.webkit.WebView
import org.json.JSONObject

class WebViewBridge(
    private val activity: MainActivity,
    private val webView: WebView
) {
    companion object {
        private const val BRIDGE_NAME = "AndroidBridge"
    }

    fun inject() {
        webView.addJavascriptInterface(this, BRIDGE_NAME)
        ErrorLogger.i("WebViewBridge", "JavaScript bridge injected")
    }

    @JavascriptInterface
    fun onError(code: String, message: String) {
        ErrorLogger.e("WebViewBridge", code, "WebView JS error: $message")
        webView.post {
            webView.evaluateJavascript("window.handleBridgeError('$code','${escapeJs(message)}')", null)
        }
    }

    @JavascriptInterface
    fun getDeviceId(): String {
        return try {
            val id = activity.packageName.hashCode().toUInt().toString().takeLast(8)
            ErrorLogger.d("WebViewBridge", "Device ID requested: $id")
            id
        } catch (e: Exception) {
            ErrorLogger.e("WebViewBridge", "WB001", "Failed to generate device ID", e)
            "00000000"
        }
    }

    @JavascriptInterface
    fun copyToClipboard(text: String) {
        try {
            val clipboard = activity.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                as android.content.ClipboardManager
            val clip = android.content.ClipData.newPlainText("P2P Chat ID", text)
            clipboard.setPrimaryClip(clip)
            ErrorLogger.i("WebViewBridge", "Copied to clipboard", mapOf("length" to text.length.toString()))
        } catch (e: Exception) {
            ErrorLogger.e("WebViewBridge", "WB002", "Failed to copy to clipboard", e)
        }
    }

    @JavascriptInterface
    fun scanQrCode() {
        ErrorLogger.i("WebViewBridge", "QR scan requested")
        activity.startQrScanner()
    }

    @JavascriptInterface
    fun pickFile() {
        ErrorLogger.i("WebViewBridge", "File pick requested")
        activity.openFilePicker()
    }

    @JavascriptInterface
    fun vibrate(durationMs: Int) {
        try {
            val vibrator = activity.getSystemService(android.content.Context.VIBRATOR_SERVICE)
                as android.os.Vibrator
            if (vibrator.hasVibrator()) {
                vibrator.vibrate(android.os.VibrationEffect.createOneShot(
                    durationMs.toLong(),
                    android.os.VibrationEffect.DEFAULT_AMPLITUDE
                ))
            }
        } catch (e: Exception) {
            ErrorLogger.e("WebViewBridge", "WB003", "Failed to vibrate", e)
        }
    }

    @JavascriptInterface
    fun log(message: String) {
        ErrorLogger.d("WebViewBridge", "JS log: $message")
    }

    @JavascriptInterface
    fun shareApp(url: String) {
        try {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "P2P Chat App")
                putExtra(Intent.EXTRA_TEXT, "Check out this P2P Chat & File Transfer app: $url")
            }
            activity.startActivity(Intent.createChooser(intent, "Share App via"))
            ErrorLogger.i("WebViewBridge", "App shared")
        } catch (e: Exception) {
            ErrorLogger.e("WebViewBridge", "WB005", "Failed to share app", e)
        }
    }

    @JavascriptInterface
    fun setKeepScreenOn(enabled: Boolean) {
        activity.setKeepScreenOn(enabled)
    }

    fun onQrScanResult(result: String) {
        webView.post {
            webView.evaluateJavascript("window.onQrScanResult('${escapeJs(result)}')", null)
        }
    }

    fun onQrScanError(error: String) {
        webView.post {
            webView.evaluateJavascript("window.onQrScanError('${escapeJs(error)}')", null)
        }
    }

    fun onFilePicked(uri: Uri?) {
        if (uri == null) {
            webView.post {
                webView.evaluateJavascript("window.onFilePicked(null)", null)
            }
            return
        }

        val fileInfo = FileHandler.getFileInfo(uri)
        if (fileInfo == null) {
            webView.post {
                webView.evaluateJavascript("window.onFilePickedError('FH003: Could not read file info')", null)
            }
            return
        }

        val fileData = FileHandler.readFileBytes(uri)
        if (fileData == null) {
            webView.post {
                webView.evaluateJavascript("window.onFilePickedError('FH004: Could not read file data')", null)
            }
            return
        }

        val base64 = android.util.Base64.encodeToString(fileData, android.util.Base64.NO_WRAP)
        val json = JSONObject().apply {
            put("name", fileInfo.name)
            put("size", fileInfo.size)
            put("mimeType", fileInfo.mimeType)
            put("data", base64)
        }.toString()

        webView.post {
            webView.evaluateJavascript("window.onFilePicked($json)", null)
        }
    }

    @JavascriptInterface
    fun saveReceivedFile(fileName: String, base64Data: String, subFolder: String = "") {
        try {
            val data = android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT)
            val savedFile = FileHandler.saveReceivedFile(fileName, data, subFolder)
            webView.post {
                if (savedFile != null) {
                    webView.evaluateJavascript(
                        "window.onFileSaved('${escapeJs(savedFile.absolutePath)}', ${data.size})", null
                    )
                } else {
                    webView.evaluateJavascript("window.onFileSavedError('FH006: Could not save file')", null)
                }
            }
        } catch (e: Exception) {
            ErrorLogger.e("WebViewBridge", "WB004", "Failed to save received file", e)
            webView.post {
                webView.evaluateJavascript("window.onFileSavedError('WB004: ${escapeJs(e.message ?: "unknown")}')", null)
            }
        }
    }

    private fun escapeJs(input: String): String {
        return input
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
    }
}
