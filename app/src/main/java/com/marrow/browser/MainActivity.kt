package com.marrow.browser

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var urlInput: EditText
    private lateinit var threadBadge: TextView
    private lateinit var fullPageBtn: Button
    private lateinit var threadModeClient: ThreadModeClient

    private lateinit var jsBanner: View
    private lateinit var jsBannerDomain: TextView
    private lateinit var jsAllowAlwaysBtn: Button
    private lateinit var jsAllowOnceBtn: Button
    private lateinit var jsKeepOffBtn: Button

    private lateinit var tabStripInner: LinearLayout
    private lateinit var tabCountBtn: TextView
    private lateinit var tabOverlay: FrameLayout
    private lateinit var tabCardList: LinearLayout

    private lateinit var suspendedOverlay: FrameLayout
    private lateinit var suspendedThumb: ImageView
    private lateinit var loadingBar: ProgressBar

    private lateinit var pipDot: View
    private lateinit var memBanner: TextView
    private lateinit var chromeBg: View
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
        urlInput         = findViewById(R.id.urlInput)
        threadBadge      = findViewById(R.id.threadBadge)
        fullPageBtn      = findViewById(R.id.fullPageBtn)
        jsBanner         = findViewById(R.id.jsBanner)
        jsBannerDomain   = findViewById(R.id.jsBannerDomain)
        jsAllowAlwaysBtn = findViewById(R.id.jsAllowAlwaysBtn)
        jsAllowOnceBtn   = findViewById(R.id.jsAllowOnceBtn)
        jsKeepOffBtn     = findViewById(R.id.jsKeepOffBtn)
        tabStripInner    = findViewById(R.id.tabStripInner)
        tabCountBtn      = findViewById(R.id.tabCountBtn)
        tabOverlay       = findViewById(R.id.tabOverlay)
        tabCardList      = findViewById(R.id.tabCardList)
        suspendedOverlay = findViewById(R.id.suspendedOverlay)
        suspendedThumb   = findViewById(R.id.suspendedThumb)
        loadingBar       = findViewById(R.id.loadingBar)
        pipDot           = findViewById(R.id.pipDot)
        memBanner        = findViewById(R.id.memBanner)
        chromeBg         = findViewById(R.id.chromeBg)

        threadModeClient = ThreadModeClient { domain -> showJsBanner(domain) }

        threadModeClient.onPageLoaded = { url ->
            // Only update the address bar if the user isn't typing in it
            if (!urlInput.hasFocus()) urlInput.setText(url)
            tabManager.updateActiveUrl(url)
            tabManager.setActiveSuspended(false)
            val title = webView.title?.takeIf { it.isNotBlank() } ?: url
            tabManager.updateActiveTitle(title)
            suspendedOverlay.visibility = View.GONE
            renderTabStrip()
            // Read the page's theme-color meta tag and tint the chrome
            readThemeColor()
        }

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val saved = prefs.getStringSet(PREFS_JS_ALWAYS, emptySet()) ?: emptySet()
        threadModeClient.loadAllowedAlways(saved)

        webView.settings.javaScriptEnabled = true
        webView.webViewClient = threadModeClient

        // Loading bar driven by real page progress
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                loadingBar.progress   = newProgress
                loadingBar.visibility = if (newProgress < 100) View.VISIBLE else View.GONE
            }
        }

        memoryMonitor = MemoryMonitor(this) { level -> updatePip(level) }

        // --- Button listeners ---

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
            // Block all remaining JS banners for this page — one tap, done
            threadModeClient.blockAllJsForPage()
            hideJsBanner()
        }

        tabCountBtn.setOnClickListener { toggleTabOverlay() }

        // Dismiss tab overlay when tapping outside (the overlay background itself)
        tabOverlay.setOnClickListener { tabOverlay.visibility = View.GONE }

        // URL bar — handle Go action and back button dismiss
        urlInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                val raw = urlInput.text.toString().trim()
                if (raw.isNotEmpty()) {
                    dismissKeyboard()
                    urlInput.clearFocus()
                    loadUrlInActiveTab(normalizeUrl(raw))
                }
                true
            } else false
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

    // --- URL handling ---

    /**
     * Normalize user input into a loadable URL.
     * - Already a URL → use as-is
     * - Looks like a domain (has a dot, no spaces) → prepend https://
     * - Everything else → DuckDuckGo HTML search (text-friendly, fits the philosophy)
     */
    private fun normalizeUrl(input: String): String {
        if (input.startsWith("http://") || input.startsWith("https://")) return input
        return if (input.contains(".") && !input.contains(" ")) {
            "https://$input"
        } else {
            "https://html.duckduckgo.com/html/?q=${Uri.encode(input)}"
        }
    }

    private fun loadUrlInActiveTab(url: String) {
        tabManager.updateActiveUrl(url)
        webView.loadUrl(url)
    }

    private fun dismissKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(urlInput.windowToken, 0)
    }

    // --- Theme color bleed ---

    /**
     * Read <meta name="theme-color"> from the loaded page via JS and tint the chrome.
     * Falls back to chrome_bg if absent or unparseable.
     * evaluateJavascript works even in thread mode — it's our own call, not the page's JS.
     */
    private fun readThemeColor() {
        webView.evaluateJavascript(
            "(function(){ var m=document.querySelector('meta[name=\"theme-color\"]'); return m?m.content:''; })()"
        ) { raw ->
            val hex = raw?.trim('"')?.trim()?.takeIf { it.startsWith("#") }
            val color = try {
                if (hex != null) Color.parseColor(hex) else null
            } catch (e: Exception) { null }
            chromeBg.setBackgroundColor(
                color ?: ContextCompat.getColor(this, R.color.chrome_bg)
            )
        }
    }

    // --- Memory pip ---

    private fun updatePip(level: MemoryMonitor.Level) {
        val color = when (level) {
            MemoryMonitor.Level.GREEN  -> ContextCompat.getColor(this, R.color.mem_green)
            MemoryMonitor.Level.YELLOW -> ContextCompat.getColor(this, R.color.mem_yellow)
            MemoryMonitor.Level.RED    -> ContextCompat.getColor(this, R.color.mem_red)
        }
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
        } catch (e: Exception) { null }
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
        tabOverlay.visibility = View.GONE

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
        updateTabCountBtn()

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
                setTextColor(ContextCompat.getColor(this@MainActivity,
                    if (isActive) R.color.accent_green else R.color.text_muted))
                setPadding(dp6 * 4, 0, dp6 * 4, 0)
                background = ContextCompat.getDrawable(this@MainActivity,
                    if (isActive) R.drawable.bg_tab_pill_active else R.drawable.bg_tab_pill)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, pillH
                ).also { it.marginEnd = dp6 }
                setOnClickListener { switchToTab(tab.id) }
            }
            tabStripInner.addView(pill)
        }

        // "+" new tab button
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

    private fun updateTabCountBtn() {
        tabCountBtn.text = tabManager.getTabs().size.toString()
    }

    // --- Tab overlay ---

    private fun toggleTabOverlay() {
        if (tabOverlay.visibility == View.VISIBLE) {
            tabOverlay.visibility = View.GONE
        } else {
            renderTabOverlay()
            tabOverlay.visibility = View.VISIBLE
        }
    }

    /** Build tab cards programmatically — same pattern as tab pills. */
    private fun renderTabOverlay() {
        tabCardList.removeAllViews()

        val dp   = resources.displayMetrics.density
        val dp8  = (8  * dp).toInt()
        val dp32 = (32 * dp).toInt()
        val dp64 = (64 * dp).toInt()
        val dp80 = (80 * dp).toInt()
        val activeId = tabManager.activeTabId

        for (tab in tabManager.getTabs()) {
            val isActive = tab.id == activeId

            // Card row
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                background  = ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_tab_card)
                setPadding(dp8, dp8, dp8, dp8)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp80
                ).also { it.bottomMargin = dp8 }
            }

            // Thumbnail
            val thumb = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(dp64, LinearLayout.LayoutParams.MATCH_PARENT)
                scaleType    = ImageView.ScaleType.CENTER_CROP
                setImageBitmap(tab.thumbnail)
                background   = ContextCompat.getDrawable(this@MainActivity, R.drawable.bg_tab_pill)
            }

            // Title + domain column
            val textCol = LinearLayout(this).apply {
                orientation  = LinearLayout.VERTICAL
                gravity      = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    .also { it.marginStart = dp8 }
            }

            val titleView = TextView(this).apply {
                text     = tab.title.let { if (it.length > 28) it.take(26) + "…" else it }
                typeface = Typeface.MONOSPACE
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setTextColor(ContextCompat.getColor(this@MainActivity,
                    if (isActive) R.color.accent_green else R.color.text_muted))
            }

            val domainView = TextView(this).apply {
                text = try { Uri.parse(tab.url).host ?: tab.url } catch (e: Exception) { tab.url }
                typeface = Typeface.MONOSPACE
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_dim))
            }

            textCol.addView(titleView)
            textCol.addView(domainView)

            // Close button
            val closeBtn = TextView(this).apply {
                text     = "×"
                typeface = Typeface.MONOSPACE
                gravity  = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                setTextColor(ContextCompat.getColor(this@MainActivity, R.color.text_dim))
                layoutParams = LinearLayout.LayoutParams(dp32, LinearLayout.LayoutParams.MATCH_PARENT)
                setOnClickListener { closeTabFromOverlay(tab.id) }
            }

            card.addView(thumb)
            card.addView(textCol)
            card.addView(closeBtn)

            card.setOnClickListener {
                tabOverlay.visibility = View.GONE
                switchToTab(tab.id)
            }

            tabCardList.addView(card)
        }
    }

    private fun closeTabFromOverlay(tabId: Int) {
        val wasActive = tabId == tabManager.activeTabId
        val newActive = tabManager.closeTab(tabId)

        // If no tabs left, open a fresh one
        if (tabManager.getTabs().isEmpty()) {
            tabOverlay.visibility = View.GONE
            openNewTab(HOME_URL)
            return
        }

        if (wasActive && newActive != null) {
            // The active tab was closed — load the new active tab immediately
            tabOverlay.visibility = View.GONE
            threadModeClient.isFullMode = newActive.isFullMode
            syncFullModeUI(newActive.isFullMode)
            suspendedThumb.setImageBitmap(newActive.thumbnail)
            suspendedOverlay.visibility = View.VISIBLE
            hideJsBanner()
            renderTabStrip()
            webView.loadUrl(newActive.url)
            return
        }

        // Non-active tab was closed — just refresh the UI
        renderTabStrip()
        renderTabOverlay()
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
        when {
            tabOverlay.visibility == View.VISIBLE -> tabOverlay.visibility = View.GONE
            urlInput.hasFocus()                   -> { dismissKeyboard(); urlInput.clearFocus() }
            webView.canGoBack()                   -> webView.goBack()
            else                                  -> super.onBackPressed()
        }
    }
}
