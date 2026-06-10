package com.marrow.browser

import android.app.DownloadManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.os.Message
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.net.URLEncoder

class MainActivity : AppCompatActivity() {

    // ── Core views ──────────────────────────────────────────────
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

    // ── Split views ──────────────────────────────────────────────
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

    // Draggable divider
    private var dividerDragStartY = 0f
    private var topWeightAtDragStart = 1f
    private var bottomWeightAtDragStart = 1f

    // ── Fullscreen video ─────────────────────────────────────────
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private lateinit var fullscreenContainer: FrameLayout

    // ── Tab + memory ─────────────────────────────────────────────
    private lateinit var tabManager: TabManager
    private lateinit var memoryMonitor: MemoryMonitor
    private lateinit var threadClient: ThreadModeClient

    // ── Popup redirect system ─────────────────────────────────────
    // dummyWebView is never attached to the layout; it is used purely
    // to receive and inspect window.open() navigation chains.
    private var dummyWebView: WebView? = null

    // ── Privacy mode ─────────────────────────────────────────────
    // Toggle with a long-press on the tab-count button.
    // When active: no-cache, no history, cleared on exit.
    private var privacyModeActive = false

    companion object {
        const val HOME              = "file:///android_asset/home.html"
        const val DDG_BASE          = "https://yandex.com/search/?text="
        const val DDG_IMAGE_BASE    = "https://yandex.com/images/search?text="
        const val COLOR_TOP_PANE    = "#5a9a5a"
        const val COLOR_BOTTOM_PANE = "#8ab8d8"
        const val COLOR_PRIVACY     = "#4a7fbf"   // blue pip when privacy mode on

        // Video file extensions and known player/embed domains
        private val VIDEO_EXTENSIONS = listOf(".mp4", ".m3u8", ".webm", ".mkv", ".ts", ".flv")
        private val VIDEO_DOMAINS    = listOf(
            "streamtape.com", "doodstream.com", "vidcloud", "streamlare.com",
            "fembed.com", "upstream.to", "mixdrop.co", "filemoon.sx",
            "embedsito.com", "voe.sx", "vidsrc", "embedrise.com",
            "vidhide.com", "streamwish.com", "bestx.stream", "swdyu.com"
        )

        // Domains known to be ad networks / trackers — popups to these are dropped
        private val AD_DOMAINS = listOf(
            "doubleclick.net", "googlesyndication.com", "adservice.google",
            "adnxs.com", "amazon-adsystem.com", "popads.net", "popcash.net",
            "propellerads.com", "taboola.com", "outbrain.com", "revcontent.com",
            "trafficjunky.com", "exoclick.com", "juicyads.com", "trafficforce.com",
            "adsterra.com", "hilltopads.net", "plugrush.com", "tsyndicate.com"
        )

        // Regex for safe hex color values from theme-color meta tags
        private val HEX_COLOR_RE = Regex("^#[0-9a-fA-F]{3}([0-9a-fA-F]{3})?$")
    }

