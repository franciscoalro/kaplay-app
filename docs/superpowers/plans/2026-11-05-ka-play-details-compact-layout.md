# KA Play Details Compact Layout Refactor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refactor the existing KA Play movie and series details screen (`MetaDetailsScreen` and related components) to a compact, streaming-optimized layout (280–340 dp banner, 12 dp button radius with 8 dp gap, 38–40 dp season chips, 220–260 dp 16:9 episode cards, and 14 sp / 21 sp synopsis) while preserving all playback, stream resolution, history, progress, and Supabase integration logic.

**Architecture:** Surgical in-place refactor of existing components (`DetailHero.kt`, `DetailActionButtons.kt`, `DetailSeriesContent.kt`, `ExpandableDescription.kt`, `MetaDetailsScreen.kt`, `MetaScreenSettingsRepository.kt`). No parallel screens or duplicate widgets are created.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, Jetpack/Material 3, Android TV D-pad focus, Coil3.

**Spec:** User instruction "KA PLAY — CORREÇÃO DO DESIGN DA PÁGINA DE DETALHES (P1 — UX/UI)"

## Global Constraints

- Preserve all playback logic, streaming source resolution, subtitle selection, and favorite/watch progress synchronization.
- No database migrations, no local cache wipe, no RPC modifications.
- Do not sign release or auto-publish. Build and verify Full Debug APK (`com.kaplay.app.debug`).
- All compilation, testing, and APK builds strictly on GitHub Actions (`buildci` remote); no local or VPS Gradle execution.
- Maintain accessible touch targets ($\ge 48$ dp) on all interactive elements.

## Review Focus

1. **Banner Height & Crop:** Mobile hero banner clamped to 280–340 dp (`(maxWidth * 0.82f).coerceIn(280.dp, 340.dp)`); artwork must not be distorted; bottom gradient smoothly fades to `KAColors.Background`.
2. **Action Buttons Touch & Focus:** Play button height 48 dp (phone) / 52 dp (tablet), 12 dp radius, 8 dp action gap, 14–15 sp text, and high-contrast D-pad focus stroke.
3. **Season Chips Uncut:** Season 1 chip starts fully visible without clipping; visual chip height 38–40 dp with $\ge 48$ dp touch target; horizontal scroll and selection preserved.
4. **Episode Cards Proportions:** Horizontal cards sized 220–260 dp, 16:9 thumbnail ratio, 8 dp radius, 10 dp gap, 14 sp title, 12 sp metadata, purple progress bar.
5. **Synopsis Typography & Expandability:** 14 sp body, 21 sp line height, 3 lines collapsed, borderless text toggle in `KAColors.PrimaryLight` or soft secondary.

---

### Task 1: Compact Hero Banner Dimensioning (`DetailHero.kt`)

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/kaplay/app/features/details/components/DetailHero.kt`
- Test: `composeApp/src/androidHostTest/kotlin/com/kaplay/app/features/details/DetailHeroCompactTest.kt`

**Interfaces:**
- Consumes: `KAColors.Background`, `maxWidth: Dp`, `isTablet: Boolean`
- Produces: `detailHeroHeight(maxWidth, isTablet): Dp` returning 280–340 dp on phones

- [x] **Step 1: Write unit test asserting hero height bounds for phone viewports**
- [x] **Step 2: Update `detailHeroHeight` in `DetailHero.kt` to `(maxWidth * 0.82f).coerceIn(280.dp, 340.dp)` and proportional gradient overlay**
- [x] **Step 3: Run CI build to verify test passes (GREEN)** (Verified on CI run `42038179700`)
- [x] **Step 4: Commit** (`1d3b567a`)

---

### Task 2: Action Buttons Spacing & 12 dp Radius (`DetailActionButtons.kt`)

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/kaplay/app/features/details/components/DetailActionButtons.kt`
- Test: `composeApp/src/androidHostTest/kotlin/com/kaplay/app/features/details/DetailActionButtonsTest.kt`

