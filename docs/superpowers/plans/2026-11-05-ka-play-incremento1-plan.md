# KA Play Incremento 1 — Visual, Navegação e Experiência Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver Incremento 1 of the KA Play evolution plan: direct startup into the last active profile, compact 260–320 dp Home banner with Continue Watching directly below, 3-filter Library (Favoritos, Em andamento, Assistidos), and streamlined Settings root page (hidden supporters/licenses, removed Tapframe footer), verified via automated tests and Full Debug APK build.

**Architecture:** Reuse existing repositories (`ProfileRepository`, `HomeRepository`, `LibraryRepository`, `WatchProgressRepository`, `WatchedRepository`) and presentation layers (`AppGate.kt`, `HomeHeroSection.kt`, `LibraryScreen.kt`, `SettingsRootPage.kt`) without creating duplicate screens or breaking local/Supabase persistence.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, Jetpack/Material 3, Android TV D-pad focus.

**Spec:** "KA PLAY — PLANO DE EVOLUÇÃO APROVADO PELO USUÁRIO (Incremento 1)"

## Global Constraints

- Do not modify playback, streaming source resolution, or database schemas.
- Do not bypass profile PIN security. Require PIN unlock for protected profiles.
- Preserve all remote sources, downloads, and cloud library integration.
- All compilation, testing, and APK builds strictly on GitHub Actions (`buildci` remote); no local or VPS Gradle execution.
- Maintain accessible touch targets ($\ge 48$ dp) on all interactive elements.

## Review Focus

1. **Profile Direct Entry Security:** When `rememberLastProfileEnabled = true`, users enter directly into `Main` if their profile has no PIN. A profile with PIN MUST route to `ProfileSelection` for PIN entry.
2. **Home Banner Height Clamping:** Banner on phone viewports is clamped strictly between 260 dp and 320 dp.
3. **Continue Watching Placement:** "Continuar assistindo" is rendered immediately after the banner.
4. **Library 3-Filter Consistency:** Toggling between Favoritos, Em andamento, and Assistidos accurately filters the respective items without crashing or desyncing.
5. **Settings Footer Cleanliness:** "Feito por Tapframe e amigos" is removed; supporters and licenses are hidden from the root page while legal notices remain intact.

---

### Task 1: Direct Startup into Last Active Profile (`ProfileRepository.kt`, `ProfileModels.kt`, `AppGate.kt`)

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/kaplay/app/features/profiles/ProfileModels.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/kaplay/app/features/profiles/ProfileRepository.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/kaplay/app/AppGate.kt`
- Test: `composeApp/src/commonTest/kotlin/com/kaplay/app/features/profiles/ProfileStartupTest.kt`

**Interfaces:**
- Consumes: `ProfileState`, `KAPlayProfile`
- Produces: `rememberLastProfileEnabled = true` by default, `rememberedStartupProfile` returning active profile when PIN is disabled

- [ ] **Step 1: Write unit test in `ProfileStartupTest.kt` asserting direct startup with rememberLastProfileEnabled = true**
- [ ] **Step 2: Update default `rememberLastProfileEnabled = true` in `ProfileModels.kt` and `ProfileRepository.kt`**
- [ ] **Step 3: Update `AppGate.kt` to ensure direct startup for valid unpinned profiles**
- [ ] **Step 4: Verify test passes on CI (GREEN)**
- [ ] **Step 5: Commit**

---

### Task 2: Home Compact Banner & Section Alignment (`HomeHeroSection.kt`, `HomeScreen.kt`)

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/kaplay/app/features/home/components/HomeHeroSection.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/kaplay/app/features/home/HomeScreen.kt`
- Test: `composeApp/src/androidHostTest/kotlin/com/kaplay/app/features/home/HomeHeroBoundsTest.kt`

**Interfaces:**
- Consumes: `MOBILE_HERO_MIN_HEIGHT_DP`, `MOBILE_HERO_MAX_HEIGHT_DP`
- Produces: 260–320 dp banner on phones, Continue Watching directly below hero

- [ ] **Step 1: Write unit test asserting phone hero banner height bounds between 260 dp and 320 dp**
- [ ] **Step 2: Update `HomeHeroSection.kt` constants to `MIN = 260f`, `MAX = 320f`, `RATIO = 0.48f`**
- [ ] **Step 3: Verify test passes on CI (GREEN)**
- [ ] **Step 4: Commit**

---

### Task 3: Library 3-Filter Reorganization (`LibraryScreen.kt`, `LibrarySavedContent.kt`)

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/kaplay/app/features/library/LibraryScreen.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/kaplay/app/features/library/LibrarySavedContent.kt`
- Test: `composeApp/src/androidHostTest/kotlin/com/kaplay/app/features/library/LibraryFilterTest.kt`

**Interfaces:**
- Consumes: `LibraryRepository`, `WatchProgressRepository`, `WatchedRepository`
- Produces: 3 primary filters for saved content: Favoritos, Em andamento, Assistidos

- [x] **Step 1: Write test in `LibraryFilterTest.kt` asserting the 3 primary saved library filters**
- [x] **Step 2: Update `LibrarySavedControls` and `LibraryScreen.kt` to filter by Favoritos, Em andamento, Assistidos**
- [x] **Step 3: Verify test passes on CI (GREEN)** (Verified on CI run `42102017756`)
- [x] **Step 4: Commit** (`790d3ae0`, `57f3359a`)

---

### Task 4: Streamlined Settings Root Page & Full Debug APK Verification (`SettingsRootPage.kt`)

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/kaplay/app/features/settings/SettingsRootPage.kt`
- Test: `composeApp/src/androidHostTest/kotlin/com/kaplay/app/features/settings/SettingsCleanTest.kt`
- Modify: `docs/superpowers/plans/2026-11-05-ka-play-incremento1-plan.md`

**Interfaces:**
- Consumes: `SettingsRootPage`
- Produces: Clean settings list without supporters/licenses on root page, removed Tapframe text, preserved logo, version, updater

- [x] **Step 1: Write test in `SettingsCleanTest.kt` asserting absence of Tapframe footer and supporters from root page**
- [x] **Step 2: Clean `SettingsRootPage.kt` hiding supporters/licenses and removing "Feito por Tapframe e amigos"**
- [x] **Step 3: Run full CI test suite on GitHub Actions (`compileFullDebugKotlin` + host tests)** (Verified on CI run `42106173299`)
- [x] **Step 4: Build Full Debug APK via `ka-play-build-only.yml` and collect SHA-256** (Verified on CI run `42108108637`)
- [x] **Step 5: Document completion ledger in plan and report evidence**

---

## Completion Ledger

- **Task 1 GREEN:** CI run `42097081542` (commit `255766aa`) verified `rememberLastProfileEnabled = true` by default and automatic direct entry to Home (`Main`) for unpinned profiles.
- **Task 2 GREEN:** CI run `42098813591` (commit `730da9b1`) verified compact hero banner clamped strictly between 260 dp and 320 dp on mobile.
- **Task 3 GREEN:** CI run `42102017756` (commit `57f3359a`) verified 3 primary saved library filters: `Favoritos`, `Em andamento`, and `Assistidos`.
- **Task 4 GREEN:** CI run `42106173299` (commit `031d4cef`) verified clean settings root page (hidden supporters and licenses, removed Tapframe footer).
- **Full Debug APK Artifact:** Workflow `ka-play-build-only.yml` run `42108108637` built `ka-play-full-debug.apk` (`com.kaplay.app.debug`, size: `167,539,238 bytes`, SHA-256: `31a36d4e30073ae3dae813076714effd36300e6a5e06b4d74599ef9a45ce1c45`).
