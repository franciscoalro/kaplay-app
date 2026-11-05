# KA Play Episode Modal Bottom Sheet & Update Release Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Modernize the Episode Action Bottom Sheet (`EpisodeWatchedActionSheet.kt`) with rich media header (16:9 thumbnail, episode code, season label, runtime), contextual action items (Play, Resume when progress exists, Toggle Watched, Mark Previous/Season, Episode Details), and three-dots (`⋮`) trigger button on cards, then verify on CI and publish release `0.9.27` to all users.

**Architecture:** Enhance `KAPlayBottomSheetActionRow` to support optional subtitles, upgrade `EpisodeWatchedActionSheet.kt` to render the rich contextual episode menu, wire three-dots (`⋮`) buttons on `EpisodeHorizontalCard` and `EpisodeListCard`, test with Compose rules, and publish via `android-release.yml`.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, Jetpack/Material 3 `ModalBottomSheet`, Coil3, Android TV remote focus.

**Spec:** User instruction "KA PLAY — MODAL BOTTOM SHEET DE AÇÕES DO EPISÓDIO"

## Global Constraints

- Preserve all playback logic, streaming source resolution, watch progress, and Supabase synchronization.
- Single-action-at-a-time discipline: dismiss sheet on selection and prevent duplicate concurrent triggers.
- Dark streaming UI: `KAColors.Surface` (`#19191F`), 20 dp top corners (`RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)`), $\ge 48$ dp touch targets.
- All compilation, testing, and release APK builds strictly on GitHub Actions (`buildci` remote); no local or VPS Gradle execution.

## Review Focus

1. **Contextual Resume Visibility:** "Continuar de onde parei" appears ONLY when `progressEntry` has saved progress (> 15s and not completed).
2. **Three-Dots Trigger Accessibility:** `⋮` button on cards must maintain $\ge 48$ dp touch target and open the sheet for that exact episode.
3. **Rich Episode Header Layout:** 16:9 thumbnail, episode title, season/episode code, and duration formatted cleanly with ellipsis.
4. **Watched State Reversibility:** Toggle watched correctly switches between "Marcar como assistido" and "Marcar como não assistido".
5. **Dismissal Integrity:** Tapping outside, dragging down, or system back dismisses cleanly without altering state.

---

### Task 1: Rich Episode Bottom Sheet & Subtitle Action Rows

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/kaplay/app/core/ui/BottomSheet.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/kaplay/app/features/details/components/EpisodeWatchedActionSheet.kt`
- Test: `composeApp/src/androidHostTest/kotlin/com/kaplay/app/features/details/EpisodeActionSheetTest.kt`

**Interfaces:**
- Consumes: `MetaVideo`, `WatchProgressEntry`, `KAPlayBottomSheetActionRow`
- Produces: `EpisodeWatchedActionSheet` with rich header, contextual resume action, and subtitle action rows

- [x] **Step 1: Write failing test in `EpisodeActionSheetTest.kt` asserting rich header, play action, and contextual resume visibility**
- [x] **Step 2: Add optional `subtitle: String? = null` to `KAPlayBottomSheetActionRow` in `BottomSheet.kt`**
- [x] **Step 3: Update `EpisodeWatchedActionSheet.kt` with rich thumbnail header and contextual action rows**
- [x] **Step 4: Verify test passes on CI (GREEN)** (Verified on CI run `42082989662`)
- [x] **Step 5: Commit** (`9b015236`, `c5cfb7b6`)

---

### Task 2: Three-Dots (`⋮`) Menu Triggers on Episode Cards

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/kaplay/app/features/details/components/DetailSeriesContent.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/kaplay/app/features/details/MetaDetailsScreen.kt`
- Test: `composeApp/src/androidHostTest/kotlin/com/kaplay/app/features/details/DetailEpisodeCardTriggersTest.kt`

**Interfaces:**
- Consumes: `onEpisodeLongPress` (opens episode sheet)
- Produces: Three-dots button `⋮` on `EpisodeHorizontalCard` and `EpisodeListCard` invoking episode action sheet

- [x] **Step 1: Write test asserting three-dots button exists on card and triggers episode actions**
- [x] **Step 2: Add three-dots button to `EpisodeHorizontalCard` and `EpisodeListCard`**
- [x] **Step 3: Wire progress entry and play actions in `MetaDetailsScreen.kt`**
- [x] **Step 4: Verify test passes on CI (GREEN)** (Verified on CI run `42082989662`)
- [x] **Step 5: Commit** (`9b015236`, `c5cfb7b6`)

---

### Task 3: Merge, Version Bump `0.9.27` & Publish Official Release to All Users

**Files:**
- Modify: `iosApp/Configuration/Version.xcconfig`
- Modify: `docs/superpowers/plans/2026-11-05-ka-play-episode-modal-bottom-sheet.md`

- [ ] **Step 1: Merge worktree branch into `build-tomato-cmp`**
- [ ] **Step 2: Bump version to `0.9.27` (Build `200`) in `Version.xcconfig`**
- [ ] **Step 3: Push `build-tomato-cmp` to `buildci`**
- [ ] **Step 4: Dispatch `android-release.yml` with `mode=publish -f android_only=true`**
- [ ] **Step 5: Verify GitHub Release `0.9.27` assets and in-app updater endpoint**

---

## Completion Ledger

- **Task 1 GREEN:** Implemented rich episode header (16:9 thumbnail, episode title, season/episode code, duration) and contextual actions (Play from beginning, Resume when progress exists, Toggle watched, Mark previous, Mark season, and View synopsis).
- **Task 2 GREEN:** Added three-dots (`⋮`) button triggers to both `EpisodeHorizontalCard` and `EpisodeListCard` and wired actions in `MetaDetailsScreen.kt`. CI run `42082989662` passed `compileFullDebugKotlin` and host unit tests with 0 failures.
