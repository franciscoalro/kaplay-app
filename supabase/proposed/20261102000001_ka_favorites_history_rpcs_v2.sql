-- KA Play Incremento 2: profile-scoped favorites/history persistence.
-- Isolated validation only; apply through the reviewed migration pipeline.
begin;

create table ka_sync.favorite_items (
  user_id uuid not null references auth.users(id) on delete cascade,
  profile_id uuid not null,
  media_id text not null check (octet_length(media_id) between 1 and 512),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (profile_id, media_id),
  foreign key (profile_id, user_id) references public.ka_profiles(id, user_id) on delete cascade
);
create table ka_sync.favorite_events (
  event_id bigint generated always as identity primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  profile_id uuid not null,
  operation text not null check (operation in ('upsert','delete')),
  media_id text not null check (octet_length(media_id) between 1 and 512),
  payload jsonb not null check (jsonb_typeof(payload) = 'object'),
  origin_client_id text check (origin_client_id is null or octet_length(origin_client_id) between 1 and 128),
  created_at timestamptz not null default now(),
  foreign key (profile_id, user_id) references public.ka_profiles(id, user_id) on delete cascade
);
create table ka_sync.history_items (
  user_id uuid not null references auth.users(id) on delete cascade,
  profile_id uuid not null,
  media_id text not null check (octet_length(media_id) between 1 and 512),
  season_num integer not null default -1,
  episode_num integer not null default -1,
  watched_at bigint not null default 0,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key (profile_id, media_id, season_num, episode_num),
  foreign key (profile_id, user_id) references public.ka_profiles(id, user_id) on delete cascade
);
create table ka_sync.history_events (
  event_id bigint generated always as identity primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  profile_id uuid not null,
  operation text not null check (operation in ('upsert','delete')),
  media_id text not null check (octet_length(media_id) between 1 and 512),
  season_num integer not null default -1,
  episode_num integer not null default -1,
  payload jsonb not null check (jsonb_typeof(payload) = 'object'),
  origin_client_id text check (origin_client_id is null or octet_length(origin_client_id) between 1 and 128),
  created_at timestamptz not null default now(),
  foreign key (profile_id, user_id) references public.ka_profiles(id, user_id) on delete cascade
);
create index favorite_items_page_idx on ka_sync.favorite_items(user_id, profile_id, updated_at desc, media_id);
create index favorite_events_cursor_idx on ka_sync.favorite_events(user_id, profile_id, event_id);
create index history_items_page_idx on ka_sync.history_items(user_id, profile_id, updated_at desc, media_id, season_num, episode_num);
create index history_events_cursor_idx on ka_sync.history_events(user_id, profile_id, event_id);

-- RLS is defense in depth; all access remains caller/profile scoped through the RPCs.
do $$ declare t regclass; begin
  foreach t in array array['ka_sync.favorite_items'::regclass,'ka_sync.favorite_events'::regclass,'ka_sync.history_items'::regclass,'ka_sync.history_events'::regclass] loop
    execute format('alter table %s enable row level security', t);
    execute format('alter table %s force row level security', t);
    execute format('create policy registered_owner_access on %s for all to authenticated using (user_id=(select auth.uid()) and not coalesce(((select auth.jwt())->>''is_anonymous'')::boolean,false)) with check (user_id=(select auth.uid()) and not coalesce(((select auth.jwt())->>''is_anonymous'')::boolean,false))', t);
    execute format('revoke all on table %s from public, anon', t);
    execute format('grant select, insert, update, delete on table %s to authenticated', t);
  end loop;
end $$;

revoke update, delete on table ka_sync.favorite_events, ka_sync.history_events from authenticated;
revoke all on sequence ka_sync.favorite_events_event_id_seq, ka_sync.history_events_event_id_seq from public, anon;
grant usage, select on sequence ka_sync.favorite_events_event_id_seq, ka_sync.history_events_event_id_seq to authenticated;

