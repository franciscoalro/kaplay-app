\set ON_ERROR_STOP on

insert into auth.users(id,email) values
 ('11111111-1111-1111-1111-111111111111','owner@example.test'),
 ('22222222-2222-2222-2222-222222222222','other@example.test');
insert into public.user_favorites(user_id,media_id) values
 ('11111111-1111-1111-1111-111111111111','legacy-favorite');
insert into public.watch_history(user_id,media_id,season_num,episode_num) values
 ('11111111-1111-1111-1111-111111111111','legacy-watch',1,1);

set role authenticated;
set request.jwt.claim.sub = '11111111-1111-1111-1111-111111111111';
set request.jwt.claims = '{"sub":"11111111-1111-1111-1111-111111111111","is_anonymous":false}';
select public.sync_push_profiles(6,
 '[{"profile_index":1,"name":"One"},{"profile_index":2,"name":"Two"}]'::jsonb,
 'test-client');
reset role;
create temp table first_profile_id as
 select id from public.ka_profiles
 where user_id='11111111-1111-1111-1111-111111111111' and profile_index=1;

set role authenticated;
select public.sync_push_profiles(6,
 '[{"profile_index":1,"name":"One renamed"},{"profile_index":2,"name":"Two"}]'::jsonb,
 'test-client');
reset role;
do $$ begin
 if (select id from public.ka_profiles where user_id='11111111-1111-1111-1111-111111111111' and profile_index=1) <>
    (select id from first_profile_id) then raise exception 'UUID changed on upsert'; end if;
 if exists(select 1 from public.user_favorites where profile_id is not null) or
    exists(select 1 from public.watch_history where profile_id is not null) then
   raise exception 'legacy row assigned automatically';
 end if;
end $$;

insert into ka_sync.watched_items(user_id,profile_id,content_id,content_type,title,season_num,episode_num,watched_at)
select '11111111-1111-1111-1111-111111111111',id,'old-data','movie','Old profile',-1,-1,1
from first_profile_id;
set role authenticated;
select public.sync_push_profiles(6,'[{"profile_index":2,"name":"Two"}]'::jsonb,'test-client');
select public.sync_push_profiles(6,
 '[{"profile_index":1,"name":"New One"},{"profile_index":2,"name":"Two"}]'::jsonb,
 'test-client');
reset role;
do $$ begin
 if (select id from public.ka_profiles where user_id='11111111-1111-1111-1111-111111111111' and profile_index=1) =
    (select id from first_profile_id) then raise exception 'UUID reused after recreation'; end if;
 if exists(select 1 from ka_sync.watched_items where content_id='old-data') then
   raise exception 'old profile data reexposed';
 end if;
end $$;

set role authenticated;
select public.sync_push_watched_items_v2(1,
 '[{"content_id":"same","content_type":"movie","title":"Movie","watched_at":10},
   {"content_id":"same","content_type":"series","title":"Series","watched_at":10}]'::jsonb,
 'sql-test');
select public.sync_delete_watched_items_v2(1,
 '[{"content_id":"same","content_type":"movie"}]'::jsonb,
 'sql-test');
do $$ begin
 if (select count(*) from public.sync_pull_watched_items_v2(1,500)) <> 1 or
    (select content_type from public.sync_pull_watched_items_v2(1,500)) <> 'series' then
   raise exception 'watched delete did not preserve the other content_type';
 end if;
end $$;

select public.sync_push_watch_progress_v2(1,
 (select jsonb_agg(jsonb_build_object(
   'progress_key',format('key-%s',lpad(g::text,4,'0')),
   'content_id','show','content_type','series','video_id',format('video-%s',g),
   'position',g,'duration',5000,'last_watched',1000)) from generate_series(1,500) g),
 'sql-test');
select public.sync_push_watch_progress_v2(1,
 (select jsonb_agg(jsonb_build_object(
   'progress_key',format('key-%s',lpad(g::text,4,'0')),
   'content_id','show','content_type','series','video_id',format('video-%s',g),
   'position',g,'duration',5000,'last_watched',1000)) from generate_series(501,1000) g),
 'sql-test');
