# marrow

A minimal, fast Android browser built for personal use.

## what it is

Marrow is a stripped-down browser that stays out of your way. No ads, no tracking, no bloat. Just pages, tabs, and a split screen that actually works.

## features

- **Split screen** — two independent panes, drag the divider to resize, double-tap to reset 50/50
- **Shared tabs** — tabs route to whichever pane is active, green = top, blue = bottom
- **Local homepage** — instant-load start page with a search bar, no network request needed
- **Memory monitor** — pip dot in the corner shows green/yellow/red memory pressure at a glance
- **Tab thumbnails** — visual tab switcher with page previews
- **Image search** — one tap to search images from the current query
- **Back navigation** — back button respects the active pane in split mode
- **DDG default** — DuckDuckGo HTML search, unrestricted in all modes

## building

Requirements: Android Studio, JDK 17, Android SDK 35

```bash
git clone https://github.com/0xdolus/marrow.git
cd marrow
./gradlew assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`

## stack

- Kotlin
- Android WebView
- No third-party dependencies

## status

Personal project. Active development. Expect rough edges.

---

*marrow — the essential part.*