create or replace function public.sync_pull_favorites_v2(p_profile_index integer, p_limit integer default 500, p_after_updated_at timestamptz default null, p_after_media_id text default null)
returns table(media_id text, created_at timestamptz, updated_at timestamptz)
language plpgsql stable security invoker set search_path=''
as $$ declare uid uuid := (select auth.uid()); pid uuid := ka_sync.resolve_profile_id_v2(p_profile_index); begin
  if p_limit is null or p_limit not between 1 and 500 then raise exception using errcode='22023',message='limit must be between 1 and 500'; end if;
  if (p_after_updated_at is null) <> (p_after_media_id is null) then raise exception using errcode='22023',message='incomplete favorite cursor'; end if;
  return query select f.media_id,f.created_at,f.updated_at from ka_sync.favorite_items f where f.user_id=uid and f.profile_id=pid and (p_after_updated_at is null or f.updated_at<p_after_updated_at or (f.updated_at=p_after_updated_at and f.media_id>p_after_media_id)) order by f.updated_at desc,f.media_id limit p_limit;
end $$;

create or replace function public.sync_pull_history_v2(p_profile_index integer, p_limit integer default 500, p_after_updated_at timestamptz default null, p_after_media_id text default null, p_after_season integer default null, p_after_episode integer default null)
returns table(media_id text, season integer, episode integer, watched_at bigint, created_at timestamptz, updated_at timestamptz)
language plpgsql stable security invoker set search_path=''
as $$ declare uid uuid := (select auth.uid()); pid uuid := ka_sync.resolve_profile_id_v2(p_profile_index); begin
  if p_limit is null or p_limit not between 1 and 500 then raise exception using errcode='22023',message='limit must be between 1 and 500'; end if;
  if (p_after_updated_at is null) <> (p_after_media_id is null) or (p_after_updated_at is null) <> (p_after_season is null) or (p_after_updated_at is null) <> (p_after_episode is null) then raise exception using errcode='22023',message='incomplete history cursor'; end if;
  return query select h.media_id,nullif(h.season_num,-1),nullif(h.episode_num,-1),h.watched_at,h.created_at,h.updated_at from ka_sync.history_items h where h.user_id=uid and h.profile_id=pid and (p_after_updated_at is null or h.updated_at<p_after_updated_at or (h.updated_at=p_after_updated_at and (h.media_id,h.season_num,h.episode_num)>(p_after_media_id,p_after_season,p_after_episode))) order by h.updated_at desc,h.media_id,h.season_num,h.episode_num limit p_limit;
end $$;

create or replace function public.sync_push_favorites_v2(p_profile_index integer,p_items jsonb,p_origin_client_id text default null) returns void language plpgsql volatile security invoker set search_path='' as $$
declare uid uuid := (select auth.uid()); pid uuid := ka_sync.resolve_profile_id_v2(p_profile_index); x record; inserted_media text; begin
 perform ka_sync.assert_mutation_request_v2(p_items,p_origin_client_id);
 if exists(select 1 from jsonb_array_elements(p_items) v where jsonb_typeof(v)<>'object' or jsonb_typeof(v->'media_id') is distinct from 'string' or octet_length(v->>'media_id') not between 1 and 512) then raise exception using errcode='22023',message='invalid favorite item'; end if;
 if exists(select 1 from jsonb_to_recordset(p_items) r(media_id text) group by media_id having count(*)>1) then raise exception using errcode='22023',message='duplicate favorite key'; end if;
 for x in select * from jsonb_to_recordset(p_items) r(media_id text) loop
   insert into ka_sync.favorite_items(user_id,profile_id,media_id) values(uid,pid,x.media_id) on conflict(profile_id,media_id) do nothing returning media_id into inserted_media;
   if found then
     insert into ka_sync.favorite_events(user_id,profile_id,operation,media_id,payload,origin_client_id) values(uid,pid,'upsert',inserted_media,jsonb_build_object('media_id',inserted_media),p_origin_client_id);
   end if;
 end loop;
end $$;

