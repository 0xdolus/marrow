package com.marrow.browser

import android.app.ActivityManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebSettings
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.net.URLEncoder

class MainActivity : AppCompatActivity() {

    // ── Core views ──────────────────────────────────────────────
    private lateinit var chromeBg: LinearLayout
    private lateinit var webView: WebView
    private lateinit var threadBadge: TextView
    private lateinit var fullBtn: TextView
    private lateinit var urlInput: EditText
    private lateinit var tabCountBtn: TextView
    private lateinit var tabStripInner: LinearLayout
    private lateinit var tabOverlay: FrameLayout
    private lateinit var loadingBar: ProgressBar
    private lateinit var pipDot: TextView
    private lateinit var memBanner: TextView
    private lateinit var imgSearchBtn: TextView

    // ── JS banner ────────────────────────────────────────────────
    private lateinit var jsBanner: LinearLayout
    private lateinit var jsBannerDomain: TextView
    private lateinit var jsAllowBtn: TextView
    private lateinit var jsAllowOnceBtn: TextView
    private lateinit var jsKeepOffBtn: TextView

    // ── CF banner ────────────────────────────────────────────────
    private lateinit var cfBanner: LinearLayout
    private lateinit var cfBannerDomain: TextView
    private lateinit var cfAllowBtn: TextView
    private lateinit var cfDismissBtn: TextView
    private var pendingCfDomain: String? = null

    // ── Split views ──────────────────────────────────────────────
    private lateinit var splitWebView: WebView
    private lateinit var splitDivider: View
    private lateinit var splitBtn: TextView
    private lateinit var exitSplitBtn: TextView
    private lateinit var topPaneContainer: LinearLayout

    private var isSplitMode = false
    private var splitPaneActive = false  // false = top pane active, true = bottom pane active

    // ── Tab + memory ─────────────────────────────────────────────
    private lateinit var tabManager: TabManager
    private lateinit var memoryMonitor: MemoryMonitor

    // ── State ────────────────────────────────────────────────────
    private var isFullMode = false
    private lateinit var threadClient: ThreadModeClient

    companion object {
        const val DDG_BASE = "https://html.duckduckgo.com/html/?q="
        const val DDG_IMAGE_BASE = "https://duckduckgo.com/?iax=images&ia=images&q="
    }

    // ════════════════════════════════════════════════════════════
    // onCreate
    // ════════════════════════════════════════════════════════════
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bindViews()
        setupTabManager()
        setupMemoryMonitor()
        setupMainWebView()
        setupSplitWebView()
        setupUrlBar()
        setupButtons()

