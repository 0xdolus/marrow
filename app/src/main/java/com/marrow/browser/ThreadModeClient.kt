package com.marrow.browser

import android.graphics.Bitmap
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient

class ThreadModeClient(
    private val onJsRequested: (domain: String) -> Unit = {}
) : WebViewClient() {

    var onPageLoaded: ((url: String) -> Unit)? = null
    var onCloudflareDetected: ((domain: String) -> Unit)? = null

    override fun shouldOverrideUrlLoading(
        view: WebView,
        request: WebResourceRequest
    ): Boolean = false

    override fun onPageFinished(view: WebView, url: String) {
        onPageLoaded?.invoke(url)
    }

    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        // no-op — kept for future use
    }

    // Stubs kept so MainActivity compiles without changes
    fun allowAlways(domain: String) {}
    fun allowOnce(domain: String) {}
    fun blockAllJsForPage() {}
    fun setCfBypass(domain: String) {}
    fun loadAllowedAlways(domains: Set<String>) {}
}
