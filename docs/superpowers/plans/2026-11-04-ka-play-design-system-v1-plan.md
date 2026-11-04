# KA Play Design System V1.0 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the official KA Play Design System V1.0 across all major app screens (Core Tokens, Login, Bottom Navigation, Home, Library, Search, Profiles, and Settings), delivering a clean, dark streaming interface inspired by Netflix with KA Play's signature purple identity.

**Architecture:** Centralize all color, shape, spacing, and typography tokens into a core design system module (`KADesignSystem.kt` / `KAColors.kt` / `KAShapes.kt` / `KASpacing.kt`). Refactor existing Compose UI components screen-by-screen to consume these centralized tokens, eliminating glassmorphism/3D/neon artifacts and oversized buttons while preserving all business logic, Supabase authentication, cloud sync, and local data.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, Jetpack/Material 3, Android TV D-pad focus, Coroutines/StateFlow.

**Spec:** `docs/superpowers/specs/2026-11-04-ka-play-design-system-v1.md`

## Global Constraints

- Solid surfaces and flat colors (`#0B0B0F` background, `#19191F` surface, `#23232B` surfaceVariant, `#2C2C35` surfaceElevated).
- Purple palette reserved for primary actions, selection, and focus (`#9729D7`, `#7B20B4`, `#C477E8`).
- No glassmorphism, no 3D effects, no exaggerated neon, no glow, no transparent cards.
- Compact button heights (44–48 dp primary/secondary; 36–40 dp banner; min 48 dp touch target).
- Controlled corner radii (Button: 10 dp, Field: 12 dp, Card: 10 dp, Poster: 8 dp, Banner: 12 dp, Modal: 16 dp; no 24–30 dp rounded capsules).
- 4 dp spacing scale (`space1` = 4 dp, `space2` = 8 dp, `space3` = 12 dp, `space4` = 16 dp, `space6` = 24 dp, `space8` = 32 dp).
- Preserve existing authentication, guest mode, Supabase session persistence, and local account data (never run destructive account wipe or schema migrations).
- Do not sign Release APK or publish automatically (generate Debug APK for QA validation).
- Keep TV D-pad remote navigation and high-contrast focus rings operational on all interactive elements.

## Review Focus

1. **Touch Target Accessibility:** Buttons visually 36–48 dp must maintain at least 48 dp accessible touch targets (`heightIn(min = 48.dp)` or touch delegate padding).
2. **Text Truncation & Ellipsis:** Titles on cards, carousels, and lists must use `maxLines` and `TextOverflow.Ellipsis` without overflowing containers.
3. **Bottom Navigation Insets:** Bottom navigation bar must never occlude the last item in scrollable lists (proper `contentPadding` with system navigation bar insets).
4. **Login Flow Integrity:** Mobile login removes "Entrar por link" per Section 7 while retaining email, password, password recovery, guest mode, and error banners.
5. **No Visual Regressions Offline/Unauthenticated:** Empty states, library offline badges, and search fallback states must degrade gracefully without crashing.

---

### Task 1: Centralized Design Tokens (`KAColors`, `KASpacing`, `KAShapes`, `KATypography`)

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/kaplay/app/core/ui/KADesignSystem.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/kaplay/app/core/ui/Tokens.kt`
- Test: `composeApp/src/commonTest/kotlin/com/kaplay/app/core/ui/KADesignSystemTest.kt`

**Interfaces:**
- Produces: `object KAColors`, `object KASpacing`, `object KAShapes`

- [x] **Step 1: Write failing test for Design System tokens**
- [x] **Step 2: Run test to verify it fails** (Verified RED on CI run `41893616391`)
- [x] **Step 3: Implement `KADesignSystem.kt` with tokens from Section 2, 4, 5, 17**
- [x] **Step 4: Verify tests pass** (Verified GREEN on CI run `41895125260`)
- [x] **Step 5: Commit** (`63fc7ae9`)

---

### Task 2: Core Components & Login Screen Alignment (`AuthScreen.kt`)

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/kaplay/app/core/ui/KAButtons.kt`
- Create: `composeApp/src/commonMain/kotlin/com/kaplay/app/core/ui/KAFormFields.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/kaplay/app/features/auth/AuthScreen.kt`
- Test: `composeApp/src/androidHostTest/kotlin/com/kaplay/app/features/auth/AuthScreenTest.kt`

