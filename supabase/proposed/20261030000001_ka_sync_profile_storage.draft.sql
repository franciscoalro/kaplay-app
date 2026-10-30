-- KA Play sync profile storage — PROPOSAL ONLY
-- Do not execute. This file is a reviewed design draft, not a production migration.
-- It intentionally aborts until the product confirms legacy -> profile 1 and
-- the backup verification succeeds.

begin;

do $$
begin
  raise exception using
    errcode = 'P0001',
    message = 'Draft only: confirm legacy profile ownership and backup before applying KA sync storage changes';
end
$$;

/*
PHASE A — legacy profile scope, after explicit product confirmation

alter table public.user_favorites
  add column profile_index integer not null default 1;

alter table public.watch_history
  add column profile_index integer not null default 1;

alter table public.user_favorites
  add constraint user_favorites_profile_index_check
  check (profile_index between 1 and 6);

alter table public.watch_history
  add constraint watch_history_profile_index_check
  check (profile_index between 1 and 6);

-- Verify no duplicate keys before replacing the old primary keys.
-- Abort if either query returns rows:
select user_id, profile_index, media_id, count(*)
from public.user_favorites
group by user_id, profile_index, media_id
having count(*) > 1;

select user_id, profile_index, media_id, season_num, episode_num, count(*)
from public.watch_history
group by user_id, profile_index, media_id, season_num, episode_num
having count(*) > 1;

alter table public.user_favorites drop constraint user_favorites_pkey;
alter table public.user_favorites
  add constraint user_favorites_pkey primary key (user_id, profile_index, media_id);

alter table public.watch_history drop constraint watch_history_pkey;
alter table public.watch_history
  add constraint watch_history_pkey
  primary key (user_id, profile_index, media_id, season_num, episode_num);

create index user_favorites_profile_updated_idx
  on public.user_favorites (user_id, profile_index, updated_at desc);

create index watch_history_profile_updated_idx
  on public.watch_history (user_id, profile_index, updated_at desc);

-- Recreate policies with authenticated ownership and retain RLS.
-- Do not grant direct access to anon.
*/

/*
PHASE B — dedicated sync storage

The legacy watch_history row has no video_id or progress_key. Do not invent
those values. Create dedicated tables only after approving the schema in
PLANO_IMPLEMENTACAO_SYNC_VALIDADO.md:

- public.ka_library_items
- public.ka_library_events
- public.ka_watched_items
- public.ka_watched_events
- public.ka_watch_progress
- public.ka_watch_progress_events

Every table must carry user_id + profile_index and have RLS. Progress must use
progress_key as the logical key and preserve video_id, position, duration and
last_watched. Event tables must have a monotonic event_id and tombstones.
*/

rollback;
