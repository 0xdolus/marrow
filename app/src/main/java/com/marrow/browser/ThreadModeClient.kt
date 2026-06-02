package com.marrow.browser

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream

open class ThreadModeClient : WebViewClient() {

    var isFullMode = false

    override fun shouldInterceptRequest(
        view: WebView,
        request: WebResourceRequest
    ): WebResourceResponse? {
        if (isFullMode) return null

        val url = request.url.toString()

        if (isImageUrl(url) || isFontUrl(url)) {
            return emptyResponse()
        }

        return null
    }

    private fun isImageUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains(".jpg")  ||
               lower.contains(".jpeg") ||
               lower.contains(".png")  ||
               lower.contains(".gif")  ||
               lower.contains(".webp") ||
               lower.contains(".svg")  ||
               lower.contains(".ico")  ||
               lower.contains(".avif") ||
               lower.contains("image/")
    }

    private fun isFontUrl(url: String): Boolean {
        val lower = url.lowercase()
        return lower.contains(".woff")                ||
               lower.contains(".woff2")               ||
               lower.contains(".ttf")                 ||
               lower.contains(".otf")                 ||
               lower.contains(".eot")                 ||
               lower.contains("fonts.googleapis.com") ||
               lower.contains("fonts.gstatic.com")
    }

    private fun emptyResponse(): WebResourceResponse {
        return WebResourceResponse(
            "text/plain",
            "utf-8",
            ByteArrayInputStream(ByteArray(0))
        )
    }
}
