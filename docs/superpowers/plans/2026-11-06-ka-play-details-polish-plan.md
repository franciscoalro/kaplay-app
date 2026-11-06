# KA Play Details Page Final Polish Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Polish the existing Details page with a real episode count, cleaner episode metadata/surfaces, and less aggressive hero artwork scaling while preserving all playback and profile state behavior.

**Architecture:** Reuse `DetailSeriesListHeader`, `SeriesSeasonSelector`, `KASeasonDropdown`, `DetailSeriesListEpisode`, `EpisodeListCard`, and `DetailHero`. Add only pure formatting/policy helpers where tests need deterministic behavior; keep episode grouping, progress lookup, callbacks, trailer plumbing, and background-mode branches unchanged.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, Material 3, Coil3, Robolectric Android host tests, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-11-06-ka-play-details-polish.md` plus the user-provided KA PLAY — POLIMENTO FINAL DA PÁGINA DE DETALHES Markdown.

## Global Constraints

- Do not rebuild existing Details components or add a second episode-list implementation.
- Preserve vertical episodes, `KASeasonDropdown`, playback/resume, history, watched state, ratings, progress, favorites, profile isolation, sync, trailer callbacks, and background modes.
- Use `KAColors`/`KAShapes`; no glassmorphism, glow, 3D, heavy shadows, or exaggerated transparency.
- Keep phone hero bounds `280.dp..340.dp` and tablet hero bounds `300.dp..420.dp`.
- Keep thumbnails at `128.dp` and `16:9` in the vertical list; keep menu target `48.dp`.
- Do not modify authentication, profile, watch-progress, watched, Supabase/RPC, or stream-resolution code.
- Do not run a Release workflow or publish users; build and validate Full Debug only.
- Do not run local Gradle; compile/tests run on CI.

## Review Focus

1. **Duplicate episode identity:** badge and title may contain episode identity; metadata must not repeat the S/E code.
2. **Season count freshness:** changing the selected season must update the count from that season's deduplicated list only.
3. **Completed progress:** completed entries must show watched state without an active progress bar.
4. **Hero modes:** normal, cinematic, dominant-color, and trailer-ready paths must retain their existing callbacks and visual branches.
5. **Narrow layout:** count/title/dropdown must not overlap below the inline-header breakpoint.

---

### Task 1: Compact Episode Header with Real Count

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/kaplay/app/features/details/components/DetailSeriesContent.kt`
- Test: `composeApp/src/androidHostTest/kotlin/com/kaplay/app/features/details/DetailVerticalEpisodeListTest.kt`

**Interfaces:**
- Consumes: `groupedEpisodes: Map<Int, List<MetaVideo>>`, `currentSeason: Int`, existing `KASeasonDropdown` and callbacks.
- Produces: `internal fun countUniqueSeasonEpisodes(episodes: List<MetaVideo>): Int`, using the existing canonical episode identity (`canonicalEpisodeKey()`).

- [x] **Step 1: Add failing tests** named `countsOnlyUniqueEpisodesForSelectedSeason` and `countChangesWithSelectedSeason`; assert duplicate episode records count once and different season lists produce independent counts.
- [x] **Step 2: Run the focused host test on CI and observe unresolved-function failure.**
- [x] **Step 3: Implement `countUniqueSeasonEpisodes` and render `settings_meta_episodes` with `($count)` beside the existing season/dropdown header, using the current season list only.**
- [x] **Step 4: Preserve the existing inline/stacked responsive policy, season callback, long-press callback, and no-playback-on-selection behavior.**
- [x] **Step 5: Run the targeted details host tests; expect PASS.**
- [x] **Step 6: Commit `feat(details): show current season episode count`.**

---

