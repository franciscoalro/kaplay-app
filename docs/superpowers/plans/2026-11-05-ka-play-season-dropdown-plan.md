# KA Play Season Dropdown Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the horizontal season chips with a compact, streaming-style `KASeasonDropdown` (48 dp height, 10 dp radius, `KAColors.SurfaceVariant` trigger, `KAColors.Surface` menu, `KAColors.PrimaryLight` highlight) in `DetailSeriesContent.kt`, test in CI, and publish the official `0.9.26` release to all users.

**Architecture:** Implement `KASeasonDropdown` inside `DetailSeriesContent.kt` and invoke it from `SeriesSeasonSelector`, replacing `SeasonTextChipScrollRow`. Preserve `selectedSeasonOverride`, episode resolution, D-pad navigation, and poster view toggle.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, Jetpack/Material 3 `DropdownMenu` and `DropdownMenuItem`, Android TV focus.

**Spec:** `docs/superpowers/specs/2026-11-05-ka-play-season-dropdown-spec.md`

## Global Constraints

- Do not modify playback, streaming source resolution, or Supabase sync.
- Preserve all existing season grouping and preferred episode logic.
- All compilation, tests, and release APK builds strictly on GitHub Actions (`buildci` remote).
- Maintain minimum 48 dp accessible touch target and high-contrast D-pad focus stroke.

## Review Focus

1. **Dropdown Trigger Bounds:** Minimum 48 dp height, 10 dp radius, `KAColors.SurfaceVariant`, content-wrapped width.
2. **Menu Interaction & Scroll:** `heightIn(max = 320.dp)` allows clean scrolling for series with 10–20+ seasons.
3. **Selection Highlighting:** Active season rendered in `KAColors.PrimaryLight` with `Icons.Default.Check`.
4. **Immediate Episode Refresh:** Selecting an item immediately updates `currentSeason` and dismisses the dropdown.
5. **D-pad Remote Navigation:** High contrast 2 dp focus border on Android TV without trapping focus.

---

### Task 1: Implement `KASeasonDropdown` in `DetailSeriesContent.kt`

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/kaplay/app/features/details/components/DetailSeriesContent.kt`
- Test: `composeApp/src/androidHostTest/kotlin/com/kaplay/app/features/details/DetailSeasonDropdownTest.kt`

**Interfaces:**
- Consumes: `seasons: List<Int>`, `selectedSeason: Int`, `onSeasonSelected: (Int) -> Unit`, `KAColors`, `KAShapes`
- Produces: `KASeasonDropdown` composable and its invocation in `SeriesSeasonSelector`

- [ ] **Step 1: Write failing test in `DetailSeasonDropdownTest.kt` asserting dropdown trigger and item selection**
- [ ] **Step 2: Run test on CI to verify failure (RED)**
- [ ] **Step 3: Implement `KASeasonDropdown` and integrate in `DetailSeriesContent.kt`**
- [ ] **Step 4: Run test on CI to verify success (GREEN)**
- [ ] **Step 5: Commit**

---

### Task 2: Full CI Verification & Preflight Audit

**Files:**
- Modify: `docs/superpowers/plans/2026-11-05-ka-play-season-dropdown-plan.md`

- [ ] **Step 1: Run full CI test suite on GitHub Actions (`compileFullDebugKotlin` + host tests)**
- [ ] **Step 2: Verify git status is clean and all tests pass**
- [ ] **Step 3: Document completion ledger in plan**
- [ ] **Step 4: Commit**

---

### Task 3: Merge, Version Bump `0.9.26` & Publish Release to All Users

**Files:**
- Modify: `iosApp/Configuration/Version.xcconfig`

- [ ] **Step 1: Merge branch into `build-tomato-cmp`**
- [ ] **Step 2: Bump version to `0.9.26` (Build `199`) in `Version.xcconfig`**
- [ ] **Step 3: Push `build-tomato-cmp` to `buildci`**
- [ ] **Step 4: Dispatch `android-release.yml` with `mode=publish -f android_only=true`**
- [ ] **Step 5: Verify GitHub Release `0.9.26` assets and in-app updater endpoint**
