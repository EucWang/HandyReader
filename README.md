<p align="center">
  <img src="./infos/banners/banner_en.png" alt="HandyReader Banner" />
</p>

<p align="center">
  <em>Read freely, listen endlessly.</em>
</p>

<p align="center">
  <strong>English</strong> | <a href="infos/README.zh.md">中文</a> | <a href="infos/README.fr.md">Français</a> | <a href="infos/README.de.md">Deutsch</a> | <a href="infos/README.es.md">Español</a> | <a href="infos/README.pt.md">Português</a> | <a href="infos/README.ru.md">Русский</a> | <a href="infos/README.hi.md">हिन्दी</a> | <a href="infos/README.ja.md">日本語</a> | <a href="infos/README.ar.md">العربية</a>
</p>

<p align="center">
  <a href="https://www.gnu.org/licenses/gpl-3.0.en.html"><img src="https://img.shields.io/github/license/EucWang/HandyReader" alt="License" /></a>
  <img src="https://img.shields.io/badge/minSdk-23-orange" alt="minSdk" />
  <img src="https://img.shields.io/badge/platform-Android-brightgreen" alt="Platform" />
  <a href="https://handyreader.top/download.html"><img src="https://img.shields.io/badge/Download-handyreader.top-blue" alt="Download" /></a>
</p>

<p align="center">
  <a href="https://play.google.com/store/apps/details?id=com.wxn.reader"><strong>Get it on Google Play</strong></a>
  &nbsp;·&nbsp;
  <a href="https://handyreader.top/download.html"><strong>Download APK</strong></a>
  &nbsp;·&nbsp;
  <a href="https://github.com/EucWang/HandyReader/releases"><strong>GitHub Releases</strong></a>
</p>

---

HandyReader is a free, open-source e-book and audiobook reader for Android. It supports a wide range of formats, features an offline neural AI text-to-speech engine, deep Material You customization, and keeps all your data on your device.

## Screenshots

<table>
  <tr>
    <td width="25%" align="center"><img src="./infos/screenshots/screenshot_bookshelf.webp" alt="Library" /></td>
    <td width="25%" align="center"><img src="./infos/screenshots/screenshot_reading.webp" alt="Reading" /></td>
    <td width="25%" align="center"><img src="./infos/screenshots/screenshot_highlight.webp" alt="Highlights & Notes" /></td>
    <td width="25%" align="center"><img src="./infos/screenshots/screenshot_tts.webp" alt="Text-to-Speech" /></td>
  </tr>
  <tr>
    <td align="center"><sub>Library</sub></td>
    <td align="center"><sub>Reading</sub></td>
    <td align="center"><sub>Highlights & Notes</sub></td>
    <td align="center"><sub>Text-to-Speech</sub></td>
  </tr>
</table>

## Features

### 📚 Multi-Format Reader
Read e-books and listen to audiobooks in one app.
- **E-books**: EPUB, MOBI, AZW, AZW3, FB2, TXT, Markdown, HTML, PDF
- **Audiobooks**: MP3, M4A, AAC (powered by Media3/ExoPlayer)
- Native C++ parsing engine (libmobi, libxml2, CSSParser) for speed and fidelity

### 🎯 Offline AI Text-to-Speech
The standout feature. Listen to any book with natural, neural-network voices — **fully offline, no internet required**.
- Three engines: **Offline Neural AI** (sherpa-onnx), **Edge TTS** (online), **System TTS** (fallback)
- Background playback with notification controls
- Adjustable speed and pitch, sleep timer, chapter skipping
- Downloadable voice models for multiple languages

### 🎨 Material You Design & Deep Customization
- Dynamic colors on Android 12+ (Material You wallpaper-based theming)
- 12 color schemes, 11 reading themes
- Custom reading backgrounds (solid colors or gallery images)
- Three font sources: system fonts, downloadable catalog fonts, or import your own

### 📝 Annotations & Notes
- Highlights, underlines, and notes with custom colors
- In-book search and annotation filtering
- Dedicated notes browser

### 📖 OPDS Online Catalogs
- Browse and download books from OPDS 1.x online catalogs
- Authentication support (username/password)
- Built-in public catalogs + add your own

### 🃏 Quote Cards
Generate beautiful, shareable quote cards from selected text.
- 5 styles: Minimal White, Dark Night, Parchment, Cover Poster, Big Quote
- 4 aspect ratios (3:4, 1:1, 9:16, 4:5)
- Save to gallery or share directly

### 📊 Reading Statistics
- Daily, total, and per-book reading time
- Reading streaks (current & longest)
- Reading habits heatmap
- Progress tracking (not started / in progress / finished)

### 🗂️ Smart Library
- Unlimited shelves
- Grid and list layouts
- Filter by status, file type, last opened, title, or progress
- Sort and organize large collections