### Task 2: Darker Cards and Non-Duplicated Metadata

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/kaplay/app/features/details/components/DetailSeriesContent.kt`
- Test: `composeApp/src/androidHostTest/kotlin/com/kaplay/app/features/details/DetailEpisodeListCardTest.kt`

**Interfaces:**
- Consumes: existing `MetaVideo`, runtime/rating/date formatting, progress/watched rendering.
- Produces: `internal fun buildEpisodeMetadataLine(runtimeLabel: String?, ratingLabel: String?, formattedDate: String?): String`, excluding season/episode identity.

- [x] **Step 1: Add failing tests** named `metadataLineDoesNotRepeatSeasonEpisodeCode` and `metadataLineKeepsRuntimeRatingAndDate`; assert the line omits S/E code while retaining available secondary data.
- [x] **Step 2: Run the focused host test on CI and observe unresolved-function failure.**
- [x] **Step 3: Implement the helper and replace the inline metadata builder; keep the thumbnail `EpisodeCodeBadge` as the single episode identity display.**
- [x] **Step 4: Change the list card surface to `KAColors.Surface` and border to `KAColors.Outline.copy(alpha = 0.35f)`, preserving the white focused border, 16:9 image, progress bar, watched badge, and 48dp menu.**
- [x] **Step 5: Run card, dropdown, vertical-list, and details host tests; expect PASS.**
- [x] **Step 6: Commit `fix(details): reduce episode card contrast and duplicate metadata`.**

---

### Task 3: Hero Contrast and Artwork Framing

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/kaplay/app/features/details/components/DetailHero.kt`
- Test: `composeApp/src/androidHostTest/kotlin/com/kaplay/app/features/details/DetailHeroCompactTest.kt`

**Interfaces:**
- Consumes: existing `DetailHero` trailer/background/inset inputs and `detailHeroHeight`.
- Produces: `internal const val DETAIL_HERO_ARTWORK_SCALE: Float = 1f` as the explicit non-exaggerated base scale policy.

- [x] **Step 1: Add a failing test** named `heroArtworkBaseScaleDoesNotApplyFixedZoom`; assert `DETAIL_HERO_ARTWORK_SCALE == 1f`.
- [x] **Step 2: Run the focused host test on CI and observe unresolved-constant failure.**
- [x] **Step 3: Replace the fixed `1.08f` artwork/trailer scale with `DETAIL_HERO_ARTWORK_SCALE`; retain `heroStretchZoom` for scroll/stretch behavior.**
- [x] **Step 4: Add a subtle top-to-transparent black scrim behind hero chrome while retaining the existing bottom gradient, status-bar inset, trailer player, mute control, and background-mode inputs.**
- [x] **Step 5: Run hero compact and details host tests; expect PASS and unchanged `280..340.dp` phone bounds.**
- [x] **Step 6: Commit `fix(details): refine hero framing and contrast`.**

---

### Task 4: Full CI Verification and Debug APK

**Files:**
- Modify: `docs/superpowers/plans/2026-11-06-ka-play-details-polish-plan.md`

- [x] **Step 1: Push the feature branch to `buildci` and run the targeted details host test command on CI.**
- [x] **Step 2: Run `kaplay-build` for compile/security checks and host regression tests.**
- [x] **Step 3: Trigger `KA Play Android build only`; collect final Full Debug APK metadata and SHA-256.**
- [x] **Step 4: Capture available CI screenshots; explicitly report if they do not cover Details UI.**
- [x] **Step 5: Request a fresh whole-branch code review; fix Critical/Important findings with RED→GREEN tests.**
- [x] **Step 6: Update this plan and ledger with exact evidence; do not trigger `android-release.yml`.**

## Verification Evidence

- Targeted Details host workflow `42286201087` passed on `b97b449d`.
- `kaplay-build` compiled the branch and ran the expanded Details filter, but the full regression invocation exposed three pre-existing Details test failures (`EpisodeActionSheetTest` x2 and `RatingsVisibilityTest` x1); none touch the changed hero/count/card paths. This is recorded as a regression-suite limitation, not masked.
- `KA Play Android build only` run `42284208707` built and uploaded the Full Debug APK from `a23a52e8`; the final hero-only layering fix is source-equivalent for build output and requires a fresh build if a hash tied exactly to `b97b449d` is required. No release workflow was triggered.
- Final APK artifact metadata: `167542254` bytes, SHA-256 `de401d9019a9338a63b43fafe9cd44fe64064a2dd2320dbdd9b593c07b2e645b`.
- CI screenshots available from the build workflow cover Library/Profile smoke surfaces, not the Details screen; no Details screenshot claim is made.
- Final fresh review at `b97b449d`: no findings; `git diff --check` passed.
