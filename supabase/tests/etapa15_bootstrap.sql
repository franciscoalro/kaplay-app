\set ON_ERROR_STOP on

do $$ begin create role anon nologin; exception when duplicate_object then null; end $$;
do $$ begin create role authenticated nologin; exception when duplicate_object then null; end $$;

create schema auth;
create table auth.users(id uuid primary key, email text);
create or replace function auth.uid() returns uuid language sql stable as $$
  select nullif(current_setting('request.jwt.claim.sub', true), '')::uuid
$$;
create or replace function auth.jwt() returns jsonb language sql stable as $$
  select coalesce(nullif(current_setting('request.jwt.claims', true), '')::jsonb, '{}'::jsonb)
$$;
grant usage on schema auth to authenticated, anon;
grant execute on function auth.uid(), auth.jwt() to authenticated, anon;

create table public.user_favorites(
  user_id uuid not null references auth.users(id),
  media_id text not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key(user_id, media_id)
);

create table public.watch_history(
  user_id uuid not null references auth.users(id),
  media_id text not null,
  season_num integer not null default -1,
  episode_num integer not null default -1,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  primary key(user_id, media_id, season_num, episode_num)
);