### 🔤 Dictionary & Translation
- Built-in dictionary (WordNet, ECDICT, and more)
- Online AI translation (Meta m2/m100 model)
- Lookup history
- Integration with external dictionary apps

### 🔒 Privacy First
- All data stored locally on your device
- No third-party analytics or tracking
- Fully open source under GPLv3

### 💾 Backup & Restore
- Export/import your reading data (ZIP)
- Includes progress, annotations, notes, bookmarks, shelves, statistics, and preferences
- Content-hash-based smart merging across devices

## Why HandyReader?

| | HandyReader | Typical Readers |
|---|:---:|:---:|
| **Offline AI TTS** | ✅ Neural voices, no internet | ❌ System TTS only |
| **Audiobook support** | ✅ MP3/M4A/AAC | ❌ E-books only |
| **Format coverage** | ✅ 12+ formats | ⚠️ Usually 3-5 |
| **Customization depth** | ✅ Themes, fonts, backgrounds, layouts | ⚠️ Limited |
| **Open source** | ✅ GPLv3 | ⚠️ Often closed |
| **Quote cards** | ✅ Built-in | ❌ Rare |

## Getting Started

**Requirements**: Android 6.0 (API 23) or above.

1. **Google Play** (recommended for auto-updates): [Install HandyReader](https://play.google.com/store/apps/details?id=com.wxn.reader)
2. **APK download** (devices without Google services supported): [Download from handyreader.top](https://handyreader.top/download.html)
3. **GitHub Releases** (browse all versions): [Releases](https://github.com/EucWang/HandyReader/releases)

Open a book from your device or import one — HandyReader will handle EPUB, MOBI, AZW3, FB2, TXT, MD, HTML, PDF, and audiobook files. You can also open files sent from other apps.

## Coming Soon

- 🔄 WebDAV reading progress sync
- 📡 OPDS 2.0 support
- 📚 CBR/CBZ comic format support
- 🎧 M4B audiobook format with chapter support
- 🎙️ Online Edge TTS voices
- 📄 Improved PDF / Markdown / HTML parsing

> The project is actively developed. Check [Releases](https://github.com/EucWang/HandyReader/releases) for the latest changes.

## Tech Stack

| Category | Technology |
|---|---|
| **UI** | Jetpack Compose, Material Design 3, Navigation Compose |
| **DI** | Hilt (Dagger) |
| **Database** | Room |
| **Preferences** | DataStore |
| **Async** | Coroutines & Flow |
| **Image loading** | Coil 3 |
| **Media playback** | Media3 / ExoPlayer |
| **TTS** | sherpa-onnx (offline neural), Edge TTS, Android TTS |
| **Parsing** | libmobi (C++/JNI), jsoup, CSSParser |
| **Networking** | Ktor, OkHttp |

## Build from Source

This project requires **Android Studio Ladybug** (or newer), **JDK 17**, and the **Android NDK**.

```bash
git clone https://github.com/EucWang/HandyReader.git
cd HandyReader
./gradlew :app:assembleDebug
```

> **Note**: Native modules (`mobi`, `jp2forandroid`, `text2speech`) require the NDK to compile. Build on Windows, macOS, or Linux — see the project docs for details.

<details>
<summary>Build variants</summary>

- `assembleDebug` — Debug APK for development
- `assembleRelease` — Release APK (requires signing config in `key.properties`)
- `bundleRelease` — Release AAB for Google Play

</details>

## License

[![GNU GPLv3 License](./infos/Licence.svg)](https://www.gnu.org/licenses/gpl-3.0.en.html)

This project is licensed under the **GNU General Public License v3.0** — see the [LICENSE](LICENSE) file for details.

## Acknowledgments

HandyReader builds on the work of many open-source projects:

- [Skydoves](https://github.com/skydoves) — ColorPicker Compose
- [Shivamdhuria](https://github.com/Shivamdhuria) — Palette library
- [androidSpeech](https://github.com/gotev/android-speech) — Text-to-Speech
- [libmobi](https://github.com/bfabiszewski/libmobi) — MOBI/AZW library
- [tidy-html5](https://github.com/htacg/tidy-html5) — HTML tidy
- [utfcpp](https://github.com/nemtrif/utfcpp) — UTF-8 library
- [CSSParser](https://github.com/luojilab/CSSParser) — CSS parser
- [minizip](http://www.winimage.com/zLibDll/minizip.html) — ZIP library
- [jp2ForAndroid](https://github.com/EucWang/jp2ForAndroid) — JPEG2000 decoder
- [libxml2](https://gitlab.gnome.org/GNOME/libxml2) — XML parser
- [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) — Offline neural TTS engine
