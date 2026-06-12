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
    private lateinit var bottomChrome: LinearLayout
    private lateinit var webView: WebView
    private lateinit var urlInput: EditText
    private lateinit var tabCountBtn: TextView
    private lateinit var tabOverlay: FrameLayout
    private lateinit var loadingBar: ProgressBar
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

    // ── UI Fullscreen (long-press URL bar) ───────────────────────

    // ── Fullscreen video ─────────────────────────────────────────
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private lateinit var fullscreenContainer: FrameLayout

    // ── Tab + memory ─────────────────────────────────────────────
    private lateinit var tabManager: TabManager
    private lateinit var threadClient: ThreadModeClient

    // ── Popup redirect system ─────────────────────────────────────
    // dummyWebView is never attached to the layout; it is used purely
    // to receive and inspect window.open() navigation chains.
    private var dummyWebView: WebView? = null

    // ── Privacy mode ─────────────────────────────────────────────
    // Toggle with a long-press on the tab-count button.
    // When active: no-cache, no history, cleared on exit.
    private var privacyModeActive = false
    private var selectedEngine = "Google" // overwritten in onCreate from prefs
    private val GITHUB_RELEASES = "https://api.github.com/repos/0xdolus/marrow/releases/latest"


    companion object {
        const val HOME              = "file:///android_asset/home.html"
        val SCROLL_SCRIPT = """
(function() {
  if (window.__scrollBridgeInstalled) return;
  window.__scrollBridgeInstalled = true;
  var lastY = 0, accDown = 0, accUp = 0, hidden = false;
  window.addEventListener('scroll', function() {
    var y = window.scrollY;
    var delta = y - lastY;
    lastY = y;
    if (delta > 0) { accDown += delta; accUp = 0; }
    else           { accUp += Math.abs(delta); accDown = 0; }
      hidden = true; accDown = 0;
      ScrollBridge.onScrollDirectionChanged('down');
    } else if (hidden && (accUp > 50 || y === 0)) {
      hidden = false; accUp = 0;
      ScrollBridge.onScrollDirectionChanged('up');
    }
  }, { passive: true });
})();
""".trimIndent()
        const val DDG_BASE          = "https://yandex.com/search/?text="

        val SEARCH_ENGINES = mapOf(
            "Google"       to "https://www.google.com/search?q=",
            "DuckDuckGo"   to "https://duckduckgo.com/?q=",
            "Brave Search" to "https://search.brave.com/search?q=",
            "Perplexity"   to "https://www.perplexity.ai/search?q=",
            "Bing"         to "https://www.bing.com/search?q=",
            "Kagi"         to "https://kagi.com/search?q=",
            "Startpage"    to "https://www.startpage.com/search?q=",
            "Ecosia"       to "https://www.ecosia.org/search?q=",
            "Qwant"        to "https://www.qwant.com/?q="
        )


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

        bindViews()
        setupTabManager()
        setupMainWebView()
        setupSplitWebView()
        setupDividerDrag()
        setupUrlBar()
        setupButtons()
        checkForUpdate()
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
        webView.destroy()
        splitWebView.destroy()
        dummyWebView?.destroy()
        dummyWebView = null
        super.onDestroy()
    }

    // ════════════════════════════════════════════════════════════
    // State save/restore — survive process kill (scenario B)
    // ════════════════════════════════════════════════════════════
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val tab = tabManager.getActiveTab()
        if (tab != null && tab.url.isNotBlank() && tab.url != HOME) {
            outState.putString("active_url", tab.url)
            outState.putString("active_title", tab.title)
        }
    }

    override fun onRestoreInstanceState(savedState: Bundle) {
        super.onRestoreInstanceState(savedState)
        val url = savedState.getString("active_url") ?: return
        val title = savedState.getString("active_title") ?: ""
        tabManager.updateActiveUrl(url)
        tabManager.updateActiveTitle(title)
        webView.loadUrl(url)
        urlInput.setText(title.takeIf { it.isNotBlank() } ?: url)
        // Without this call the tab strip keeps showing the stale HOME tab
        // created by setupTabManager() rather than the restored tab's title/URL.
        renderTabStrip()
    }

    // ════════════════════════════════════════════════════════════
    // View binding
    // ════════════════════════════════════════════════════════════
    private fun bindViews() {
        chromeBg            = findViewById(R.id.chromeBg)
        bottomChrome        = findViewById(R.id.bottomChrome)
        webView             = findViewById(R.id.webView)
        urlInput            = findViewById(R.id.urlInput)
        tabCountBtn         = findViewById(R.id.tabCountBtn)
        tabOverlay          = findViewById(R.id.tabOverlay)
        loadingBar          = findViewById(R.id.loadingBar)
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
        fullscreenContainer = findViewById(R.id.fullscreenContainer)
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

    private fun loadEnginePreference() {
        val prefs = getSharedPreferences("marrow_prefs", MODE_PRIVATE)
        selectedEngine = prefs.getString("selected_engine", "Brave Search") ?: "Brave Search"
    }

    // ════════════════════════════════════════════════════════════
    // Memory monitor
    // ════════════════════════════════════════════════════════════


    override fun onResume() { super.onResume() }
    override fun onPause()  { super.onPause() }

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
            }
        }

        webView.webViewClient = threadClient
        webView.addJavascriptInterface(ScrollBridge(), "ScrollBridge")
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

        webView.setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
            if (urlInput.hasFocus()) return@setOnScrollChangeListener
        }
        webView.setOnTouchListener { _, _ ->
            if (isSplitMode && splitPaneActive) setActivePane(false)
            false
        }

        setupDownloadListener()
    }

    // ════════════════════════════════════════════════════════════
    // Chrome show / hide helpers
    // ════════════════════════════════════════════════════════════






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

        // Attach to hidden container so WebViewTransport works correctly
        val dummyContainer = findViewById<FrameLayout>(R.id.dummyWebViewContainer)
        dummyContainer.removeAllViews()
        dummyContainer.addView(dummy)

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
                        dummyContainer.removeAllViews()
                        navigateTo(url, webView)
                        renderTabStrip()
                    }
                    return true
                }

                // First real URL — now open the redirect tab
                runOnUiThread {
                    tabManager.openOrGetRedirectTab(url)
                    renderTabStrip()
                }
                return false
            }
        }

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
                    showChrome()
                }
                view?.evaluateJavascript(SCROLL_SCRIPT, null)
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
            setSupportMultipleWindows(true)
            blockNetworkImage                = false
            loadsImagesAutomatically         = true
            domStorageEnabled                = true

            userAgentString = userAgentString.replace("; wv", "")

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

    // ── Chrome auto-hide ─────────────────────────────────────────
    private var chromeHidden = false

    private fun hideChrome() {
        if (chromeHidden) return
        chromeHidden = true
        bottomChrome.animate()
            .translationY(bottomChrome.height.toFloat())
            .setDuration(200).start()
    }

    fun showChrome() {
        if (!chromeHidden) return
        chromeHidden = false
        bottomChrome.animate()
            .translationY(0f)
            .setDuration(200).start()
    }

    inner class ScrollBridge {
        @android.webkit.JavascriptInterface
        fun onScrollDirectionChanged(direction: String) {
            runOnUiThread {
                if (direction == "down") hideChrome() else showChrome()
            }
        }
    }
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
            "Privacy mode ON"
        else
            "Privacy mode OFF"
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        // Reload active page so new settings take effect immediately
        activeWebView().reload()
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
        topTitleBar.visibility = View.GONE
        bottomTitleBar.visibility = View.GONE

        snapToEqual()

        splitWebView.loadUrl(HOME)

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
            topTitleBar.visibility = View.GONE
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
            if (hasFocus) showChrome()
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
                val title = if (isSplitMode && splitPaneActive)
                    splitWebView.title?.takeIf { it.isNotBlank() }
                else
                    webView.title?.takeIf { it.isNotBlank() }
                urlInput.setText(if (url == HOME) "marrow" else (title ?: ""))
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

        urlInput.setOnLongClickListener {
            togglePrivacyMode()
            true
        }
    }

    private fun navigateTo(url: String, target: WebView) {
        target.loadUrl(url)
    }

    private fun normalizeUrl(input: String): String {
        if (input.startsWith("http://") || input.startsWith("https://")) return input
        // Intentionally NOT passing file:// through — loadUrl() bypasses the
        // shouldOverrideUrlLoading() file-scheme block, so we treat it as a
        // plain search term instead to prevent local file disclosure.
        val tldRegex = Regex("^[^\\s.]+\\.[a-zA-Z]{2,}(\\.[a-zA-Z]{2,})?(/.*)?$")
        if (!input.contains(" ") && tldRegex.matches(input)) return "https://$input"
        val base = SEARCH_ENGINES[selectedEngine] ?: DDG_BASE
        return base + URLEncoder.encode(input, "UTF-8")
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
                tabCountBtn.setOnLongClickListener {
            openNewTab()
            true
        }
        bottomChrome.setOnLongClickListener {
            tabManager.getActiveTab()?.let { closeTab(it.id) }
            true
        }
        var chromSwipeStartX = 0f
        bottomChrome.setOnTouchListener { _, ev ->
            when (ev.action) {
                android.view.MotionEvent.ACTION_DOWN -> { chromSwipeStartX = ev.x; false }
                android.view.MotionEvent.ACTION_UP -> {
                    val diff = chromSwipeStartX - ev.x
                    val userTabs = tabManager.getUserTabs()
                    val currentIndex = userTabs.indexOfFirst { it.id == tabManager.activeTabId }
                    when {
                        diff > 150 && currentIndex < userTabs.size - 1 ->
                            { switchToTab(userTabs[currentIndex + 1].id); true }
                        diff < -150 && currentIndex > 0 ->
                            { switchToTab(userTabs[currentIndex - 1].id); true }
                        else -> false
                    }
                }
                else -> false
            }
        }
        splitBtn.setOnClickListener     { enterSplitMode() }
        exitSplitBtn.setOnClickListener { exitSplitMode() }

        topTitleBar.setOnClickListener    { if (isSplitMode) setActivePane(false) }
        bottomTitleBar.setOnClickListener { if (isSplitMode) setActivePane(true) }
    }

    // ════════════════════════════════════════════════════════════
    // Image search
    // ════════════════════════════════════════════════════════════
    private fun searchImages() {
        val activeUrl = activeWebView().url ?: ""
        if (activeUrl == HOME) {
            activeWebView().evaluateJavascript("document.getElementById('q').value") { value ->
                val query = value?.trim('"') ?: ""
                if (query.isNotBlank()) {
                    runOnUiThread {
                        val base = SEARCH_ENGINES[selectedEngine] ?: return@runOnUiThread
                        navigateTo(base + URLEncoder.encode(query, "UTF-8"), activeWebView())
                    }
                }
            }
            return
        }
        val query = extractSearchQuery(activeUrl).ifBlank {
            urlInput.text.toString().trim()
        }
        if (query.isBlank()) return
        val base = SEARCH_ENGINES[selectedEngine] ?: return
        navigateTo(base + URLEncoder.encode(query, "UTF-8"), activeWebView())
    }

    private fun checkForUpdate() {
        Thread {
            try {
                val url = java.net.URL(GITHUB_RELEASES)
                val json = url.readText()
                val tag = Regex("\"tag_name\":\\s*\"v?([0-9.]+)\"").find(json)?.groupValues?.get(1) ?: return@Thread
                val remote = tag.replace(".", "").trimStart('0').toIntOrNull() ?: return@Thread
                val local = packageManager.getPackageInfo(packageName, 0).versionCode
                if (remote > local) runOnUiThread {
                    android.app.AlertDialog.Builder(this)
                        .setTitle("Update available")
                        .setMessage("Version $tag is available on GitHub.")
                        .setPositiveButton("View") { _, _ ->
                            navigateTo("https://github.com/0xdolus/marrow/releases/latest", activeWebView())
                        }
                        .setNegativeButton("Later", null)
                        .show()
                }
            } catch (_: Exception) {}
        }.start()
    }

    private fun showEnginePicker() {
        val engines = SEARCH_ENGINES.keys.toList()

        val dialog = android.app.Dialog(this, android.R.style.Theme_Black_NoTitleBar)

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#161616"))
            val r = (16 * resources.displayMetrics.density).toInt()
            background = android.graphics.drawable.GradientDrawable().also {
                it.setColor(Color.parseColor("#161616"))
                it.cornerRadius = r.toFloat()
            }
        }

        val scroll = ScrollView(this).apply {
            isScrollbarFadingEnabled = true
        }
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        engines.forEachIndexed { index, name ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                val pad = (11 * resources.displayMetrics.density).toInt()
                val padH = (16 * resources.displayMetrics.density).toInt()
                setPadding(padH, pad, padH, pad)
                setBackgroundColor(if (name == selectedEngine) Color.parseColor("#1a221a") else Color.TRANSPARENT)
                isClickable = true
                isFocusable = true
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    foreground = android.graphics.drawable.RippleDrawable(
                        android.content.res.ColorStateList.valueOf(Color.parseColor("#2a2a2a")),
                        null, null
                    )
                }
            }
            val label = TextView(this).apply {
                text = name
                textSize = 13.5f
                setTextColor(if (name == selectedEngine) Color.parseColor("#e8e8e8") else Color.parseColor("#c8c8c8"))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            val dot = View(this).apply {
                val size = (6 * resources.displayMetrics.density).toInt()
                layoutParams = LinearLayout.LayoutParams(size, size)
                background = android.graphics.drawable.GradientDrawable().also {
                    it.shape = android.graphics.drawable.GradientDrawable.OVAL
                    it.setColor(Color.parseColor("#4a8c4a"))
                }
                visibility = if (name == selectedEngine) View.VISIBLE else View.GONE
            }
            row.addView(label)
            row.addView(dot)

            if (index > 0) {
                val divider = View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        (1 * resources.displayMetrics.density).toInt()
                    )
                    setBackgroundColor(Color.parseColor("#1f1f1f"))
                }
                list.addView(divider)
            }

            row.setOnClickListener {
                selectedEngine = name
                getSharedPreferences("marrow_prefs", MODE_PRIVATE)
                    .edit().putString("selected_engine", name).apply()
                dialog.dismiss()
            }
            list.addView(row)
        }

        scroll.addView(list)
        container.addView(scroll)

        dialog.setContentView(container)
        dialog.window?.apply {
            setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            val dm = resources.displayMetrics
            val width = (dm.widthPixels * 0.72).toInt()
            val maxH = (220 * dm.density).toInt()
            setLayout(width, android.view.WindowManager.LayoutParams.WRAP_CONTENT)
            scroll.layoutParams = FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT, maxH
            )
            val attr = attributes
            attr.gravity = android.view.Gravity.TOP or android.view.Gravity.START
            attributes = attr
        }
        dialog.show()
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
        tabCountBtn.text = tabManager.getUserTabs().size.toString()
    }


    // ════════════════════════════════════════════════════════════
    // Tab actions
    // ════════════════════════════════════════════════════════════
    private fun openNewTab() {
        showChrome()
        captureActivePane()
        val result = tabManager.openTab(HOME)
        if (result.oldestClosed) {
            Toast.makeText(this, "Tab limit reached — oldest tab closed", Toast.LENGTH_SHORT).show()
        }
        // Tabs always belong to the main webView, not the split pane.
        webView.loadUrl(HOME)
        renderTabStrip()
    }

    private fun switchToTab(id: Int) {
        captureActivePane()
        val tab = tabManager.switchToTab(id) ?: return
        // Tabs always belong to the main webView; activeWebView() would wrongly
        // target splitWebView when the bottom pane is focused.
        webView.loadUrl(tab.url)
        urlInput.setText(if (tab.url == HOME) "marrow" else tab.title.takeIf { it.isNotBlank() } ?: "")
        renderTabStrip()
    }

    private fun closeTab(id: Int) {
        if (tabManager.getRedirectTab()?.id == id) {
            dummyWebView?.destroy()
            dummyWebView = null
        }
        val next = tabManager.closeTab(id)
        val target = activeWebView()
        if (next != null && !next.isRedirectTab) {
            target.loadUrl(next.url)
            urlInput.setText(if (next.url == HOME) "marrow" else next.title.takeIf { it.isNotBlank() } ?: "")
        } else if (tabManager.getUserTabs().isEmpty()) {
            openNewTab()
        }
        renderTabStrip()
    }

    // ════════════════════════════════════════════════════════════
    // Helpers
    // ════════════════════════════════════════════════════════════
    private fun activeWebView(): WebView =
        if (isSplitMode && splitPaneActive) splitWebView else webView

    private fun captureActivePane() {
        val wv = activeWebView()
        val w = wv.width.takeIf  { it > 0 } ?: return
        val h = wv.height.takeIf { it > 0 } ?: return
        // Draw and save the thumbnail entirely on the UI thread so the bitmap
        // is fully populated before it is handed to the tab manager.
        runOnUiThread {
            try {
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                val canvas = android.graphics.Canvas(bmp)
                wv.draw(canvas)
                tabManager.updateActiveThumbnail(bmp)
            } catch (e: Exception) {
                // Skip thumbnail if capture fails
            }
        }
    }

    private fun domainFrom(url: String): String {
        if (url == HOME) return "marrow"
        return try {
            android.net.Uri.parse(url).host?.removePrefix("www.") ?: url
        } catch (e: Exception) { url }
    }
}


