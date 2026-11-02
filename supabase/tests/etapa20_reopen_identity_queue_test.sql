-- KA Play Incremento 2 deterministic persistence/identity/offline queue checks.
-- ISOLATED VALIDATION ONLY. Never run against production.
-- Executed after the v2 schema and RPC tests by the isolated PostgreSQL workflow.

\set ON_ERROR_STOP on

insert into auth.users(id, email) values
  ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'inc2-a@example.test'),
  ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'inc2-b@example.test')
on conflict (id) do nothing;

-- Login as account A and create two independent profile slots.
set role authenticated;
set request.jwt.claim.sub = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa';
set request.jwt.claims = '{"sub":"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa","is_anonymous":false}';
select public.sync_push_profiles(6,
  '[{"profile_index":1,"name":"A-one"},{"profile_index":2,"name":"A-two"}]'::jsonb,
  'inc2-device-a');

-- Offline queue replay: replaying an identical queued mutation after reconnect is a no-op.
select public.sync_push_library_items_v2(1,
  '[{"content_id":"inc2-title","content_type":"movie","name":"Queued title","added_at":10}]'::jsonb,
  'inc2-device-a');
select public.sync_push_library_items_v2(1,
  '[{"content_id":"inc2-title","content_type":"movie","name":"Queued title","added_at":10}]'::jsonb,
  'inc2-device-a');
select public.sync_push_watch_progress_v2(1,
  '[{"progress_key":"inc2-progress","content_id":"inc2-title","content_type":"movie","video_id":"inc2-video","position":20,"duration":100,"last_watched":200}]'::jsonb,
  'inc2-device-a');
select public.sync_push_watch_progress_v2(1,
  '[{"progress_key":"inc2-progress","content_id":"inc2-title","content_type":"movie","video_id":"inc2-video","position":20,"duration":100,"last_watched":200}]'::jsonb,
  'inc2-device-a');
select public.sync_push_watched_items_v2(1,
  '[{"content_id":"inc2-title","content_type":"movie","title":"Queued title","watched_at":300}]'::jsonb,
  'inc2-device-a');
select public.sync_push_watched_items_v2(1,
  '[{"content_id":"inc2-title","content_type":"movie","title":"Queued title","watched_at":300}]'::jsonb,
  'inc2-device-a');

select
  'INC2_AFTER_REPLAY' as checkpoint,
  (select count(*) from ka_sync.library_items where content_id = 'inc2-title') as library_items,
  (select count(*) from ka_sync.watch_progress where progress_key = 'inc2-progress') as watch_progress,
  (select count(*) from ka_sync.watched_items where content_id = 'inc2-title') as watched_items;

do $$
begin
  if (select count(*) from ka_sync.library_events where content_id = 'inc2-title') <> 1
     or (select count(*) from ka_sync.watch_progress_events where progress_key = 'inc2-progress') <> 1
     or (select count(*) from ka_sync.library_items where content_id = 'inc2-title') <> 1
     or (select count(*) from ka_sync.watch_progress where progress_key = 'inc2-progress') <> 1
     or (select count(*) from ka_sync.watched_items where content_id = 'inc2-title') <> 1 then
    raise exception 'INC2 FAILED: duplicate reconnect replay was not idempotent';
  end if;
end $$;

-- A new database session models process close/reopen; committed rows and cursors must survive.
\connect kaplay_sync_test
set role authenticated;
set request.jwt.claim.sub = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa';
set request.jwt.claims = '{"sub":"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa","is_anonymous":false}';
do $$
begin
  if not exists (select 1 from public.sync_pull_library_v2(1, 50) where content_id = 'inc2-title') then
    raise exception 'INC2 FAILED: library row did not persist across reopen';
  end if;
  if not exists (select 1 from public.sync_pull_watch_progress_v2(1, 50) where progress_key = 'inc2-progress' and position = 20) then
    raise exception 'INC2 FAILED: progress row did not persist across reopen';
  end if;
  if not exists (select 1 from public.sync_pull_watched_items_v2(1, 50) where content_id = 'inc2-title') then
    raise exception 'INC2 FAILED: watched row did not persist across reopen';
  end if;
end $$;

-- Logout/login identity boundary: account B must not see A, then A must recover A's data.
reset role;
set role authenticated;
set request.jwt.claim.sub = 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb';
set request.jwt.claims = '{"sub":"bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb","is_anonymous":false}';
do $$
begin
  if exists (select 1 from public.sync_pull_profiles() where name in ('A-one','A-two')) then
    raise exception 'INC2 FAILED: account B saw account A profiles';
  end if;
  begin
    perform public.sync_pull_library_v2(1, 50);
    raise exception 'INC2 FAILED: account B accessed account A profile slot';
  exception when sqlstate '42501' then null;
  end;
end $$;
reset role;

set role authenticated;
set request.jwt.claim.sub = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa';
set request.jwt.claims = '{"sub":"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa","is_anonymous":false}';
do $$
begin
  if (select count(*) from public.sync_pull_profiles()) <> 2 then
    raise exception 'INC2 FAILED: account A profile list changed across logout/login';
  end if;
  if not exists (select 1 from public.sync_pull_library_v2(1, 50) where content_id = 'inc2-title') then
    raise exception 'INC2 FAILED: account A data missing after logout/login';
  end if;
end $$;
reset role;

-- Profile isolation within one account: profile 2 cannot read profile 1's content.
set role authenticated;
set request.jwt.claim.sub = 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa';
set request.jwt.claims = '{"sub":"aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa","is_anonymous":false}';
do $$
begin
  if exists (select 1 from public.sync_pull_library_v2(2, 50) where content_id = 'inc2-title') then
    raise exception 'INC2 FAILED: profile 2 saw profile 1 library data';
  end if;
  if exists (select 1 from public.sync_pull_watch_progress_v2(2, 50) where progress_key = 'inc2-progress') then
    raise exception 'INC2 FAILED: profile 2 saw profile 1 progress data';
  end if;
  if exists (select 1 from public.sync_pull_watched_items_v2(2, 50) where content_id = 'inc2-title') then
    raise exception 'INC2 FAILED: profile 2 saw profile 1 watched data';
  end if;
end $$;
reset role;

select 'ETAPA20_REOPEN_IDENTITY_QUEUE_OK' as result;
