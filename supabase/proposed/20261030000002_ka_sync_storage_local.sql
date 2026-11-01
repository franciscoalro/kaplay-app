-- KA Play sync storage for isolated validation only.
-- Do not apply to production without review of RELATORIO_TESTES_SYNC_ISOLADO.md.

create table public.ka_library_items(
 user_id uuid not null references auth.users(id) on delete cascade,
 profile_index integer not null check(profile_index between 1 and 6),
 content_id text not null,
 content_type text not null,
 name text not null default '',
 poster text,
 poster_shape text not null default 'POSTER',
 background text,
 logo text,
 description text,
 release_info text,
 imdb_rating real,
 genres text[] not null default '{}',
 addon_base_url text,
 added_at bigint not null default 0,
 updated_at timestamptz not null default now(),
 primary key(user_id,profile_index,content_id,content_type)
);
create table public.ka_library_events(
 event_id bigint generated always as identity primary key,
 user_id uuid not null references auth.users(id) on delete cascade,
 profile_index integer not null check(profile_index between 1 and 6),
 operation text not null check(operation in ('upsert','delete')),
 content_id text not null,
 content_type text not null,
 payload jsonb not null,
 origin_client_id text,
 created_at timestamptz not null default now()
);
create index ka_library_events_scope_idx on public.ka_library_events(user_id,profile_index,event_id);

create table public.ka_watched_items(
 user_id uuid not null references auth.users(id) on delete cascade,
 profile_index integer not null check(profile_index between 1 and 6),
 content_id text not null,
 content_type text not null,
 title text not null default '',
 season_num integer not null default -1,
 episode_num integer not null default -1,
 watched_at bigint not null default 0,
 updated_at timestamptz not null default now(),
 primary key(user_id,profile_index,content_id,content_type,season_num,episode_num)
);
create table public.ka_watched_events(
 event_id bigint generated always as identity primary key,
 user_id uuid not null references auth.users(id) on delete cascade,
 profile_index integer not null check(profile_index between 1 and 6),
 operation text not null check(operation in ('upsert','delete')),
 content_id text not null,
 content_type text not null,
 payload jsonb not null,
 origin_client_id text,
 created_at timestamptz not null default now()
);
create index ka_watched_events_scope_idx on public.ka_watched_events(user_id,profile_index,event_id);

create table public.ka_watch_progress(
 user_id uuid not null references auth.users(id) on delete cascade,
 profile_index integer not null check(profile_index between 1 and 6),
 progress_key text not null,
 content_id text not null,
 content_type text not null,
 video_id text not null,
 season_num integer not null default -1,
 episode_num integer not null default -1,
 position bigint not null default 0,
 duration bigint not null default 0,
 last_watched bigint not null default 0,
 updated_at timestamptz not null default now(),
 primary key(user_id,profile_index,progress_key)
);
create table public.ka_watch_progress_events(
 event_id bigint generated always as identity primary key,
 user_id uuid not null references auth.users(id) on delete cascade,
 profile_index integer not null check(profile_index between 1 and 6),
 operation text not null check(operation in ('upsert','delete')),
 progress_key text not null,
 payload jsonb not null,
 origin_client_id text,
 created_at timestamptz not null default now()
);
create index ka_watch_progress_events_scope_idx on public.ka_watch_progress_events(user_id,profile_index,event_id);
create index ka_watch_progress_freshness_idx on public.ka_watch_progress(user_id,profile_index,last_watched desc);

create or replace function public.ka_assert_profile_access(p_profile_id integer)
returns void language plpgsql security definer set search_path = public, pg_temp as $$
begin
 if auth.uid() is null then raise exception 'authentication required' using errcode='42501'; end if;
 if not exists(select 1 from public.ka_profiles where user_id=auth.uid() and profile_index=p_profile_id) then
  raise exception 'profile access denied' using errcode='42501';
 end if;
end $$;
revoke all on function public.ka_assert_profile_access(integer) from public;

alter table public.ka_library_items enable row level security;
alter table public.ka_library_events enable row level security;
alter table public.ka_watched_items enable row level security;
alter table public.ka_watched_events enable row level security;
alter table public.ka_watch_progress enable row level security;
alter table public.ka_watch_progress_events enable row level security;
revoke all on public.ka_library_items,public.ka_library_events,public.ka_watched_items,public.ka_watched_events,public.ka_watch_progress,public.ka_watch_progress_events from anon,authenticated;
