# KA Play Definitive Vertical Episode List Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make the existing smartphone details experience definitively vertical by forcing the compact season dropdown on phones, keeping the existing root `LazyColumn`, and preserving episode actions, progress, ratings, and season state.

**Architecture:** Reuse `MetaDetailsScreen.addLazyEpisodeListItems()`, `DetailSeriesListHeader`, `DetailSeriesListEpisode`, `EpisodeListCard`, and `KASeasonDropdown`. Add only a small pure policy helper for the effective season selector mode; tablet preferences remain unchanged. No new screen, repository, persistence layer, or nested scroll container is introduced.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, Material 3, Robolectric host tests, GitHub Actions buildci.

**Spec:** User-provided KA PLAY — CORREÇÃO DEFINITIVA DA LISTA DE EPISÓDIOS Markdown.

## Global Constraints

- Smartphones must present episodes vertically.
- Tablets may preserve the configured mode.
- Do not erase saved preferences.
- Do not create a second `LazyColumn` inside the main vertical scroll.
- Reuse existing callbacks, `EpisodeWatchedActionSheet`, progress, watched, ratings, and canonical identifiers.
- Use existing `KAColors` and `KAShapes`; do not create a competing theme.
- Do not modify authentication, repositories, Supabase RPCs, stream resolution, or playback history.
- Do not publish a Release or update all users from this plan; produce a Full Debug APK only.
- All compilation and APK builds run remotely through GitHub Actions (`buildci`).

## Review Focus

1. **Stored horizontal selector preference on phone:** a persisted `Posters` mode must still render the compact dropdown on phones.
2. **Season switching:** selecting another season must replace only the visible episode list and must not trigger playback.
3. **Single scroll container:** the implementation must continue emitting `item`/`itemsIndexed` into the existing root `LazyColumn`.
4. **Episode state preservation:** episode click, long-press/menu, progress, watched indicator, and ratings must remain wired through `DetailSeriesListEpisode`.
5. **Narrow width header:** the season title and dropdown must remain readable and avoid overlap at phone widths.

---

### Task 1: Phone Season Selector Policy

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/kaplay/app/features/details/components/DetailSeriesContent.kt`
- Test: `composeApp/src/androidHostTest/kotlin/com/kaplay/app/features/details/DetailVerticalEpisodeListTest.kt`

**Interfaces:**
- Consumes: existing `SeasonViewMode`, `MetaEpisodeCardStyle`, and `isTablet` policy.
- Produces: `internal fun resolveEffectiveSeasonViewMode(isTablet: Boolean, configuredMode: SeasonViewMode): SeasonViewMode`, returning `Text` on phones and the stored mode on tablets.

- [ ] **Step 1: Add a failing test** named `phoneAlwaysUsesTextSeasonSelectorEvenWhenPostersAreStored`; assert `resolveEffectiveSeasonViewMode(false, SeasonViewMode.Posters) == SeasonViewMode.Text`.
- [ ] **Step 2: Run the focused host test and observe the expected unresolved-function failure.**
- [ ] **Step 3: Implement the pure helper and use it in `SeriesSeasonSelector` with the existing tablet detection from `BoxWithConstraints`.**
- [ ] **Step 4: Ensure the phone path renders only `KASeasonDropdown`; preserve the configured poster/text mode on tablets.**
- [ ] **Step 5: Run the focused test and the existing details host tests; expect all to pass.**
- [ ] **Step 6: Commit with `fix(details): force compact season dropdown on phones`.**

---

### Task 2: Same-Line Season Header and Layout Regression Tests

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/kaplay/app/features/details/components/DetailSeriesContent.kt`
- Test: `composeApp/src/androidHostTest/kotlin/com/kaplay/app/features/details/DetailVerticalEpisodeListTest.kt`

**Interfaces:**
- Consumes: `DetailSeriesListHeader`, `KASeasonDropdown`, `SeriesContentSizing`.
- Produces: compact header policy `internal fun shouldUseInlineSeasonHeader(maxWidthDp: Float): Boolean`, true at or above 360 dp and false below it.

- [ ] **Step 1: Add failing tests** for inline header at 390 dp and stacked header below 360 dp.
- [ ] **Step 2: Run the focused tests and observe the expected unresolved-function failure.**
- [ ] **Step 3: Implement the pure width policy and update `DetailSeriesListHeader` to place the episode title/dropdown in one row when the policy is true, while allowing a two-row fallback for narrow widths.**
- [ ] **Step 4: Keep the existing season selection callback and do not start playback from selector changes.**
- [ ] **Step 5: Run focused details host tests and verify they pass.**
- [ ] **Step 6: Commit with `feat(details): compact inline season header for phones`.**

---

### Task 3: Full Verification and Debug APK

**Files:**
- Modify: `docs/superpowers/plans/2026-11-06-ka-play-episode-list-definitive-plan.md`

- [ ] **Step 1: Push the feature branch to `buildci` and run `kaplay-build` with compile and host tests.**
- [ ] **Step 2: Trigger `ka-play-build-only.yml` on the feature branch; do not trigger `android-release.yml`.**
- [ ] **Step 3: Collect Full Debug APK metadata and SHA-256.**
- [ ] **Step 4: Run the final branch review with a fresh reviewer and record Critical/Important fixes or deferred minors.**
- [ ] **Step 5: Update this plan ledger with exact run IDs, test results, APK SHA-256, and release-isolation evidence.**
