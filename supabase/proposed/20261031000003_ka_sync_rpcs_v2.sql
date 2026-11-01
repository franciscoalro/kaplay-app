-- KA Play Etapa 15 / Migration C3 candidate.
-- ISOLATED VALIDATION ONLY. DO NOT APPLY TO PRODUCTION.
-- UUID-scoped synchronization RPCs for the current Kotlin v2 contracts.

begin;

-- All public RPCs resolve the caller-owned profile slot to its immutable UUID here.
create or replace function ka_sync.resolve_profile_id_v2(p_profile_index integer)
returns uuid
language plpgsql
stable
security invoker
set search_path = ''
as $$
declare
  v_user_id uuid := (select auth.uid());
  v_profile_id uuid;
  v_is_anonymous boolean := coalesce(((select auth.jwt()) ->> 'is_anonymous')::boolean, false);
begin
  if v_user_id is null or v_is_anonymous then
    raise exception using errcode = '42501', message = 'registered authentication required';
  end if;
  if p_profile_index is null or p_profile_index not between 1 and 6 then
    raise exception using errcode = '22023', message = 'invalid profile index';
  end if;

  select p.id into v_profile_id
  from public.ka_profiles p
  where p.user_id = v_user_id and p.profile_index = p_profile_index;

  if v_profile_id is null then
    raise exception using errcode = '42501', message = 'profile access denied';
  end if;
  return v_profile_id;
end
$$;

create or replace function ka_sync.assert_mutation_request_v2(p_value jsonb, p_origin_client_id text)
returns void
language plpgsql
immutable
security invoker
set search_path = ''
as $$
begin
  if jsonb_typeof(p_value) is distinct from 'array' then
    raise exception using errcode = '22023', message = 'payload must be an array';
  end if;
  if jsonb_array_length(p_value) > 500 then
    raise exception using errcode = '22023', message = 'batch exceeds 500 items';
  end if;
  if octet_length(p_value::text) > 1048576 then
    raise exception using errcode = '22023', message = 'payload exceeds 1 MiB';
  end if;
  if p_origin_client_id is not null and
     (octet_length(p_origin_client_id) not between 1 and 128 or p_origin_client_id <> btrim(p_origin_client_id)) then
    raise exception using errcode = '22023', message = 'invalid origin client id';
  end if;
end
$$;

-- Library --------------------------------------------------------------------
create or replace function public.sync_get_library_delta_cursor_v2(p_profile_index integer)
returns bigint
language plpgsql
stable
security invoker
set search_path = ''
as $$
declare
  v_user_id uuid := (select auth.uid());
  v_profile_id uuid := ka_sync.resolve_profile_id_v2(p_profile_index);
begin
  return coalesce((
    select max(e.event_id) from ka_sync.library_events e
    where e.user_id = v_user_id and e.profile_id = v_profile_id
  ), 0);
end
$$;

create or replace function public.sync_pull_library_v2(
  p_profile_index integer,
  p_limit integer default 500,
  p_after_added_at bigint default null,
  p_after_content_id text default null,
  p_after_content_type text default null
)
returns table (
  content_id text, content_type text, name text, poster text, poster_shape text,
  background text, logo text, description text, release_info text,
  imdb_rating real, genres text[], addon_base_url text, added_at bigint
)
language plpgsql
stable
security invoker
set search_path = ''
as $$
declare
  v_user_id uuid := (select auth.uid());
  v_profile_id uuid := ka_sync.resolve_profile_id_v2(p_profile_index);
begin
  if p_limit is null or p_limit not between 1 and 500 then
    raise exception using errcode = '22023', message = 'limit must be between 1 and 500';
  end if;
  if (p_after_added_at is null) <> (p_after_content_id is null) or
     (p_after_added_at is null) <> (p_after_content_type is null) then
    raise exception using errcode = '22023', message = 'incomplete library cursor';
  end if;
  if p_after_content_id is not null and
     (octet_length(p_after_content_id) not between 1 and 512 or
      octet_length(p_after_content_type) not between 1 and 64) then
    raise exception using errcode = '22023', message = 'invalid library cursor';
  end if;

  return query
  select i.content_id, i.content_type, i.name, i.poster, i.poster_shape,
         i.background, i.logo, i.description, i.release_info,
         i.imdb_rating, i.genres, i.addon_base_url, i.added_at
  from ka_sync.library_items i
  where i.user_id = v_user_id and i.profile_id = v_profile_id
    and (p_after_added_at is null or i.added_at < p_after_added_at or
         (i.added_at = p_after_added_at and
          (i.content_id, i.content_type) > (p_after_content_id, p_after_content_type)))
  order by i.added_at desc, i.content_id, i.content_type
  limit p_limit;
