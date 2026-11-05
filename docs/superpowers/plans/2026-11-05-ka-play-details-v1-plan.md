# KA Play Details Screen (MetaDetailsScreen) Modernization Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Modernize the movie and series details experience (`MetaDetailsScreen`) with KA Play Design System V1.0 tokens, delivering 10 dp compact action buttons, purple primary play action, clean 16:9 episode cards with purple progress, 8 dp season chips, and seamless backdrop gradient integration.

**Architecture:** Refactor the dedicated details components (`DetailActionButtons.kt`, `DetailMetaInfo.kt`, `DetailSeriesContent.kt`, `DetailHero.kt`, `DetailFloatingHeader.kt`) to consume centralized tokens (`KAColors`, `KASpacing`, `KAShapes`), standardizing touch targets to >= 48 dp and removing legacy rounded 40 dp pill shapes and elevated shadow artifacts.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, Jetpack/Material 3, Android TV D-pad focus, Coil3.

**Spec:** `docs/superpowers/specs/2026-11-05-ka-play-details-v1-spec.md`

## Global Constraints

- Background `#0B0B0F` (`KAColors.Background`), surface `#19191F` (`KAColors.Surface`), surfaceVariant `#23232B` (`KAColors.SurfaceVariant`).
- Primary action color `#9729D7` (`KAColors.Primary`), text white.
- Action button height 48 dp with 10 dp corner radius (`KAShapes.Button`), minimum 48 dp touch target.
- Season selector chips: 8 dp corner radius (`KAShapes.Chip`), active in `KAColors.Primary`.
- Episode cards: 10 dp corner radius (`KAShapes.Card`), 16:9 aspect ratio, purple progress bar (`KAColors.Primary`).
- Metadata badges: 8 dp corner radius (`KAShapes.Chip`), 1 dp `KAColors.Outline` border.
- Preserve all playback logic, streaming source resolution, subtitle selection, and favorite/watch progress synchronization.
- All testing on CI via GitHub Actions (`buildci` remote); no local/VPS Gradle execution.

## Review Focus

1. **Touch Target Accessibility:** All buttons and icons (Play, Save, Shuffle, Menu, Seasons, Back) must maintain >= 48 dp touch targets.
2. **Text Ellipsis:** Episode titles and overviews must clamp cleanly with `maxLines` and `TextOverflow.Ellipsis`.
3. **Hero Gradient Contrast:** Backdrop gradient must fade seamlessly to `#0B0B0F` without harsh visual borders.
4. **Episode Progress Parity:** Episodes in progress must render with `KAColors.Primary` progress bar and correct fraction.
5. **D-pad Focus Navigation:** Remote control navigation on Android TV must exhibit clear focus feedback without getting trapped.

---

### Task 1: Action Buttons Modernization (`DetailActionButtons.kt`)

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/kaplay/app/features/details/components/DetailActionButtons.kt`
- Test: `composeApp/src/androidHostTest/kotlin/com/kaplay/app/features/details/DetailActionButtonsTest.kt`

**Interfaces:**
- Consumes: `KAColors`, `KAShapes`, `KASpacing`
- Produces: `DetailActionButtons`, `DetailActions` with 10 dp radius and 48 dp touch targets

- [x] **Step 1: Write failing test for action button tokens and radius**
- [x] **Step 2: Run test on CI to verify failure (RED)**
- [x] **Step 3: Update `DetailActionButtons.kt` with `KAShapes.Button`, `KAColors.Primary`, and 48 dp height**
- [x] **Step 4: Verify test passes on CI (GREEN)** (Verified on CI run `42017293505`)
- [x] **Step 5: Commit** (`3964d60b`)

---

### Task 2: Metadata and Header Alignment (`DetailMetaInfo.kt`, `DetailFloatingHeader.kt`)

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/kaplay/app/features/details/components/DetailMetaInfo.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/kaplay/app/features/details/components/DetailFloatingHeader.kt`
- Test: `composeApp/src/androidHostTest/kotlin/com/kaplay/app/features/details/DetailMetaInfoTest.kt`

**Interfaces:**
- Consumes: `KAColors`, `KAShapes`, `KASpacing`
- Produces: `DetailMetaInfo`, `DetailHeroMetaBadge`, `DetailFloatingHeader`

- [x] **Step 1: Write failing test for metadata badges and header colors**
- [x] **Step 2: Run test on CI to verify failure (RED)**
- [x] **Step 3: Update `DetailMetaInfo.kt` (8 dp badge) and `DetailFloatingHeader.kt` (`KAColors.Background` fade)**
- [x] **Step 4: Verify test passes on CI (GREEN)** (Verified on CI run `42019098105`)
- [x] **Step 5: Commit** (`2248f6c5`)

---

### Task 3: Series Content, Season Selector & Episode Cards (`DetailSeriesContent.kt`)

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/kaplay/app/features/details/components/DetailSeriesContent.kt`
- Test: `composeApp/src/androidHostTest/kotlin/com/kaplay/app/features/details/DetailSeriesContentTest.kt`

**Interfaces:**
- Consumes: `KAColors`, `KAShapes`, `KAPlayProgressBar`
- Produces: Season selector chips (8 dp), 16:9 episode cards (10 dp) with purple progress bar

- [x] **Step 1: Write failing test for season chip radius and card shapes**
- [x] **Step 2: Run test on CI to verify failure (RED)**
- [x] **Step 3: Update `DetailSeriesContent.kt` with `KAShapes.Chip`, `KAShapes.Card`, and `KAColors.Primary`**
- [x] **Step 4: Verify test passes on CI (GREEN)** (Verified on CI run `42021197515`)
- [x] **Step 5: Commit** (`a8bc1a35`)

---

### Task 4: Hero Backdrop, Screen Integration & Full CI Verification (`DetailHero.kt`, `MetaDetailsScreen.kt`)

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/kaplay/app/features/details/components/DetailHero.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/kaplay/app/features/details/MetaDetailsScreen.kt`
- Modify: `docs/superpowers/plans/2026-11-05-ka-play-details-v1-plan.md` (completion ledger)

- [x] **Step 1: Update `DetailHero.kt` bottom fade to `KAColors.Background`**
- [x] **Step 2: Run full CI test suite on GitHub Actions (`compileFullDebugKotlin` + host tests)**
- [x] **Step 3: Dispatch independent code reviewer subagent**
- [x] **Step 4: Resolve any reviewer findings in ONE fix pass**
- [x] **Step 5: Document completion ledger and commit**

---

## Completion Ledger

- **Task 1 GREEN:** CI run `42017293505` (commit `3964d60b`) passed `compileFullDebugKotlin` in 9m2s and Android host unit tests.
- **Task 2 GREEN:** CI run `42019098105` (commit `2248f6c5`) passed `compileFullDebugKotlin` in 12m1s and Android host unit tests.
- **Task 3 GREEN:** CI run `42021197515` (commit `a8bc1a35`) passed `compileFullDebugKotlin` in 9m18s and Android host unit tests.
- **Task 4 GREEN:** All details components integrated with `KAColors.Background`, `KAColors.Primary`, `KAShapes.Button` (10 dp), `KAShapes.Card` (10 dp), and `KAShapes.Chip` (8 dp).
