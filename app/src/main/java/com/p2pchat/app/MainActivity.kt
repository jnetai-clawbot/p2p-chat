package com.p2pchat.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import android.view.KeyEvent
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.webkit.WebViewAssetLoader

class MainActivity : AppCompatActivity() {
    companion object {
        private const val QR_SCAN_REQUEST = 1001
    }

    private lateinit var webView: WebView
    private lateinit var bridge: WebViewBridge
    private lateinit var assetLoader: WebViewAssetLoader
    private var exitConfirmed = false
    private var filePickCallback: ((Uri?) -> Unit)? = null

    private val qrScanLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val scannedData = result.data?.getStringExtra(QrScannerActivity.EXTRA_RESULT) ?: ""
                bridge.onQrScanResult(scannedData)
            } else {
                val error = result.data?.getStringExtra(QrScannerActivity.EXTRA_ERROR)
                    ?: "QR scan cancelled or failed"
                bridge.onQrScanError(error)
            }
        }

    private val filePickerLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            filePickCallback?.invoke(uri)
            filePickCallback = null
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ErrorLogger.init(this)
        FileHandler.init(this)
        ErrorLogger.i("MainActivity", "onCreate started", mapOf(
            "versionCode" to "8",
            "versionName" to "1.0.8"
        ))

        webView = WebView(this).apply {
            id = android.R.id.content
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        setContentView(webView)

        configureWebView()
        bridge = WebViewBridge(this, webView)
        bridge.inject()

        webView.loadUrl("https://appassets.androidplatform.net/assets/web/index.html")

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                showExitConfirmation()
            }
        })
    }

    private fun configureWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = true
            databaseEnabled = true
            setGeolocationEnabled(false)
            cacheMode = WebSettings.LOAD_DEFAULT
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            userAgentString = "${userAgentString} P2PChatApp/1.0"
            mediaPlaybackRequiresUserGesture = false
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(false)
            displayZoomControls = false
        }

        WebView.setWebContentsDebuggingEnabled(false)

        assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? {
                return try {
                    assetLoader.shouldInterceptRequest(request.url)
                } catch (e: Exception) {
                    ErrorLogger.e("MainActivity", "MA002", "Asset loader error: ${request.url}", e)
                    super.shouldInterceptRequest(view, request)
                }
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                ErrorLogger.e("MainActivity", "MA003",
                    "WebView error: code=${error.errorCode} desc=${error.description} url=${request.url}")
            }

            override fun onReceivedHttpError(
                view: WebView,
                request: WebResourceRequest,
                errorResponse: WebResourceResponse
            ) {
                ErrorLogger.w("MainActivity", "MA004",
                    "HTTP error: ${errorResponse.statusCode} for ${request.url}")
            }

            override fun onPageFinished(view: WebView, url: String) {
                super.onPageFinished(view, url)
                ErrorLogger.i("MainActivity", "Page loaded", mapOf("url" to url))
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                ErrorLogger.d("WebView", "[${consoleMessage.messageLevel()}] ${consoleMessage.message()}")
                return true
            }

            override fun onShowFileChooser(
                webView: WebView,
                filePathCallback: ValueCallback<Array<Uri>>,
                fileChooserParams: FileChooserParams
            ): Boolean {
                openFilePickerForWeb()
                return true
            }
        }
    }

    private fun showExitConfirmation() {
        AlertDialog.Builder(this)
            .setTitle(R.string.exit_confirm_title)
            .setMessage(R.string.exit_confirm_message)
            .setPositiveButton(R.string.exit_confirm_yes) { _, _ -> finishAndRemoveTask() }
            .setNegativeButton(R.string.exit_confirm_no, null)
            .setCancelable(true)
            .show()
    }

    fun startQrScanner() {
        try {
            val intent = Intent(this, QrScannerActivity::class.java)
            qrScanLauncher.launch(intent)
        } catch (e: Exception) {
            ErrorLogger.e("MainActivity", "MA005", "Failed to start QR scanner", e)
            bridge.onQrScanError("MA005: ${e.message}")
        }
    }

    fun openFilePicker() {
        try {
            filePickerLauncher.launch("*/*")
            filePickCallback = { uri ->
                bridge.onFilePicked(uri)
            }
        } catch (e: Exception) {
            ErrorLogger.e("MainActivity", "MA006", "Failed to open file picker", e)
        }
    }

    fun setKeepScreenOn(enabled: Boolean) {
        runOnUiThread {
            if (enabled) {
                window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            ErrorLogger.i("MainActivity", "Keep screen on: $enabled")
        }
    }

    private fun openFilePickerForWeb() {
        try {
            filePickerLauncher.launch("*/*")
            filePickCallback = { uri ->
                bridge.onFilePicked(uri)
            }
        } catch (e: Exception) {
            ErrorLogger.e("MainActivity", "MA007", "Failed to open web file picker", e)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        ErrorLogger.i("MainActivity", "onDestroy", mapOf(
            "totalErrors" to ErrorLogger.getErrorCount().toString()
        ))
    }
}
