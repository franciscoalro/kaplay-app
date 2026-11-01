-- KA Play Migration A — local isolated validation only.
-- Do not apply to production until legacy ownership is explicitly confirmed.

begin;

alter table public.user_favorites add column profile_index integer not null default 1;
alter table public.watch_history add column profile_index integer not null default 1;
alter table public.user_favorites add constraint user_favorites_profile_index_check check (profile_index between 1 and 6);
alter table public.watch_history add constraint watch_history_profile_index_check check (profile_index between 1 and 6);

do $$
begin
  if exists (select 1 from public.user_favorites group by user_id, profile_index, media_id having count(*) > 1) then
    raise exception 'duplicate favorite after profile assignment' using errcode='23505';
  end if;
  if exists (select 1 from public.watch_history group by user_id, profile_index, media_id, season_num, episode_num having count(*) > 1) then
    raise exception 'duplicate history after profile assignment' using errcode='23505';
  end if;
  if exists (
    select 1 from public.user_favorites f
    where not exists (select 1 from public.ka_profiles p where p.user_id=f.user_id and p.profile_index=f.profile_index)
  ) then raise exception 'favorite has no owning profile' using errcode='23503'; end if;
  if exists (
    select 1 from public.watch_history h
    where not exists (select 1 from public.ka_profiles p where p.user_id=h.user_id and p.profile_index=h.profile_index)
  ) then raise exception 'history has no owning profile' using errcode='23503'; end if;
end
$$;

alter table public.user_favorites drop constraint user_favorites_pkey;
alter table public.user_favorites add constraint user_favorites_pkey primary key (user_id, profile_index, media_id);
alter table public.watch_history drop constraint watch_history_pkey;
alter table public.watch_history add constraint watch_history_pkey primary key (user_id, profile_index, media_id, season_num, episode_num);
create index user_favorites_profile_updated_idx on public.user_favorites(user_id, profile_index, updated_at desc);
create index watch_history_profile_updated_idx on public.watch_history(user_id, profile_index, updated_at desc);

commit;
