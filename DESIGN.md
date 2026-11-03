---
name: Kino
description: A few-megabyte Stremio/Nuvio client in the Spotlight style, with a true-black ground, the focused title's art filling the screen, and white as the cursor.
colors:
  ground: "#08080A"
  container: "#17171A"
  container-high: "#222226"
  container-highest: "#2E2E33"
  text: "#FFFFFF"
  variant: "#A3A3AB"
  outline-variant: "#3A3A42"
  secondary-container: "#FFFFFF2E"
  inverse: "#F2F2F4"
  amber: "#FFB627"
typography:
  display:
    fontFamily: "sans-serif (Roboto, platform default)"
    fontSize: "34sp phone / 40sp TV (44sp as the TV hero's fallback title)"
    fontWeight: 700
    lineHeight: 1.1
  title:
    fontFamily: "sans-serif (Roboto)"
    fontSize: "22sp phone / 24sp TV"
    fontWeight: 700
    lineHeight: 1.1
  row:
    fontFamily: "sans-serif-medium (Roboto Medium)"
    fontSize: "17sp phone / 19sp TV"
    fontWeight: 500
    lineHeight: 1.1
  body:
    fontFamily: "sans-serif (Roboto)"
    fontSize: "14sp phone / 18sp TV"
    fontWeight: 400
    lineHeight: 1.25
  meta:
    fontFamily: "sans-serif-medium (Roboto Medium)"
    fontSize: "13sp phone / 16sp TV"
    fontWeight: 500
    lineHeight: 1.1
  label:
    fontFamily: "sans-serif-medium (Roboto Medium)"
    fontSize: "14sp phone / 16sp TV"
    fontWeight: 700
    lineHeight: 1.1
rounded:
  button: "6dp (Shape.S): buttons, list rows, inputs, snackbar"
  card: "8dp (Shape.M): cards, thumbnails, episode rows"
  ring: "12dp (card radius plus the 4dp gap)"
  hero-poster: "14dp"
  tab: "20dp (Shape.PILL): chips, TV tabs"
spacing:
  page-tv: "48dp"
  page-phone: "16dp"
  tabs-tv: "64dp"
  card-gap-tv: "12dp"
  card-gap-phone: "8dp"
  poster-tv: "100dp (150dp tall; 267dp wide when focused)"
  poster-phone: "108dp"
  continue-tv: "200x112dp"
  continue-phone: "220x124dp"
components:
  button:
    backgroundColor: "{colors.secondary-container}"
    textColor: "{colors.text}"
    typography: "{typography.label}"
    rounded: "{rounded.button}"
    height: "48dp min"
  button-primary-phone:
    backgroundColor: "{colors.text}"
    textColor: "{colors.ground}"
  button-focused:
    backgroundColor: "{colors.text}"
    textColor: "{colors.ground}"
  poster-card:
    backgroundColor: "{colors.container}"
    rounded: "{rounded.card}"
    width: "{spacing.poster-tv}"
  poster-card-focused-tv:
    width: "267dp (16:9)"
    outline: "2dp white ring, 4dp outside the art"
  continue-card:
    rounded: "{rounded.card}"
    progress: "4dp, white 30% track, amber fill"
  tab-tv:
    textColor: "white at 62%"
    typography: "16sp Roboto Medium"
  tab-tv-current:
    textColor: "{colors.text}"
    underline: "2dp {colors.amber}"
  tab-tv-focused:
    backgroundColor: "{colors.text}"
    textColor: "{colors.ground}"
    rounded: "{rounded.tab}"
  snackbar:
    backgroundColor: "{colors.inverse}"
    textColor: "{colors.ground}"
---

# Design System: Kino

## Overview

**Creative North Star: "Spotlight"**

The title you're on gets the whole screen. On TV the focused title's backdrop fills all of it, its logo, meta and synopsis sit top-left, and the rows sit in the darkened lower half. The focused poster widens from 2:3 into a 16:9 card that shows its own backdrop, so the cursor is the brightest and largest thing on screen. On phone the first title is a large floating card on a glow tinted from its art, and the hero scrolls away with the rows.

The user chose this direction on 2026-11-03 from three home mockups (`mockups/home.html`, option C), replacing both the Netflix-style "Marquee" system and the Material You "Tonal" work in progress. Brand commitments from PRODUCT.md still hold: a dark living-room ground, horizontal poster rows, no Netflix red or marks.

The system is still built from platform Views and `GradientDrawable`s, with no Material Components, no Compose and no font downloads. Light is a feature.