    // ════════════════════════════════════════════════════════════
    // onCreate
    // ════════════════════════════════════════════════════════════
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        fullscreenContainer = FrameLayout(this).apply {
            visibility = View.GONE
        }
        addContentView(
            fullscreenContainer,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

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

    // ════════════════════════════════════════════════════════════
    // onDestroy — privacy cleanup
    // ════════════════════════════════════════════════════════════
    override fun onDestroy() {
        if (privacyModeActive) {
            webView.clearCache(true)
            webView.clearFormData()
            webView.clearHistory()
            splitWebView.clearCache(true)
            splitWebView.clearFormData()
            splitWebView.clearHistory()
            CookieManager.getInstance().removeAllCookies(null)
            WebStorage.getInstance().deleteAllData()
        }
        dummyWebView?.destroy()
        dummyWebView = null
        super.onDestroy()
    }

    // ════════════════════════════════════════════════════════════
    // View binding
    // ════════════════════════════════════════════════════════════
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

    // ════════════════════════════════════════════════════════════
    // Tab manager
    // ════════════════════════════════════════════════════════════
    private fun setupTabManager() {
        tabManager = TabManager()
        tabManager.openTab(HOME)
        renderTabStrip()
    }

    // ════════════════════════════════════════════════════════════
    // Memory monitor
    // ════════════════════════════════════════════════════════════
    private fun setupMemoryMonitor() {
        memoryMonitor = MemoryMonitor(this) { level ->
            runOnUiThread { updatePip(level) }
        }
    }

    private fun updatePip(level: MemoryMonitor.Level) {
        // Privacy mode overrides the pip color to blue
        if (privacyModeActive) {
            pipDot.background.setTint(Color.parseColor(COLOR_PRIVACY))
            memBanner.visibility = View.GONE
            return
        }
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

    // ════════════════════════════════════════════════════════════
    // Main WebView setup
    // ════════════════════════════════════════════════════════════
    private fun setupMainWebView() {
        threadClient = ThreadModeClient()

        threadClient.onPageLoaded = { url ->
            runOnUiThread {
                if (privacyModeActive) webView.clearHistory()
                if (!splitPaneActive) urlInput.setText(if (url == HOME) "marrow" else (webView.title?.takeIf { it.isNotBlank() } ?: ""))
                val title = webView.title ?: ""
                tabManager.updateActiveTitle(title)
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

            // ── Popup / redirect tab handling ──────────────────────────
            override fun onCreateWindow(
                view: WebView,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message
            ): Boolean {
                handlePopupWindow(resultMsg)
                return true
            }

            // ── Fullscreen video ───────────────────────────────────────
            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                customView = view
                customViewCallback = callback
                fullscreenContainer.addView(view)
                fullscreenContainer.visibility = View.VISIBLE
                window.decorView.systemUiVisibility =
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            }

            override fun onHideCustomView() {
                fullscreenContainer.removeAllViews()
                fullscreenContainer.visibility = View.GONE
                window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
                customViewCallback?.onCustomViewHidden()
                customView = null
                customViewCallback = null
            }
        }

        applyFullSettings(webView)
        topModeRow.visibility = View.GONE

        webView.setOnTouchListener { _, _ ->
            if (isSplitMode && splitPaneActive) setActivePane(false)
            false
        }

        setupDownloadListener()
    }

    // ════════════════════════════════════════════════════════════
    // Download listener
    // ════════════════════════════════════════════════════════════
    private fun setupDownloadListener() {
        webView.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            try {
                val cookies = CookieManager.getInstance().getCookie(url) ?: ""
                val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)

                val request = DownloadManager.Request(android.net.Uri.parse(url)).apply {
                    setMimeType(mimeType)
                    if (cookies.isNotBlank()) addRequestHeader("Cookie", cookies)
                    addRequestHeader("User-Agent", userAgent)
                    setTitle(fileName)
                    setDescription("Downloading…")
                    setNotificationVisibility(
                        DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                    )
                    setDestinationInExternalPublicDir(
                        android.os.Environment.DIRECTORY_DOWNLOADS,
                        fileName
                    )
                }

                val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                dm.enqueue(request)
                Toast.makeText(this, "Download started: $fileName", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Download failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ════════════════════════════════════════════════════════════
    // Popup / redirect tab system
    // ════════════════════════════════════════════════════════════
    /**
     * Called when a page calls window.open(). We route the popup through a
     * detached dummy WebView, inspect each URL it navigates to, and either:
     *   • promote it to the main WebView if it looks like a video/player URL
     *   • drop it silently if it matches a known ad/tracker domain
     *   • otherwise track it in the visible redirect tab so the user can see
     *     where the chain leads and tap into it if they want
     */
    private fun handlePopupWindow(resultMsg: Message) {
        // Tear down any previous dummy
        dummyWebView?.destroy()

        val dummy = WebView(this).also { dummyWebView = it }
        applyFullSettings(dummy)

        dummy.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val url    = request.url.toString()
                val scheme = request.url.scheme?.lowercase() ?: return true

                // Hard-block dangerous schemes
                if (scheme == "intent" || scheme == "market") return true

                // Drop ad/tracker URLs silently
                if (isAdUrl(url)) return true

                // Promote video URLs to the main WebView
                if (isVideoUrl(url)) {
                    runOnUiThread {
                        tabManager.closeRedirectTab()
                        dummyWebView?.destroy()
                        dummyWebView = null
                        navigateTo(url, webView)
                        renderTabStrip()
                    }
                    return true
                }

                // Otherwise: update the redirect tab to reflect the latest destination
                runOnUiThread {
                    tabManager.updateRedirectTabUrl(url)
                    renderTabStrip()
                }
                return false
            }
        }

        // Create (or reuse) the redirect tab so it's visible in the strip
        tabManager.openOrGetRedirectTab("about:blank")
        renderTabStrip()

        // Hand the dummy WebView to the site as its popup container
        val transport = resultMsg.obj as? WebView.WebViewTransport
        transport?.webView = dummy
        resultMsg.sendToTarget()
    }

    private fun isVideoUrl(url: String): Boolean {
        val lower = url.lowercase()
        return VIDEO_EXTENSIONS.any { lower.contains(it) } ||
               VIDEO_DOMAINS.any    { lower.contains(it) }
    }

    private fun isAdUrl(url: String): Boolean {
        val lower = url.lowercase()
        return AD_DOMAINS.any { lower.contains(it) }
    }

    // ════════════════════════════════════════════════════════════
    // Split WebView setup
    // ════════════════════════════════════════════════════════════
    private fun setupSplitWebView() {
        applyFullSettings(splitWebView)

        splitWebView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val scheme = request?.url?.scheme?.lowercase() ?: return false
                if (scheme == "intent" || scheme == "market" ||
                    scheme == "javascript" || scheme == "file") return true
                return false
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                if (url == null) return
                if (privacyModeActive) view?.clearHistory()
                runOnUiThread {
                    if (splitPaneActive) urlInput.setText("")
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

            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                customView = view
                customViewCallback = callback
                fullscreenContainer.addView(view)
                fullscreenContainer.visibility = View.VISIBLE
                window.decorView.systemUiVisibility =
                    View.SYSTEM_UI_FLAG_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            }

            override fun onHideCustomView() {
                fullscreenContainer.removeAllViews()
                fullscreenContainer.visibility = View.GONE
                window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
                customViewCallback?.onCustomViewHidden()
                customView = null
                customViewCallback = null
            }
        }

        splitWebView.setOnTouchListener { _, _ ->
            if (isSplitMode && !splitPaneActive) setActivePane(true)
            false
        }
    }

    // ════════════════════════════════════════════════════════════
    // Draggable divider
    // ════════════════════════════════════════════════════════════
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

    // ════════════════════════════════════════════════════════════
    // WebView settings — applied to every WebView instance
    // ════════════════════════════════════════════════════════════
    private fun applyFullSettings(wv: WebView) {
        wv.settings.apply {
            javaScriptEnabled                = true
            blockNetworkImage                = false
            loadsImagesAutomatically         = true
            domStorageEnabled                = true

            // ── Security fixes ──────────────────────────────────
            mediaPlaybackRequiresUserGesture = true   // prevent autoplaying media
            @Suppress("DEPRECATION")
            setSafeBrowsingEnabled(true)              // Google Safe Browsing
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW

            // ── Privacy mode adjustments ─────────────────────────
            cacheMode = if (privacyModeActive)
                WebSettings.LOAD_NO_CACHE
            else
                WebSettings.LOAD_DEFAULT
            if (privacyModeActive) setGeolocationEnabled(false)
        }
    }

    // ════════════════════════════════════════════════════════════
    // Privacy mode toggle
    // ════════════════════════════════════════════════════════════
    private fun togglePrivacyMode() {
        privacyModeActive = !privacyModeActive
        applyFullSettings(webView)
        applyFullSettings(splitWebView)
        // Immediately clear history/cache when turning on
        if (privacyModeActive) {
            webView.clearHistory()
            webView.clearCache(true)
            webView.clearFormData()
        }
        val msg = if (privacyModeActive)
            "🔒 Privacy mode ON — no history or cache"
        else
            "Privacy mode OFF"
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        // Refresh pip dot appearance
        memoryMonitor.start()
    }

    // ════════════════════════════════════════════════════════════
    // Split mode
    // ════════════════════════════════════════════════════════════
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

        if (privacyModeActive) {
            splitWebView.clearCache(true)
            splitWebView.clearFormData()
            splitWebView.clearHistory()
        }

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

    // ════════════════════════════════════════════════════════════
    // Active pane
    // ════════════════════════════════════════════════════════════
    private fun setActivePane(bottom: Boolean) {
        splitPaneActive = bottom

        if (!isSplitMode) {
            urlInput.setText(tabManager.getActiveTab()?.title?.takeIf { it.isNotBlank() } ?: "")
            updatePaneIndicator(false)
            return
        }

        updatePaneIndicator(bottom)

        if (bottom) {
            urlInput.setText("")
            splitDivider.setBackgroundColor(Color.parseColor(COLOR_BOTTOM_PANE))
            topModeRow.visibility      = View.GONE
            topTitleBar.visibility     = View.VISIBLE
            splitDimOverlay.visibility = View.VISIBLE
            topTitleBar.setBackgroundColor(Color.parseColor("#1a1a1a"))
            topTitleBar.setTextColor(Color.parseColor("#555555"))
            bottomTitleBar.setBackgroundColor(Color.parseColor(COLOR_BOTTOM_PANE))
            bottomTitleBar.setTextColor(Color.parseColor("#0f0f0f"))
        } else {
            urlInput.setText(tabManager.getActiveTab()?.title?.takeIf { it.isNotBlank() } ?: "")
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

    // ════════════════════════════════════════════════════════════
    // URL bar
    // ════════════════════════════════════════════════════════════
    private fun setupUrlBar() {
        urlInput.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                val realUrl = if (isSplitMode && splitPaneActive)
                    splitWebView.url ?: ""
                else
                    tabManager.getActiveTab()?.url ?: ""
                if (realUrl != HOME) urlInput.setText(realUrl)
                else urlInput.setText("")
                urlInput.selectAll()
            } else {
                val url = if (isSplitMode && splitPaneActive)
                    splitWebView.url ?: ""
                else
                    tabManager.getActiveTab()?.url ?: ""
                urlInput.setText(if (url == HOME) "marrow" else (webView.title?.takeIf { it.isNotBlank() } ?: ""))
            }
        }

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

    // ════════════════════════════════════════════════════════════
    // Back navigation
    // ════════════════════════════════════════════════════════════
    override fun onBackPressed() {
        if (customView != null) {
            webView.webChromeClient?.onHideCustomView()
            return
        }
        val active = activeWebView()
        if (active.canGoBack()) {
            active.goBack()
        } else {
            super.onBackPressed()
        }
    }

    // ════════════════════════════════════════════════════════════
    // Buttons
    // ════════════════════════════════════════════════════════════
    private fun setupButtons() {
        tabCountBtn.setOnClickListener {
            if (tabOverlay.visibility == View.VISIBLE) {
                tabOverlay.visibility = View.GONE
            } else {
                renderTabOverlay()
                tabOverlay.visibility = View.VISIBLE
            }
        }
        // Long-press tab count button → toggle privacy mode
        tabCountBtn.setOnLongClickListener {
            togglePrivacyMode()
            true
        }

        imgSearchBtn.setOnClickListener { searchImages() }
        splitBtn.setOnClickListener     { enterSplitMode() }
        exitSplitBtn.setOnClickListener { exitSplitMode() }

        topTitleBar.setOnClickListener    { if (isSplitMode) setActivePane(false) }
        bottomTitleBar.setOnClickListener { if (isSplitMode) setActivePane(true) }
    }

    // ════════════════════════════════════════════════════════════
    // Image search
    // ════════════════════════════════════════════════════════════
    private fun searchImages() {
        val current = urlInput.text.toString()
        val query = extractSearchQuery(current).ifBlank { current }
        if (query.isBlank()) return
        val target = activeWebView()
        navigateTo(DDG_IMAGE_BASE + URLEncoder.encode(query, "UTF-8"), target)
    }

    private fun extractSearchQuery(url: String): String {
        return try {
            val uri = android.net.Uri.parse(url)
            uri.getQueryParameter("text") ?: uri.getQueryParameter("q") ?: ""
        } catch (e: Exception) { "" }
    }

    // ════════════════════════════════════════════════════════════
    // Theme color — sanitised to prevent injection via meta tag
    // ════════════════════════════════════════════════════════════
    private fun readThemeColor() {
        webView.evaluateJavascript(
            "(function(){ var m = document.querySelector('meta[name=theme-color]'); " +
            "return m ? m.getAttribute('content') : ''; })()"
        ) { value ->
            val raw = value?.trim('"') ?: ""
            runOnUiThread {
                val safeColor = if (HEX_COLOR_RE.matches(raw)) {
                    try { Color.parseColor(raw) } catch (e: Exception) { Color.parseColor("#0f0f0f") }
                } else {
                    Color.parseColor("#0f0f0f")
                }
                chromeBg.setBackgroundColor(safeColor)
            }
        }
    }

    // ════════════════════════════════════════════════════════════
    // Tab strip + overlay
    // ════════════════════════════════════════════════════════════
    private fun renderTabStrip() {
        tabStripInner.removeAllViews()
        val userTabs    = tabManager.getUserTabs()
        val redirectTab = tabManager.getRedirectTab()
        val displayTabs = tabManager.getTabs()   // includes redirect tab if present

        // Tab count shows only user tabs
        tabCountBtn.text = userTabs.size.toString()

        for (tab in displayTabs) {
            val label = when {
                tab.isRedirectTab      -> "⇄"
                tab.url == HOME        -> "home"
                tab.title.isNotBlank() -> tab.title.take(16)
                else                   -> domainFrom(tab.url)
            }
            val pill = TextView(this).apply {
                text = label
                textSize = 11f
                setTextColor(
                    if (tab.isRedirectTab) Color.parseColor("#c8a840")
                    else                   Color.parseColor("#c8bfaf")
                )
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

        val atLimit = userTabs.size >= TabManager.MAX_TABS
        val addBtn = TextView(this).apply {
            text = if (atLimit) "4/4" else "+"
            textSize = if (atLimit) 10f else 16f
            setTextColor(Color.parseColor(if (atLimit) "#c0392b" else "#c8bfaf"))
            setPadding(24, 12, 24, 12)
            background = getDrawable(R.drawable.bg_tab_pill)
            setOnClickListener {
                if (atLimit) {
                    Toast.makeText(
                        this@MainActivity,
                        "Tab limit reached — close a tab first",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    openNewTab()
                }
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
                    tab.isRedirectTab      -> "⇄ redirect  —  ${tab.url.take(40)}"
                    tab.url == HOME        -> "marrow home"
                    tab.title.isNotBlank() -> tab.title
                    else                   -> tab.url
                }
                textSize = 12f
                setTextColor(
                    if (tab.isRedirectTab) Color.parseColor("#c8a840")
                    else                   Color.parseColor("#f0ead6")
                )
                layoutParams = LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                )
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

    // ════════════════════════════════════════════════════════════
    // Tab actions
    // ════════════════════════════════════════════════════════════
    private fun openNewTab() {
        captureActivePane()
        val result = tabManager.openTab(HOME)
        if (result.oldestClosed) {
            Toast.makeText(this, "Oldest tab closed", Toast.LENGTH_SHORT).show()
        }
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
        urlInput.setText(if (tab.url == HOME) "marrow" else tab.title.takeIf { it.isNotBlank() } ?: "")
        renderTabStrip()
        tabOverlay.visibility = View.GONE
    }

    private fun closeTab(id: Int) {
        // If closing the redirect tab, also destroy the dummy WebView
        if (tabManager.getRedirectTab()?.id == id) {
            dummyWebView?.destroy()
            dummyWebView = null
        }
        if (privacyModeActive) {
            webView.clearCache(true)
            webView.clearFormData()
        }
        val next = tabManager.closeTab(id)
        val target = activeWebView()
        if (next != null) {
            target.loadUrl(next.url)
            urlInput.setText(if (next.url == HOME) "marrow" else next.title.takeIf { it.isNotBlank() } ?: "")
        } else {
            target.loadUrl(HOME)
            urlInput.setText("marrow")
        }
        renderTabStrip()
        tabOverlay.visibility = View.GONE
    }

    // ════════════════════════════════════════════════════════════
    // Helpers
    // ════════════════════════════════════════════════════════════
    private fun activeWebView(): WebView =
        if (isSplitMode && splitPaneActive) splitWebView else webView

    private fun captureActivePane() {
        val wv = activeWebView()
        try {
            val bmp = Bitmap.createBitmap(
                wv.width.takeIf  { it > 0 } ?: 1,
                wv.height.takeIf { it > 0 } ?: 1,
                Bitmap.Config.ARGB_8888
            )
            val canvas = android.graphics.Canvas(bmp)
            wv.draw(canvas)
            tabManager.updateActiveThumbnail(bmp)
        } catch (e: Exception) {
            // Skip thumbnail if capture fails
        }
    }

    private fun domainFrom(url: String): String {
        if (url == HOME) return "marrow"
        return try {
            android.net.Uri.parse(url).host?.removePrefix("www.") ?: url
        } catch (e: Exception) { url }
    }
}
