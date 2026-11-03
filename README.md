# Kino

A very light way to watch what your Stremio-style addons provide, on Android TV and phones. One APK of about 5 MB.

- **Netflix-style browsing.** As you move around, the title you're on fills the screen, with rows of posters below.
- **Works with your addons.** Add any Stremio addon link, or sign in to your **Nuvio** account to bring your addons, My List and Continue Watching with you.
- **Three steps to playing.** Pick a title, pick a stream, play. Resume where you stopped, on any device.
- **TV first.** Everything works with a remote. Voice search works on remotes with a mic.

Kino hosts no content and isn't affiliated with Nuvio or Stremio. A fresh install comes with Cinemeta (catalogs and details only); streams come from addons you add.

## Install

Download the APK from Releases and open it on your phone or TV. On a TV, a downloader app or `adb install kino.apk` works.

## Using it

- **Sign in to Nuvio:** Settings → Sign in to Nuvio. A code appears; open nuvio.tv/link on your phone and enter it. Or use your email and password.
- **Add an addon:** Settings → Add an addon, then paste the addon's `manifest.json` link.
- **Use another player:** Settings → Player switches between Kino's player and any video app you have (VLC, mpv, Just Player).

Kino plays direct stream links. Torrent-only results are hidden, because Kino doesn't ship a torrent engine.

## Building

JDK 17 and the Android SDK (platform 35).

```bash
./gradlew assembleRelease
```

Release signing reads a git-ignored `keystore.properties` (`storeFile`, `storePassword`, `keyAlias`, `keyPassword`). Without it, the APK builds unsigned.

```bash
./gradlew testDebugUnitTest
```

### How it's built

Plain Kotlin and Android Views; the platform's own HTTP, JSON and storage. The real dependencies are Media3 ExoPlayer and Material Components. Files in `app/src/main/java/app/kino/`:

| File | What it does |
|---|---|
| `Addons.kt`, `Meta.kt` | Stremio addon protocol: manifest, catalog, meta, stream |
| `Nuvio.kt` | Nuvio account: sign-in (email or TV code), pulling addons, My List and progress, pushing changes |
| `Store.kt` | Progress, My List and settings on the device |
| `MainActivity.kt`, `Pages.kt` | Home, Search, My List, Settings |
| `DetailsActivity.kt`, `StreamsActivity.kt`, `PlayerActivity.kt` | Title → stream → playback |
| `Ui.kt`, `Posters.kt`, `Images.kt` | Palette, type, focus, poster rows, image cache |

`PRODUCT.md` and `DESIGN.md` hold the product and design decisions. The TV banner is drawn by `tools/banner.py`.
