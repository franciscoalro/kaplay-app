\set ON_ERROR_STOP on

insert into auth.users(id, email) values
  ('00000000-0000-0000-0000-000000000001', 'etapa18-a@example.invalid'),
  ('00000000-0000-0000-0000-000000000002', 'etapa18-b@example.invalid')
on conflict (id) do nothing;

insert into public.ka_profiles(user_id, profile_index, name)
values
  ('00000000-0000-0000-0000-000000000001', 1, 'A one'),
  ('00000000-0000-0000-0000-000000000002', 1, 'B one')
on conflict (user_id, profile_index) do nothing;

select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000001', false);

-- A valid owned profile receives one hour.
select public.ka_claim_ad_reward(
  1,
  '10000000-0000-0000-0000-000000000001'
);

-- Replaying the same event is idempotent: the balance timestamp is unchanged.
with first_claim as (
  select expires_at from public.ka_ad_reward_balances
  where user_id = '00000000-0000-0000-0000-000000000001' and profile_index = 1
)
select case when (
  (public.ka_claim_ad_reward(1, '10000000-0000-0000-0000-000000000001')->>'expires_at')::timestamptz
) = (select expires_at from first_claim)
then 1 else 1 / 0 end as duplicate_event_is_idempotent;

-- An authenticated user cannot claim for a profile they do not own.
do $$
begin
  begin
    perform public.ka_claim_ad_reward(2, '10000000-0000-0000-0000-000000000002');
    raise exception 'cross-profile claim unexpectedly succeeded';
  exception when sqlstate '42501' then
    null;
  end;
end $$;

-- A different authenticated user cannot replay another user's event.
select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000002', false);
do $$
begin
  begin
    perform public.ka_claim_ad_reward(1, '10000000-0000-0000-0000-000000000001');
    raise exception 'cross-user event replay unexpectedly succeeded';
  exception when sqlstate '42501' then
    null;
  end;
end $$;

select 'rewards_ownership_idempotency=PASS' as assertion;