        navigateTo("https://html.duckduckgo.com/html/", webView)
    }

    // ════════════════════════════════════════════════════════════
    // View binding
    // ════════════════════════════════════════════════════════════
    private fun bindViews() {
        chromeBg       = findViewById(R.id.chromeBg)
        webView        = findViewById(R.id.webView)
        threadBadge    = findViewById(R.id.threadBadge)
        fullBtn        = findViewById(R.id.fullBtn)
        urlInput       = findViewById(R.id.urlInput)
        tabCountBtn    = findViewById(R.id.tabCountBtn)
        tabStripInner  = findViewById(R.id.tabStripInner)
        tabOverlay     = findViewById(R.id.tabOverlay)
        loadingBar     = findViewById(R.id.loadingBar)
        pipDot         = findViewById(R.id.pipDot)
        memBanner      = findViewById(R.id.memBanner)
        imgSearchBtn   = findViewById(R.id.imgSearchBtn)

        jsBanner       = findViewById(R.id.jsBanner)
        jsBannerDomain = findViewById(R.id.jsBannerDomain)
        jsAllowBtn     = findViewById(R.id.jsAllowBtn)
        jsAllowOnceBtn = findViewById(R.id.jsAllowOnceBtn)
        jsKeepOffBtn   = findViewById(R.id.jsKeepOffBtn)

        cfBanner       = findViewById(R.id.cfBanner)
        cfBannerDomain = findViewById(R.id.cfBannerDomain)
        cfAllowBtn     = findViewById(R.id.cfAllowBtn)
        cfDismissBtn   = findViewById(R.id.cfDismissBtn)

        splitWebView      = findViewById(R.id.splitWebView)
        splitDivider      = findViewById(R.id.splitDivider)
        splitBtn          = findViewById(R.id.splitBtn)
        exitSplitBtn      = findViewById(R.id.exitSplitBtn)
        topPaneContainer  = findViewById(R.id.topPaneContainer)
    }

    // ════════════════════════════════════════════════════════════
    // Tab manager
    // ════════════════════════════════════════════════════════════
    private fun setupTabManager() {
        tabManager = TabManager()
        val result = tabManager.openTab("DuckDuckGo", "https://html.duckduckgo.com/html/")
        renderTabStrip()
    }

    // ════════════════════════════════════════════════════════════
    // Memory monitor
    // ════════════════════════════════════════════════════════════
    private fun setupMemoryMonitor() {
        val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        memoryMonitor = MemoryMonitor(am) { level, message ->
            runOnUiThread { updatePip(level, message) }
        }
    }

    private fun updatePip(level: Int, message: String) {
        val color = when (level) {
            MemoryMonitor.LEVEL_GREEN  -> "#5a9a5a"
            MemoryMonitor.LEVEL_YELLOW -> "#c8a840"
            else                       -> "#c0392b"
        }
        pipDot.background.setTint(Color.parseColor(color))
        if (level == MemoryMonitor.LEVEL_RED) {
            memBanner.text = message
            memBanner.visibility = View.VISIBLE
        } else {
            memBanner.visibility = View.GONE
        }
    }

    override fun onResume() { super.onResume(); memoryMonitor.start() }
    override fun onPause()  { super.onPause();  memoryMonitor.stop()  }

    // ════════════════════════════════════════════════════════════
    // Main WebView setup
    // ════════════════════════════════════════════════════════════
    private fun setupMainWebView() {
        threadClient = ThreadModeClient(
            onJsRequested = { domain -> runOnUiThread { showJsBanner(domain) } },
            onPageLoaded  = { url, title -> runOnUiThread {
                urlInput.setText(url)
                tabManager.updateActiveTitle(title ?: "")
                tabManager.updateActiveUrl(url)
                renderTabStrip()
                readThemeColor()
            }},
            onCloudflareDetected = { domain -> runOnUiThread { showCfBanner(domain) } }
        )

        webView.webViewClient = threadClient
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                loadingBar.visibility = if (newProgress < 100) View.VISIBLE else View.INVISIBLE
                loadingBar.progress = newProgress
            }
        }

        applyThreadSettings(webView)

        webView.setOnClickListener { setActivePane(false) }
    }

    // ════════════════════════════════════════════════════════════
    // Split WebView setup
    // ════════════════════════════════════════════════════════════
    private fun setupSplitWebView() {
        applyThreadSettings(splitWebView)

        splitWebView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                return false
            }
        }

        splitWebView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (splitPaneActive) {
                    loadingBar.visibility = if (newProgress < 100) View.VISIBLE else View.INVISIBLE
                    loadingBar.progress = newProgress
                }
            }
        }

        splitWebView.setOnClickListener { setActivePane(true) }
    }

    private fun applyThreadSettings(wv: WebView) {
        wv.settings.apply {
            javaScriptEnabled = false
            blockNetworkImage = true
            loadsImagesAutomatically = false
            cacheMode = WebSettings.LOAD_NO_CACHE
        }
    }

    // ════════════════════════════════════════════════════════════
    // Split mode
    // ════════════════════════════════════════════════════════════
    private fun enterSplitMode() {
        isSplitMode = true
        splitPaneActive = false

        splitWebView.visibility = View.VISIBLE
        splitDivider.visibility = View.VISIBLE
        exitSplitBtn.visibility = View.VISIBLE
        splitBtn.visibility     = View.GONE

        // Give both panes equal weight
        val topParams = topPaneContainer.layoutParams as LinearLayout.LayoutParams
        topParams.weight = 1f
        topPaneContainer.layoutParams = topParams

        val bottomParams = splitWebView.layoutParams as LinearLayout.LayoutParams
        bottomParams.weight = 1f
        splitWebView.layoutParams = bottomParams

        splitWebView.loadUrl(DDG_BASE)
        setActivePane(false)
    }

    private fun exitSplitMode() {
        isSplitMode = false
        splitPaneActive = false

        splitWebView.stopLoading()
        splitWebView.loadUrl("about:blank")
        splitWebView.visibility = View.GONE
        splitDivider.visibility = View.GONE
        exitSplitBtn.visibility = View.GONE
        splitBtn.visibility     = View.VISIBLE

        setActivePane(false)
    }

    private fun setActivePane(bottom: Boolean) {
        splitPaneActive = bottom

        if (!isSplitMode) {
            urlInput.setText(tabManager.activeTab()?.url ?: "")
            return
        }

        if (bottom) {
            urlInput.setText(splitWebView.url ?: "")
            splitDivider.setBackgroundColor(Color.parseColor("#8ab8d8"))
        } else {
            urlInput.setText(tabManager.activeTab()?.url ?: "")
            splitDivider.setBackgroundColor(Color.parseColor("#5a9a5a"))
        }
    }

    // ════════════════════════════════════════════════════════════
    // URL bar
    // ════════════════════════════════════════════════════════════
    private fun setupUrlBar() {
        urlInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                val url = normalizeUrl(urlInput.text.toString().trim())
                hideKeyboard()
                if (isSplitMode && splitPaneActive) {
                    navigateTo(url, splitWebView)
                } else {
                    navigateTo(url, webView)
                }
                true
            } else false
        }
    }

    private fun navigateTo(url: String, target: WebView) {
        hideBanners()
        target.loadUrl(url)
    }

    private fun normalizeUrl(input: String): String {
        if (input.startsWith("http://") || input.startsWith("https://")) return input
        if (input.contains(".") && !input.contains(" ")) return "https://$input"
        return DDG_BASE + URLEncoder.encode(input, "UTF-8")
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(urlInput.windowToken, 0)
    }

    // ════════════════════════════════════════════════════════════
    // Buttons
    // ════════════════════════════════════════════════════════════
    private fun setupButtons() {

        // Full / thread toggle
        fullBtn.setOnClickListener {
            isFullMode = !isFullMode
            val target = if (isSplitMode && splitPaneActive) splitWebView else webView
            if (isFullMode) {
                target.settings.javaScriptEnabled = true
                target.settings.loadsImagesAutomatically = true
                target.settings.blockNetworkImage = false
                fullBtn.text = "Thread mode"
            } else {
                target.settings.javaScriptEnabled = false
                target.settings.loadsImagesAutomatically = false
                target.settings.blockNetworkImage = true
                target.clearCache(true)
                fullBtn.text = getString(R.string.full_mode)
            }
            target.reload()
        }

        // Tab count → overlay
        tabCountBtn.setOnClickListener {
            if (tabOverlay.visibility == View.VISIBLE) {
                tabOverlay.visibility = View.GONE
            } else {
                renderTabOverlay()
                tabOverlay.visibility = View.VISIBLE
            }
        }

        // Image search
        imgSearchBtn.setOnClickListener { searchImages() }

        // Split enter
        splitBtn.setOnClickListener { enterSplitMode() }

        // Split exit
        exitSplitBtn.setOnClickListener { exitSplitMode() }

        // JS banner buttons
        jsAllowBtn.setOnClickListener {
            val domain = jsBannerDomain.text.toString()
            threadClient.allowDomain(domain)
            hideJsBanner()
            webView.reload()
        }
        jsAllowOnceBtn.setOnClickListener {
            threadClient.allowOnce()
            hideJsBanner()
            webView.reload()
        }
        jsKeepOffBtn.setOnClickListener { hideJsBanner() }

        // CF banner buttons
        cfAllowBtn.setOnClickListener {
            val domain = pendingCfDomain ?: return@setOnClickListener
            threadClient.setCfBypass(domain)
            hideCfBanner()
            webView.reload()
        }
        cfDismissBtn.setOnClickListener { hideCfBanner() }
    }

    // ════════════════════════════════════════════════════════════
    // Banners
    // ════════════════════════════════════════════════════════════
    private fun showJsBanner(domain: String) {
        jsBannerDomain.text = domain
        jsBanner.visibility = View.VISIBLE
        cfBanner.visibility = View.GONE
    }

    private fun hideJsBanner() { jsBanner.visibility = View.GONE }

    private fun showCfBanner(domain: String) {
        pendingCfDomain = domain
        cfBannerDomain.text = getString(R.string.cf_message) + "\n$domain"
        cfBanner.visibility = View.VISIBLE
        jsBanner.visibility = View.GONE
    }

    private fun hideCfBanner() {
        cfBanner.visibility = View.GONE
        pendingCfDomain = null
    }

    private fun hideBanners() {
        hideJsBanner()
        hideCfBanner()
    }

    // ════════════════════════════════════════════════════════════
    // Image search
    // ════════════════════════════════════════════════════════════
    private fun searchImages() {
        val current = urlInput.text.toString()
        val query = extractSearchQuery(current).ifBlank { current }
        if (query.isBlank()) return
        val target = if (isSplitMode && splitPaneActive) splitWebView else webView
        target.settings.javaScriptEnabled = true
        target.settings.loadsImagesAutomatically = true
        target.settings.blockNetworkImage = false
        isFullMode = true
        fullBtn.text = "Thread mode"
        navigateTo(DDG_IMAGE_BASE + URLEncoder.encode(query, "UTF-8"), target)
    }

    private fun extractSearchQuery(url: String): String {
        return try {
            val uri = android.net.Uri.parse(url)
            uri.getQueryParameter("q") ?: ""
        } catch (e: Exception) { "" }
    }

    // ════════════════════════════════════════════════════════════
    // Theme color
    // ════════════════════════════════════════════════════════════
    private fun readThemeColor() {
        webView.evaluateJavascript(
            "(function(){ var m = document.querySelector('meta[name=theme-color]'); return m ? m.getAttribute('content') : ''; })()"
        ) { value ->
            val raw = value?.trim('"') ?: ""
            runOnUiThread {
                try {
                    if (raw.startsWith("#")) {
                        chromeBg.setBackgroundColor(Color.parseColor(raw))
                    } else {
                        chromeBg.setBackgroundColor(Color.parseColor("#0f0f0f"))
                    }
                } catch (e: Exception) {
                    chromeBg.setBackgroundColor(Color.parseColor("#0f0f0f"))
                }
            }
        }
    }

    // ════════════════════════════════════════════════════════════
    // Tab strip + overlay
    // ════════════════════════════════════════════════════════════
    private fun renderTabStrip() {
        tabStripInner.removeAllViews()
        tabCountBtn.text = tabManager.count().toString()

        for (tab in tabManager.tabs) {
            val pill = TextView(this).apply {
                text = tab.title.take(16).ifBlank { "New Tab" }
                textSize = 11f
                setTextColor(Color.parseColor("#c8bfaf"))
                setPadding(24, 12, 24, 12)
                background = if (tab.id == tabManager.activeId)
                    getDrawable(R.drawable.bg_tab_pill_active)
                else
                    getDrawable(R.drawable.bg_tab_pill)
                setOnClickListener { switchToTab(tab.id) }
            }
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginEnd = 6 }
            tabStripInner.addView(pill, lp)
        }

        val addBtn = TextView(this).apply {
            text = "+"
            textSize = 16f
            setTextColor(Color.parseColor("#c8bfaf"))
            setPadding(24, 12, 24, 12)
            background = getDrawable(R.drawable.bg_tab_pill)
            setOnClickListener { openNewTab() }
        }
        tabStripInner.addView(addBtn)
    }

    private fun renderTabOverlay() {
        tabOverlay.removeAllViews()
        val scroll = ScrollView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        val list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }

        for (tab in tabManager.tabs) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                background = getDrawable(R.drawable.bg_tab_card)
                setPadding(16, 12, 16, 12)
                gravity = android.view.Gravity.CENTER_VERTICAL
            }

            val thumb = tab.thumbnail
            if (thumb != null) {
                val iv = android.widget.ImageView(this).apply {
                    setImageBitmap(thumb)
                    layoutParams = LinearLayout.LayoutParams(120, 80).apply { marginEnd = 12 }
                    scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                }
                card.addView(iv)
            }

            val title = TextView(this).apply {
                text = tab.title.ifBlank { tab.url }
                textSize = 12f
                setTextColor(Color.parseColor("#f0ead6"))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            card.addView(title)

            val closeBtn = TextView(this).apply {
                text = "✕"
                textSize = 14f
                setTextColor(Color.parseColor("#c8bfaf"))
                setPadding(12, 0, 0, 0)
                setOnClickListener { closeTab(tab.id) }
            }
            card.addView(closeBtn)

            card.setOnClickListener { switchToTab(tab.id) }

            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 8 }
            list.addView(card, lp)
        }

        scroll.addView(list)
        tabOverlay.addView(scroll)
    }

    // ════════════════════════════════════════════════════════════
    // Tab actions
    // ════════════════════════════════════════════════════════════
    private fun openNewTab() {
        captureWebView()
        val result = tabManager.openTab("New Tab", DDG_BASE)
        when (result) {
            TabManager.OpenResult.OPENED -> {
                webView.loadUrl(DDG_BASE)
                isFullMode = false
                syncFullModeUI()
                renderTabStrip()
                tabOverlay.visibility = View.GONE
            }
            TabManager.OpenResult.REPLACED -> {
                Toast.makeText(this, "Oldest tab closed", Toast.LENGTH_SHORT).show()
                webView.loadUrl(DDG_BASE)
                isFullMode = false
                syncFullModeUI()
                renderTabStrip()
                tabOverlay.visibility = View.GONE
            }
        }
    }

    private fun switchToTab(id: String) {
        captureWebView()
        val tab = tabManager.switchToTab(id) ?: return
        webView.loadUrl(tab.url)
        urlInput.setText(tab.url)
        isFullMode = false
        syncFullModeUI()
        renderTabStrip()
        tabOverlay.visibility = View.GONE
        hideBanners()
    }

    private fun closeTab(id: String) {
        val next = tabManager.closeTab(id)
        if (next != null) {
            webView.loadUrl(next.url)
            urlInput.setText(next.url)
        } else {
            webView.loadUrl(DDG_BASE)
            urlInput.setText(DDG_BASE)
        }
        renderTabStrip()
        tabOverlay.visibility = View.GONE
    }

    private fun captureWebView() {
        webView.isDrawingCacheEnabled = true
        val bmp = Bitmap.createBitmap(webView.drawingCache)
        webView.isDrawingCacheEnabled = false
        tabManager.updateActiveThumbnail(bmp)
    }

    private fun syncFullModeUI() {
        fullBtn.text = if (isFullMode) "Thread mode" else getString(R.string.full_mode)
        webView.settings.javaScriptEnabled = isFullMode
        webView.settings.loadsImagesAutomatically = isFullMode
        webView.settings.blockNetworkImage = !isFullMode
    }
}
