package com.marrow.browser

import android.content.Context
import android.graphics.Color
import android.graphics.Bitmap
import android.os.Bundle
import android.view.MotionEvent
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

    private lateinit var chromeBg: LinearLayout
    private lateinit var webView: WebView
    private lateinit var urlInput: EditText
    private lateinit var tabCountBtn: TextView
    private lateinit var tabStripInner: LinearLayout
    private lateinit var tabOverlay: FrameLayout
    private lateinit var loadingBar: ProgressBar
    private lateinit var pipDot: TextView
    private lateinit var memBanner: TextView
    private lateinit var imgSearchBtn: TextView
    private lateinit var paneIndicator: View

    private lateinit var splitWebView: WebView
    private lateinit var splitDivider: View
    private lateinit var splitBtn: TextView
    private lateinit var exitSplitBtn: TextView
    private lateinit var topPaneContainer: LinearLayout
    private lateinit var topModeRow: LinearLayout
    private lateinit var topTitleBar: TextView
    private lateinit var bottomTitleBar: TextView
    private lateinit var splitDimOverlay: View
    private lateinit var bottomPaneContainer: LinearLayout

    private var isSplitMode = false
    private var splitPaneActive = false

    private var dividerDragStartY = 0f
    private var topWeightAtDragStart = 1f
    private var bottomWeightAtDragStart = 1f

    private lateinit var tabManager: TabManager
    private lateinit var memoryMonitor: MemoryMonitor
    private lateinit var threadClient: ThreadModeClient

    companion object {
        const val HOME           = "file:///android_asset/home.html"
        const val DDG_BASE       = "https://html.duckduckgo.com/html/?q="
        const val DDG_IMAGE_BASE = "https://duckduckgo.com/?iax=images&ia=images&q="
        const val COLOR_TOP_PANE    = "#5a9a5a"
        const val COLOR_BOTTOM_PANE = "#8ab8d8"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        bindViews()
        setupTabManager()
        setupMemoryMonitor()
        setupMainWebView()
        setupSplitWebView()
        setupDividerDrag()
        setupUrlBar()
        setupButtons()
        navigateTo(HOME, webView)
    }

    private fun bindViews() {
        chromeBg            = findViewById(R.id.chromeBg)
        webView             = findViewById(R.id.webView)
        urlInput            = findViewById(R.id.urlInput)
        tabCountBtn         = findViewById(R.id.tabCountBtn)
        tabStripInner       = findViewById(R.id.tabStripInner)
        tabOverlay          = findViewById(R.id.tabOverlay)
        loadingBar          = findViewById(R.id.loadingBar)
        pipDot              = findViewById(R.id.pipDot)
        memBanner           = findViewById(R.id.memBanner)
        imgSearchBtn        = findViewById(R.id.imgSearchBtn)
        paneIndicator       = findViewById(R.id.paneIndicator)
        splitWebView        = findViewById(R.id.splitWebView)
        splitDivider        = findViewById(R.id.splitDivider)
        splitBtn            = findViewById(R.id.splitBtn)
        exitSplitBtn        = findViewById(R.id.exitSplitBtn)
        topPaneContainer    = findViewById(R.id.topPaneContainer)
        topModeRow          = findViewById(R.id.topModeRow)
        topTitleBar         = findViewById(R.id.topTitleBar)
        bottomTitleBar      = findViewById(R.id.bottomTitleBar)
        splitDimOverlay     = findViewById(R.id.splitDimOverlay)
        bottomPaneContainer = findViewById(R.id.bottomPaneContainer)
    }

    private fun setupTabManager() {
        tabManager = TabManager()
        tabManager.openTab(HOME)
        renderTabStrip()
    }

    private fun setupMemoryMonitor() {
        memoryMonitor = MemoryMonitor(this) { level ->
            runOnUiThread { updatePip(level) }
        }
    }

    private fun updatePip(level: MemoryMonitor.Level) {
        val color = when (level) {
            MemoryMonitor.Level.GREEN  -> "#5a9a5a"
            MemoryMonitor.Level.YELLOW -> "#c8a840"
            MemoryMonitor.Level.RED    -> "#c0392b"
        }
        pipDot.background.setTint(Color.parseColor(color))
        if (level == MemoryMonitor.Level.RED && !isSplitMode) {
            memBanner.text = getString(R.string.mem_warning)
            memBanner.visibility = View.VISIBLE
        } else {
            memBanner.visibility = View.GONE
        }
    }

    override fun onResume() { super.onResume(); memoryMonitor.start() }
    override fun onPause()  { super.onPause();  memoryMonitor.stop()  }

    private fun setupMainWebView() {
        threadClient = ThreadModeClient()
        threadClient.onPageLoaded = { url ->
            runOnUiThread {
                if (!splitPaneActive) urlInput.setText(if (url == HOME) "marrow" else (webView.title?.takeIf { it.isNotBlank() } ?: domainFrom(url)))
                tabManager.updateActiveTitle(webView.title ?: "")
                tabManager.updateActiveUrl(url)
                renderTabStrip()
                readThemeColor()
                if (isSplitMode) topTitleBar.text = domainFrom(url)
            }
        }
        webView.webViewClient = threadClient
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (!splitPaneActive) {
                    loadingBar.visibility = if (newProgress < 100) View.VISIBLE else View.INVISIBLE
                    loadingBar.progress = newProgress
                }
            }
        }
        applyFullSettings(webView)
        topModeRow.visibility = View.GONE
        webView.setOnTouchListener { _, _ ->
            if (isSplitMode && splitPaneActive) setActivePane(false)
            false
        }
    }

    private fun setupSplitWebView() {
        applyFullSettings(splitWebView)
        splitWebView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean = false
            override fun onPageFinished(view: WebView?, url: String?) {
                if (url == null) return
                runOnUiThread {
                    if (splitPaneActive) urlInput.setText(domainFrom(url))
                    bottomTitleBar.text = domainFrom(url)
                }
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
        splitWebView.setOnTouchListener { _, _ ->
            if (isSplitMode && !splitPaneActive) setActivePane(true)
            false
        }
    }

    private fun setupDividerDrag() {
        var lastTapTime = 0L
        splitDivider.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dividerDragStartY = event.rawY
                    val topParams = topPaneContainer.layoutParams as LinearLayout.LayoutParams
                    val botParams = bottomPaneContainer.layoutParams as LinearLayout.LayoutParams
                    topWeightAtDragStart    = topParams.weight
                    bottomWeightAtDragStart = botParams.weight
                    val now = System.currentTimeMillis()
                    if (now - lastTapTime < 350) snapToEqual()
                    lastTapTime = now
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val totalHeight = (topPaneContainer.height + bottomPaneContainer.height).toFloat()
                    if (totalHeight == 0f) return@setOnTouchListener true
                    val delta = event.rawY - dividerDragStartY
                    val deltaWeight = (delta / totalHeight) * (topWeightAtDragStart + bottomWeightAtDragStart)
                    val newTop    = (topWeightAtDragStart + deltaWeight).coerceIn(0.2f, 1.8f)
                    val newBottom = (topWeightAtDragStart + bottomWeightAtDragStart) - newTop
                    val topParams = topPaneContainer.layoutParams as LinearLayout.LayoutParams
                    topParams.weight = newTop
                    topPaneContainer.layoutParams = topParams
                    val botParams = bottomPaneContainer.layoutParams as LinearLayout.LayoutParams
                    botParams.weight = newBottom
                    bottomPaneContainer.layoutParams = botParams
                    true
                }
                else -> false
            }
        }
    }

    private fun snapToEqual() {
        val topParams = topPaneContainer.layoutParams as LinearLayout.LayoutParams
        topParams.weight = 1f
        topPaneContainer.layoutParams = topParams
        val botParams = bottomPaneContainer.layoutParams as LinearLayout.LayoutParams
        botParams.weight = 1f
        bottomPaneContainer.layoutParams = botParams
    }

    private fun applyFullSettings(wv: WebView) {
        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            blockNetworkImage = false
            loadsImagesAutomatically = true
            cacheMode = WebSettings.LOAD_DEFAULT
        }
    }

    private fun enterSplitMode() {
        isSplitMode = true
        splitPaneActive = false
        bottomPaneContainer.visibility = View.VISIBLE
        splitDivider.visibility        = View.VISIBLE
        exitSplitBtn.visibility        = View.VISIBLE
        splitBtn.visibility            = View.GONE
        topTitleBar.visibility         = View.VISIBLE
        bottomTitleBar.visibility      = View.VISIBLE
        snapToEqual()
        splitWebView.loadUrl(HOME)
        bottomTitleBar.text = "marrow"
        topTitleBar.text = domainFrom(webView.url ?: "")
        setActivePane(false)
    }

    private fun exitSplitMode() {
        isSplitMode = false
        splitPaneActive = false
        splitWebView.stopLoading()
        splitWebView.loadUrl("about:blank")
        bottomPaneContainer.visibility = View.GONE
        splitDivider.visibility        = View.GONE
        exitSplitBtn.visibility        = View.GONE
        splitBtn.visibility            = View.VISIBLE
        topTitleBar.visibility         = View.GONE
        bottomTitleBar.visibility      = View.GONE
        splitDimOverlay.visibility     = View.GONE
        topModeRow.visibility          = View.VISIBLE
        setActivePane(false)
    }

    private fun setActivePane(bottom: Boolean) {
        splitPaneActive = bottom
        if (!isSplitMode) {
            urlInput.setText(domainFrom(tabManager.getActiveTab()?.url ?: ""))
            updatePaneIndicator(false)
            return
        }
        updatePaneIndicator(bottom)
        if (bottom) {
            urlInput.setText(domainFrom(splitWebView.url ?: ""))
            splitDivider.setBackgroundColor(Color.parseColor(COLOR_BOTTOM_PANE))
            topModeRow.visibility      = View.GONE
            topTitleBar.visibility     = View.VISIBLE
            splitDimOverlay.visibility = View.VISIBLE
            topTitleBar.setBackgroundColor(Color.parseColor("#1a1a1a"))
            topTitleBar.setTextColor(Color.parseColor("#555555"))
            bottomTitleBar.setBackgroundColor(Color.parseColor(COLOR_BOTTOM_PANE))
            bottomTitleBar.setTextColor(Color.parseColor("#0f0f0f"))
        } else {
            urlInput.setText(domainFrom(tabManager.getActiveTab()?.url ?: ""))
            splitDivider.setBackgroundColor(Color.parseColor(COLOR_TOP_PANE))
            topModeRow.visibility      = View.VISIBLE
            topTitleBar.visibility     = View.GONE
            splitDimOverlay.visibility = View.GONE
            topTitleBar.setBackgroundColor(Color.parseColor(COLOR_TOP_PANE))
            topTitleBar.setTextColor(Color.parseColor("#0f0f0f"))
            bottomTitleBar.setBackgroundColor(Color.parseColor("#1a1a1a"))
            bottomTitleBar.setTextColor(Color.parseColor("#555555"))
        }
    }

    private fun updatePaneIndicator(bottomActive: Boolean) {
        if (!isSplitMode) {
            paneIndicator.visibility = View.GONE
            return
        }
        paneIndicator.visibility = View.VISIBLE
        paneIndicator.setBackgroundColor(
            Color.parseColor(if (bottomActive) COLOR_BOTTOM_PANE else COLOR_TOP_PANE)
        )
    }

    private fun setupUrlBar() {
        urlInput.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                val realUrl = if (isSplitMode && splitPaneActive) splitWebView.url ?: "" else tabManager.getActiveTab()?.url ?: ""
                if (realUrl != HOME) urlInput.setText(realUrl) else urlInput.setText("")
                urlInput.selectAll()
            } else {
                val url = if (isSplitMode && splitPaneActive) splitWebView.url ?: "" else tabManager.getActiveTab()?.url ?: ""
                urlInput.setText(if (url == HOME) "marrow" else (webView.title?.takeIf { it.isNotBlank() } ?: domainFrom(url)))
            }
        }
        urlInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                val url = normalizeUrl(urlInput.text.toString().trim())
                hideKeyboard()
                if (isSplitMode && splitPaneActive) navigateTo(url, splitWebView)
                else navigateTo(url, webView)
                true
            } else false
        }
    }

    private fun navigateTo(url: String, target: WebView) {
        target.loadUrl(url)
    }

    private fun normalizeUrl(input: String): String {
        if (input.startsWith("http://") || input.startsWith("https://")) return input
        if (input.startsWith("file://")) return input
        if (input.contains(".") && !input.contains(" ")) return "https://$input"
        return DDG_BASE + URLEncoder.encode(input, "UTF-8")
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(urlInput.windowToken, 0)
    }

    override fun onBackPressed() {
        val active = activeWebView()
        if (active.canGoBack()) active.goBack()
        else super.onBackPressed()
    }

    private fun setupButtons() {
        tabCountBtn.setOnClickListener {
            if (tabOverlay.visibility == View.VISIBLE) tabOverlay.visibility = View.GONE
            else { renderTabOverlay(); tabOverlay.visibility = View.VISIBLE }
        }
        imgSearchBtn.setOnClickListener { searchImages() }
        splitBtn.setOnClickListener     { enterSplitMode() }
        exitSplitBtn.setOnClickListener { exitSplitMode() }
        topTitleBar.setOnClickListener    { if (isSplitMode) setActivePane(false) }
        bottomTitleBar.setOnClickListener { if (isSplitMode) setActivePane(true) }
    }

    private fun searchImages() {
        val activeWv = activeWebView()
        val fromUrl = extractSearchQuery(activeWv.url ?: "")
        val fromBar = urlInput.text.toString().trim()
        val fromTitle = activeWv.title?.trim() ?: ""
        val query = when {
            fromUrl.isNotBlank()                             -> fromUrl
            fromBar.isNotBlank() && !fromBar.contains(".") -> fromBar
            fromTitle.isNotBlank()                           -> fromTitle
            else                                             -> return
        }
        navigateTo(DDG_IMAGE_BASE + URLEncoder.encode(query, "UTF-8"), activeWv)
    }

    private fun extractSearchQuery(url: String): String {
        return try {
            android.net.Uri.parse(url).getQueryParameter("q") ?: ""
        } catch (e: Exception) { "" }
    }

    private fun readThemeColor() {
        webView.evaluateJavascript(
            "(function(){ var m = document.querySelector('meta[name=theme-color]'); return m ? m.getAttribute('content') : ''; })()"
        ) { value ->
            val raw = value?.trim('"') ?: ""
            runOnUiThread {
                try {
                    chromeBg.setBackgroundColor(if (raw.startsWith("#")) Color.parseColor(raw) else Color.parseColor("#0f0f0f"))
                } catch (e: Exception) {
                    chromeBg.setBackgroundColor(Color.parseColor("#0f0f0f"))
                }
            }
        }
    }

    private fun renderTabStrip() {
        tabStripInner.removeAllViews()
        tabCountBtn.text = tabManager.getTabs().size.toString()
        for (tab in tabManager.getTabs()) {
            val pill = TextView(this).apply {
                text = when {
                    tab.url == HOME        -> "home"
                    tab.title.isNotBlank() -> tab.title.take(16)
                    else                   -> domainFrom(tab.url)
                }
                textSize = 11f
                setTextColor(Color.parseColor("#c8bfaf"))
                setPadding(24, 12, 24, 12)
                background = if (tab.id == tabManager.activeTabId)
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
        val atLimit = tabManager.getTabs().size >= 4
        val addBtn = TextView(this).apply {
            text = if (atLimit) "4/4" else "+"
            textSize = if (atLimit) 10f else 16f
            setTextColor(Color.parseColor(if (atLimit) "#c0392b" else "#c8bfaf"))
            setPadding(24, 12, 24, 12)
            background = getDrawable(R.drawable.bg_tab_pill)
            setOnClickListener {
                if (atLimit) Toast.makeText(this@MainActivity, "Tab limit reached — close a tab first", Toast.LENGTH_SHORT).show()
                else openNewTab()
            }
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
        for (tab in tabManager.getTabs()) {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                background = getDrawable(R.drawable.bg_tab_card)
                setPadding(16, 12, 16, 12)
                gravity = android.view.Gravity.CENTER_VERTICAL
            }
            tab.thumbnail?.let { thumb ->
                card.addView(android.widget.ImageView(this).apply {
                    setImageBitmap(thumb)
                    layoutParams = LinearLayout.LayoutParams(120, 80).apply { marginEnd = 12 }
                    scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                })
            }
            card.addView(TextView(this).apply {
                text = when {
                    tab.url == HOME        -> "marrow home"
                    tab.title.isNotBlank() -> tab.title
                    else                   -> tab.url
                }
                textSize = 12f
                setTextColor(Color.parseColor("#f0ead6"))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            })
            card.addView(TextView(this).apply {
                text = "✕"
                textSize = 14f
                setTextColor(Color.parseColor("#c8bfaf"))
                setPadding(12, 0, 0, 0)
                setOnClickListener { closeTab(tab.id) }
            })
            card.setOnClickListener { switchToTab(tab.id) }
            list.addView(card, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = 8 })
        }
        scroll.addView(list)
        tabOverlay.addView(scroll)
    }

    private fun openNewTab() {
        captureActivePane()
        val result = tabManager.openTab(HOME)
        if (result.oldestClosed) Toast.makeText(this, "Oldest tab closed", Toast.LENGTH_SHORT).show()
        activeWebView().loadUrl(HOME)
        renderTabStrip()
        tabOverlay.visibility = View.GONE
    }

    private fun switchToTab(id: Int) {
        captureActivePane()
        val prev = activeWebView()
        prev.loadUrl("about:blank")
        prev.clearCache(false)
        val tab = tabManager.switchToTab(id) ?: return
        val target = activeWebView()
        target.loadUrl(tab.url)
        urlInput.setText(domainFrom(tab.url))
        renderTabStrip()
        tabOverlay.visibility = View.GONE
    }

    private fun closeTab(id: Int) {
        val next = tabManager.closeTab(id)
        val target = activeWebView()
        if (next != null) {
            target.loadUrl(next.url)
            urlInput.setText(domainFrom(next.url))
        } else {
            target.loadUrl(HOME)
            urlInput.setText("marrow")
        }
        renderTabStrip()
        tabOverlay.visibility = View.GONE
    }

    private fun activeWebView(): WebView =
        if (isSplitMode && splitPaneActive) splitWebView else webView

    private fun captureActivePane() {
        val wv = activeWebView()
        try {
            val bmp = Bitmap.createBitmap(
                wv.width.takeIf { it > 0 } ?: 1,
                wv.height.takeIf { it > 0 } ?: 1,
                Bitmap.Config.ARGB_8888
            )
            val canvas = android.graphics.Canvas(bmp)
            wv.draw(canvas)
            tabManager.updateActiveThumbnail(bmp)
        } catch (e: Exception) { }
    }

    private fun domainFrom(url: String): String {
        if (url == HOME) return "marrow"
        return try {
            android.net.Uri.parse(url).host?.removePrefix("www.") ?: url
        } catch (e: Exception) { url }
    }
}