select public.sync_push_watch_progress_v2(1,
 (select jsonb_agg(jsonb_build_object(
   'progress_key',format('key-%s',lpad(g::text,4,'0')),
   'content_id','show','content_type','series','video_id',format('video-%s',g),
   'position',g,'duration',5000,'last_watched',1000)) from generate_series(1001,1201) g),
 'sql-test');

create temp table progress_page1 as select * from public.sync_pull_watch_progress_v2(1,500);
create temp table progress_page2 as select * from public.sync_pull_watch_progress_v2(1,500,1000,(select max(progress_key) from progress_page1));
create temp table progress_page3 as select * from public.sync_pull_watch_progress_v2(1,500,1000,(select max(progress_key) from progress_page2));
do $$ begin
 if (select count(*) from progress_page1) <> 500 or
    (select count(*) from progress_page2) <> 500 or
    (select count(*) from progress_page3) <> 201 then
   raise exception 'progress keyset page sizes invalid';
 end if;
 if (select count(distinct progress_key) from (
      select progress_key from progress_page1 union all
      select progress_key from progress_page2 union all
      select progress_key from progress_page3) q) <> 1201 then
   raise exception 'progress keyset lost or duplicated rows';
 end if;
end $$;

create temp table stale_event_count as select count(*) n from ka_sync.watch_progress_events;
select public.sync_push_watch_progress_v2(1,
 '[{"progress_key":"key-0001","content_id":"show","content_type":"series","video_id":"video-1","position":9999,"duration":9999,"last_watched":998}]'::jsonb,
 'sql-test');
do $$ begin
 if (select position from ka_sync.watch_progress where progress_key='key-0001') <> 1 then
   raise exception 'stale progress overwrote newer value';
 end if;
 if (select count(*) from ka_sync.watch_progress_events) <> (select n from stale_event_count) then
   raise exception 'stale push emitted event';
 end if;
end $$;

create temp table invalid_batch_counts as
 select (select count(*) from ka_sync.watch_progress) rows_before,
        (select count(*) from ka_sync.watch_progress_events) events_before;
do $$ begin
 begin
   perform public.sync_push_watch_progress_v2(1,
     (select jsonb_agg(jsonb_build_object('progress_key',g::text,'content_id','x','content_type','series','video_id',g::text))
      from generate_series(1,501) g),'sql-test');
   raise exception 'expected oversized batch rejection';
 exception when sqlstate '22023' then null;
 end;
 if (select count(*) from ka_sync.watch_progress) <> (select rows_before from invalid_batch_counts) or
    (select count(*) from ka_sync.watch_progress_events) <> (select events_before from invalid_batch_counts) then
   raise exception 'invalid batch was not rolled back atomically';
 end if;
end $$;

set request.jwt.claims = '{"sub":"11111111-1111-1111-1111-111111111111","is_anonymous":true}';
do $$ begin
 begin perform public.sync_get_library_delta_cursor_v2(1); raise exception 'expected anonymous rejection';
 exception when sqlstate '42501' then null; end;
end $$;
set request.jwt.claim.sub = '22222222-2222-2222-2222-222222222222';
set request.jwt.claims = '{"sub":"22222222-2222-2222-2222-222222222222","is_anonymous":false}';
do $$ begin
 begin perform public.sync_get_library_delta_cursor_v2(1); raise exception 'expected cross-user rejection';
 exception when sqlstate '42501' then null; end;
end $$;
reset role;

do $$ begin
 if exists(select 1 from pg_proc p join pg_namespace n on n.oid=p.pronamespace
           where n.nspname='public' and p.proname like 'sync_%_v2' and p.prosecdef) then
   raise exception 'SECURITY DEFINER v2 RPC found';
 end if;
 if exists(select 1 from information_schema.routine_privileges
           where routine_schema='public' and routine_name like 'sync_%_v2' and grantee in ('PUBLIC','anon')) then
   raise exception 'unexpected PUBLIC/anon execute';
 end if;
end $$;

select 'ETAPA15_SYNC_V2_ISOLATED_OK' as result, 1201 as progress_rows, 15 as v2_rpcs;
