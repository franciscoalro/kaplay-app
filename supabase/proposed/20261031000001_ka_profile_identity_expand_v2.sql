-- KA Play Etapa 15 / Migration A1 candidate.
-- ISOLATED VALIDATION ONLY. DO NOT APPLY TO PRODUCTION.
-- Expands profile identity without assigning legacy rows to profile 1.

begin;

-- A stable UUID is the ownership boundary. Profile indexes remain user-facing slots only.
do $$
begin
  if not exists (
    select 1 from pg_constraint
    where conrelid = 'public.ka_profiles'::regclass
      and conname = 'ka_profiles_id_user_id_key'
  ) then
    alter table public.ka_profiles
      add constraint ka_profiles_id_user_id_key unique (id, user_id);
  end if;
end
$$;

create or replace function public.ka_profile_identity_immutable()
returns trigger
language plpgsql
security invoker
set search_path = ''
as $$
begin
  if new.id is distinct from old.id or new.user_id is distinct from old.user_id then
    raise exception using errcode = '22023', message = 'profile identity is immutable';
  end if;
  return new;
end
$$;

drop trigger if exists ka_profiles_identity_immutable on public.ka_profiles;
create trigger ka_profiles_identity_immutable
before update of id, user_id on public.ka_profiles
for each row execute function public.ka_profile_identity_immutable();

-- Replaces delete/reinsert with UUID-preserving UPSERT.
create or replace function public.sync_push_profiles(
  p_client_max_profiles integer,
  p_profiles jsonb,
  p_origin_client_id text default null
)
returns void
language plpgsql
volatile
security invoker
set search_path = ''
as $$
declare
  v_user_id uuid := (select auth.uid());
  v_is_anonymous boolean := coalesce(((select auth.jwt()) ->> 'is_anonymous')::boolean, false);
begin
  if v_user_id is null or v_is_anonymous then
    raise exception using errcode = '42501', message = 'registered authentication required';
  end if;
  if p_client_max_profiles is null or p_client_max_profiles not between 1 and 6 then
    raise exception using errcode = '22023', message = 'invalid profile limit';
  end if;
  if jsonb_typeof(p_profiles) is distinct from 'array' or jsonb_array_length(p_profiles) > p_client_max_profiles then
    raise exception using errcode = '22023', message = 'profiles must be a bounded array';
  end if;
  if octet_length(p_profiles::text) > 65536 or octet_length(coalesce(p_origin_client_id, '')) > 128 then
    raise exception using errcode = '22023', message = 'profile payload exceeds limit';
  end if;
  if exists (
    select 1
    from jsonb_to_recordset(p_profiles) as x(profile_index integer, name text)
    group by x.profile_index having count(*) > 1
  ) then
    raise exception using errcode = '22023', message = 'duplicate profile index';
  end if;

  insert into public.ka_profiles (
    user_id, profile_index, name, avatar_color_hex, avatar_id, avatar_url,
    profile_background_id, profile_background_url,
    uses_primary_addons, uses_primary_plugins, updated_at
  )
  select
    v_user_id, x.profile_index, btrim(x.name), coalesce(x.avatar_color_hex, '#1E88E5'),
    x.avatar_id, x.avatar_url, x.profile_background_id, x.profile_background_url,
    coalesce(x.uses_primary_addons, false), coalesce(x.uses_primary_plugins, false), now()
  from jsonb_to_recordset(p_profiles) as x(
    profile_index integer, name text, avatar_color_hex text, avatar_id text, avatar_url text,
    profile_background_id text, profile_background_url text,
    uses_primary_addons boolean, uses_primary_plugins boolean
  )
  where x.profile_index between 1 and p_client_max_profiles
    and char_length(btrim(x.name)) between 1 and 80
  on conflict (user_id, profile_index) do update set
    name = excluded.name,
    avatar_color_hex = excluded.avatar_color_hex,
    avatar_id = excluded.avatar_id,
    avatar_url = excluded.avatar_url,
    profile_background_id = excluded.profile_background_id,
    profile_background_url = excluded.profile_background_url,
    uses_primary_addons = excluded.uses_primary_addons,
    uses_primary_plugins = excluded.uses_primary_plugins,
    updated_at = excluded.updated_at;

  delete from public.ka_profiles p
  where p.user_id = v_user_id
    and not exists (
      select 1
      from jsonb_to_recordset(p_profiles) as x(profile_index integer)
      where x.profile_index = p.profile_index
    );

  if (select count(*) from public.ka_profiles where user_id = v_user_id) <>
     jsonb_array_length(p_profiles) then
    raise exception using errcode = '22023', message = 'invalid profile payload';
  end if;
end
$$;

-- Nullable expansion only. No default and no automatic backfill.
alter table public.user_favorites add column if not exists profile_id uuid;
alter table public.watch_history add column if not exists profile_id uuid;

create table if not exists public.ka_legacy_profile_assignments (
  source_table text not null check (source_table in ('user_favorites', 'watch_history')),
  user_id uuid not null references auth.users(id) on delete cascade,
  legacy_key jsonb not null check (jsonb_typeof(legacy_key) = 'object'),
  profile_id uuid not null,
  evidence text not null check (char_length(btrim(evidence)) between 1 and 2000),
  mapped_by uuid not null references auth.users(id),
  mapped_at timestamptz not null default now(),
  primary key (source_table, user_id, legacy_key),
  foreign key (profile_id, user_id)
    references public.ka_profiles(id, user_id)
    on update restrict on delete restrict
);

drop policy if exists "ka_profiles_owner_access" on public.ka_profiles;
drop policy if exists ka_profiles_owner_select_v2 on public.ka_profiles;
drop policy if exists ka_profiles_owner_insert_v2 on public.ka_profiles;
drop policy if exists ka_profiles_owner_update_v2 on public.ka_profiles;
drop policy if exists ka_profiles_owner_delete_v2 on public.ka_profiles;
create policy ka_profiles_owner_select_v2 on public.ka_profiles for select to authenticated
  using (user_id = (select auth.uid()) and not coalesce(((select auth.jwt())->>'is_anonymous')::boolean, false));
create policy ka_profiles_owner_insert_v2 on public.ka_profiles for insert to authenticated
  with check (user_id = (select auth.uid()) and not coalesce(((select auth.jwt())->>'is_anonymous')::boolean, false));
create policy ka_profiles_owner_update_v2 on public.ka_profiles for update to authenticated
  using (user_id = (select auth.uid()) and not coalesce(((select auth.jwt())->>'is_anonymous')::boolean, false))
  with check (user_id = (select auth.uid()) and not coalesce(((select auth.jwt())->>'is_anonymous')::boolean, false));
create policy ka_profiles_owner_delete_v2 on public.ka_profiles for delete to authenticated
  using (user_id = (select auth.uid()) and not coalesce(((select auth.jwt())->>'is_anonymous')::boolean, false));

alter table public.ka_legacy_profile_assignments enable row level security;
alter table public.ka_legacy_profile_assignments force row level security;
revoke all on table public.ka_legacy_profile_assignments from public, anon, authenticated;
revoke all on function public.ka_profile_identity_immutable() from public, anon, authenticated;
revoke all on function public.sync_push_profiles(integer, jsonb, text) from public, anon;
grant execute on function public.sync_push_profiles(integer, jsonb, text) to authenticated;

commit;

-- Rollback for isolated validation:
-- restore the previous sync_push_profiles definition, drop the trigger/function,
-- drop ka_legacy_profile_assignments, and drop nullable profile_id columns only if still empty.
