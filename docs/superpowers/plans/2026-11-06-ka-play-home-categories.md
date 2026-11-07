# KA Play Home Catalog Categories Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Prevent Home catalog rows from collapsing to one item by resolving provider names per section, exposing valid dynamic Tomato categories, preserving an 18-item visual preview with accurate Ver tudo state, and moving the reward CTA out of poster content.

**Architecture:** Keep network parsing in TomatoProvider and catalog identity in TomatoCatalog. HomeRepository remains the aggregation boundary and publishes section diagnostics without sensitive data. HomeScreen renders only the bounded preview while using available counts/pagination for navigation; settings continue to own explicit user enable/disable preferences.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, Kotlin serialization, coroutines, Android host tests, GitHub Actions.

**Spec:** User-provided KA PLAY Home categories correction request in the conversation.

## Global Constraints

- Do not invent content or duplicate legitimate items.
- Keep Home previews bounded to 18 posters; do not load thousands of posters.
- Preserve horizontal 2:3 poster carousels, compact headers, black background, and Ver tudo navigation.
- Do not modify Supabase, migrations, local data deletion, auth, playback, or repositories outside this feature.
- Do not publish or merge automatically.
- Never log tokens, private URLs, or sensitive data.

## Review Focus

- Feed sections with IDs but missing names after the first global lookup window must still resolve items per section.
- Dynamic provider sections must not duplicate fixed sections or override explicit disabled preferences.
- Categories with exactly 1, 5, 18, 20, and paginated 100+ items must report counts and render only the bounded preview.
- Offline/empty/error sections must not produce fake rows or stale partial rows.
- Reward CTA must remain clickable and visible without covering poster cards at top insets and after scroll.

---

### Task 1: Fix Tomato enrichment starvation

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/kaplay/app/features/tomato/TomatoProvider.kt`
- Test: `composeApp/src/commonTest/kotlin/com/kaplay/app/features/tomato/TomatoProviderTest.kt`

**Interfaces:**
- Produces `internal fun detailLookupIds(rawSections: List<JsonObject>): List<Long>`.

- [ ] Write a failing test proving missing-name IDs from Aventura and Romance are included.
- [ ] Run the targeted Tomato test and observe the missing-symbol failure.
- [ ] Implement per-section missing-name lookup with a bounded per-section cap and distinct IDs.
- [ ] Run the targeted test and the complete TomatoProviderTest class.
- [ ] Commit `fix(tomato): resolve feed names per section`.

### Task 2: Discover dynamic Tomato sections safely

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/kaplay/app/features/tomato/TomatoCatalog.kt`, `composeApp/src/commonMain/kotlin/com/kaplay/app/features/home/HomeRepository.kt`, `composeApp/src/commonMain/kotlin/com/kaplay/app/features/home/HomeCatalogSettingsRepository.kt`
- Test: `composeApp/src/commonTest/kotlin/com/kaplay/app/features/home/HomeCatalogDefinitionTest.kt`, `composeApp/src/commonTest/kotlin/com/kaplay/app/features/tomato/TomatoProviderTest.kt`

**Interfaces:**
- Dynamic Tomato definitions use normalized key `tomato:<section-key>`, the provider title, and `supportsPagination = false`.
- Fixed nine sections remain first; dynamic sections are appended by normalized key and never duplicated.

- [ ] Add failing tests for normalization/deduplication and dynamic section definition generation.
- [ ] Implement cached provider section descriptors populated from feed parsing and refresh Home definitions after feed availability without recursive refresh loops.
- [ ] Preserve stored disabled preferences by key; new sections default enabled.
- [ ] Run Home definition/settings tests and Tomato tests.
- [ ] Commit `feat(home): include dynamic provider sections`.

### Task 3: Make counts and Ver tudo truthful

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/kaplay/app/features/home/HomeRepository.kt`, `composeApp/src/commonMain/kotlin/com/kaplay/app/features/home/HomeModels.kt`, `composeApp/src/commonMain/kotlin/com/kaplay/app/features/home/HomeScreen.kt`
- Test: `composeApp/src/commonTest/kotlin/com/kaplay/app/features/home/HomeCatalogSectionTest.kt`, new Home aggregation test if needed.

**Interfaces:**
- `HomeCatalogSection.availableItemCount` is the provider raw count after legitimate deduplication.
- `HomeCatalogSection.canOpenCatalog(18)` is true only when more than the preview or a real next page exists.

- [ ] Add failing tests for counts 1/5/18/20 and paginated sections.
- [ ] Ensure providers receive at most the preview fetch limit, preserve `nextSkip`, and do not claim pagination for Tomato.
- [ ] Keep rendering at `section.items.take(18)` and pass the existing poster callbacks/keys.
- [ ] Add non-sensitive diagnostic logging at the aggregation boundary with category, item count, available count, and hasMore.
- [ ] Run Home model/repository tests and compile checks.
- [ ] Commit `fix(home): preserve catalog counts and preview navigation`.

### Task 4: Reposition reward CTA

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/kaplay/app/features/home/HomeScreen.kt`
- Test: `composeApp/src/commonTest/kotlin/com/kaplay/app/features/home/HomeRewardLabelTest.kt` and/or Android host UI test.

- [ ] Add a failing layout assertion that the reward button is anchored in the header container, not over the scroll content.
- [ ] Move the existing `HomeRewardButton` into the fixed header action row while preserving state, authentication, and countdown callbacks.
- [ ] Run reward and Home UI tests.
- [ ] Commit `fix(home): anchor reward action in header`.

### Task 5: Verification and artifacts

**Files:**
- Modify: `.github/workflows/details-host-tests.yml` or Home workflow only if required.
- Create: `docs/superpowers/sdd/2026-11-06-ka-play-home-categories/diagnostics.md`.

- [ ] Run targeted Home/Tomato host tests through GitHub Actions.
- [ ] Run full CI workflow and read the result.
- [ ] Build Full Debug APK, compute SHA-256, and capture a real Home screenshot from the connected app/browser harness when available.
- [ ] Record provider/category/item counts, root cause, changed files, tests, artifact path, hash, and regression results without secrets.
- [ ] Request whole-branch review before any merge or publish.