end
$$;

create or replace function public.sync_pull_library_delta_v2(
  p_profile_index integer, p_since_event_id bigint, p_limit integer
)
returns table (
  event_id bigint, operation text, content_id text, content_type text,
  name text, poster text, poster_shape text, background text, logo text,
  description text, release_info text, imdb_rating real, genres text[],
  addon_base_url text, added_at bigint
)
language plpgsql
stable
security invoker
set search_path = ''
as $$
declare
  v_user_id uuid := (select auth.uid());
  v_profile_id uuid := ka_sync.resolve_profile_id_v2(p_profile_index);
begin
  if p_since_event_id is null or p_since_event_id < 0 or p_limit is null or p_limit not between 1 and 500 then
    raise exception using errcode = '22023', message = 'invalid delta cursor or limit';
  end if;
  return query
  select e.event_id, e.operation, e.content_id, e.content_type,
         coalesce(e.payload ->> 'name', ''), e.payload ->> 'poster',
         coalesce(e.payload ->> 'poster_shape', 'POSTER'), e.payload ->> 'background',
         e.payload ->> 'logo', e.payload ->> 'description', e.payload ->> 'release_info',
         nullif(e.payload ->> 'imdb_rating', '')::real,
         coalesce(array(select jsonb_array_elements_text(coalesce(e.payload -> 'genres', '[]'::jsonb))), '{}'::text[]),
         e.payload ->> 'addon_base_url', coalesce((e.payload ->> 'added_at')::bigint, 0)
  from ka_sync.library_events e
  where e.user_id = v_user_id and e.profile_id = v_profile_id and e.event_id > p_since_event_id
  order by e.event_id
  limit p_limit;
end
$$;

create or replace function public.sync_push_library_items_v2(
  p_profile_index integer, p_items jsonb, p_origin_client_id text default null
)
returns void
language plpgsql
volatile
security invoker
set search_path = ''
as $$
declare
  v_user_id uuid := (select auth.uid());
  v_profile_id uuid := ka_sync.resolve_profile_id_v2(p_profile_index);
  x record;
  v_row ka_sync.library_items%rowtype;
