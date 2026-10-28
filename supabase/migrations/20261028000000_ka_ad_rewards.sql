-- KA Play rewarded-ad backend.
-- The mobile client never receives a service-role key; only this SECURITY DEFINER
-- function can extend a user's ad-free balance.

create table if not exists public.ka_ad_reward_balances (
    user_id uuid not null references auth.users(id) on delete cascade,
    profile_index integer not null,
    expires_at timestamptz not null,
    updated_at timestamptz not null default now(),
    primary key (user_id, profile_index),
    constraint ka_ad_reward_profile_index_check check (profile_index between 1 and 10)
);

create table if not exists public.ka_ad_reward_claims (
    event_id uuid primary key,
    user_id uuid not null references auth.users(id) on delete cascade,
    profile_index integer not null,
    claimed_at timestamptz not null default now()
);

create index if not exists ka_ad_reward_claims_user_claimed_at_idx
    on public.ka_ad_reward_claims (user_id, claimed_at desc);

alter table public.ka_ad_reward_balances enable row level security;
alter table public.ka_ad_reward_claims enable row level security;

revoke all on public.ka_ad_reward_balances from anon, authenticated;
revoke all on public.ka_ad_reward_claims from anon, authenticated;
grant select on public.ka_ad_reward_balances to authenticated;

drop policy if exists ka_ad_reward_balances_select_own on public.ka_ad_reward_balances;
create policy ka_ad_reward_balances_select_own
    on public.ka_ad_reward_balances
    for select
    to authenticated
    using (user_id = (select auth.uid()));

create or replace function public.ka_claim_ad_reward(
    p_profile_index integer,
    p_event_id uuid
)
returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
    v_user_id uuid := auth.uid();
    v_current_expiry timestamptz;
    v_new_expiry timestamptz;
    v_claim_inserted boolean;
begin
    if v_user_id is null then
        raise exception 'authentication required' using errcode = '42501';
    end if;

    if p_event_id is null then
        raise exception 'event id required' using errcode = '22023';
    end if;

    if p_profile_index is null or p_profile_index not between 1 and 10 then
        raise exception 'invalid profile index' using errcode = '22023';
    end if;

    insert into public.ka_ad_reward_claims(event_id, user_id, profile_index)
    values (p_event_id, v_user_id, p_profile_index)
    on conflict (event_id) do nothing;
    v_claim_inserted := found;

    if not v_claim_inserted then
        select expires_at
          into v_new_expiry
          from public.ka_ad_reward_balances
         where user_id = v_user_id
           and profile_index = p_profile_index;
        return jsonb_build_object('expires_at', coalesce(v_new_expiry, now()));
    end if;

    if (
        select count(*)
          from public.ka_ad_reward_claims
         where user_id = v_user_id
           and claimed_at >= now() - interval '24 hours'
    ) > 24 then
        raise exception 'daily reward limit reached' using errcode = 'P0001';
    end if;

    select expires_at
      into v_current_expiry
      from public.ka_ad_reward_balances
     where user_id = v_user_id
       and profile_index = p_profile_index
     for update;

    v_new_expiry := greatest(coalesce(v_current_expiry, now()), now()) + interval '1 hour';

    insert into public.ka_ad_reward_balances(user_id, profile_index, expires_at, updated_at)
    values (v_user_id, p_profile_index, v_new_expiry, now())
    on conflict (user_id, profile_index) do update
        set expires_at = excluded.expires_at,
            updated_at = excluded.updated_at;

    return jsonb_build_object('expires_at', v_new_expiry);
end;
$$;

revoke all on function public.ka_claim_ad_reward(integer, uuid) from public;
grant execute on function public.ka_claim_ad_reward(integer, uuid) to authenticated;