create or replace function public.sync_push_history_v2(p_profile_index integer,p_items jsonb,p_origin_client_id text default null) returns void language plpgsql volatile security invoker set search_path='' as $$
declare uid uuid := (select auth.uid()); pid uuid := ka_sync.resolve_profile_id_v2(p_profile_index); x record; changed boolean; begin
 perform ka_sync.assert_mutation_request_v2(p_items,p_origin_client_id);
 if exists(select 1 from jsonb_array_elements(p_items) v where jsonb_typeof(v)<>'object' or jsonb_typeof(v->'media_id') is distinct from 'string' or octet_length(v->>'media_id') not between 1 and 512 or (v?'season' and jsonb_typeof(v->'season')<>'number') or (v?'episode' and jsonb_typeof(v->'episode')<>'number')) then raise exception using errcode='22023',message='invalid history item'; end if;
 if exists(select 1 from jsonb_to_recordset(p_items) r(media_id text,season integer,episode integer) group by media_id,coalesce(season,-1),coalesce(episode,-1) having count(*)>1) then raise exception using errcode='22023',message='duplicate history key'; end if;
 for x in select * from jsonb_to_recordset(p_items) r(media_id text,season integer,episode integer,watched_at bigint) loop
   insert into ka_sync.history_items as h(user_id,profile_id,media_id,season_num,episode_num,watched_at) values(uid,pid,x.media_id,coalesce(x.season,-1),coalesce(x.episode,-1),coalesce(x.watched_at,0)) on conflict(profile_id,media_id,season_num,episode_num) do update set watched_at=excluded.watched_at,updated_at=now() where excluded.watched_at>h.watched_at returning true into changed;
   if coalesce(changed,false) then insert into ka_sync.history_events(user_id,profile_id,operation,media_id,season_num,episode_num,payload,origin_client_id) values(uid,pid,'upsert',x.media_id,coalesce(x.season,-1),coalesce(x.episode,-1),jsonb_build_object('media_id',x.media_id,'season_num',coalesce(x.season,-1),'episode_num',coalesce(x.episode,-1),'watched_at',coalesce(x.watched_at,0)),p_origin_client_id); end if;
   changed:=false;
 end loop;
end $$;

create or replace function public.sync_delete_favorites_v2(p_profile_index integer,p_keys jsonb,p_origin_client_id text default null) returns void language plpgsql volatile security invoker set search_path='' as $$ declare uid uuid:=(select auth.uid()); pid uuid:=ka_sync.resolve_profile_id_v2(p_profile_index); k text; begin perform ka_sync.assert_mutation_request_v2(p_keys,p_origin_client_id); for k in select jsonb_array_elements_text(p_keys) loop delete from ka_sync.favorite_items f where f.user_id=uid and f.profile_id=pid and f.media_id=k returning f.media_id into k; if found then insert into ka_sync.favorite_events(user_id,profile_id,operation,media_id,payload,origin_client_id) values(uid,pid,'delete',k,jsonb_build_object('media_id',k),p_origin_client_id); end if; end loop; end $$;
create or replace function public.sync_delete_history_v2(p_profile_index integer,p_keys jsonb,p_origin_client_id text default null) returns void language plpgsql volatile security invoker set search_path='' as $$ declare uid uuid:=(select auth.uid()); pid uuid:=ka_sync.resolve_profile_id_v2(p_profile_index); x record; begin perform ka_sync.assert_mutation_request_v2(p_keys,p_origin_client_id); for x in select * from jsonb_to_recordset(p_keys) r(media_id text,season integer,episode integer) loop delete from ka_sync.history_items h where h.user_id=uid and h.profile_id=pid and h.media_id=x.media_id and h.season_num=coalesce(x.season,-1) and h.episode_num=coalesce(x.episode,-1); if found then insert into ka_sync.history_events(user_id,profile_id,operation,media_id,season_num,episode_num,payload,origin_client_id) values(uid,pid,'delete',x.media_id,coalesce(x.season,-1),coalesce(x.episode,-1),jsonb_build_object('media_id',x.media_id),p_origin_client_id); end if; end loop; end $$;

revoke all on function public.sync_pull_favorites_v2(integer,integer,timestamptz,text),public.sync_pull_history_v2(integer,integer,timestamptz,text,integer,integer),public.sync_push_favorites_v2(integer,jsonb,text),public.sync_push_history_v2(integer,jsonb,text),public.sync_delete_favorites_v2(integer,jsonb,text),public.sync_delete_history_v2(integer,jsonb,text) from public,anon;
grant execute on function public.sync_pull_favorites_v2(integer,integer,timestamptz,text),public.sync_pull_history_v2(integer,integer,timestamptz,text,integer,integer),public.sync_push_favorites_v2(integer,jsonb,text),public.sync_push_history_v2(integer,jsonb,text),public.sync_delete_favorites_v2(integer,jsonb,text),public.sync_delete_history_v2(integer,jsonb,text) to authenticated;
commit;