**Key characteristics:**
- True-black ground (#08080A) with flat tonal containers. Depth comes from scrims, not shadows (the phone hero card's elevation is the one exception).
- White is the cursor: focused controls invert to white with ground ink, and focused imagery gets a 2dp white ring with a 4dp gap.
- One accent, amber, for progress, the wordmark and the current page.
- TV navigation is a row of text tabs along the top. There is no rail, so the rows get the full width.

## Colors

- **Ground** (#08080A): every screen, the scrims, the phone bottom bar at 96%, and the ink on any white control.
- **Container ladder** (#17171A, #222226, #2E2E33): resting list rows, inputs and placeholder art.
- **White** (#FFFFFF): primary type and the focus colour. The code's `C.primary`, `C.primaryContainer` and `C.onSurface` all resolve to it.
- **Variant** (#A3A3AB): secondary text and inactive phone nav. Meta over art uses white at 72%, the TV synopsis white at 82%, and inactive TV tabs white at 62%.
- **White 18%** (`C.secondaryContainer`): resting buttons and selected chips. It works over both art and ground.
- **Amber** (#FFB627): `C.amber`. See the rule below.

**The Amber Budget Rule.** Amber marks progress (cards, episode thumbnails, the player scrubber), the wordmark (bolt and "kino"), and the current page (the TV tab's underline, the phone bar's icon and label). Nothing else, including focus, buttons, ratings and headings.

**The White Means Focus Rule.** On TV, a solid white surface means focus. The phone's single primary action (Play) is the only exception.

Dynamic colour (Material You) was removed: the palette is fixed so the brand looks the same on every TV box.

## Typography

System Roboto, in weight steps. Every style has a phone size and a TV size (`T` in Ui.kt), and the TV size is the legibility floor.

- **Display**: the fallback title when a title has no logo art (44sp in the TV hero).
- **Row**: row headings, in Roboto Medium at white 90%. They are quieter than the art they introduce.
- **Meta**: a white star icon (`ic_star`) and the rating in bold white, then year · genres · runtime. The rating comes first.
- **Logo first**: the hero shows the title's logo art (trimmed of transparent margins and sized to its own aspect, up to 36% of the screen width × 104dp on TV, or 72% of the card width × 68dp on phone). Display text is only the fallback.

## Layout

**TV (960×540dp).**
- **Tabs:** a 64dp bar at 48dp from the left edge, holding the wordmark, then a Search icon, Home, My List and Settings.
- **Hero:** the backdrop is full-screen, under a bottom-up scrim (the lower 80%), a left-to-right scrim (70% of the width) and a top scrim (22%) behind the tabs. The hero text starts 92dp down and is 46% of the screen wide; the synopsis is capped at 2 lines.
- **Rows:** start at 55% of the height. The focused row snaps to the top of the row list, and within a row the focused card slides to the row's start while it widens. Cards that have scrolled past the start are clipped, so the focused card always comes first.
- **Other pages:** start below the tab bar.

**Phone.**
- **Top:** the wordmark and a Search button sit under the status bar.
- **Hero card:** 78% of the width (at most 340dp) and 1.42 times as tall as it is wide. It uses the title's backdrop, cropped to portrait, because posters print their own title and that would clash with the logo. Logo, meta, Play and My List sit centred at its foot.
- **Glow:** a radial glow behind the card takes its hue from the art's average colour, capped at 55% saturation and 32% value.
- **Rest of the screen:** a status-bar scrim fades in over rows as they scroll under it, and the bottom bar shows icons over labels.

## Motion

There are three motions:
- Focusing a TV poster runs one 260ms animator (ease 0.2, 0.8, 0.2, 1) that widens the card to 16:9, fades in its backdrop face, and slides the row so the card leads.
- The hero backdrop crossfades over 400ms once the user has rested on a title for 220ms.
- Grid posters in My List scale to 1.06 on focus.

## Components

- **Poster card:** 8dp corners, art clipped to its outline, and the title as fallback text while art loads. On TV, a 4dp padding around the art holds the focus ring.
- **Continue Watching card:** a 16:9 backdrop still under a black scrim. It shows the name (Label), "S1 · E4 · 31 min left" (Meta at 75% white), and a 4dp progress line (4% minimum fill). Selecting it goes straight to streams.
- **Buttons:** 6dp corners, 48dp minimum height. They rest on white at 18% (Play on the phone rests white) and invert to white when focused or pressed.
- **Tabs (TV):** text at 62% white, white with an amber underline on the current page, and a white pill with ground ink when focused. Only the current page's tab can take focus from outside the bar, so pressing ↑ lands on it.

## Other pages

All pages use the same system. Corners come from `Shape` (6dp for buttons, rows, inputs and the snackbar; 8dp for cards and thumbnails; 20dp for chips and TV tabs). Page edges are 48dp on TV and 16dp on phone. Nothing springs or scales except grid posters.

- **Details:** the same hero as Home (a full-screen backdrop on TV, the logo sized to its own aspect, the ★ meta line, a 3-line synopsis), then Play and My List, then the season chips.
  - Season chips are pills: a hairline outline at rest, white 18% when selected, white when focused.
  - Episode rows show a 16:9 thumbnail (8dp corners) with a 3dp amber progress line. Under focus they get a raised fill and a 2dp white ring, and the text stays white.
- **Streams:** a full-screen backdrop dimmed to 70% ground, with the title and episode line. Streams are grouped under addon section titles in list rows that invert to white under focus.
- **Settings:** section titles over list rows, in a column capped at 640dp.
- **Search:** a 6dp field that gets a 2dp white stroke under focus, then the same poster rows as Home, including the widening focus.
- **My List:** a grid of posters that scale to 1.06 under focus.
- **Sign-in:** the device code sits in a container box with a hairline outline, not a white box (white means focus). Inputs match the search field.
- **Player:** the scrubber and played bar are amber, because they show progress.

## Known gaps

- `.impeccable/design.json` was generated from the previous system and is out of date.

## Do's and Don'ts

**Do:**
- Build from the Ui.kt primitives (`text`, `button`, `listRow`, `focusRing`, `backdrop`, `wordmark`) and the `C` roles.
- Lay type over art with ground-coloured or black scrims.
- Give every type size a TV twin.

**Don't:**
- Use amber for anything outside its budget.
- Add borders to cards at rest, or shadows for depth.
- Bring back a rail on TV or a focus-following hero on phone.
- Add Material Components, Compose, Palette or a downloaded font.
