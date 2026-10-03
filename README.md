<div align="center">

<img src="https://readme-typing-svg.demolab.com?font=JetBrains+Mono&weight=700&size=42&pause=1200&color=FF4D8D&center=true&vCenter=true&width=435&lines=marrow;the+essential+part" alt="marrow" />

**A minimal, fast Android browser built for personal use.**
No ads. No tracking. No bloat. Just pages, tabs, and a split screen that actually works.

![Platform](https://img.shields.io/badge/platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)
![SDK](https://img.shields.io/badge/SDK-35-FF4D8D?style=for-the-badge)
![Dependencies](https://img.shields.io/badge/dependencies-0-1a1a2e?style=for-the-badge)
![Status](https://img.shields.io/badge/status-active%20dev-F5A623?style=for-the-badge)

</div>

---

## 🔥 Features

| | Feature | Details |
|---|---|---|
| ✂️ | **Split screen** | Two independent panes. Drag the divider to resize, double-tap to reset to 50/50 |
| 🗂️ | **Shared tabs** | Tabs route to the active pane: 🟢 green = top, 🔵 blue = bottom |
| ⚡ | **Local homepage** | Instant-load start page with a search bar, no network request needed |
| 📊 | **Memory monitor** | A pip dot in the corner shows memory pressure: 🟢 / 🟡 / 🔴 |
| 🖼️ | **Tab thumbnails** | Visual tab switcher with page previews |
| 🔎 | **Image search** | One tap to search images from the current query |
| ⬅️ | **Smart back** | Back button respects the active pane in split mode |
| 🦆 | **DDG default** | DuckDuckGo HTML search, unrestricted in all modes |

---

## 🧩 How split mode works

```
┌──────────────────────┐
│  🟢 Pane A (top)      │  ← tabs routed here when green is active
│                      │
├──────── ▬▬▬ ─────────┤  ← drag to resize · double-tap = 50/50
│  🔵 Pane B (bottom)   │  ← tabs routed here when blue is active
│                      │
└──────────────────────┘
```

<!-- 🎞️ Record a short screen capture of split mode, save as docs/demo.gif, then uncomment:
<p align="center"><img src="docs/demo.gif" width="280" alt="marrow demo" /></p>
-->

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
- **Zero third-party dependencies**

---

## 🚧 Status

Personal project, under active development. Expect rough edges.

---

<div align="center">

*marrow — the essential part.*


</div>
