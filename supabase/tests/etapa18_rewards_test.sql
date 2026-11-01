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

set role authenticated;
select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000001', false);

-- A valid owned profile receives one hour.
select public.ka_claim_ad_reward(
  1,
  '10000000-0000-0000-0000-000000000001'
);

-- Replaying the same event is idempotent: it does not create a second claim.
select public.ka_claim_ad_reward(1, '10000000-0000-0000-0000-000000000001');
reset role;
do $$
declare
  claim_count integer;
begin
  select count(*) into claim_count
  from public.ka_ad_reward_claims
  where event_id = '10000000-0000-0000-0000-000000000001';
  if claim_count <> 1 then
    raise exception 'duplicate event created % claims', claim_count;
  end if;
end $$;

-- An authenticated user cannot claim for a profile they do not own.
set role authenticated;
select set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000001', false);
do $$
begin
  begin
    perform public.ka_claim_ad_reward(2, '10000000-0000-0000-0000-000000000002');
    raise exception 'cross-profile claim unexpectedly succeeded';
  exception when sqlstate '42501' then
    null;
  end;
end $$;

-- The 25th claim in a rolling 24-hour window is rejected.
do $$
declare
  i integer;
begin
  for i in 2..24 loop
    perform public.ka_claim_ad_reward(1, md5('quota-' || i)::uuid);
  end loop;
  begin
    perform public.ka_claim_ad_reward(1, md5('quota-25')::uuid);
    raise exception 'daily quota unexpectedly accepted a 25th claim';
  exception when sqlstate 'P0001' then
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
reset role;

select 'rewards_ownership_idempotency=PASS' as assertion;