**Interfaces:**
- Consumes: `KAShapes`, `KAColors.Primary`, `KAColors.SurfaceVariant`
- Produces: `DetailActionButtons` with 12 dp radius, 8 dp action gaps, 15 sp text, and $\ge 48$ dp touch targets

- [x] **Step 1: Update test in `DetailActionButtonsTest.kt` for 12 dp radius and 8 dp gap**
- [x] **Step 2: Update `DetailActionButtons.kt` with `RoundedCornerShape(12.dp)`, 8 dp spacing, and 15 sp font**
- [x] **Step 3: Run CI build to verify test passes (GREEN)** (Verified on CI run `42039950395`)
- [x] **Step 4: Commit** (`d14aee5f`)

---

### Task 3: Uncut Compact Season Chips (`DetailSeriesContent.kt`)

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/kaplay/app/features/details/components/DetailSeriesContent.kt`
- Test: `composeApp/src/androidHostTest/kotlin/com/kaplay/app/features/details/DetailSeriesContentTest.kt`

**Interfaces:**
- Consumes: `KAColors.Primary`, `KAColors.SurfaceVariant`, `KAShapes.Chip`
- Produces: `SeasonTextChipScrollRow` with 38 dp visual chip height, $\ge 48$ dp touch target, 14 dp horizontal padding, and initial scroll positioning without cutting chip 1

- [x] **Step 1: Write test in `DetailSeriesContentTest.kt` asserting season 1 chip is visible at opening**
- [x] **Step 2: Update `SeasonTextChipScrollRow` and smartphone `seriesContentSizing` in `DetailSeriesContent.kt`**
- [x] **Step 3: Run CI build to verify test passes (GREEN)** (Verified on CI run `42041390378`)
- [x] **Step 4: Commit** (`90197931`)

---

### Task 4: Compact 16:9 Episode Cards (`DetailSeriesContent.kt`)

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/kaplay/app/features/details/components/DetailSeriesContent.kt`
- Test: `composeApp/src/androidHostTest/kotlin/com/kaplay/app/features/details/DetailEpisodeCardMetricsTest.kt`

**Interfaces:**
- Consumes: `EpisodeHorizontalCardMetrics`, `rememberEpisodeHorizontalCardMetrics`
- Produces: Episode card width 220–260 dp, 16:9 ratio, 8 dp radius, 10 dp gap, 14 sp title, 12 sp meta

- [x] **Step 1: Write unit test asserting compact episode card width and spacing**
- [x] **Step 2: Update `rememberEpisodeHorizontalCardMetrics` in `DetailSeriesContent.kt` for phone viewports**
- [x] **Step 3: Run CI build to verify test passes (GREEN)** (Verified on CI run `42044363780`)
- [x] **Step 4: Commit** (`7c9996e7`)

---

### Task 5: Synopsis Typography, Section Order & Full Debug APK Verification (`ExpandableDescription.kt`, `MetaScreenSettingsRepository.kt`, `MetaDetailsScreen.kt`)

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/kaplay/app/features/details/components/ExpandableDescription.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/kaplay/app/features/details/MetaScreenSettingsRepository.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/kaplay/app/features/details/MetaDetailsScreen.kt`
- Modify: `docs/superpowers/plans/2026-11-05-ka-play-details-compact-layout.md`

- [x] **Step 1: Refine `ExpandableDescription.kt` with 14 sp, 21 sp line height, 3 collapsed lines, clean text link**
- [x] **Step 2: Reorder default sections in `MetaScreenSettingsRepository.kt` to place Episodes right after Overview**
- [x] **Step 3: Run full CI test suite on GitHub Actions**
- [ ] **Step 4: Build Full Debug APK via `ka-play-build-only.yml` and collect SHA-256**
- [ ] **Step 5: Document completion ledger in plan and report evidence**
