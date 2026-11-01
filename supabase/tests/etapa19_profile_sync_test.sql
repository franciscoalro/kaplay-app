-- etapa19_profile_sync_test.sql
-- Isolated TDD assertions for ka-profile-sync-v1 contract (Task 2).
-- Run against: isolated postgres:17-alpine via run_etapa15_sync_v2_isolated.sh
-- Contract: docs/contracts/ka-profile-sync-v1.md

\set ON_ERROR_STOP on

-- Setup: two users with profiles pushed via sync_push_profiles
insert into auth.users(id,email) values
  ('11111111-1111-1111-1111-111111111111','owner@example.test'),
  ('22222222-2222-2222-2222-222222222222','other@example.test')
  on conflict (id) do nothing;

-- User 1: push two profiles
set role authenticated;
set request.jwt.claim.sub = '11111111-1111-1111-1111-111111111111';
set request.jwt.claims = '{"sub":"11111111-1111-1111-1111-111111111111","is_anonymous":false}';
select public.sync_push_profiles(6,
  '[{"profile_index":1,"name":"ProfileOne"},{"profile_index":2,"name":"ProfileTwo"}]'::jsonb,
  'etapa19-owner');
reset role;

-- User 2: push one profile
set role authenticated;
set request.jwt.claim.sub = '22222222-2222-2222-2222-222222222222';
set request.jwt.claims = '{"sub":"22222222-2222-2222-2222-222222222222","is_anonymous":false}';
select public.sync_push_profiles(6,
  '[{"profile_index":1,"name":"OtherProfile"}]'::jsonb,
  'etapa19-other');
reset role;

------------------------------------------------------------
-- ASSERTION 1: same-user profile access
-- sync_pull_profiles must return exactly the caller's own profiles
------------------------------------------------------------
set role authenticated;
set request.jwt.claim.sub = '11111111-1111-1111-1111-111111111111';
set request.jwt.claims = '{"sub":"11111111-1111-1111-1111-111111111111","is_anonymous":false}';
create temp table same_user_profiles as
  select profile_index, name from public.sync_pull_profiles()
  order by profile_index;
reset role;
do $$ begin
  if (select count(*) from same_user_profiles) <> 2 then
    raise exception 'ASSERTION 1 FAILED: expected 2 profiles, got %',
      (select count(*) from same_user_profiles);
  end if;
  if (select count(*) from same_user_profiles where name = 'ProfileOne') <> 1 or
     (select count(*) from same_user_profiles where name = 'ProfileTwo') <> 1 then
    raise exception 'ASSERTION 1 FAILED: same-user names do not match';
  end if;
end $$;

------------------------------------------------------------
-- ASSERTION 2: cross-user rejection
-- User 1 must NOT see User 2's profile in sync_pull_profiles
------------------------------------------------------------
set role authenticated;
set request.jwt.claim.sub = '11111111-1111-1111-1111-111111111111';
set request.jwt.claims = '{"sub":"11111111-1111-1111-1111-111111111111","is_anonymous":false}';
do $$ begin
  if exists (
    select 1 from public.sync_pull_profiles() p where p.name = 'OtherProfile'
  ) then
    raise exception 'ASSERTION 2 FAILED: cross-user data was returned';
  end if;
end $$;
reset role;

------------------------------------------------------------
-- ASSERTION 3: anonymous rejection
-- sync_pull_profiles must reject unauthenticated sessions
------------------------------------------------------------
set role authenticated;
set request.jwt.claim.sub = '00000000-0000-0000-0000-000000000000';
set request.jwt.claims = '{"sub":"00000000-0000-0000-0000-000000000000","is_anonymous":true}';
do $$ begin
  perform public.sync_pull_profiles();
  raise exception 'ASSERTION 3 FAILED: anonymous session was not rejected';
exception when sqlstate '42501' then null;  -- permission denied
         when others then
    if sqlstate <> '42501' then
      raise exception 'ASSERTION 3 FAILED: wrong error code %', sqlstate;
    end if;
end $$;
reset role;

------------------------------------------------------------
-- ASSERTION 4: anonymous rejection on sync_push_profiles
------------------------------------------------------------
set role authenticated;
set request.jwt.claim.sub = '00000000-0000-0000-0000-000000000000';
set request.jwt.claims = '{"sub":"00000000-0000-0000-0000-000000000000","is_anonymous":true}';
do $$ begin
  perform public.sync_push_profiles(6,
    '[{"profile_index":1,"name":"AnonTest"}]'::jsonb, 'etapa19-anon');
  raise exception 'ASSERTION 4 FAILED: anonymous push was not rejected';
exception when sqlstate '42501' then null;
         when others then
    if sqlstate <> '42501' then
      raise exception 'ASSERTION 4 FAILED: wrong error code %', sqlstate;
    end if;
end $$;
reset role;

------------------------------------------------------------
-- ASSERTION 5: stable error — invalid profile_index rejected
-- profile_index must be 1-6
------------------------------------------------------------
set role authenticated;
set request.jwt.claim.sub = '11111111-1111-1111-1111-111111111111';
set request.jwt.claims = '{"sub":"11111111-1111-1111-1111-111111111111","is_anonymous":false}';

