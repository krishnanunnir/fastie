---
name: Fastie
description: A few-megabyte Stremio/Nuvio client that plays the Netflix TV canon straight on a near-black living-room ground.
colors:
  ground: "#0F0F11"
  surface: "#1C1C21"
  raised: "#2B2B31"
  text: "#FFFFFF"
  synopsis: "#D6D6DA"
  muted: "#A3A3A8"
  muted-on-light: "#4A4A52"
  hairline: "#3A3A42"
  snack: "#F2F2F4"
  amber: "#FFB627"
typography:
  display:
    fontFamily: "sans-serif (Roboto, platform default)"
    fontSize: "30sp phone / 34sp TV"
    fontWeight: 700
    lineHeight: 1.1
  title:
    fontFamily: "sans-serif (Roboto, platform default)"
    fontSize: "20sp phone / 22sp TV"
    fontWeight: 700
    lineHeight: 1.1
  row:
    fontFamily: "sans-serif (Roboto, platform default)"
    fontSize: "16sp phone / 19sp TV"
    fontWeight: 700
    lineHeight: 1.1
  body:
    fontFamily: "sans-serif (Roboto, platform default)"
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
  thumb: "4dp"
  card: "6dp"
  episode: "8dp"
  pill: "20dp"
  disc: "24dp"
spacing:
  xs: "4dp"
  sm: "8dp"
  md: "12dp"
  lg: "16dp"
  xl: "28dp"
  page-tv: "40dp"
  page-phone: "16dp"
  rail: "76dp"
  poster-tv: "118dp"
  poster-phone: "108dp"
components:
  button:
    backgroundColor: "{colors.raised}"
    textColor: "{colors.text}"
    typography: "{typography.label}"
    rounded: "{rounded.card}"
    padding: "10dp 22dp 10dp 18dp"
    height: "44dp TV / 48dp phone (min)"
  button-focused:
    backgroundColor: "{colors.text}"
    textColor: "{colors.ground}"
  button-primary-phone:
    backgroundColor: "{colors.text}"
    textColor: "{colors.ground}"
    rounded: "{rounded.card}"
  list-row:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.text}"
    typography: "{typography.label}"
    rounded: "{rounded.card}"
    padding: "14dp 18dp"
  list-row-focused:
    backgroundColor: "{colors.text}"
    textColor: "{colors.ground}"
  chip-season:
    backgroundColor: "transparent"
    textColor: "{colors.muted}"
    typography: "{typography.label}"
    rounded: "{rounded.pill}"
    padding: "8dp 16dp"
    height: "40dp TV / 44dp phone (min)"
  chip-season-selected:
    backgroundColor: "{colors.raised}"
    textColor: "{colors.text}"
  chip-season-focused:
    backgroundColor: "{colors.text}"
    textColor: "{colors.ground}"
  poster-card:
    backgroundColor: "{colors.surface}"
    rounded: "{rounded.card}"
    width: "{spacing.poster-tv}"
  episode-row:
    backgroundColor: "transparent"
    rounded: "{rounded.episode}"
    padding: "8dp 12dp 8dp 8dp"
  episode-row-focused:
    backgroundColor: "{colors.raised}"
  input-field:
    backgroundColor: "{colors.surface}"
    textColor: "{colors.text}"
    rounded: "{rounded.card}"
    padding: "12dp 18dp"
  rail-item-focused:
    backgroundColor: "{colors.text}"
    textColor: "{colors.ground}"
    rounded: "{rounded.disc}"
    size: "48dp"
  snackbar:
    backgroundColor: "{colors.snack}"
    textColor: "{colors.ground}"
    typography: "{typography.label}"
    rounded: "{rounded.card}"
    padding: "14dp 18dp"
---

# Design System: Fastie

## Overview

**Creative North Star: "The Living-Room Marquee"**

The poster you're on is the whole screen. Fastie plays the streaming-category canon straight, with Netflix's TV app as the bar: a near-black ground, the focused title's backdrop filling the top of the screen behind left and bottom scrims, and horizontal rows of 2:3 posters below. Chrome recedes; artwork, title logos and white type carry everything. The only colour the app owns is one amber, and it is spent sparingly.

The system is built from platform Android Views and a handful of `GradientDrawable`s: no Material Components, no Compose, no custom fonts. Lightness is a visual constraint as much as an engineering one. Every surface is a flat tonal step on the ground, every state is a drawable swap, and the only motion is a 150ms focus scale and a 400ms backdrop crossfade. The same code renders TV (D-pad, read from 3 m) and phone (touch), with deliberate differences in navigation, hero behaviour and type size.

Rejected by the brief and absent from the build: the grid-of-cards dashboard, the settings-heavy "media center" look, and Netflix's red or marks.