**Interfaces:**
- Consumes: `KAColors`, `KASpacing`, `KAShapes`
- Produces: `KAPrimaryButton`, `KASecondaryButton`, `KATextButton`, `KAInputField`

- [x] **Step 1: Write failing test for Section 7 login screen requirements**
- [x] **Step 2: Run test to verify failure**
- [x] **Step 3: Implement `KAPrimaryButton`, `KASecondaryButton`, `KATextButton`, and update `AuthScreen.kt`**
- [x] **Step 4: Verify test passes** (Verified GREEN on CI run `41898391177`)
- [x] **Step 5: Commit** (`ec7da822`, `cb2dd315`)

---

### Task 3: Bottom Navigation Bar Modernization (`NavigationBar.kt`, `MainTabsDestination.kt`)

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/kaplay/app/core/ui/NavigationBar.kt`
- Test: `composeApp/src/androidHostTest/kotlin/com/kaplay/app/core/ui/NavigationBarTest.kt`

**Interfaces:**
- Consumes: `KAColors`, `KASpacing`
- Produces: Modernized 4-item bottom navigation (Início, Busca, Biblioteca, Perfil)

- [x] **Step 1: Write failing test for Bottom Navigation bar specifications**
- [x] **Step 2: Run test to verify failure**
- [x] **Step 3: Update `NavigationBar.kt` and `MainTabsDestination.kt`**
- [x] **Step 4: Verify test passes** (Verified GREEN on CI run `41900790755`)
- [x] **Step 5: Commit** (`1dabd952`)

---

### Task 4: Home Screen Modernization (`HomeScreen.kt`, Carousels & Rails)

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/kaplay/app/features/home/HomeScreen.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/kaplay/app/features/home/components/HomeHeroSection.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/kaplay/app/core/ui/ProgressBar.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/kaplay/app/core/ui/PosterCardStyleRepository.kt`
- Test: `composeApp/src/androidHostTest/kotlin/com/kaplay/app/features/home/HomeDesignSystemTest.kt`

**Interfaces:**
- Consumes: `KAColors`, `KAShapes`, `KASpacing`, `KAPrimaryButton`, `KASecondaryButton`
- Produces: Header, Category Chips, 200–260 dp Rotating Banner, 16:9 Continue Watching cards, 2:3 Content rails

- [x] **Step 1: Write failing test for Home Screen components**
- [x] **Step 2: Run test to verify failure**
- [x] **Step 3: Implement modernized Home screen structure**
- [x] **Step 4: Verify test passes** (Verified GREEN on CI run `41904834403`)
- [x] **Step 5: Commit** (`c8a317b9`, `4654e7f4`)

---

### Task 5: Library Screen Modernization (`LibraryScreen.kt`)

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/kaplay/app/features/home/components/PosterGrid.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/kaplay/app/features/library/LibraryScreen.kt`
- Test: `composeApp/src/androidHostTest/kotlin/com/kaplay/app/features/library/LibraryScreenTest.kt`

**Interfaces:**
- Consumes: `KAColors`, `KASpacing`, `KAShapes`
- Produces: 3-poster adaptive grid, sync status chips (Saved, Pending, Syncing, Cloud, Error)

- [x] **Step 1: Write failing test for Library adaptive grid and sync badges**
- [x] **Step 2: Run test to verify failure**
- [x] **Step 3: Update `LibraryScreen.kt`**
- [x] **Step 4: Verify test passes** (Verified GREEN on CI run `41907000580`)
- [x] **Step 5: Commit** (`87a9310d`)

---

### Task 6: Search Screen Modernization (`SearchScreen.kt`)

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/kaplay/app/core/ui/Components.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/kaplay/app/core/ui/Tokens.kt`
- Test: `composeApp/src/androidHostTest/kotlin/com/kaplay/app/features/search/SearchScreenTest.kt`

