<div align="center">

<img src="https://readme-typing-svg.demolab.com?font=JetBrains+Mono&weight=700&size=42&pause=1200&color=4ADE80&center=true&vCenter=true&width=435&lines=marrow;the+essential+part" alt="marrow" />

<strong>A minimal, fast Android browser built for people who like the internet without the circus.</strong>

No ads. No tracking. No bloat. Just pages, tabs, and a split screen that actually behaves.

![Platform](https://img.shields.io/badge/platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)
![SDK](https://img.shields.io/badge/SDK-35-4ADE80?style=for-the-badge)
![Dependencies](https://img.shields.io/badge/dependencies-0-111111?style=for-the-badge)
![Status](https://img.shields.io/badge/status-active%20dev-F5A623?style=for-the-badge)

</div>

---

## ⚙️ What is marrow?

`marrow` is a deliberately small Android browser built around a simple idea: the web should be fast, private, and usable without turning your phone into a billboard.

It uses a native Android UI with `WebView` under the hood, keeps the dependency graph at zero, and focuses on the features that matter: split browsing, local-first tools, privacy controls, and tab management that doesn't feel like a spreadsheet with feelings.

---

## ✨ Highlights

- ✂️ **Dual-pane split screen** with two independent `WebView` instances
- ⚡ **Offline-first homepage** served from local assets for instant load times
- 🔎 **30 homepage search engines** and **9 native picker engines**
- 🕶️ **Privacy mode** that wipes browsing data and disables sensitive APIs
- 🗂️ **Tab system** with thumbnails, popup switching, and a visual tab overview
- 🖼️ **Built-in image viewer** for local images and base64-backed previews
- 📦 **Pure Kotlin + Android WebView** with zero third-party dependencies
- 🧠 **Behavioral tuning** like pane-aware back navigation and split-screen UX details

---

## ✂️ Split screen architecture

```
┌──────────────────────┐
│  🟢 Top pane          │  ← routing target when green is active
│                      │
├──────── ▬▬▬ ─────────┤  ← drag to resize · double-tap = 50/50
│  🔵 Bottom pane       │  ← routing target when blue is active
│                      │
└──────────────────────┘
```

- Two independent `WebView` instances, top and bottom
- Drag the divider to resize the panes
- Double-tap the split handle to snap back to a 50/50 layout
- Tabs route to the currently active pane
- Back behavior respects the active pane instead of randomly doing whatever it wants
- A dedicated exit-split control sits at the bottom for quick escape

---

## 🔎 Search engine system

Marrow ships with two separate engine lists for different surfaces.

| Surface | Engines | Default |
|---|---:|---|
| Homepage dropdown | 30 | Google (`localStorage`) |
| Native URL bar picker | 9 | Brave Search (`SharedPreferences`) |

<details>
<summary><b>Homepage engines (30)</b></summary>

Google, Bing, Yahoo, Yandex, DuckDuckGo, Brave, YouTube, Gibiru, Perplexity, You.com, Startpage, Baidu, Kagi, Ecosia, Qwant, Naver, Swisscows, Reddit, Andi, Phind, Mojeek, MetaGer, Wikipedia, Gigablast, Presearch, Searx, Dogpile, Google Scholar, Wolfram Alpha, AOL Search

</details>

<details>
<summary><b>Native picker engines (9)</b></summary>

Google, DuckDuckGo, Brave Search, Perplexity, Bing, Kagi, Startpage, Ecosia, Qwant

</details>

Image search follows the currently selected native engine. Because apparently the browser should be opinionated, but not chaotic.

---

## 🗂️ Tabs and browsing flow

- Up to **4 tabs** before the oldest one gets evicted like an old file in a temp folder
- Separate system tab handling for popups and redirect traffic
- Tab popup lets you switch, close, or create a new tab
- Long-press the tab counter to open a new tab immediately
- Tab previews are captured from the active pane
- Visual tab switcher keeps things readable instead of “why is this list so ancient?”

---

## 🏠 Homepage and local-first UX

Marrow includes a local asset homepage at `file:///android_asset/home.html`, which means:

- instant loading with no network dependency
- a fast search entry point
- a lightweight status pip in the corner
- fewer pointless network requests before the browser even starts doing browser things

---

## 🕶️ Privacy controls

- One toggle clears history, cache, and form data
- Geolocation is disabled in privacy mode
- Cache handling is switched to a more restrictive mode
- Cookies are cleared when the app is destroyed
- The status pip turns **blue** while privacy mode is active

This is not a privacy theater product. It is a browser that actually tries to behave.

---

## 🖼️ Image viewer

Long-press the split button (or the exit-split button) to open the system image picker.

- Multi-select image import
- Previous / next navigation with a counter
- Pinch-to-zoom and UI zoom controls
- Base64 image payloads rendered inside a local HTML wrapper

It is basically the browser equivalent of “yeah, I have a gallery, but make it useful.”

---

## 🧰 Under the hood

- Fullscreen video support
- `<input type="file">` uploads via native file chooser
- Cookie persistence with flush-on-pause behavior
- Ad/tracker popup blocklist covering known bad actors like `doubleclick` and `taboola`
- Popup URL promotion for video and player streams
- Reads the page's `theme-color` meta tag for a cleaner UI feel
- Google Safe Browsing enabled
- GitHub release-based update checks
- Download listener with cookie-aware support
- Browser bar auto-hides on scroll
- Landscape and rotation responsiveness

---

## 🛠️ Build

### Requirements

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

This is a personal project under active development. That means it works, it is useful, and it may occasionally remind you that software is still a living thing.

Expect rough edges. Expect improvements. Expect fewer features than a megacorp browser—and a lot more sanity.

---

<div align="center">

<sub>marrow — the essential part.</sub>

</div>
