# Product

<!-- impeccable:product-schema 1 -->

## Platform

android

## Stack

Delegated (agreed in planning, 2026-11-02): native Kotlin with Android Views, no Compose, no React Native. Platform APIs for networking (`HttpURLConnection`), JSON (`org.json`) and storage. The only real dependency is Media3 ExoPlayer for in-app playback. Target: a release APK of about 3–5 MB. Material Components was added later (2026-11-03) for the widgets, taking the APK to about 5.4 MB. One APK serves phones and Android TV.

## Users

People who already use Stremio-style addons (and often Nuvio) and want a fast, plain app for watching what those addons provide. Primary scene: the couch, an Android TV box or TV, a D-pad remote, viewed from 3 m. Secondary: a phone in bed or on the go. Public release (GitHub, possibly F-Droid), so first-run setup must work for people who are not technical.

## Product Purpose

Browse catalogs, pick a title, pick a stream, play it, and resume later, with as little app in the way as possible. Success: it starts fast, scrolls smoothly on cheap TV boxes, and a new user goes from install to playing something in under two minutes.

## Positioning

A Nuvio-compatible client that weighs a few megabytes. It reads the same Stremio addon protocol and can sign in to an existing Nuvio account, so addons, library and watch progress carry over. It doesn't replace the user's setup; it's a lighter window onto it.

## Operating Context

- Stremio addon protocol: `manifest.json`, `/catalog/`, `/meta/`, `/stream/`, all plain HTTP JSON.
- Nuvio account sync (unofficial client): server discovery at `{server}/.well-known/nuvio` (official server `https://api.nuvio.tv`, which publishes its own publishable key). Supabase auth (email/password; TV device-code login via `start_device_login_session` / `poll_tv_login_session` / the `tv-logins-exchange` function). Data comes from the `addons` table and the `sync_pull_*` / `sync_push_*` RPCs (watch progress, library, watched), scoped by `profile_id` (1 = primary).
- External players (VLC, mpv, Just Player) are first-class alternatives to the in-app player.

## Capabilities and Constraints

- Core features: addon management, home catalog rows, search, details page (seasons and episodes), best-stream pick with the full list on the details page, in-app player (subtitles, audio tracks, resume), external player hand-off, continue watching, library, next episode, Nuvio account sign-in and sync, settings.
- TV comes first: everything must be reachable by D-pad with a visible focus state; no screen may need touch.
- Kino ships no stream sources. A fresh install has Cinemeta (catalogs and metadata) only; streams come from addons the user adds or syncs from their Nuvio account.
- The Nuvio backend is third-party. Its API can change or refuse unofficial clients; Kino must keep working without an account.
- Undecided: Trakt, watch party, multiple profiles beyond reading `profile_id` 1.

## Brand Commitments

Name: **Kino** (renamed from Fastie on 2026-11-03). The user's stated idea: "beautiful and simple and easy to read, something like Netflix but with our streams."

Standing preference (confirmed 2026-11-02): play the category standard straight, with Netflix's TV app as the bar. Dark living-room ground; the focused title's backdrop fills the top of the screen; horizontal poster rows; the focused poster scales up with a clean outline. Don't copy Netflix's red or its marks.

## Evidence on Hand

No logo, screenshots, testimonials or users yet. Don't invent any.

## Product Principles

1. **Content first, app last.** Posters and titles lead; chrome recedes.
2. **Three steps to playing.** Title → stream → play. Anything that adds a step must justify itself.
3. **Light is a feature.** Every dependency, animation and image must earn its weight on a cheap TV box.
4. **Your setup, not ours.** Respect the user's addons and Nuvio account; never lock data in.
5. **Readable from the couch.** If it can't be read at 3 m, it isn't done.

## Accessibility & Inclusion

D-pad and keyboard can reach everything, with an obvious focus state. Text stays legible at TV distance (minimum ~18sp on TV for body text). Content descriptions on poster-only items. Respect system font scale on phones.
