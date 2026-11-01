-- Harden rewarded-ad claims to the authenticated user's owned profile.
-- This migration is intended for isolated CI review; it must not be applied to
-- the production Supabase project without an explicit release authorization.

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
    v_existing_user_id uuid;
    v_existing_profile_index integer;
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
    if p_profile_index is null or p_profile_index not between 1 and 6 then
        raise exception 'invalid profile index' using errcode = '22023';
    end if;
    if not exists (
        select 1
        from public.ka_profiles
        where user_id = v_user_id
          and profile_index = p_profile_index
    ) then
        raise exception 'profile ownership required' using errcode = '42501';
    end if;

    select c.user_id, c.profile_index
      into v_existing_user_id, v_existing_profile_index
      from public.ka_ad_reward_claims c
     where c.event_id = p_event_id
     for update;

    if found then
        if v_existing_user_id <> v_user_id
           or v_existing_profile_index <> p_profile_index then
            raise exception 'event already claimed' using errcode = '42501';
        end if;
        select expires_at
          into v_new_expiry
          from public.ka_ad_reward_balances
         where user_id = v_user_id
           and profile_index = p_profile_index;
        return jsonb_build_object('expires_at', coalesce(v_new_expiry, now()));
    end if;

    insert into public.ka_ad_reward_claims(event_id, user_id, profile_index)
    values (p_event_id, v_user_id, p_profile_index)
    on conflict (event_id) do nothing;
    v_claim_inserted := found;
    if not v_claim_inserted then
        raise exception 'event already claimed' using errcode = '42501';
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
