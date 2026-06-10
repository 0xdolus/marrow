package com.marrow.browser

import android.graphics.Bitmap

data class Tab(
    val id: Int,
    var url: String,
    var title: String,
    var isFullMode: Boolean = false,
    var isSuspended: Boolean = false,
    var thumbnail: Bitmap? = null,
    val isRedirectTab: Boolean = false          // system-managed; excluded from MAX_TABS
)

class TabManager {

    private val tabs = mutableListOf<Tab>()
    private var nextId = 0

    var activeTabId: Int = -1
        private set

    companion object {
        const val MAX_TABS = 4
    }

    data class OpenResult(val tab: Tab, val oldestClosed: Boolean)

    // ── User tabs (excludes the redirect tab) ─────────────────────────────────
    fun getUserTabs(): List<Tab> = tabs.filter { !it.isRedirectTab }

    // ── Open a new user tab; only user tabs count against MAX_TABS ─────────────
    fun openTab(url: String): OpenResult {
        val userTabs = getUserTabs()
        val oldestClosed = userTabs.size >= MAX_TABS
        if (oldestClosed) {
            val oldest = userTabs.first()
            tabs.remove(oldest)
        }
        val tab = Tab(id = nextId++, url = url, title = url)
        tabs.add(tab)
        activeTabId = tab.id
        return OpenResult(tab, oldestClosed)
    }

    // ── Redirect tab: create once, reuse forever ───────────────────────────────
    fun openOrGetRedirectTab(url: String): Tab {
        val existing = getRedirectTab()
        if (existing != null) {
            existing.url = url
            existing.title = "⇄ redirect"
            activeTabId = existing.id
            return existing
        }
        val tab = Tab(
            id = nextId++,
            url = url,
            title = "⇄ redirect",
            isRedirectTab = true
        )
        tabs.add(tab)
        activeTabId = tab.id
        return tab
    }

    fun getRedirectTab(): Tab? = tabs.find { it.isRedirectTab }

    fun closeRedirectTab() {
        val redirectTab = getRedirectTab() ?: return
        closeTab(redirectTab.id)
    }

    fun updateRedirectTabUrl(url: String) {
        getRedirectTab()?.url = url
    }

    // ── Close a tab ────────────────────────────────────────────────────────────
    /**
     * Close a tab by id. Fallback prefers a user tab over the redirect tab.
     * Returns the new active tab, or null if no tabs remain.
     */
    fun closeTab(id: Int): Tab? {
        val index = tabs.indexOfFirst { it.id == id }
        if (index == -1) return null
        tabs.removeAt(index)
        if (tabs.isEmpty()) {
            activeTabId = -1
            return null
        }
        if (activeTabId == id) {
            // Prefer a user tab as the fallback — never auto-activate redirect tab
            val userTabs = getUserTabs()
            val fallback = if (userTabs.isNotEmpty()) {
                val adjustedIndex = index.coerceAtMost(userTabs.size - 1)
                userTabs[adjustedIndex]
            } else {
                tabs[minOf(index, tabs.size - 1)]
            }
            activeTabId = fallback.id
        }
        return getActiveTab()
    }

    // ── Accessors ──────────────────────────────────────────────────────────────
    fun getActiveTab(): Tab? = tabs.find { it.id == activeTabId }

    fun getTabs(): List<Tab> = tabs.toList()

    fun getTab(id: Int): Tab? = tabs.find { it.id == id }

    fun switchToTab(id: Int): Tab? {
        val tab = tabs.find { it.id == id } ?: return null
        activeTabId = id
        return tab
    }

    fun updateActiveUrl(url: String)           { getActiveTab()?.url       = url     }
    fun updateActiveTitle(title: String)       { getActiveTab()?.title     = title   }
    fun updateActiveThumbnail(bmp: Bitmap?)    { getActiveTab()?.thumbnail = bmp     }
    fun setActiveSuspended(suspended: Boolean) { getActiveTab()?.isSuspended = suspended }
}
