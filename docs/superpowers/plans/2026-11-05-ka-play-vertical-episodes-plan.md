# KA Play Vertical Episodes List & Compact Cards Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enable the native vertical episode list on smartphones (`MetaEpisodeCardStyle.List`), refactor `EpisodeListCard` to a compact 16:9 row layout (min 96 dp height, 128 dp 16:9 thumbnail, 14 sp title, 12 sp meta, 48 dp three-dots options button), and verify via automated tests and Full Debug APK build without auto-publishing release.

**Architecture:** Use `effectiveEpisodeCardStyle` in `MetaDetailsScreen.kt` (returning `List` on phones and `settings.episodeCardStyle` on tablets), refactor `EpisodeListCard` in `DetailSeriesContent.kt` for true 16:9 thumbnail proportions and clean text hierarchy, and leverage the existing virtualized `addLazyEpisodeListItems()` in the root `LazyColumn`.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, Jetpack/Material 3, Android TV D-pad focus, Coil3.

**Spec:** "KA PLAY — CORREÇÃO DA LISTA VERTICAL DE EPISÓDIOS COM EXEMPLOS COMPOSE"

## Global Constraints

- Do not modify playback, streaming source resolution, watch progress, or Supabase synchronization.
- Do not create a nested LazyColumn inside the root LazyColumn.
- Avoid automatic release/publishing ("quando estiver funcional evitar atualizações para todos os usuários"). Build Full Debug APK (`com.kaplay.app.debug`).
- All compilation, testing, and APK builds strictly on GitHub Actions (`buildci` remote); no local or VPS Gradle execution.

## Review Focus

1. **Smartphone Vertical List Activation:** When running on phone viewports (`!isTablet`), `effectiveEpisodeCardStyle` must evaluate to `MetaEpisodeCardStyle.List`, activating `addLazyEpisodeListItems()` in the root `LazyColumn`.
2. **16:9 Thumbnail Proportion:** Thumbnail in `EpisodeListCard` must be 128 dp with `aspectRatio(16f / 9f)`, never square or stretched.
3. **Typography & Title Clamping:** Title 14 sp SemiBold clamped to 2 lines (`TextOverflow.Ellipsis`), subtitle 12 sp `KAColors.TextSecondary`.
4. **Three-Dots Button Accessibility:** Options button `⋮` must be placed to the right of text with a 48 dp touch target (`size(48.dp)`).
5. **Dropdown Season Sync:** Changing seasons via `KASeasonDropdown` immediately updates the vertical episode list without displaying previous season episodes.

---

### Task 1: Activate Vertical Episode List on Smartphones (`MetaDetailsScreen.kt`, `MetaScreenSettingsRepository.kt`)

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/kaplay/app/features/details/MetaDetailsScreen.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/kaplay/app/features/details/MetaScreenSettingsRepository.kt`
- Test: `composeApp/src/androidHostTest/kotlin/com/kaplay/app/features/details/DetailVerticalEpisodeListTest.kt`

**Interfaces:**
- Consumes: `isTablet: Boolean`, `metaScreenSettingsUiState.episodeCardStyle`
- Produces: `effectiveEpisodeCardStyle` (returning `List` when `!isTablet`), `effectiveSettings` passed to `configuredMetaSectionItems` and `episodeListGroupedEpisodes`

- [ ] **Step 1: Write unit test in `DetailVerticalEpisodeListTest.kt` asserting effective card style is List on smartphones**
- [ ] **Step 2: Update `MetaScreenSettingsRepository.kt` default `episodeCardStyle` to `MetaEpisodeCardStyle.List`**
- [ ] **Step 3: Update `MetaDetailsScreen.kt` to derive `effectiveEpisodeCardStyle` and pass `effectiveSettings`**
- [ ] **Step 4: Verify test passes on CI (GREEN)**
- [ ] **Step 5: Commit**

---

### Task 2: Compact 16:9 Row Refactor in `EpisodeListCard` (`DetailSeriesContent.kt`)

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/kaplay/app/features/details/components/DetailSeriesContent.kt`
- Test: `composeApp/src/androidHostTest/kotlin/com/kaplay/app/features/details/DetailEpisodeListCardTest.kt`

**Interfaces:**
- Consumes: `MetaVideo`, `WatchProgressEntry`, `onEpisodeClick`, `onEpisodeLongPress`
- Produces: `EpisodeListCard` with 96 dp min height, 128 dp 16:9 thumbnail, 14 sp title, 12 sp meta, and 48 dp options button

- [ ] **Step 1: Write test in `DetailEpisodeListCardTest.kt` asserting card hierarchy and 16:9 thumbnail structure**
- [ ] **Step 2: Refactor `EpisodeListCard` in `DetailSeriesContent.kt` to compact 16:9 row layout**
- [ ] **Step 3: Verify test passes on CI (GREEN)**
- [ ] **Step 4: Commit**

---

### Task 3: Full CI Verification & Full Debug APK Build

**Files:**
- Modify: `docs/superpowers/plans/2026-11-05-ka-play-vertical-episodes-plan.md`

- [ ] **Step 1: Run full CI test suite on GitHub Actions (`compileFullDebugKotlin` + host tests)**
- [ ] **Step 2: Trigger `ka-play-build-only.yml` to build Full Debug APK (`com.kaplay.app.debug`)**
- [ ] **Step 3: Collect APK SHA-256 and metadata without publishing release**
- [ ] **Step 4: Document completion ledger in plan and report evidence**