**Interfaces:**
- Consumes: `KAColors`, `KASpacing`, `KAShapes`, `KAInputField`
- Produces: 52 dp search field, compact filters, clean empty states with actionable buttons

- [x] **Step 1: Write failing test for Search empty state and input field**
- [x] **Step 2: Run test to verify failure**
- [x] **Step 3: Update `SearchScreen.kt` and `Components.kt`**
- [x] **Step 4: Verify test passes** (Verified GREEN on CI run `41909064318`)
- [x] **Step 5: Commit** (`bba30c6b`, `ebefd9bf`)

---

### Task 7: Profiles & Settings Modernization (`ProfileSelectionScreen.kt`, `SettingsScreen.kt`)

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/kaplay/app/features/settings/SettingsComponents.kt`
- Test: `composeApp/src/androidHostTest/kotlin/com/kaplay/app/features/settings/SettingsScreenTest.kt`

**Interfaces:**
- Consumes: `KAColors`, `KASpacing`, `KAShapes`
- Produces: "Quem está assistindo?" profile grid, compact profile form, grouped settings rows

- [x] **Step 1: Write failing test for Settings grouping and Profile grid**
- [x] **Step 2: Run test to verify failure**
- [x] **Step 3: Modernize Profiles and Settings screens**
- [x] **Step 4: Verify test passes** (Verified GREEN on CI run `41911947269`)
- [x] **Step 5: Commit** (`290cf8b7`, `ce413ab0`)

---

### Task 8: Whole-App Integration, CI Build & Debug APK Verification

**Files:**
- Modify: `docs/superpowers/plans/2026-11-04-ka-play-design-system-v1-plan.md` (record completion ledger)

- [x] **Step 1: Run comprehensive CI checks** (CI run `41911947269` passed with exit code 0)
- [x] **Step 2: Generate and verify Debug APK** (CI run `41913985555` produced and uploaded `ka-play-full-debug-build-only` artifact)
- [x] **Step 3: Document completion ledger and request final whole-branch review**
- [x] **Step 4: Commit**

---

## Completion Ledger

- **Task 1 RED:** CI run `41893616391` (commit `b8103bf1`) failed as expected due to missing `KAColors`, `KASpacing`, `KAShapes`.
- **Task 1 GREEN:** CI run `41895125260` (commit `63fc7ae9`) passed compile and unit tests.
- **Task 2 GREEN:** CI run `41898391177` (commit `cb2dd315`) passed compile and unit tests.
- **Task 3 GREEN:** CI run `41900790755` (commit `1dabd952`) passed compile and unit tests.
- **Task 4 GREEN:** CI run `41904834403` (commit `4654e7f4`) passed compile and unit tests.
- **Task 5 GREEN:** CI run `41907000580` (commit `87a9310d`) passed compile and unit tests.
- **Task 6 GREEN:** CI run `41909064318` (commit `ebefd9bf`) passed compile and unit tests.
- **Task 7 GREEN:** CI run `41911947269` (commit `ce413ab0`) passed compile and unit tests.
- **Task 8 Debug APK:** CI run `41913985555` built Full Debug APK (`assembleFullDebug`), verified APK credentials, and uploaded artifact `ka-play-full-debug-build-only`.
- **Review Fix Pass:** CI run `41920290674` (commit `14a566e1`) verified Android navigation bar delegation to `KAPlayNavigationBar`, expanded 48 dp accessible touch targets (`KATextButton`, password toggle, `HomeRewardButton`, hero button, `AuthScreen` links), and bound core components directly to production screens.