begin
  perform ka_sync.assert_mutation_request_v2(p_items, p_origin_client_id);
  if exists (select 1 from jsonb_array_elements(p_items) v where jsonb_typeof(v) <> 'object') or
     exists (select 1 from jsonb_array_elements(p_items) v where
       jsonb_typeof(v -> 'content_id') is distinct from 'string' or octet_length(v ->> 'content_id') not between 1 and 512 or
       jsonb_typeof(v -> 'content_type') is distinct from 'string' or octet_length(v ->> 'content_type') not between 1 and 64 or
       (v ? 'name' and jsonb_typeof(v -> 'name') <> 'string') or octet_length(coalesce(v ->> 'name', '')) > 512 or
       (v ? 'poster' and jsonb_typeof(v -> 'poster') not in ('string','null')) or octet_length(coalesce(v ->> 'poster', '')) > 4096 or
       (v ? 'poster_shape' and jsonb_typeof(v -> 'poster_shape') <> 'string') or octet_length(coalesce(v ->> 'poster_shape', 'POSTER')) > 32 or
       (v ? 'background' and jsonb_typeof(v -> 'background') not in ('string','null')) or octet_length(coalesce(v ->> 'background', '')) > 4096 or
       (v ? 'logo' and jsonb_typeof(v -> 'logo') not in ('string','null')) or octet_length(coalesce(v ->> 'logo', '')) > 4096 or
       (v ? 'description' and jsonb_typeof(v -> 'description') not in ('string','null')) or octet_length(coalesce(v ->> 'description', '')) > 16384 or
       (v ? 'release_info' and jsonb_typeof(v -> 'release_info') not in ('string','null')) or octet_length(coalesce(v ->> 'release_info', '')) > 512 or
       (v ? 'addon_base_url' and jsonb_typeof(v -> 'addon_base_url') not in ('string','null')) or octet_length(coalesce(v ->> 'addon_base_url', '')) > 4096 or
       (v ? 'genres' and jsonb_typeof(v -> 'genres') <> 'array') or jsonb_array_length(coalesce(v -> 'genres', '[]'::jsonb)) > 50 or
       exists (select 1 from jsonb_array_elements(coalesce(v -> 'genres', '[]'::jsonb)) g where jsonb_typeof(g) <> 'string' or octet_length(g #>> '{}') > 512)
     ) then
    raise exception using errcode = '22023', message = 'invalid library item';
  end if;
  if exists (
    select 1 from jsonb_to_recordset(p_items) r(content_id text, content_type text)
    group by r.content_id, r.content_type having count(*) > 1
  ) then
    raise exception using errcode = '22023', message = 'duplicate library key';
  end if;

  for x in
    select r.*, coalesce(array(select jsonb_array_elements_text(coalesce(v -> 'genres', '[]'::jsonb))), '{}'::text[]) genres_value
    from jsonb_array_elements(p_items) v
    cross join lateral jsonb_to_record(v) as r(
      content_id text, content_type text, name text, poster text, poster_shape text,
      background text, logo text, description text, release_info text,
      imdb_rating real, addon_base_url text, added_at bigint
    )
  loop
    insert into ka_sync.library_items as i (
      user_id, profile_id, content_id, content_type, name, poster, poster_shape,
      background, logo, description, release_info, imdb_rating, genres,
      addon_base_url, added_at, updated_at
    ) values (
      v_user_id, v_profile_id, x.content_id, x.content_type, coalesce(x.name, ''), x.poster,
      coalesce(x.poster_shape, 'POSTER'), x.background, x.logo, x.description, x.release_info,
      x.imdb_rating, x.genres_value, x.addon_base_url, coalesce(x.added_at, 0), now()
    )
    on conflict (profile_id, content_id, content_type) do update set
      name = excluded.name, poster = excluded.poster, poster_shape = excluded.poster_shape,
      background = excluded.background, logo = excluded.logo, description = excluded.description,
      release_info = excluded.release_info, imdb_rating = excluded.imdb_rating,
      genres = excluded.genres, addon_base_url = excluded.addon_base_url,
      added_at = excluded.added_at, updated_at = excluded.updated_at
    where (i.name, i.poster, i.poster_shape, i.background, i.logo, i.description,
           i.release_info, i.imdb_rating, i.genres, i.addon_base_url, i.added_at)
      is distinct from
          (excluded.name, excluded.poster, excluded.poster_shape, excluded.background, excluded.logo,
           excluded.description, excluded.release_info, excluded.imdb_rating, excluded.genres,
           excluded.addon_base_url, excluded.added_at)
    returning * into v_row;

    if found then
      insert into ka_sync.library_events(user_id, profile_id, operation, content_id, content_type, payload, origin_client_id)
      values (v_user_id, v_profile_id, 'upsert', x.content_id, x.content_type,
              to_jsonb(v_row) - 'user_id' - 'profile_id' - 'updated_at', p_origin_client_id);
    end if;
  end loop;
end
$$;

create or replace function public.sync_delete_library_items_v2(
  p_profile_index integer, p_keys jsonb, p_origin_client_id text default null
)
returns void
language plpgsql
volatile
security invoker
set search_path = ''
as $$
declare
  v_user_id uuid := (select auth.uid());
  v_profile_id uuid := ka_sync.resolve_profile_id_v2(p_profile_index);
  x record;
  v_row ka_sync.library_items%rowtype;
begin
  perform ka_sync.assert_mutation_request_v2(p_keys, p_origin_client_id);
  if exists (select 1 from jsonb_array_elements(p_keys) v where jsonb_typeof(v) <> 'object' or
      jsonb_typeof(v -> 'content_id') is distinct from 'string' or octet_length(v ->> 'content_id') not between 1 and 512 or
      jsonb_typeof(v -> 'content_type') is distinct from 'string' or octet_length(v ->> 'content_type') not between 1 and 64) then
    raise exception using errcode = '22023', message = 'invalid library key';
  end if;
  if exists (select 1 from jsonb_to_recordset(p_keys) r(content_id text, content_type text)
             group by r.content_id, r.content_type having count(*) > 1) then
    raise exception using errcode = '22023', message = 'duplicate library key';
  end if;

  for x in select * from jsonb_to_recordset(p_keys) r(content_id text, content_type text) loop
    delete from ka_sync.library_items i
    where i.user_id = v_user_id and i.profile_id = v_profile_id
      and i.content_id = x.content_id and i.content_type = x.content_type
    returning i.* into v_row;
    if found then
      insert into ka_sync.library_events(user_id, profile_id, operation, content_id, content_type, payload, origin_client_id)
      values (v_user_id, v_profile_id, 'delete', x.content_id, x.content_type,
              to_jsonb(v_row) - 'user_id' - 'profile_id' - 'updated_at', p_origin_client_id);
    end if;
  end loop;
end
$$;

-- Watched --------------------------------------------------------------------
create or replace function public.sync_get_watched_items_delta_cursor_v2(p_profile_index integer)
returns bigint language plpgsql stable security invoker set search_path = '' as $$
declare v_user_id uuid := (select auth.uid()); v_profile_id uuid := ka_sync.resolve_profile_id_v2(p_profile_index);
begin return coalesce((select max(e.event_id) from ka_sync.watched_events e where e.user_id=v_user_id and e.profile_id=v_profile_id),0); end $$;

create or replace function public.sync_pull_watched_items_v2(
  p_profile_index integer, p_limit integer default 500, p_after_watched_at bigint default null,
  p_after_content_id text default null, p_after_content_type text default null,
  p_after_season integer default null, p_after_episode integer default null
)
returns table(content_id text, content_type text, title text, season integer, episode integer, watched_at bigint)
language plpgsql stable security invoker set search_path = '' as $$
declare v_user_id uuid := (select auth.uid()); v_profile_id uuid := ka_sync.resolve_profile_id_v2(p_profile_index);
begin
  if p_limit is null or p_limit not between 1 and 500 then raise exception using errcode='22023', message='limit must be between 1 and 500'; end if;
  if (p_after_watched_at is null) <> (p_after_content_id is null) or (p_after_watched_at is null) <> (p_after_content_type is null) or
     (p_after_watched_at is null) <> (p_after_season is null) or (p_after_watched_at is null) <> (p_after_episode is null) then
    raise exception using errcode='22023', message='incomplete watched cursor';
  end if;
  if p_after_content_id is not null and (octet_length(p_after_content_id) not between 1 and 512 or octet_length(p_after_content_type) not between 1 and 64) then
    raise exception using errcode='22023', message='invalid watched cursor';
  end if;
  return query select w.content_id,w.content_type,w.title,nullif(w.season_num,-1),nullif(w.episode_num,-1),w.watched_at
  from ka_sync.watched_items w where w.user_id=v_user_id and w.profile_id=v_profile_id and
    (p_after_watched_at is null or w.watched_at < p_after_watched_at or
     (w.watched_at=p_after_watched_at and (w.content_id,w.content_type,w.season_num,w.episode_num) >
      (p_after_content_id,p_after_content_type,p_after_season,p_after_episode)))
  order by w.watched_at desc,w.content_id,w.content_type,w.season_num,w.episode_num limit p_limit;
end $$;

create or replace function public.sync_pull_watched_items_delta_v2(p_profile_index integer,p_since_event_id bigint,p_limit integer)
returns table(event_id bigint,operation text,content_id text,content_type text,title text,season integer,episode integer,watched_at bigint)
language plpgsql stable security invoker set search_path = '' as $$
declare v_user_id uuid := (select auth.uid()); v_profile_id uuid := ka_sync.resolve_profile_id_v2(p_profile_index);
begin
  if p_since_event_id is null or p_since_event_id<0 or p_limit is null or p_limit not between 1 and 500 then raise exception using errcode='22023',message='invalid delta cursor or limit'; end if;
  return query select e.event_id,e.operation,e.content_id,e.content_type,coalesce(e.payload->>'title',''),
    nullif((e.payload->>'season_num')::integer,-1),nullif((e.payload->>'episode_num')::integer,-1),coalesce((e.payload->>'watched_at')::bigint,0)
  from ka_sync.watched_events e where e.user_id=v_user_id and e.profile_id=v_profile_id and e.event_id>p_since_event_id order by e.event_id limit p_limit;
end $$;

create or replace function public.sync_push_watched_items_v2(p_profile_index integer,p_items jsonb,p_origin_client_id text default null)
returns void language plpgsql volatile security invoker set search_path = '' as $$
declare
  v_user_id uuid := (select auth.uid()); v_profile_id uuid := ka_sync.resolve_profile_id_v2(p_profile_index);
  x record; v_row ka_sync.watched_items%rowtype; v_season integer; v_episode integer;
begin
  perform ka_sync.assert_mutation_request_v2(p_items,p_origin_client_id);
  if exists(select 1 from jsonb_array_elements(p_items) v where jsonb_typeof(v)<>'object' or
    jsonb_typeof(v->'content_id') is distinct from 'string' or octet_length(v->>'content_id') not between 1 and 512 or
    jsonb_typeof(v->'content_type') is distinct from 'string' or octet_length(v->>'content_type') not between 1 and 64 or
    (v?'title' and jsonb_typeof(v->'title')<>'string') or octet_length(coalesce(v->>'title',''))>512) then
    raise exception using errcode='22023',message='invalid watched item'; end if;
  if exists(select 1 from jsonb_to_recordset(p_items) r(content_id text,content_type text,season integer,episode integer)
    group by r.content_id,r.content_type,coalesce(r.season,-1),coalesce(r.episode,-1) having count(*)>1) then
    raise exception using errcode='22023',message='duplicate watched key'; end if;
  for x in select * from jsonb_to_recordset(p_items) r(content_id text,content_type text,title text,season integer,episode integer,watched_at bigint) loop
    v_season:=coalesce(x.season,-1); v_episode:=coalesce(x.episode,-1);
    insert into ka_sync.watched_items as w(user_id,profile_id,content_id,content_type,title,season_num,episode_num,watched_at,updated_at)
    values(v_user_id,v_profile_id,x.content_id,x.content_type,coalesce(x.title,''),v_season,v_episode,coalesce(x.watched_at,0),now())
    on conflict(profile_id,content_id,content_type,season_num,episode_num) do update set title=excluded.title,watched_at=excluded.watched_at,updated_at=excluded.updated_at
    where (w.title,w.watched_at) is distinct from (excluded.title,excluded.watched_at) returning * into v_row;
    if found then insert into ka_sync.watched_events(user_id,profile_id,operation,content_id,content_type,payload,origin_client_id)
      values(v_user_id,v_profile_id,'upsert',x.content_id,x.content_type,to_jsonb(v_row)-'user_id'-'profile_id'-'updated_at',p_origin_client_id); end if;
  end loop;
end $$;

create or replace function public.sync_delete_watched_items_v2(p_profile_index integer,p_keys jsonb,p_origin_client_id text default null)
returns void language plpgsql volatile security invoker set search_path = '' as $$
declare
  v_user_id uuid := (select auth.uid()); v_profile_id uuid := ka_sync.resolve_profile_id_v2(p_profile_index);
  x record; v_row ka_sync.watched_items%rowtype;
begin
  perform ka_sync.assert_mutation_request_v2(p_keys,p_origin_client_id);
  if exists(select 1 from jsonb_array_elements(p_keys) v where jsonb_typeof(v)<>'object' or
    jsonb_typeof(v->'content_id') is distinct from 'string' or octet_length(v->>'content_id') not between 1 and 512 or
    jsonb_typeof(v->'content_type') is distinct from 'string' or octet_length(v->>'content_type') not between 1 and 64) then
    raise exception using errcode='22023',message='invalid watched key'; end if;
  if exists(select 1 from jsonb_to_recordset(p_keys) r(content_id text,content_type text,season integer,episode integer)
    group by r.content_id,r.content_type,coalesce(r.season,-1),coalesce(r.episode,-1) having count(*)>1) then
    raise exception using errcode='22023',message='duplicate watched key'; end if;
  for x in select * from jsonb_to_recordset(p_keys) r(content_id text,content_type text,season integer,episode integer) loop
    delete from ka_sync.watched_items w where w.user_id=v_user_id and w.profile_id=v_profile_id and w.content_id=x.content_id and
      w.content_type=x.content_type and w.season_num=coalesce(x.season,-1) and w.episode_num=coalesce(x.episode,-1) returning w.* into v_row;
    if found then insert into ka_sync.watched_events(user_id,profile_id,operation,content_id,content_type,payload,origin_client_id)
      values(v_user_id,v_profile_id,'delete',x.content_id,x.content_type,to_jsonb(v_row)-'user_id'-'profile_id'-'updated_at',p_origin_client_id); end if;
  end loop;
end $$;

-- Watch progress -------------------------------------------------------------
create or replace function public.sync_get_watch_progress_delta_cursor_v2(p_profile_index integer)
returns bigint language plpgsql stable security invoker set search_path = '' as $$
declare v_user_id uuid := (select auth.uid()); v_profile_id uuid := ka_sync.resolve_profile_id_v2(p_profile_index);
begin return coalesce((select max(e.event_id) from ka_sync.watch_progress_events e where e.user_id=v_user_id and e.profile_id=v_profile_id),0); end $$;

create or replace function public.sync_pull_watch_progress_v2(p_profile_index integer,p_limit integer default 500,p_after_last_watched bigint default null,p_after_progress_key text default null)
returns table(content_id text,content_type text,video_id text,season integer,episode integer,"position" bigint,duration bigint,last_watched bigint,progress_key text)
language plpgsql stable security invoker set search_path = '' as $$
declare v_user_id uuid := (select auth.uid()); v_profile_id uuid := ka_sync.resolve_profile_id_v2(p_profile_index);
begin
  if p_limit is null or p_limit not between 1 and 500 then raise exception using errcode='22023',message='limit must be between 1 and 500'; end if;
  if (p_after_last_watched is null)<>(p_after_progress_key is null) then raise exception using errcode='22023',message='incomplete progress cursor'; end if;
  if p_after_progress_key is not null and octet_length(p_after_progress_key) not between 1 and 1024 then raise exception using errcode='22023',message='invalid progress cursor'; end if;
  return query select w.content_id,w.content_type,w.video_id,nullif(w.season_num,-1),nullif(w.episode_num,-1),w.position,w.duration,w.last_watched,w.progress_key
  from ka_sync.watch_progress w where w.user_id=v_user_id and w.profile_id=v_profile_id and
    (p_after_last_watched is null or w.last_watched<p_after_last_watched or (w.last_watched=p_after_last_watched and w.progress_key>p_after_progress_key))
  order by w.last_watched desc,w.progress_key limit p_limit;
end $$;

create or replace function public.sync_pull_watch_progress_delta_v2(p_profile_index integer,p_since_event_id bigint,p_limit integer)
returns table(event_id bigint,operation text,progress_key text,content_id text,content_type text,video_id text,season integer,episode integer,"position" bigint,duration bigint,last_watched bigint)
language plpgsql stable security invoker set search_path = '' as $$
declare v_user_id uuid := (select auth.uid()); v_profile_id uuid := ka_sync.resolve_profile_id_v2(p_profile_index);
begin
  if p_since_event_id is null or p_since_event_id<0 or p_limit is null or p_limit not between 1 and 500 then raise exception using errcode='22023',message='invalid delta cursor or limit'; end if;
  return query select e.event_id,e.operation,e.progress_key,coalesce(e.payload->>'content_id',''),coalesce(e.payload->>'content_type',''),coalesce(e.payload->>'video_id',''),
    nullif((e.payload->>'season_num')::integer,-1),nullif((e.payload->>'episode_num')::integer,-1),coalesce((e.payload->>'position')::bigint,0),
    coalesce((e.payload->>'duration')::bigint,0),coalesce((e.payload->>'last_watched')::bigint,0)
  from ka_sync.watch_progress_events e where e.user_id=v_user_id and e.profile_id=v_profile_id and e.event_id>p_since_event_id order by e.event_id limit p_limit;
end $$;

create or replace function public.sync_push_watch_progress_v2(p_profile_index integer,p_entries jsonb,p_origin_client_id text default null)
returns void language plpgsql volatile security invoker set search_path = '' as $$
declare
  v_user_id uuid := (select auth.uid()); v_profile_id uuid := ka_sync.resolve_profile_id_v2(p_profile_index);
  x record; v_row ka_sync.watch_progress%rowtype;
begin
  perform ka_sync.assert_mutation_request_v2(p_entries,p_origin_client_id);
  if exists(select 1 from jsonb_array_elements(p_entries) v where jsonb_typeof(v)<>'object' or
    jsonb_typeof(v->'progress_key') is distinct from 'string' or octet_length(v->>'progress_key') not between 1 and 1024 or
    jsonb_typeof(v->'content_id') is distinct from 'string' or octet_length(v->>'content_id') not between 1 and 512 or
    jsonb_typeof(v->'content_type') is distinct from 'string' or octet_length(v->>'content_type') not between 1 and 64 or
    jsonb_typeof(v->'video_id') is distinct from 'string' or octet_length(v->>'video_id') not between 1 and 1024) then
    raise exception using errcode='22023',message='invalid progress entry'; end if;
  if exists(select 1 from jsonb_to_recordset(p_entries) r(progress_key text) group by r.progress_key having count(*)>1) then
    raise exception using errcode='22023',message='duplicate progress key'; end if;
  for x in select * from jsonb_to_recordset(p_entries) r(progress_key text,content_id text,content_type text,video_id text,season integer,episode integer,"position" bigint,duration bigint,last_watched bigint) loop
    if coalesce(x.position,0)<0 or coalesce(x.duration,0)<0 then raise exception using errcode='22023',message='negative progress value'; end if;
    insert into ka_sync.watch_progress as w(user_id,profile_id,progress_key,content_id,content_type,video_id,season_num,episode_num,position,duration,last_watched,updated_at)
    values(v_user_id,v_profile_id,x.progress_key,x.content_id,x.content_type,x.video_id,coalesce(x.season,-1),coalesce(x.episode,-1),coalesce(x.position,0),coalesce(x.duration,0),coalesce(x.last_watched,0),now())
    on conflict(profile_id,progress_key) do update set content_id=excluded.content_id,content_type=excluded.content_type,video_id=excluded.video_id,
      season_num=excluded.season_num,episode_num=excluded.episode_num,position=excluded.position,duration=excluded.duration,last_watched=excluded.last_watched,updated_at=excluded.updated_at
    where excluded.last_watched>=w.last_watched and
      (w.content_id,w.content_type,w.video_id,w.season_num,w.episode_num,w.position,w.duration,w.last_watched) is distinct from
      (excluded.content_id,excluded.content_type,excluded.video_id,excluded.season_num,excluded.episode_num,excluded.position,excluded.duration,excluded.last_watched)
    returning * into v_row;
    if found then insert into ka_sync.watch_progress_events(user_id,profile_id,operation,progress_key,payload,origin_client_id)
      values(v_user_id,v_profile_id,'upsert',x.progress_key,to_jsonb(v_row)-'user_id'-'profile_id'-'updated_at',p_origin_client_id); end if;
  end loop;
end $$;

create or replace function public.sync_delete_watch_progress_v2(p_profile_index integer,p_keys jsonb,p_origin_client_id text default null)
returns void language plpgsql volatile security invoker set search_path = '' as $$
declare
  v_user_id uuid := (select auth.uid()); v_profile_id uuid := ka_sync.resolve_profile_id_v2(p_profile_index);
  v_key text; v_row ka_sync.watch_progress%rowtype;
begin
  perform ka_sync.assert_mutation_request_v2(p_keys,p_origin_client_id);
  if exists(select 1 from jsonb_array_elements(p_keys) v where jsonb_typeof(v)<>'string' or octet_length(v #>> '{}') not between 1 and 1024) then
    raise exception using errcode='22023',message='invalid progress key'; end if;
  if exists(select 1 from jsonb_array_elements_text(p_keys) k group by k having count(*)>1) then
    raise exception using errcode='22023',message='duplicate progress key'; end if;
  for v_key in select jsonb_array_elements_text(p_keys) loop
    delete from ka_sync.watch_progress w where w.user_id=v_user_id and w.profile_id=v_profile_id and w.progress_key=v_key returning w.* into v_row;
    if found then insert into ka_sync.watch_progress_events(user_id,profile_id,operation,progress_key,payload,origin_client_id)
      values(v_user_id,v_profile_id,'delete',v_key,to_jsonb(v_row)-'user_id'-'profile_id'-'updated_at',p_origin_client_id); end if;
  end loop;
end $$;

-- SECURITY INVOKER requires only the table rights used by these RPCs; RLS remains authoritative.
revoke all on function ka_sync.resolve_profile_id_v2(integer) from public, anon;
revoke all on function ka_sync.assert_mutation_request_v2(jsonb,text) from public, anon;
grant execute on function ka_sync.resolve_profile_id_v2(integer) to authenticated;
grant execute on function ka_sync.assert_mutation_request_v2(jsonb,text) to authenticated;

revoke all on table ka_sync.library_items,ka_sync.watched_items,ka_sync.watch_progress,
  ka_sync.library_events,ka_sync.watched_events,ka_sync.watch_progress_events from authenticated;
grant select,insert,update,delete on table ka_sync.library_items,ka_sync.watched_items,ka_sync.watch_progress to authenticated;
grant select,insert on table ka_sync.library_events,ka_sync.watched_events,ka_sync.watch_progress_events to authenticated;
revoke all on all sequences in schema ka_sync from authenticated;
grant usage on all sequences in schema ka_sync to authenticated;

revoke all on function public.sync_get_library_delta_cursor_v2(integer) from public,anon;
revoke all on function public.sync_pull_library_v2(integer,integer,bigint,text,text) from public,anon;
revoke all on function public.sync_pull_library_delta_v2(integer,bigint,integer) from public,anon;
revoke all on function public.sync_push_library_items_v2(integer,jsonb,text) from public,anon;
revoke all on function public.sync_delete_library_items_v2(integer,jsonb,text) from public,anon;
revoke all on function public.sync_get_watched_items_delta_cursor_v2(integer) from public,anon;
revoke all on function public.sync_pull_watched_items_v2(integer,integer,bigint,text,text,integer,integer) from public,anon;
revoke all on function public.sync_pull_watched_items_delta_v2(integer,bigint,integer) from public,anon;
revoke all on function public.sync_push_watched_items_v2(integer,jsonb,text) from public,anon;
revoke all on function public.sync_delete_watched_items_v2(integer,jsonb,text) from public,anon;
revoke all on function public.sync_get_watch_progress_delta_cursor_v2(integer) from public,anon;
revoke all on function public.sync_pull_watch_progress_v2(integer,integer,bigint,text) from public,anon;
revoke all on function public.sync_pull_watch_progress_delta_v2(integer,bigint,integer) from public,anon;
revoke all on function public.sync_push_watch_progress_v2(integer,jsonb,text) from public,anon;
revoke all on function public.sync_delete_watch_progress_v2(integer,jsonb,text) from public,anon;

grant execute on function public.sync_get_library_delta_cursor_v2(integer) to authenticated;
grant execute on function public.sync_pull_library_v2(integer,integer,bigint,text,text) to authenticated;
grant execute on function public.sync_pull_library_delta_v2(integer,bigint,integer) to authenticated;
grant execute on function public.sync_push_library_items_v2(integer,jsonb,text) to authenticated;
grant execute on function public.sync_delete_library_items_v2(integer,jsonb,text) to authenticated;
grant execute on function public.sync_get_watched_items_delta_cursor_v2(integer) to authenticated;
grant execute on function public.sync_pull_watched_items_v2(integer,integer,bigint,text,text,integer,integer) to authenticated;
grant execute on function public.sync_pull_watched_items_delta_v2(integer,bigint,integer) to authenticated;
grant execute on function public.sync_push_watched_items_v2(integer,jsonb,text) to authenticated;
grant execute on function public.sync_delete_watched_items_v2(integer,jsonb,text) to authenticated;
grant execute on function public.sync_get_watch_progress_delta_cursor_v2(integer) to authenticated;
grant execute on function public.sync_pull_watch_progress_v2(integer,integer,bigint,text) to authenticated;
grant execute on function public.sync_pull_watch_progress_delta_v2(integer,bigint,integer) to authenticated;
grant execute on function public.sync_push_watch_progress_v2(integer,jsonb,text) to authenticated;
grant execute on function public.sync_delete_watch_progress_v2(integer,jsonb,text) to authenticated;

commit;

-- ISOLATED VALIDATION ONLY. DO NOT APPLY TO PRODUCTION.
