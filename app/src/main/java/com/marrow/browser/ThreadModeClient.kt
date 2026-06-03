package com.marrow.browser

import android.graphics.Bitmap
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.TextView
import java.io.ByteArrayInputStream

class ThreadModeClient(
    private val urlText: TextView,
    private val onJsRequested: (domain: String) -> Unit
) : WebViewClient() {

    var isFullMode = false

    // Set by MainActivity after construction — called on UI thread when page finishes loading
    var onPageLoaded: ((url: String) -> Unit)? = null

    private val jsAllowedAlways = mutableSetOf<String>()
    private val jsAllowedOnce   = mutableSetOf<String>()
    private val jsPending       = mutableSetOf<String>()

    fun loadAllowedAlways(domains: Set<String>) { jsAllowedAlways.addAll(domains) }

    fun allowAlways(domain: String) {
        jsAllowedAlways.add(domain)
        jsPending.remove(domain)
    }

    fun allowOnce(domain: String) {
        jsAllowedOnce.add(domain)
        jsPending.remove(domain)
    }

    fun denyJs(domain: String) { jsPending.remove(domain) }

    private fun isJsAllowed(domain: String) =
        jsAllowedAlways.contains(domain) || jsAllowedOnce.contains(domain)

    override fun onPageFinished(view: WebView, url: String) {
        urlText.text = url
        onPageLoaded?.invoke(url)
    }

    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        // Clear once-allowed set and pending set on each new navigation
        jsAllowedOnce.clear()
        jsPending.clear()
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