-- Test null
do $$ begin
  perform public.sync_push_profiles(null,
    '[{"profile_index":1,"name":"Test"}]'::jsonb, 'etapa19-test');
  raise exception 'ASSERTION 5a FAILED: null max_profiles not rejected';
exception when sqlstate '22023' then null;
         when others then
    if sqlstate <> '22023' then raise exception 'ASSERTION 5a FAILED: wrong code %', sqlstate; end if;
end $$;

-- Test > 6
do $$ begin
  perform public.sync_push_profiles(7,
    '[{"profile_index":1,"name":"Test"}]'::jsonb, 'etapa19-test');
  raise exception 'ASSERTION 5b FAILED: max_profiles > 6 not rejected';
exception when sqlstate '22023' then null;
         when others then
    if sqlstate <> '22023' then raise exception 'ASSERTION 5b FAILED: wrong code %', sqlstate; end if;
end $$;

-- Test > batch size
do $$ begin
  perform public.sync_push_profiles(6,
    '[{"profile_index":1,"name":"A"},{"profile_index":2,"name":"B"},{"profile_index":3,"name":"C"},{"profile_index":4,"name":"D"},{"profile_index":5,"name":"E"},{"profile_index":6,"name":"F"},{"profile_index":7,"name":"G"}]'::jsonb,
    'etapa19-test');
  raise exception 'ASSERTION 5c FAILED: batch > max_profiles not rejected';
exception when sqlstate '22023' then null;
         when others then
    if sqlstate <> '22023' then raise exception 'ASSERTION 5c FAILED: wrong code %', sqlstate; end if;
end $$;
reset role;

------------------------------------------------------------
-- ASSERTION 6: profile-index isolation
-- RLS policy must enforce user_id = auth.uid() on ka_profiles
-- Verify direct SELECT is blocked for other user's rows
------------------------------------------------------------
set role authenticated;
set request.jwt.claim.sub = '11111111-1111-1111-1111-111111111111';
set request.jwt.claims = '{"sub":"11111111-1111-1111-1111-111111111111","is_anonymous":false}';
do $$ begin
  perform 1 from public.ka_profiles
  where user_id = '22222222-2222-2222-2222-222222222222'
    and profile_index = 1
    and name = 'OtherProfile';
  raise exception 'ASSERTION 6 FAILED: direct SELECT returned cross-user row via RLS';
exception when no_data_found then null;
         when sqlstate '42501' then null;
end $$;
reset role;

------------------------------------------------------------
-- ASSERTION 7: profile reassignment rejected
-- sync_push_profiles must only write rows with user_id = auth.uid()
-- (enforced by auth.uid() in the INSERT SELECT)
------------------------------------------------------------
set role authenticated;
set request.jwt.claim.sub = '11111111-1111-1111-1111-111111111111';
set request.jwt.claims = '{"sub":"11111111-1111-1111-1111-111111111111","is_anonymous":false}';
-- Push a profile — it must be owned by user 1, never user 2
select public.sync_push_profiles(6,
  '[{"profile_index":3,"name":"UserOneOnly"}]'::jsonb, 'etapa19-reassign-check');
create temp table reassign_check as
  select user_id, profile_index from public.ka_profiles
  where profile_index = 3;
reset role;
do $$ begin
  if (select count(*) from reassign_check) <> 1 then
    raise exception 'ASSERTION 7 FAILED: expected exactly 1 row after push';
  end if;
  if (select user_id from reassign_check) <> '11111111-1111-1111-1111-111111111111' then
    raise exception 'ASSERTION 7 FAILED: profile was written under wrong user_id';
  end if;
end $$;

------------------------------------------------------------
-- ASSERTION 8: stable error on non-array payload
------------------------------------------------------------
set role authenticated;
set request.jwt.claim.sub = '11111111-1111-1111-1111-111111111111';
set request.jwt.claims = '{"sub":"11111111-1111-1111-1111-111111111111","is_anonymous":false}';
do $$ begin
  perform public.sync_push_profiles(6, '{"not":"an array"}'::jsonb, 'etapa19-test');
  raise exception 'ASSERTION 8 FAILED: non-array payload not rejected';
exception when sqlstate '22023' then null;
         when others then
    if sqlstate <> '22023' then raise exception 'ASSERTION 8 FAILED: wrong code %', sqlstate; end if;
end $$;
reset role;

------------------------------------------------------------
-- Guard: verify no SECURITY DEFINER on profile RPCs
------------------------------------------------------------
do $$ begin
  if exists(
    select 1 from pg_proc p
    join pg_namespace n on n.oid = p.pronamespace
    where n.nspname = 'public'
      and p.proname in ('sync_pull_profiles', 'sync_push_profiles')
      and p.prosecdef
  ) then
    raise exception 'ASSERTION GUARD FAILED: profile RPCs must not be SECURITY DEFINER';
  end if;
end $$;

select 'ETAPA19_PROFILE_SYNC_TEST_OK' as result;
