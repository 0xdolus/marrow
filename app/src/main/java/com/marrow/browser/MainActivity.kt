package com.marrow.browser

import android.content.Context
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

    // JS banner
    private lateinit var jsBanner: View
    private lateinit var jsBannerDomain: TextView
    private lateinit var jsAllowAlwaysBtn: Button
    private lateinit var jsAllowOnceBtn: Button
    private lateinit var jsKeepOffBtn: Button

    private var isFullMode = false
    private var pendingJsDomain: String? = null

    companion object {
        const val HOME_URL        = "https://text.npr.org"
        const val PREFS_NAME      = "marrow_prefs"
        const val PREFS_JS_ALWAYS = "js_allowed_always"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView          = findViewById(R.id.webView)
        urlText          = findViewById(R.id.urlText)
        threadBadge      = findViewById(R.id.threadBadge)
        fullPageBtn      = findViewById(R.id.fullPageBtn)
        jsBanner         = findViewById(R.id.jsBanner)
        jsBannerDomain   = findViewById(R.id.jsBannerDomain)
        jsAllowAlwaysBtn = findViewById(R.id.jsAllowAlwaysBtn)
        jsAllowOnceBtn   = findViewById(R.id.jsAllowOnceBtn)
        jsKeepOffBtn     = findViewById(R.id.jsKeepOffBtn)

        threadModeClient = ThreadModeClient(urlText) { domain ->
            showJsBanner(domain)
        }

        // Load persisted always-allowed domains
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getStringSet(PREFS_JS_ALWAYS, emptySet()) ?: emptySet()
        threadModeClient.loadAllowedAlways(saved)

        // JS enabled so WebView requests JS files — ThreadModeClient blocks them unless allowed
        webView.settings.javaScriptEnabled = true
        webView.webViewClient = threadModeClient

        fullPageBtn.setOnClickListener {
            isFullMode = !isFullMode
            threadModeClient.isFullMode = isFullMode
            hideJsBanner()
            if (isFullMode) {
                fullPageBtn.text = getString(R.string.thread_mode_short)
                threadBadge.visibility = View.GONE
            } else {
                fullPageBtn.text = getString(R.string.full_page)
                threadBadge.visibility = View.VISIBLE
            }
            webView.reload()
        }

        jsAllowAlwaysBtn.setOnClickListener {
            pendingJsDomain?.let { domain ->
                threadModeClient.allowAlways(domain)
                // Persist to SharedPreferences
                val p = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                val current = p.getStringSet(PREFS_JS_ALWAYS, mutableSetOf())
                    ?.toMutableSet() ?: mutableSetOf()
                current.add(domain)
                p.edit().putStringSet(PREFS_JS_ALWAYS, current).apply()
                hideJsBanner()
                webView.reload()
            }
        }

        jsAllowOnceBtn.setOnClickListener {
            pendingJsDomain?.let { domain ->
                threadModeClient.allowOnce(domain)
                hideJsBanner()
                webView.reload()
            }
        }

        jsKeepOffBtn.setOnClickListener {
            pendingJsDomain?.let { domain ->
                threadModeClient.denyJs(domain)
                hideJsBanner()
            }
        }

        webView.loadUrl(HOME_URL)
    }

    private fun showJsBanner(domain: String) {
        pendingJsDomain    = domain
        jsBannerDomain.text = domain
        jsBanner.visibility = View.VISIBLE
    }

    private fun hideJsBanner() {
        pendingJsDomain     = null
        jsBanner.visibility = View.GONE
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
