package com.marrow.browser

import android.os.Bundle
import android.view.View
import android.webkit.WebView
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var urlText: TextView
    private lateinit var threadBadge: TextView
    private lateinit var fullPageBtn: Button
    private lateinit var threadModeClient: ThreadModeClient

    companion object {
        const val HOME_URL = "https://text.npr.org"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView     = findViewById(R.id.webView)
        urlText     = findViewById(R.id.urlText)
        threadBadge = findViewById(R.id.threadBadge)
        fullPageBtn = findViewById(R.id.fullPageBtn)

        threadModeClient = ThreadModeClient()
        webView.webViewClient = threadModeClient
        webView.settings.javaScriptEnabled = false

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                urlText.text = url
            }

            override fun shouldInterceptRequest(
                view: WebView,
                request: android.webkit.WebResourceRequest
            ): android.webkit.WebResourceResponse? {
                return threadModeClient.shouldInterceptRequest(view, request)
            }
        }

        fullPageBtn.setOnClickListener {
            if (threadModeClient.isFullMode) {
                threadModeClient.isFullMode = false
                webView.settings.javaScriptEnabled = false
                fullPageBtn.text = getString(R.string.full_page)
                threadBadge.visibility = View.VISIBLE
            } else {
                threadModeClient.isFullMode = true
                webView.settings.javaScriptEnabled = true
                fullPageBtn.text = getString(R.string.thread_mode_short)
                threadBadge.visibility = View.GONE
            }
            webView.reload()
        }

        webView.loadUrl(HOME_URL)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
