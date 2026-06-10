package com.marrow.browser

import android.graphics.Bitmap
import android.net.http.SslError
import android.webkit.SslErrorHandler
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient

class ThreadModeClient : WebViewClient() {

    var onPageLoaded: ((url: String) -> Unit)? = null

    companion object {
        // Schemes that must never be handed to a third-party app or executed inline.
        // "file" is also blocked to prevent local-file exfiltration via web content.
        private val BLOCKED_SCHEMES = setOf("intent", "market", "javascript", "file")
    }

    // ── URL interception ───────────────────────────────────────────────────────
    override fun shouldOverrideUrlLoading(
        view: WebView,
        request: WebResourceRequest
    ): Boolean {
        val scheme = request.url.scheme?.lowercase() ?: return false
        // Silently drop dangerous schemes; returning true means "we handled it"
        // (i.e. block it) — the WebView will not load the URL.
        if (scheme in BLOCKED_SCHEMES) return true
        return false
    }

    // ── SSL errors: cancel, never proceed blindly ──────────────────────────────
    override fun onReceivedSslError(
        view: WebView,
        handler: SslErrorHandler,
        error: SslError
    ) {
        // Cancelling causes the WebView to show its built-in SSL error page,
        // which is far safer than calling handler.proceed() on a bad certificate.
        handler.cancel()
    }

    // ── Page lifecycle ─────────────────────────────────────────────────────────
    override fun onPageFinished(view: WebView, url: String) {
        onPageLoaded?.invoke(url)
    }

    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        // no-op — kept for future use
    }
}
