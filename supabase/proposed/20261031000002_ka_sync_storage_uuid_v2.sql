-- KA Play Etapa 15 / Migration B2 candidate.
-- ISOLATED VALIDATION ONLY. DO NOT APPLY TO PRODUCTION.
-- New private-schema storage keyed by immutable profile UUID.

begin;

create schema if not exists ka_sync;
revoke all on schema ka_sync from public, anon;
grant usage on schema ka_sync to authenticated;

create table ka_sync.library_items (
  user_id uuid not null references auth.users(id) on delete cascade,
  profile_id uuid not null,
  content_id text not null check (octet_length(content_id) between 1 and 512),
  content_type text not null check (octet_length(content_type) between 1 and 64),
  name text not null default '' check (octet_length(name) <= 512),
  poster text check (poster is null or octet_length(poster) <= 4096),
  poster_shape text not null default 'POSTER' check (octet_length(poster_shape) <= 32),
  background text check (background is null or octet_length(background) <= 4096),
  logo text check (logo is null or octet_length(logo) <= 4096),
  description text check (description is null or octet_length(description) <= 16384),
  release_info text check (release_info is null or octet_length(release_info) <= 512),
  imdb_rating real,
  genres text[] not null default '{}' check (cardinality(genres) <= 50),
  addon_base_url text check (addon_base_url is null or octet_length(addon_base_url) <= 4096),
  added_at bigint not null default 0,
  updated_at timestamptz not null default now(),
  primary key (profile_id, content_id, content_type),
  foreign key (profile_id, user_id) references public.ka_profiles(id, user_id)
    on update restrict on delete cascade
);

create table ka_sync.library_events (
  event_id bigint generated always as identity primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  profile_id uuid not null,
  operation text not null check (operation in ('upsert', 'delete')),
  content_id text not null check (octet_length(content_id) between 1 and 512),
  content_type text not null check (octet_length(content_type) between 1 and 64),
  payload jsonb not null check (jsonb_typeof(payload) = 'object' and octet_length(payload::text) <= 1048576),
  origin_client_id text check (origin_client_id is null or octet_length(origin_client_id) between 1 and 128),
  created_at timestamptz not null default now(),
  foreign key (profile_id, user_id) references public.ka_profiles(id, user_id)
    on update restrict on delete cascade
);

create table ka_sync.watched_items (
  user_id uuid not null references auth.users(id) on delete cascade,
  profile_id uuid not null,
  content_id text not null check (octet_length(content_id) between 1 and 512),
  content_type text not null check (octet_length(content_type) between 1 and 64),
  title text not null default '' check (octet_length(title) <= 512),
  season_num integer not null default -1,
  episode_num integer not null default -1,
  watched_at bigint not null default 0,
  updated_at timestamptz not null default now(),
  primary key (profile_id, content_id, content_type, season_num, episode_num),
  foreign key (profile_id, user_id) references public.ka_profiles(id, user_id)
    on update restrict on delete cascade
);

create table ka_sync.watched_events (
  event_id bigint generated always as identity primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  profile_id uuid not null,
  operation text not null check (operation in ('upsert', 'delete')),
  content_id text not null check (octet_length(content_id) between 1 and 512),
  content_type text not null check (octet_length(content_type) between 1 and 64),
  payload jsonb not null check (jsonb_typeof(payload) = 'object' and octet_length(payload::text) <= 1048576),
  origin_client_id text check (origin_client_id is null or octet_length(origin_client_id) between 1 and 128),
  created_at timestamptz not null default now(),
  foreign key (profile_id, user_id) references public.ka_profiles(id, user_id)
    on update restrict on delete cascade
);

create table ka_sync.watch_progress (
  user_id uuid not null references auth.users(id) on delete cascade,
  profile_id uuid not null,
  progress_key text not null check (octet_length(progress_key) between 1 and 1024),
  content_id text not null check (octet_length(content_id) between 1 and 512),
  content_type text not null check (octet_length(content_type) between 1 and 64),
  video_id text not null check (octet_length(video_id) between 1 and 1024),
  season_num integer not null default -1,
  episode_num integer not null default -1,
  position bigint not null default 0 check (position >= 0),
  duration bigint not null default 0 check (duration >= 0),
  last_watched bigint not null default 0,
  updated_at timestamptz not null default now(),
  primary key (profile_id, progress_key),
  foreign key (profile_id, user_id) references public.ka_profiles(id, user_id)
    on update restrict on delete cascade
);

create table ka_sync.watch_progress_events (
  event_id bigint generated always as identity primary key,
  user_id uuid not null references auth.users(id) on delete cascade,
  profile_id uuid not null,
  operation text not null check (operation in ('upsert', 'delete')),
  progress_key text not null check (octet_length(progress_key) between 1 and 1024),
  payload jsonb not null check (jsonb_typeof(payload) = 'object' and octet_length(payload::text) <= 1048576),
  origin_client_id text check (origin_client_id is null or octet_length(origin_client_id) between 1 and 128),
  created_at timestamptz not null default now(),
  foreign key (profile_id, user_id) references public.ka_profiles(id, user_id)
    on update restrict on delete cascade
);

create index library_items_owner_page_idx on ka_sync.library_items
  (user_id, profile_id, added_at desc, content_id, content_type);
create index library_events_owner_cursor_idx on ka_sync.library_events (user_id, profile_id, event_id);
create index watched_items_owner_page_idx on ka_sync.watched_items
  (user_id, profile_id, watched_at desc, content_id, content_type, season_num, episode_num);
create index watched_events_owner_cursor_idx on ka_sync.watched_events (user_id, profile_id, event_id);
create index watch_progress_owner_page_idx on ka_sync.watch_progress
  (user_id, profile_id, last_watched desc, progress_key);
create index watch_progress_events_owner_cursor_idx on ka_sync.watch_progress_events
  (user_id, profile_id, event_id);

-- Private schema is not exposed by default; RLS remains defense in depth for RPC invokers.
do $$
declare
  v_table regclass;
begin
  foreach v_table in array array[
    'ka_sync.library_items'::regclass, 'ka_sync.library_events'::regclass,
    'ka_sync.watched_items'::regclass, 'ka_sync.watched_events'::regclass,
    'ka_sync.watch_progress'::regclass, 'ka_sync.watch_progress_events'::regclass
  ] loop
    execute format('alter table %s enable row level security', v_table);
    execute format('alter table %s force row level security', v_table);
    execute format('revoke all on table %s from public, anon', v_table);
    execute format('grant select, insert, update, delete on table %s to authenticated', v_table);
    execute format(
      'create policy registered_owner_access on %s for all to authenticated using '
      || '(user_id = (select auth.uid()) and not coalesce(((select auth.jwt())->>''is_anonymous'')::boolean, false)) '
      || 'with check (user_id = (select auth.uid()) and not coalesce(((select auth.jwt())->>''is_anonymous'')::boolean, false))',
      v_table
    );
  end loop;
end
$$;

revoke all on all sequences in schema ka_sync from public, anon;
grant usage, select on all sequences in schema ka_sync to authenticated;

commit;
