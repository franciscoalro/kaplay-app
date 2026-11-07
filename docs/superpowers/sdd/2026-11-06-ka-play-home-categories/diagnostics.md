# Home catalog diagnosis — 2026-11-07

## Root cause

The Tomato feed can return anime IDs without display names. The previous enrichment
resolved only the first 24 IDs across the entire feed. Cards in later sections
without names were then dropped by `titleFromFeedItem`, leaving categories such as
Aventura and Romance with fewer posters than the source supplied. The feed also
used a fixed set of nine Home definitions and truncated the category page to 20
cards even though `Ver tudo` could be offered after the 18-card Home preview.

## Change

- Resolve missing names in a bounded sample of 20 cards **per section** and reuse
  resolved names across sections. Cache incomplete enrichment for one minute, so
  later requests can recover from temporary detail failures.
- Preserve the original nine Tomato definitions and append normalized, distinct
  provider sections. Existing disabled preferences remain keyed to the section.
- Keep the Home preview at 18 cards. The Tomato category page exposes all cards
  with valid names already returned by the feed, without claiming pagination.
  Addon and server pagination continue through their existing catalog loaders.
- Keep `Ver tudo` available for populated Home sections and move the reward action
  into a fixed black header clear of poster cards.
- Log only category keys and counts: received, deduplicated, resolved, displayed,
  available, and has-more. No tokens or URLs are logged.

## Evidence and limits

- Default Tomato definitions: 9. Additional definitions depend on the live feed.
  Provider counts and per-category item counts cannot be asserted from source code
  or CI compilation; collect them from the new diagnostics on a connected app.
- An unnamed Tomato card beyond the first 20 cards of its section still requires
  a successful detail lookup before it can be displayed. Tomato does not advertise
  pagination, so the app does not claim more pages for that source.
- Offline, empty, profile-switch, reopen, scroll performance, and real Home visual
  flows require device verification. No production Supabase data was changed.

## Verification

- `git diff --check`: passed.
- GitHub Actions `kaplay-build` run `42378117097`: Full Debug and Play Store Kotlin
  compilation, security pattern check, Android host tests, and Full Debug APK job
  passed. The host test selection includes Tomato feed/category tests and Home
  catalog definition/section tests.
- Full Debug artifact: `home-full-debug-apk`, downloaded from run `42378117097`.
- APK SHA-256: `98e2f4641f6d2449be16f7d8d7655c3f94047423037f1f8311aa43a63c6e5d08`.
  This is the hash of `androidApp-full-debug.apk`, not GitHub's ZIP artifact digest.
- Real Home screenshot: pending isolated device execution. The repository's
  `AGENTS.md` prohibits local Android builds and installation on the known device.

## Regression and rollback

No host-test regressions were reported by run `42377059184`. Visual/device
regressions are unverified. Revert the isolated Home branch commits to restore the
previous catalog and reward behavior; do not merge until visual evidence and
review are complete.
