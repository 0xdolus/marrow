package com.marrow.browser

import android.graphics.Bitmap

data class Tab(
    val id: Int,
    var url: String,
    var title: String,
    var isFullMode: Boolean = false,
    var isSuspended: Boolean = false,
    var thumbnail: Bitmap? = null
)

class TabManager {

    private val tabs = mutableListOf<Tab>()
    private var nextId = 0

    var activeTabId: Int = -1
        private set

    companion object {
        const val MAX_TABS = 4
    }

    // Returns the new tab, plus whether the oldest was auto-closed to make room
    data class OpenResult(val tab: Tab, val oldestClosed: Boolean)

    fun openTab(url: String): OpenResult {
        val oldestClosed = tabs.size >= MAX_TABS
        if (oldestClosed) tabs.removeAt(0)
        val tab = Tab(id = nextId++, url = url, title = url)
        tabs.add(tab)
        activeTabId = tab.id
        return OpenResult(tab, oldestClosed)
    }

    fun getActiveTab(): Tab? = tabs.find { it.id == activeTabId }

    fun getTabs(): List<Tab> = tabs.toList()

    fun getTab(id: Int): Tab? = tabs.find { it.id == id }

    fun switchToTab(id: Int): Tab? {
        val tab = tabs.find { it.id == id } ?: return null
        activeTabId = id
        return tab
    }

    fun updateActiveUrl(url: String) { getActiveTab()?.url = url }

    fun updateActiveTitle(title: String) { getActiveTab()?.title = title }

    fun updateActiveThumbnail(bmp: Bitmap?) { getActiveTab()?.thumbnail = bmp }

    fun setActiveSuspended(suspended: Boolean) { getActiveTab()?.isSuspended = suspended }
}
