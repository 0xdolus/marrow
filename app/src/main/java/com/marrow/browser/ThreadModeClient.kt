package com.marrow.browser

import android.graphics.Bitmap
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream

class ThreadModeClient(
    private val onJsRequested: (domain: String) -> Unit
) : WebViewClient() {

    var isFullMode = false

    // Called on the UI thread when a page finishes loading — set by MainActivity
    var onPageLoaded: ((url: String) -> Unit)? = null

    private val jsAllowedAlways = mutableSetOf<String>()
    private val jsAllowedOnce   = mutableSetOf<String>()
    private val jsPending       = mutableSetOf<String>()

    // When true, all JS requests are silently blocked — no more banners this page
    private var jsBlockedForPage = false

    fun loadAllowedAlways(domains: Set<String>) { jsAllowedAlways.addAll(domains) }

    fun allowAlways(domain: String) {
        jsAllowedAlways.add(domain)
        jsPending.remove(domain)
    }

    fun allowOnce(domain: String) {
        jsAllowedOnce.add(domain)
        jsPending.remove(domain)
    }

    /** User chose Keep off — silence all further JS banners for this page load. */
    fun blockAllJsForPage() {
        jsBlockedForPage = true
        jsPending.clear()
    }

    private fun isJsAllowed(domain: String) =
        jsAllowedAlways.contains(domain) || jsAllowedOnce.contains(domain)

    override fun onPageFinished(view: WebView, url: String) {
        // URL bar update and all other side effects handled by onPageLoaded in MainActivity
        onPageLoaded?.invoke(url)
    }

    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        // Reset all per-page JS state on each new navigation
        jsAllowedOnce.clear()
        jsPending.clear()
        jsBlockedForPage = false
    }

    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest
    ): WebResourceResponse? {
        if (isFullMode) return null

        val url    = request.url.toString()
        val domain = request.url.host ?: return null

        if (isImageUrl(url) || isFontUrl(url)) return emptyResponse()

        if (isJsUrl(url)) {
            // User already said Keep off for this page — block silently, no banner
            if (jsBlockedForPage) return emptyResponse()
            if (isJsAllowed(domain)) return null
            if (!jsPending.contains(domain)) {
                jsPending.add(domain)
                view.post { onJsRequested(domain) }
            }
            return emptyResponse()
        }

        return null
    }

    private fun isImageUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains(".jpg")  || lower.contains(".jpeg") ||
               lower.contains(".png")  || lower.contains(".gif")  ||
               lower.contains(".webp") || lower.contains(".svg")  ||
               lower.contains(".ico")  || lower.contains(".avif") ||
               lower.contains("image/")
    }

    private fun isFontUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains(".woff")  || lower.contains(".woff2") ||
               lower.contains(".ttf")   || lower.contains(".otf")   ||
               lower.contains(".eot")   ||
               lower.contains("fonts.googleapis.com") ||
               lower.contains("fonts.gstatic.com")
    }

    private fun isJsUrl(url: String): Boolean {
        val lower = url.lowercase()
        return (lower.contains(".js?") || lower.endsWith(".js")) &&
               !lower.contains(".json")
    }

    private fun emptyResponse() = WebResourceResponse(
        "text/plain", "utf-8", ByteArrayInputStream(ByteArray(0))
    )
}
