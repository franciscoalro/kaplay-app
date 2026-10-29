-- KA Play profile storage and synchronization RPCs.
-- The mobile client sends the complete profile list on every write; replacing the
-- caller's rows in one transaction keeps profile indexes deterministic.

create table if not exists public.ka_profiles (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references auth.users(id) on delete cascade,
    profile_index integer not null check (profile_index between 1 and 6),
    name text not null check (char_length(btrim(name)) between 1 and 80),
    avatar_color_hex text not null default '#1E88E5',
    avatar_id text,
    avatar_url text,
    profile_background_id text,
    profile_background_url text,
    uses_primary_addons boolean not null default false,
    uses_primary_plugins boolean not null default false,
    pin_enabled boolean not null default false,
    pin_locked_until timestamptz,
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now(),
    unique (user_id, profile_index)
);

alter table public.ka_profiles enable row level security;

revoke all on table public.ka_profiles from anon;
grant select, insert, update, delete on table public.ka_profiles to authenticated;

drop policy if exists "ka_profiles_owner_access" on public.ka_profiles;
create policy "ka_profiles_owner_access"
    on public.ka_profiles
    for all
    to authenticated
    using ((select auth.uid()) = user_id)
    with check ((select auth.uid()) = user_id);

create or replace function public.sync_pull_profiles()
returns setof public.ka_profiles
language sql
stable
security invoker
set search_path = public
as $$
    select p.*
    from public.ka_profiles p
    where p.user_id = (select auth.uid())
    order by p.profile_index;
$$;

create or replace function public.sync_push_profiles(
    p_client_max_profiles integer,
    p_profiles jsonb,
    p_origin_client_id text default null
)
returns void
language plpgsql
volatile
security invoker
set search_path = public
as $$
begin
    if (select auth.uid()) is null then
        raise exception using errcode = '42501', message = 'Authentication required';
    end if;
    if p_client_max_profiles is null or p_client_max_profiles < 1 or p_client_max_profiles > 6 then
        raise exception using errcode = '22023', message = 'Invalid profile limit';
    end if;
    if jsonb_typeof(p_profiles) <> 'array' then
        raise exception using errcode = '22023', message = 'Profiles must be an array';
    end if;
    if jsonb_array_length(p_profiles) > p_client_max_profiles then
        raise exception using errcode = '22023', message = 'Too many profiles';
    end if;

    delete from public.ka_profiles
    where user_id = (select auth.uid());

    insert into public.ka_profiles (
        user_id,
        profile_index,
        name,
        avatar_color_hex,
        avatar_id,
        avatar_url,
        profile_background_id,
        profile_background_url,
        uses_primary_addons,
        uses_primary_plugins
    )
    select
        (select auth.uid()),
        p.profile_index,
        btrim(p.name),
        p.avatar_color_hex,
        p.avatar_id,
        p.avatar_url,
        p.profile_background_id,
        p.profile_background_url,
        coalesce(p.uses_primary_addons, false),
        coalesce(p.uses_primary_plugins, false)
    from jsonb_to_recordset(p_profiles) as p(
        profile_index integer,
        name text,
        avatar_color_hex text,
        avatar_id text,
        avatar_url text,
        profile_background_id text,
        profile_background_url text,
        uses_primary_addons boolean,
        uses_primary_plugins boolean
    );
end;
$$;

grant execute on function public.sync_pull_profiles() to authenticated;
grant execute on function public.sync_push_profiles(integer, jsonb, text) to authenticated;
