package com.marrow.browser

import android.graphics.Bitmap
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream

class ThreadModeClient(
    private val onJsRequested: (domain: String) -> Unit
) : WebViewClient() {

    var isFullMode = false

    var onPageLoaded: ((url: String) -> Unit)? = null
    var onCloudflareDetected: ((domain: String) -> Unit)? = null

    private val jsAllowedAlways = mutableSetOf<String>()
    private val jsAllowedOnce   = mutableSetOf<String>()
    private val jsPending       = mutableSetOf<String>()

    private var jsBlockedForPage = false
    private var cfBypassDomain: String? = null

    fun loadAllowedAlways(domains: Set<String>) { jsAllowedAlways.addAll(domains) }

    fun allowAlways(domain: String) {
        jsAllowedAlways.add(domain)
        jsPending.remove(domain)
    }

    fun allowOnce(domain: String) {
        jsAllowedOnce.add(domain)
        jsPending.remove(domain)
    }

    fun blockAllJsForPage() {
        jsBlockedForPage = true
        jsPending.clear()
    }

    fun setCfBypass(domain: String) {
        cfBypassDomain = domain
    }

    private fun isJsAllowed(domain: String) =
        jsAllowedAlways.contains(domain) || jsAllowedOnce.contains(domain)

    override fun shouldOverrideUrlLoading(
        view: WebView,
        request: WebResourceRequest
    ): Boolean {
        // Always allow navigation away from the local homepage
        val url = request.url.toString()
        if (url.startsWith("file://")) return false
        return false
    }

    override fun onPageFinished(view: WebView, url: String) {
        val title = view.title ?: ""

        if (!isFullMode && title == "Just a moment...") {
            val domain = try { Uri.parse(url).host ?: url } catch (e: Exception) { url }
            view.post { onCloudflareDetected?.invoke(domain) }
        } else {
            cfBypassDomain = null
        }

        onPageLoaded?.invoke(url)
    }

    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        // Don't reset JS state when navigating from the homepage
        if (url.startsWith("file://")) return
        jsAllowedOnce.clear()
        jsPending.clear()
        jsBlockedForPage = false
    }

    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest
    ): WebResourceResponse? {
        if (isFullMode) return null

        val url = request.url.toString()

        // Never intercept local file requests — homepage needs JS to navigate
        if (url.startsWith("file://")) return null

        val domain = request.url.host ?: return null

        if (isImageUrl(url) || isFontUrl(url)) return emptyResponse()

        if (isJsUrl(url)) {
            if (jsBlockedForPage) return emptyResponse()
            if (isJsAllowed(domain) || domain == cfBypassDomain) return null
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
