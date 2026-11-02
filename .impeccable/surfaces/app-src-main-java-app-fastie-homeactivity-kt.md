---
version: 1
slug: "app-src-main-java-app-fastie-homeactivity-kt"
primary_target: "app/src/main/java/app/fastie/MainActivity.kt"
related_targets: ["app/src/main/java/app/fastie/Posters.kt", "app/src/main/java/app/fastie/Ui.kt"]
---

# Home and browse (TV-first, phone too)

Mode: Operate. Audience: Stremio/Nuvio addon users on the couch with a remote. Job: find something and start it in three steps (title → stream → play).

## Direction contract

THESIS: The title you're on gets the whole screen. On TV the focused title's backdrop fills the screen, and the focused poster widens into a 16:9 card that shows its own art. On phone the first title floats as a large card on a glow tinted from it. This refuses the grid-of-cards dashboard and the settings-heavy "media center" look.

OWN-WORLD: A true-black ground (#08080A), white type, and white as the focus colour. Amber (#FFB627) is used only for progress, the wordmark and the current page. Corners are 8dp on cards and 6dp on buttons, with no borders at rest. Type is the system sans, in weight steps.

STORY: Open the app → the first row is focused → move right and each poster widens while the backdrop crossfades behind it → press ↑ for the tabs → OK opens details → Play → streams → playback.

FIRST VIEWPORT: TV: the wordmark and text tabs across the top, a full-screen backdrop under bottom, left and top scrims, and the logo, ★ rating meta and a 2-line synopsis top-left. Rows start at 55% of the height. Phone: the wordmark and Search, then a floating backdrop card with the logo, meta, Play and My List, then the rows, then the bottom bar.

FORM: Option C, "Spotlight", which the user picked on 2026-11-03 from mockups/home.html. It replaces the earlier Netflix-canon and Material You directions.

FINISH: unreviewed and undocumented is unfinished; this build ends with the finish review, the verdict, DESIGN.md, and every shipping raster carrying its provenance
