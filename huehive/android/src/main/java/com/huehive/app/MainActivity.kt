package com.huehive.app

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.WindowInsets
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout

/**
 * Hue Hive for Android: hosts the shared web client (bundled in assets) in a WebView.
 * Offline games run entirely on the device; online games talk to a Hue Hive server.
 */
class MainActivity : Activity() {

    private lateinit var web: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = FrameLayout(this)
        root.setBackgroundColor(BG)
        web = WebView(this)
        web.setBackgroundColor(BG)
        root.addView(web, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))

        // Android 15 draws apps edge to edge; keep the page clear of the system bars and keyboard.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            root.setOnApplyWindowInsetsListener { v, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout() or WindowInsets.Type.ime())
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                WindowInsets.CONSUMED
            }
        }

        with(web.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            mediaPlaybackRequiresUserGesture = false
            textZoom = 100
        }
        web.webChromeClient = WebChromeClient()
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url
                if (url.scheme == "file") return false
                // Links to the outside world open in the browser.
                return try {
                    startActivity(Intent(Intent.ACTION_VIEW, url)); true
                } catch (e: Exception) {
                    true
                }
            }
        }
        web.addJavascriptInterface(Bridge(), "HueHiveAndroid")
        setContentView(root)

        if (savedInstanceState == null || web.restoreState(savedInstanceState) == null) {
            web.loadUrl(startUrl(intent))
        }
    }

    /** Invite links (https://server/?room=ABCDE) open straight into the join screen. */
    private fun startUrl(intent: Intent?): String {
        val room = intent?.data?.getQueryParameter("room")
        return if (room.isNullOrBlank()) ASSET_URL else ASSET_URL + "?room=" + Uri.encode(room)
    }

    inner class Bridge {
        @JavascriptInterface
        fun share(text: String) {
            runOnUiThread {
                val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
                startActivity(Intent.createChooser(send, "Invite friends"))
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // Let the page handle Back first (close a dialog, leave a screen); quit from the home screen.
        web.evaluateJavascript("(window.HueHive && window.HueHive.onBack()) ? 'y' : 'n'") { result ->
            if (result != "\"y\"") finish()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        web.saveState(outState)
    }

    override fun onPause() {
        web.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        web.onResume()
    }

    override fun onDestroy() {
        web.destroy()
        super.onDestroy()
    }

    companion object {
        private const val ASSET_URL = "file:///android_asset/index.html"
        private const val BG = 0xFF14161D.toInt()
    }
}
