<div align="center">

<img src="https://readme-typing-svg.demolab.com?font=JetBrains+Mono&weight=700&size=42&pause=1200&color=4ADE80&center=true&vCenter=true&width=435&lines=marrow;the+essential+part" alt="marrow" />

**A minimal, fast Android browser built for personal use.**
No bloat. No third-party libraries. Just pages, tabs, and a split screen that actually works.

![Platform](https://img.shields.io/badge/platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)
![SDK](https://img.shields.io/badge/SDK-35-4ADE80?style=for-the-badge)
![Dependencies](https://img.shields.io/badge/dependencies-0-111111?style=for-the-badge)
![Status](https://img.shields.io/badge/status-active%20dev-F5A623?style=for-the-badge)

</div>

---

## 🔥 Highlights

- ✂️ **Split screen** with two fully independent WebViews
- ⚡ **Local homepage** that loads instantly, no network needed
- 🔎 **30 search engines** on the homepage, 9 in the native picker
- 🕶️ **Privacy mode** that wipes history, cache and form data
- 🗂️ **Tabs** with thumbnails and a visual switcher
- 🖼️ **Built-in image viewer** for local images
- 📦 **Pure Kotlin + Android WebView**, zero dependencies

---

## ✂️ Split screen

```
┌──────────────────────┐
│  🟢 Top pane          │  ← tabs go here when green is active
│                      │
├──────── ▬▬▬ ─────────┤  ← drag to resize · double-tap = 50/50
│  🔵 Bottom pane       │  ← tabs go here when blue is active
│                      │
└──────────────────────┘
```

- Two independent WebViews, top and bottom
- Drag the divider to resize, double-tap to reset to 50/50
- Shared tabs route to whichever pane is active (green = top, blue = bottom)
- The back button respects the active pane
- Dedicated exit-split bar at the bottom

---

## 🔎 Search

Marrow has two separate engine lists.

| | Engines | Default |
|---|---|---|
| **Homepage dropdown** | 30 | Google (saved in `localStorage`) |
| **Native URL bar picker** | 9 | Brave Search (saved in SharedPreferences) |

<details>
<summary><b>Homepage engines (30)</b></summary>

Google, Bing, Yahoo, Yandex, DuckDuckGo, Brave, YouTube, Gibiru, Perplexity, You.com, Startpage, Baidu, Kagi, Ecosia, Qwant, Naver, Swisscows, Reddit, Andi, Phind, Mojeek, MetaGer, Wikipedia, Gigablast, Presearch, Searx, Dogpile, Google Scholar, Wolfram Alpha, AOL Search

</details>

<details>
<summary><b>Native picker engines (9)</b></summary>

Google, DuckDuckGo, Brave Search, Perplexity, Bing, Kagi, Startpage, Ecosia, Qwant

</details>

Image search uses whichever native engine is currently selected.

---

## 🗂️ Tabs

- Up to **4 tabs**; the oldest closes when you hit the limit
- A separate system tab handles popups and redirects
- Tab popup to switch, close or open a new tab
- Long-press the tab-count button for a new tab
- Tab thumbnails captured from the active pane
- Visual tab switcher

---

## 🏠 Homepage

- Local asset (`file:///android_asset/home.html`), so it opens instantly offline
- Search bar with an engine dropdown
- Small status pip in the corner

---

## 🕶️ Privacy mode

- One toggle clears history, cache and form data
- Disables geolocation and switches the cache mode
- Clears cookies when the app is destroyed
- The pip turns **blue** while privacy mode is on

---

## 🖼️ Image viewer

Long-press the split button (or the exit-split button) to open the system image picker.

- Pick multiple images at once
- Previous / next with a counter
- Pinch to zoom, plus zoom controls
- Images load as base64 data URLs inside a local HTML wrapper

---

## 🧰 Everything else

- Fullscreen video support
- File chooser for `<input type="file">` on web pages
- Cookie persistence, flushed on pause
- Popup blocklist for common ad and tracker domains (doubleclick, taboola, etc.)
- Video and player URLs get promoted out of popup windows
- Reads the page's theme-color meta tag
- Google Safe Browsing enabled
- Update check against GitHub releases
- Download listener with cookie support
- Browser bar auto-hides on scroll
- Landscape and rotation support

---

## 🛠️ Building

**Requirements**

- Android Studio
- JDK 17
- Android SDK 35

```bash
git clone https://github.com/0xdolus/marrow.git
cd marrow
./gradlew assembleDebug
```

📦 APK output: `app/build/outputs/apk/debug/app-debug.apk`

---

## 📘 Stack

- **Kotlin**
- **Android WebView**
- **No third-party libraries**

---

## 🚧 Status

Personal project, under active development. Expect rough edges.

---

<div align="center">

*marrow — the essential part.*

</div>
