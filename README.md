<div align="center">

<img src="https://readme-typing-svg.demolab.com?font=JetBrains+Mono&weight=700&size=42&pause=1200&color=4ADE80&center=true&vCenter=true&width=435&lines=marrow;the+essential+part" alt="marrow" />

<strong>A minimal, fast Android browser built for people who like the internet without the circus.</strong>

<br>

No bloat. No third-party libraries. Just pages, tabs, and a split screen that actually behaves.

<br><br>

![Platform](https://img.shields.io/badge/platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)
![SDK](https://img.shields.io/badge/SDK-35-4ADE80?style=for-the-badge)
![Dependencies](https://img.shields.io/badge/dependencies-0-111111?style=for-the-badge)
![Status](https://img.shields.io/badge/status-active%20dev-F5A623?style=for-the-badge)

</div>

---

## 🔥 What is marrow?

`marrow` is a deliberately small Android browser built around a simple idea: the web should be fast and usable without turning your phone into a billboard.

It uses a native Android UI with `WebView` under the hood, keeps the dependency graph at zero, and focuses on the features that matter: split browsing, local-first tools, privacy controls, and tab management that stays out of your way.

---

## ✨ Highlights

<table>
<tr>
<td width="50%">

### ✂️ Split screen

- Two independent `WebView` instances
- Resizable panes
- 50/50 snap
- Active-pane routing
- Pane-aware back navigation
- Dedicated exit control

</td>
<td width="50%">

### 🗂️ Tabs

- Up to **4 tabs**
- Visual tab overview
- Tab thumbnails
- Popup / redirect handling
- Long-press tab creation
- Automatic oldest-tab eviction

</td>
</tr>

<tr>
<td>

### 🔎 Search

- **30 homepage engines**
- **9 native picker engines**
- Independent defaults
- Local preference storage
- Image search follows the native engine

</td>
<td>

### 🕶️ Privacy

- Browsing-data wipe
- Geolocation disabled
- Restrictive cache mode
- Cookie cleanup on exit
- Privacy status indicator

</td>
</tr>

<tr>
<td>

### 🖼️ Image viewer

- Multi-select image import
- Previous / next navigation
- Image counter
- Pinch-to-zoom
- UI zoom controls
- Base64 image support

</td>
<td>

### ⚡ Local-first

- Offline homepage
- Local HTML assets
- No remote homepage dependency
- Lightweight startup
- Fewer unnecessary requests

</td>
</tr>
</table>

---

## ✂️ Split screen

```text
┌──────────────────────┐
│  🟢 Top pane          │
│                      │
├──────── ▬▬▬ ─────────┤
│  🔵 Bottom pane       │
│                      │
└──────────────────────┘
```

- Two independent WebViews, top and bottom
- Drag the divider to resize the panes
- Double-tap the divider to snap back to 50/50
- Tabs route to the currently active pane (green = top, blue = bottom)
- Back respects the active pane
- A dedicated exit-split bar sits at the bottom

---

## 🔎 Search engines

Marrow ships with two separate engine lists for different surfaces.

<table>
<tr>
<th>Surface</th>
<th>Engines</th>
<th>Default</th>
</tr>
<tr>
<td><b>Homepage dropdown</b></td>
<td>30</td>
<td>Google</td>
</tr>
<tr>
<td><b>Native URL bar picker</b></td>
<td>9</td>
<td>Brave Search</td>
</tr>
</table>

<details>
<summary><b>🌐 Homepage engines · 30</b></summary>
<br>

Google · Bing · Yahoo · Yandex · DuckDuckGo · Brave · YouTube · Gibiru · Perplexity · You.com · Startpage · Baidu · Kagi · Ecosia · Qwant · Naver · Swisscows · Reddit · Andi · Phind · Mojeek · MetaGer · Wikipedia · Gigablast · Presearch · Searx · Dogpile · Google Scholar · Wolfram Alpha · AOL Search

Default: Google · Storage: `localStorage`

</details>

<details>
<summary><b>📱 Native picker engines · 9</b></summary>
<br>

Google · DuckDuckGo · Brave Search · Perplexity · Bing · Kagi · Startpage · Ecosia · Qwant

Default: Brave Search · Storage: `SharedPreferences`

</details>

<details>
<summary><b>🖼️ Image search</b></summary>
<br>

Image search uses whichever native engine is currently selected.

</details>

---

## 🗂️ Tabs and browsing flow

- Up to **4 tabs**; the oldest is closed when you hit the limit
- A separate system tab handles popups and redirects
- Tab popup lets you switch, close, or open a new tab
- Long-press the tab counter to open a new tab instantly
- Thumbnails are captured from the active pane
- Visual tab switcher

---

## 🏠 Homepage

The homepage is a local asset:

```
file:///android_asset/home.html
```

- Instant loading, no network dependency
- Search bar with an engine dropdown
- A small status pip in the corner

---

## 🕶️ Privacy mode

<details>
<summary><b>🔐 What it changes</b></summary>
<br>

- One toggle clears history, cache, and form data
- Geolocation is disabled
- Cache handling switches to a more restrictive mode
- Cookies are cleared when the app is destroyed
- The status pip turns blue while privacy mode is active

</details>

---

## 🖼️ Image viewer

Long-press the split button or the exit-split button to open the system image picker.

- Multi-select image import
- Previous / next navigation with a counter
- Pinch-to-zoom and UI zoom controls
- Images render as base64 inside a local HTML wrapper

---

## 🧰 Under the hood

<details>
<summary><b>⚙️ Browser internals</b></summary>
<br>

- Fullscreen video support
- `<input type="file">` uploads via the native file chooser
- Cookie persistence with flush on pause
- Popup blocklist for known ad/tracker domains (doubleclick, taboola, etc.)
- Popup URL promotion for video and player streams
- Reads the page's theme-color meta tag
- Google Safe Browsing enabled
- GitHub release-based update checks
- Download listener with cookie support
- Browser bar auto-hides on scroll
- Landscape and rotation support

</details>

---

## 🛠️ Build

**Requirements:** Android Studio · JDK 17 · Android SDK 35

```bash
git clone https://github.com/0xdolus/marrow.git
cd marrow
./gradlew assembleDebug
```

📦 APK output: `app/build/outputs/apk/debug/app-debug.apk`

---

## 📘 Stack

<div align="center">

Kotlin · Android WebView · Android SDK 35

0 third-party dependencies

</div>

---

## 🚧 Status

A personal project under active development. It works, it's useful, and it has rough edges. Expect improvements, and fewer features than a megacorp browser.

---

<div align="center">

**marrow**

*the essential part.*

</div>
