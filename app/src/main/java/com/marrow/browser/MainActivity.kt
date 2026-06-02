package com.marrow.browser

import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var urlText: TextView

    companion object {
        // Default home page — text-only NPR, good test for Thread mode later
        const val HOME_URL = "https://text.npr.org"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        urlText = findViewById(R.id.urlText)

        // JS off by default — Stage 2 will add proper Thread mode client
        webView.settings.javaScriptEnabled = false

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView, url: String) {
                // Update URL bar when navigation completes
                urlText.text = url
            }
        }

        webView.loadUrl(HOME_URL)
    }

    // Handle back button — navigate WebView history before closing app
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}
