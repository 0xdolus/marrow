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

    private var isFullMode = false

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

        threadModeClient = ThreadModeClient(urlText)
        webView.settings.javaScriptEnabled = false
        webView.webViewClient = threadModeClient

        fullPageBtn.setOnClickListener {
            isFullMode = !isFullMode
            threadModeClient.isFullMode = isFullMode
            webView.settings.javaScriptEnabled = isFullMode

            if (isFullMode) {
                fullPageBtn.text = getString(R.string.thread_mode_short)
                threadBadge.visibility = View.GONE
            } else {
                fullPageBtn.text = getString(R.string.full_page)
                threadBadge.visibility = View.VISIBLE
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