**Key Characteristics:**
- Near-black ground (#0F0F11) with three flat tonal steps; no decorative shadows.
- Backdrop-led hero with left-to-right and bottom-up ground-coloured scrims.
- Two deliberate focus styles: imagery lifts and outlines, text controls invert to solid white.
- One accent, amber, reserved for progress, the bolt/wordmark and the active nav item.
- System sans (Roboto) in a six-step scale with separate phone and TV sizes.
- Platform Views only; Material Design Icons vector paths for every glyph.

## Colors

A cool near-black ladder with white type and a single warm amber that means "you are here" or "how far you got".

### Primary
- **Fastie Amber** (amber): the only accent. Used on exactly three things: progress bars (poster cards and episode thumbnails), the bolt mark and "fastie" wordmark (TV rail bolt, phone home wordmark, launcher icon, TV banner), and the active navigation item (rail icon, bottom-bar icon and label).

### Neutral
- **Living-Room Ground** (ground): every screen background, the scrim gradients, the bottom bar (at 96% opacity) and the text/icon colour on any white focused control.
- **Card Surface** (surface): resting list rows, input fields, poster placeholders behind loading art, episode thumbnail placeholders.
- **Raised Grey** (raised): resting buttons, pressed list rows, the selected season chip, the focused episode row fill.
- **Screen White** (text): primary type, and the focus colour itself (button/row/chip/rail fill, poster outline, input stroke).
- **Synopsis Grey** (synopsis): hero and details synopsis only, a half-step softer than white for long body copy over imagery.
- **Netflix Grey** (muted): meta lines, secondary text, placeholder titles, inactive nav items, resting chip labels, hints.
- **Muted On Light** (muted-on-light): a list row's subtitle when the row is focused (white fill).
- **Hairline** (hairline): the 1dp outline of an unselected season chip, the only resting border in the system.
- **Snack White** (snack): the transient snackbar, the one light surface.

### Named Rules
**The Amber Budget Rule.** Amber marks progress, the brand mark, and the current nav item. Nothing else: not buttons, not focus, not links, not headings. If a new element wants amber, it does not get it.

**The White Means Focus Rule.** On TV, solid white surfaces exist only under focus (the phone's single primary action is the stated exception). White is the cursor.

## Typography

**Display Font:** sans-serif (Roboto, platform default)
**Body Font:** sans-serif (Roboto)
**Label/Mono Font:** sans-serif-medium (Roboto Medium)

**Character:** The platform face, played straight; weight steps (bold, medium, regular) do the hierarchy work so nothing is downloaded and everything matches the TV's own UI.

### Hierarchy
- **Display** (700, 30sp phone / 34sp TV, 1.1): fallback title when no logo art loads; page titles (My List, Settings, Streams, Sign in).
- **Title** (700, 20sp / 22sp, 1.1): the phone "fastie" wordmark.
- **Row** (700, 16sp / 19sp, 1.1): poster-row headings and stream-group section titles.
- **Body** (400, 14sp / 18sp, 1.25): synopses (max 3 lines on home, 4 TV / 8 phone on details), status and empty-state copy.
- **Meta** (500, 13sp / 16sp, 1.1): year · genres · runtime · IMDb rating lines, list-row subtitles, episode synopses.
- **Label** (700 medium, 14sp / 16sp, 1.1): buttons, list-row titles, chips, episode titles, snackbar, poster placeholder titles.

### Named Rules
**The Two-Scale Rule.** Every style has a phone size and a TV size; never set a size without its TV twin. TV sizes are larger because the screen is read from 3 m.

**The Logo-First Title Rule.** The hero and details title is the title's own logo art when available (TV 30% width, phone ~60%); Display text is the fallback, never both.

## Layout

**TV (1920x1080).** A fixed 76dp rail on the left: bolt mark, then Search, Home, My List, Settings as 48dp icon targets, over a ground gradient fading right. The home hero is fixed: the backdrop spans 74% of screen height, the hero column (logo, meta, 3-line synopsis) sits 9% from the top at 42% width, and rows start at 50% height. The hero follows focus: it updates after the user pauses 220ms on a poster, and the backdrop crossfades. The focused row snaps to the top of the row list instead of scrolling the minimum. Posters are 118dp wide (about 6.5 across beside the rail) with 7dp margins. Page side margins are 40dp (home pages), 56dp (details, streams), 72dp (sign-in); settings and streams columns cap at 640dp and 720dp.

**Phone (1080x2280).** A bottom bar of four icon-over-label items (12sp), ground at 96% opacity, padded for the system nav bar. The home hero is static on the first catalog item, 66% of screen height, and scrolls away with the rows as their first item; it carries Play and My List buttons side by side at equal width. The amber "fastie" wordmark sits top-left under the status bar. Posters are 108dp (about 3.3 across) with 4dp margins. Side margins are 16dp. Everything draws edge to edge under transparent system bars.

**Rhythm.** A 4dp base: 4, 8, 12, 16, 28dp recur (row title top 14dp, section title 28dp above / 12dp below, list gaps 8dp, button gaps 10-12dp).

### Named Rules
**The Focus-Follows Hero Rule.** On TV the hero belongs to whatever is focused; on phone it belongs to the first title and leaves when you scroll. Don't give the phone a focus-following hero or the TV a scrolling one.

## Elevation & Depth

Flat tonal layering. Depth comes from the ground ladder (ground, surface, raised), from ground-coloured scrims over backdrops (left-to-right from solid to clear across 70% of TV width; bottom-up across the lower 55% of the backdrop), and from a scroll dim on details that fades a ground layer to 92% over the backdrop while a status-bar scrim follows it. The one true shadow is platform elevation on the snackbar (6dp). Focused posters gain 8 translationZ alongside their scale.

### Named Rules
**The Scrim Not Shadow Rule.** Type over imagery is made readable by ground-coloured gradients, never by text boxes or drop shadows. (The phone wordmark's soft 12px text shadow is the single exception, there to hold amber over a bright backdrop.)

## Shapes

Gently rounded rectangles throughout: 6dp on posters, buttons, list rows, inputs and the snackbar; 8dp on episode rows; 4dp on episode thumbnails; 20dp pills for season chips; a 24dp disc for the focused rail icon. Imagery is clipped to its outline. No borders at rest, except the unselected season chip's 1dp hairline. Progress tracks are 4dp tall (3dp on episode thumbs) with 2dp rounded ends.

## Components

Two focus styles carry the whole system, chosen by what the element is.

**The Imagery Focus Rule.** Posters lift: a 1.08 scale over 150ms plus a 3dp white outline drawn as the card's foreground at its 6dp radius. Episode rows, which are imagery-plus-text, use the quieter form: a 2dp white stroke around a raised (#2B2B31) fill at 8dp radius.

**The Text-Control Focus Rule.** Buttons, list rows, season chips and rail icons invert: solid white fill, ground-coloured text and icon. No outline, no scale.

### Buttons
- **Shape:** gently rounded (6dp), min height 44dp TV / 48dp phone, Label type, optional 22dp leading icon tinted with the text.
- **Resting:** raised grey with white text. On phone, the single primary action (Play, Open link) rests white with ground text.
- **Focus / Pressed:** white with ground text (focus); #DDDDDD pressed.
- **Layout:** TV buttons wrap content; phone buttons in a pair stretch to equal width with the icon kept beside the centred label.

### Chips
- **Style:** season pills (20dp), Label type, 8dp x 16dp padding.
- **State:** unselected is transparent with a 1dp hairline and grey text; selected is raised grey with white text; focused is white with ground text.

### Cards / Containers
- **Poster card:** 2:3 (width x 1.5), 6dp corners, surface fill behind the art, title text centred in grey as the loading/failed fallback, no border at rest. An optional progress track (white at 30%) with amber fill sits 8dp in from the bottom and sides; minimum fill 4% so a started title always shows.
- **Episode row:** 176dp (TV) / 132dp (phone) 16:9 thumbnail at 4dp with a 3dp amber progress line at its foot, then title (Label) and synopsis (Meta, grey).
- **List row:** full-width surface row (6dp) for settings and streams; title in Label, optional 3-line Meta subtitle. Focus inverts to white; the subtitle turns muted-on-light.

### Inputs / Fields
- **Style:** surface fill, 6dp corners, white text, grey hint, 12dp x 16-18dp padding; the search field leads with a grey search icon.
- **Focus:** a 2dp white stroke appears; the fill does not change.

### Navigation
- **TV rail:** 76dp, icons only, Search first. Inactive icons grey; the current page's icon amber; the focused icon a white 48dp disc with a ground icon. Entering the rail lands on the current page.
- **Phone bottom bar:** four equal items, icon over 12sp label. Inactive grey; current page amber (icon and label). Touch only, no focus treatment.

### Snackbar
A light pill-ish bar (snack white, 6dp, 6dp elevation) with ground Label text, centred 96dp from the bottom, gone after 3.5s. The only light surface in the app.

### Hero / Backdrop
Two stacked image layers crossfade over 400ms; the incoming image only replaces the current one once it has loaded. Logo art (alpha-preserving) replaces the Display title when present.

## Do's and Don'ts

### Do:
- **Do** build every new screen from the Ui.kt primitives (`text`, `button`, `listRow`, `sectionTitle`, `focusRing`, `backdrop`) and the palette object; no new hex literals for roles that already exist.
- **Do** pick the focus style by element type: imagery gets the 1.08 scale plus 3dp white outline; text controls get the solid white fill with ground text.
- **Do** give every type size a TV twin, and treat the TV twin as the legibility floor (PRODUCT.md asks ~18sp for TV body, and the Body step is 18sp).
- **Do** keep amber to progress bars, the bolt/wordmark, and the active nav item.
- **Do** lay text over imagery with ground-coloured scrims.
- **Do** use Material Design Icons vector paths as drawables, tinted by state.

### Don't:
- **Don't** add Material Components, Compose, or a font download; platform Views and the system sans only. Light is a feature.
- **Don't** use amber for buttons, focus, links, headings or highlights.
- **Don't** put borders on posters or cards at rest.
- **Don't** add shadows for depth; tonal steps and scrims carry it.
- **Don't** use Netflix's red or its marks.
- **Don't** give phone a focus-following hero, or TV a scrolling one.
