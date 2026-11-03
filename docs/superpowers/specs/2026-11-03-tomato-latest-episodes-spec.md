# Tomato latest episodes metadata

## Goal

The KA Play Tomato integration must preserve the real anime name, episode number/title, and the correct image from the `/v2/animes/feed` payload. The `Novos episódios` home section must visibly identify both the anime and the episode being released, while opening the corresponding anime detail.

## Required behavior

- Read anime identity from `anime_name` (with existing aliases as fallback) and reject technical identifiers.
- Read episode identity from `ep_id`/`episode_id`, `ep_number`/`episode_number`, and `ep_name`/`episode_name` aliases.
- Read episode artwork from `ep_thumbnail` first, then feed image aliases; preserve anime artwork for non-episode feeds.
- Do not collapse multiple latest-episode entries solely because they share the same anime id.
- For `novos-episodios`, render a stable display label containing the real anime name and `Episódio <number>`; append the episode title when available.
- Keep the canonical meta id as `tomato:<animeId>` so the existing detail/season/stream flow remains intact.
- Never uninstall the app, clear app data, or change the application id during validation.

## Acceptance evidence

- Unit tests prove feed aliases, episode metadata, thumbnail precedence, duplicate handling, and display-label formatting.
- A built app or live payload shows non-technical anime names, correct episode numbers/titles, and non-empty artwork in the latest-episode section.
- Emulator validation confirms the updated card opens the matching anime and no destructive data operation is performed.
