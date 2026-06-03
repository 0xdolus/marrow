package com.marrow.browser

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.webkit.WebView
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var urlText: TextView
    private lateinit var threadBadge: TextView
    private lateinit var fullPageBtn: Button
    private lateinit var threadModeClient: ThreadModeClient

    private lateinit var jsBanner: View
    private lateinit var jsBannerDomain: TextView
    private lateinit var jsAllowAlwaysBtn: Button
    private lateinit var jsAllowOnceBtn: Button
    private lateinit var jsKeepOffBtn: Button

    private lateinit var tabStripInner: LinearLayout
    private lateinit var suspendedOverlay: FrameLayout
    private lateinit var suspendedThumb: ImageView

    private lateinit var pipDot: View
    private lateinit var memBanner: TextView
    private lateinit var memoryMonitor: MemoryMonitor

    private val tabManager = TabManager()
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
        tabStripInner    = findViewById(R.id.tabStripInner)
        suspendedOverlay = findViewById(R.id.suspendedOverlay)
        suspendedThumb   = findViewById(R.id.suspendedThumb)
        pipDot           = findViewById(R.id.pipDot)
        memBanner        = findViewById(R.id.memBanner)

        threadModeClient = ThreadModeClient(urlText) { domain -> showJsBanner(domain) }

        threadModeClient.onPageLoaded = { url ->
            tabManager.updateActiveUrl(url)
            tabManager.setActiveSuspended(false)
            val title = webView.title?.takeIf { it.isNotBlank() } ?: url
            tabManager.updateActiveTitle(title)
            suspendedOverlay.visibility = View.GONE
            renderTabStrip()
        }

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getStringSet(PREFS_JS_ALWAYS, emptySet()) ?: emptySet()
        threadModeClient.loadAllowedAlways(saved)

        webView.settings.javaScriptEnabled = true
        webView.webViewClient = threadModeClient

        memoryMonitor = MemoryMonitor(this) { level -> updatePip(level) }

        fullPageBtn.setOnClickListener {
            val tab = tabManager.getActiveTab() ?: return@setOnClickListener
            tab.isFullMode = !tab.isFullMode
            threadModeClient.isFullMode = tab.isFullMode
            hideJsBanner()
            syncFullModeUI(tab.isFullMode)
            webView.reload()
        }

        jsAllowAlwaysBtn.setOnClickListener {
            pendingJsDomain?.let { domain ->
                threadModeClient.allowAlways(domain)
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

        openNewTab(HOME_URL)
    }

    override fun onResume() {
        super.onResume()
        memoryMonitor.start()
    }

    override fun onPause() {
        super.onPause()
        memoryMonitor.stop()
    }

    // --- Memory pip ---

    /** Update pip dot color and show/hide the low memory banner. */
    private fun updatePip(level: MemoryMonitor.Level) {
        val color = when (level) {
            MemoryMonitor.Level.GREEN  -> ContextCompat.getColor(this, R.color.mem_green)
            MemoryMonitor.Level.YELLOW -> ContextCompat.getColor(this, R.color.mem_yellow)
            MemoryMonitor.Level.RED    -> ContextCompat.getColor(this, R.color.mem_red)
        }
        // mutate() ensures we don't affect other views sharing this drawable resource
        (pipDot.background.mutate() as? GradientDrawable)?.setColor(color)
        memBanner.visibility = if (level == MemoryMonitor.Level.RED) View.VISIBLE else View.GONE
    }

    // --- Tab management ---

    private fun captureWebView(): Bitmap? {
        if (webView.width <= 0 || webView.height <= 0) return null
        return try {
            val bmp = Bitmap.createBitmap(webView.width, webView.height, Bitmap.Config.RGB_565)
            webView.draw(Canvas(bmp))
            bmp
        } catch (e: Exception) {
            null
        }
    }

    private fun openNewTab(url: String) {
        if (tabManager.getActiveTab() != null) {
            tabManager.updateActiveThumbnail(captureWebView())
            tabManager.setActiveSuspended(true)
        }

        val result = tabManager.openTab(url)

        if (result.oldestClosed) {
            Toast.makeText(this, getString(R.string.tab_limit_toast), Toast.LENGTH_SHORT).show()
        }

        threadModeClient.isFullMode = false
        syncFullModeUI(false)
        hideJsBanner()
        suspendedOverlay.visibility = View.GONE

        renderTabStrip()
        webView.loadUrl(url)
    }

    private fun switchToTab(tabId: Int) {
        val current = tabManager.getActiveTab()
        if (current?.id == tabId) return

        current?.let {
            it.thumbnail   = captureWebView()
            it.isSuspended = true
        }

        val target = tabManager.switchToTab(tabId) ?: return

        suspendedThumb.setImageBitmap(target.thumbnail)
        suspendedOverlay.visibility = View.VISIBLE

        threadModeClient.isFullMode = target.isFullMode
        syncFullModeUI(target.isFullMode)
        hideJsBanner()

        renderTabStrip()
        webView.loadUrl(target.url)
    }

    private fun renderTabStrip() {
        tabStripInner.removeAllViews()

        val activeId = tabManager.activeTabId
        val dp6      = (6 * resources.displayMetrics.density).toInt()
        val pillH    = LinearLayout.LayoutParams.MATCH_PARENT

        for (tab in tabManager.getTabs()) {
            val isActive = tab.id == activeId
            val label    = tab.title.let { if (it.length > 16) it.take(14) + "…" else it }

            val pill = TextView(this).apply {
                text     = label
                typeface = Typeface.MONOSPACE
                gravity  = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                setTextColor(ContextCompat.getColor(
                    this@MainActivity,
                    if (isActive) R.color.accent_green else R.color.text_muted
                ))
                setPadding(dp6 * 4, 0, dp6 * 4, 0)
                background = ContextCompat.getDrawable(
                    this@MainActivity,
                    if (isActive) R.drawable.bg_tab_pill_active else R.drawable.bg_tab_pill
                )
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, pillH
                ).also { it.marginEnd = dp6 }
                setOnClickListener { switchToTab(tab.id) }
            }
            tabStripInner.addView(pill)
        }

        val newBtn = TextView(this).apply {
            text     = "+"
            typeface = Typeface.MONOSPACE
            gravity  = Gravity.CENTER
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_muted))
            setPadding(dp6 * 4, 0, dp6 * 4, 0)
            background   = ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_tab_pill)
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, pillH)
            setOnClickListener { openNewTab(HOME_URL) }
        }
        tabStripInner.addView(newBtn)
    }

    // --- UI helpers ---

    private fun syncFullModeUI(isFullMode: Boolean) {
        if (isFullMode) {
            fullPageBtn.text       = getString(R.string.thread_mode_short)
            threadBadge.visibility = View.GONE
        } else {
            fullPageBtn.text       = getString(R.string.full_page)
            threadBadge.visibility = View.VISIBLE
        }
    }

    private fun showJsBanner(domain: String) {
        pendingJsDomain     = domain
        jsBannerDomain.text = domain
        jsBanner.visibility = View.VISIBLE
    }

    private fun hideJsBanner() {
        pendingJsDomain     = null
        jsBanner.visibility = View.GONE
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }
}

