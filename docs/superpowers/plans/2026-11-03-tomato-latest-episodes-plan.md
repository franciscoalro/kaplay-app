# Tomato Latest Episodes Metadata Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Preserve Tomato anime/episode metadata and make the `Novos episódios` section show the real anime name plus the posted episode number/title and artwork.

**Architecture:** Keep Tomato JSON parsing inside `TomatoProvider`, extending the feed card model with optional episode fields. `TomatoCatalog` will format only latest-episode cards for display while retaining the canonical `tomato:<animeId>` id, so existing detail and stream routing remains unchanged. Tests will cover parsing and presentation before an emulator smoke check.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, kotlinx.serialization JSON, Kotlin test, Android emulator/ADB.

**Spec:** `docs/superpowers/specs/2026-11-03-tomato-latest-episodes-spec.md`

## Global Constraints

- Anime identity comes from `anime_name` with existing aliases as fallback.
- Episode identity accepts `ep_id`/`episode_id`, `ep_number`/`episode_number`, and `ep_name`/`episode_name` aliases.
- Episode artwork has precedence over generic feed artwork for `Novos episódios`.
- Latest episode entries must not be deduplicated solely by anime id.
- Canonical IDs remain `tomato:<animeId>`.
- Do not uninstall, clear data, or change `com.kaplay.app`/`com.kaplay.app.debug` during validation.

## Review Focus

- Two new episodes for one anime must remain two cards; test the task that changes feed deduplication.
- A feed item with only `ep_anime_id` and `ep_number` must still render the anime name and episode label; test alias fallback in the parser task.
- `ep_thumbnail` must win over a generic `thumbnail`; test thumbnail precedence in the parser task.
- Missing episode number/title must degrade to the real anime name without displaying `null` or a technical id; test display formatting.
- Existing non-latest sections must retain one anime card and anime artwork; test section-aware mapping.

---

### Task 1: Preserve episode metadata in Tomato feed cards

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/kaplay/app/features/tomato/TomatoProvider.kt`
- Test: `composeApp/src/commonTest/kotlin/com/kaplay/app/features/tomato/TomatoProviderTest.kt`

**Interfaces:**
- Consumes: raw feed `JsonObject` values from `/v2/animes/feed`.
- Produces: `TomatoTitle(id, name, image, episodeId, episodeNumber, episodeName, episodeThumbnail)` and helper functions for aliases.

- [ ] **Step 1: Write failing parser tests**
  - Add a feed fixture containing `ep_anime_id`, `ep_id`, `anime_name`, `ep_number`, `ep_name`, `ep_thumbnail`, and generic `thumbnail`.
  - Assert anime id/name, episode id/number/title, and that episode thumbnail is selected first.
  - Add a fixture using `episode_id`, `episode_number`, and `episode_name` aliases.

- [ ] **Step 2: Run the focused tests and confirm failure**
  - Run `./gradlew :composeApp:testAndroidHostTest --tests 'com.kaplay.app.features.tomato.TomatoProviderTest' --no-daemon --no-parallel --max-workers=2`.
  - Expected: compile/test failure because `TomatoTitle` and episode feed helpers do not expose the asserted fields.

- [ ] **Step 3: Implement the minimal feed model and aliases**
  - Add nullable episode fields to `TomatoTitle` with defaults so search/non-episode callers remain source-compatible.
  - Add `episodeNameOf` and `episodeThumbnailOf` helpers; update `episodeFromJson` to use the same aliases and artwork precedence.
  - Build `TomatoTitle` from feed items without discarding episode fields.

- [ ] **Step 4: Run focused tests and confirm pass**
  - Re-run the focused command; expected: all `TomatoProviderTest` tests pass.

- [ ] **Step 5: Commit**
  - Commit as `fix: preserve Tomato episode feed metadata`.

### Task 2: Keep latest episode entries and format visible labels

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/kaplay/app/features/tomato/TomatoProvider.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/kaplay/app/features/tomato/TomatoCatalog.kt`
- Test: `composeApp/src/commonTest/kotlin/com/kaplay/app/features/tomato/TomatoProviderTest.kt`

**Interfaces:**
- Consumes: `FeedSectionPayload(key, title, items)` and enriched `TomatoTitle` from Task 1.
- Produces: section-aware `MetaPreview` labels and posters, preserving `tomato:<animeId>` ids.

- [ ] **Step 1: Write failing tests for latest-feed deduplication and labels**
  - Add two `Novos episódios` items with the same anime id but episode numbers 11 and 12; assert both survive section mapping.
  - Assert label output exactly follows `Anime Name — Episódio 12: Episode title` when all fields exist.
  - Assert missing number/title produces only the available safe text and never `null`, `tomato:<id>`, or an empty label.
  - Assert non-latest sections continue deduplicating by anime id and use generic anime artwork.

- [ ] **Step 2: Run focused tests and confirm failure**
  - Run the same focused Tomato test command.
  - Expected: failure because current feed mapping calls `distinctBy(::animeIdOf)` for every section and `metaPreview` only uses `title.name`/`title.image`.

- [ ] **Step 3: Implement section-aware mapping**
  - Use the normalized section key `novos-episodios` to preserve episode entries; keep existing dedupe for other sections.
  - Add a pure formatter in `TomatoCatalog` (or the smallest nearby helper) that returns anime name plus episode number/title without technical fallback values.
  - Use episode thumbnail for latest cards and retain the anime canonical id.

- [ ] **Step 4: Run focused tests and confirm pass**
  - Re-run Tomato tests and verify all parser, dedupe, and label assertions pass.

- [ ] **Step 5: Commit**
  - Commit as `fix: show Tomato latest episode identity`.

### Task 3: Build and validate the user-visible result

**Files:**
- Inspect: `composeApp/src/commonMain/kotlin/com/kaplay/app/features/home/HomeRepository.kt`
- Inspect: `composeApp/src/commonMain/kotlin/com/kaplay/app/features/catalog/CatalogScreen.kt`
- Create: `docs/agents/KA_PLAY_TOMATO_LATEST_EPISODES_EVIDENCE_20261103.md`

**Interfaces:**
- Consumes: passing Tomato unit tests and the existing production/debug package.
- Produces: emulator evidence with anime name, episode label, thumbnail, and preserved data.

- [ ] **Step 1: Run the complete relevant test suite**
  - Run `./gradlew :composeApp:testAndroidHostTest --tests 'com.kaplay.app.features.tomato.*' --no-daemon --no-parallel --max-workers=2`.
  - Expected: zero failures.

- [ ] **Step 2: Build the full Debug APK without changing package or signing configuration**
  - Use the existing stable-signing CI workflow or local equivalent.
  - Record APK path, package, version, and SHA-256.

- [ ] **Step 3: Install/update on the already running emulator without destructive commands**
  - Confirm `adb devices` lists the emulator.
  - Use `adb install -r` only; do not call `adb uninstall` or `pm clear`.

- [ ] **Step 4: Exercise the Tomato home row**
  - Open `Novos episódios`, verify the card shows the real anime name, `Episódio <number>`, optional episode title, and non-empty episode artwork.
  - Tap the card and verify it opens the same anime detail; record the visible evidence.

- [ ] **Step 5: Save sanitized evidence and final verification**
  - Record payload aliases, displayed text, image URL presence, package/version, install command, and destructive-command status in the evidence file.
  - Run `git diff --check`, relevant CI checks, and `git status --short`.
  - Commit as `test: document Tomato latest episode validation`.
