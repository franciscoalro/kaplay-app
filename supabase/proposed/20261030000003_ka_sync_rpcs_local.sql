-- KA Play sync RPCs for isolated validation only.
-- This file deliberately excludes avatars, membership and reward functions.

create or replace function public.sync_get_library_delta_cursor(p_profile_id integer)
returns bigint language plpgsql security definer set search_path=public,pg_temp as $$
begin perform public.ka_assert_profile_access(p_profile_id); return coalesce((select max(event_id) from public.ka_library_events where user_id=auth.uid() and profile_index=p_profile_id),0); end $$;

create or replace function public.sync_pull_library(p_profile_id integer,p_limit integer,p_offset integer)
returns table(content_id text,content_type text,name text,poster text,poster_shape text,background text,logo text,description text,release_info text,imdb_rating real,genres text[],addon_base_url text,added_at bigint)
language plpgsql security definer set search_path=public,pg_temp as $$
begin
 perform public.ka_assert_profile_access(p_profile_id);
 if p_limit < 1 or p_limit > 1000 or p_offset < 0 then raise exception 'invalid pagination' using errcode='22023'; end if;
 return query select i.content_id,i.content_type,i.name,i.poster,i.poster_shape,i.background,i.logo,i.description,i.release_info,i.imdb_rating,i.genres,i.addon_base_url,i.added_at from public.ka_library_items i where i.user_id=auth.uid() and i.profile_index=p_profile_id order by i.added_at desc,i.content_id limit p_limit offset p_offset;
end $$;

create or replace function public.sync_pull_library_delta(p_profile_id integer,p_since_event_id bigint,p_limit integer)
returns table(event_id bigint,operation text,content_id text,content_type text,name text,poster text,poster_shape text,background text,logo text,description text,release_info text,imdb_rating real,genres text[],addon_base_url text,added_at bigint)
language plpgsql security definer set search_path=public,pg_temp as $$
begin
 perform public.ka_assert_profile_access(p_profile_id);
 if p_limit < 1 or p_limit > 1000 then raise exception 'invalid limit' using errcode='22023'; end if;
 return query select e.event_id,e.operation,e.content_id,e.content_type,
  e.payload->>'name',e.payload->>'poster',coalesce(e.payload->>'poster_shape','POSTER'),e.payload->>'background',e.payload->>'logo',e.payload->>'description',e.payload->>'release_info',(e.payload->>'imdb_rating')::real,coalesce(array(select jsonb_array_elements_text(coalesce(e.payload->'genres','[]'::jsonb))), '{}'),e.payload->>'addon_base_url',(e.payload->>'added_at')::bigint
 from public.ka_library_events e where e.user_id=auth.uid() and e.profile_index=p_profile_id and e.event_id>p_since_event_id order by e.event_id limit p_limit;
end $$;

create or replace function public.sync_push_library_items(p_profile_id integer,p_items jsonb,p_origin_client_id text default null)
returns void language plpgsql security definer set search_path=public,pg_temp as $$
declare x record; changed boolean; payload jsonb;
begin
 perform public.ka_assert_profile_access(p_profile_id);
 if jsonb_typeof(p_items)<>'array' or jsonb_array_length(p_items)>1000 then raise exception 'invalid items' using errcode='22023'; end if;
 for x in select * from jsonb_to_recordset(p_items) as r(content_id text,content_type text,name text,poster text,poster_shape text,background text,logo text,description text,release_info text,imdb_rating real,genres text[],addon_base_url text,added_at bigint) loop
  insert into public.ka_library_items(user_id,profile_index,content_id,content_type,name,poster,poster_shape,background,logo,description,release_info,imdb_rating,genres,addon_base_url,added_at)
  values(auth.uid(),p_profile_id,x.content_id,x.content_type,coalesce(x.name,''),x.poster,coalesce(x.poster_shape,'POSTER'),x.background,x.logo,x.description,x.release_info,x.imdb_rating,coalesce(x.genres,'{}'),x.addon_base_url,coalesce(x.added_at,0))
  on conflict(user_id,profile_index,content_id,content_type) do update set name=excluded.name,poster=excluded.poster,poster_shape=excluded.poster_shape,background=excluded.background,logo=excluded.logo,description=excluded.description,release_info=excluded.release_info,imdb_rating=excluded.imdb_rating,genres=excluded.genres,addon_base_url=excluded.addon_base_url,added_at=excluded.added_at,updated_at=now()
  where excluded.added_at >= public.ka_library_items.added_at and (public.ka_library_items.name,public.ka_library_items.poster,public.ka_library_items.poster_shape,public.ka_library_items.background,public.ka_library_items.logo,public.ka_library_items.description,public.ka_library_items.release_info,public.ka_library_items.imdb_rating,public.ka_library_items.genres,public.ka_library_items.addon_base_url,public.ka_library_items.added_at) is distinct from (excluded.name,excluded.poster,excluded.poster_shape,excluded.background,excluded.logo,excluded.description,excluded.release_info,excluded.imdb_rating,excluded.genres,excluded.addon_base_url,excluded.added_at)
  returning true into changed;
  if changed then select to_jsonb(i) into payload from public.ka_library_items i where i.user_id=auth.uid() and i.profile_index=p_profile_id and i.content_id=x.content_id and i.content_type=x.content_type; insert into public.ka_library_events(user_id,profile_index,operation,content_id,content_type,payload,origin_client_id) values(auth.uid(),p_profile_id,'upsert',x.content_id,x.content_type,payload,p_origin_client_id); end if;
  changed:=false;
 end loop;
end $$;

create or replace function public.sync_delete_library_items(p_profile_id integer,p_keys jsonb,p_origin_client_id text default null)
returns void language plpgsql security definer set search_path=public,pg_temp as $$
declare x record; old jsonb;
begin
 perform public.ka_assert_profile_access(p_profile_id);
 if jsonb_typeof(p_keys)<>'array' or jsonb_array_length(p_keys)>1000 then raise exception 'invalid keys' using errcode='22023'; end if;
 for x in select * from jsonb_to_recordset(p_keys) as r(content_id text,content_type text) loop
  select to_jsonb(i) into old from public.ka_library_items i where i.user_id=auth.uid() and i.profile_index=p_profile_id and i.content_id=x.content_id and i.content_type=x.content_type;
  delete from public.ka_library_items where user_id=auth.uid() and profile_index=p_profile_id and content_id=x.content_id and content_type=x.content_type;
  if old is not null then insert into public.ka_library_events(user_id,profile_index,operation,content_id,content_type,payload,origin_client_id) values(auth.uid(),p_profile_id,'delete',x.content_id,x.content_type,old,p_origin_client_id); end if;
 end loop;
end $$;

create or replace function public.sync_get_watched_items_delta_cursor(p_profile_id integer)
returns bigint language plpgsql security definer set search_path=public,pg_temp as $$ begin perform public.ka_assert_profile_access(p_profile_id); return coalesce((select max(event_id) from public.ka_watched_events where user_id=auth.uid() and profile_index=p_profile_id),0); end $$;

create or replace function public.sync_pull_watched_items(p_profile_id integer,p_page integer,p_page_size integer)
returns table(content_id text,content_type text,title text,season integer,episode integer,watched_at bigint)
language plpgsql security definer set search_path=public,pg_temp as $$
begin perform public.ka_assert_profile_access(p_profile_id); if p_page<1 or p_page_size<1 or p_page_size>1000 then raise exception 'invalid pagination' using errcode='22023'; end if; return query select w.content_id,w.content_type,w.title,nullif(w.season_num,-1),nullif(w.episode_num,-1),w.watched_at from public.ka_watched_items w where w.user_id=auth.uid() and w.profile_index=p_profile_id order by w.watched_at desc,w.content_id offset (p_page-1)*p_page_size limit p_page_size; end $$;

create or replace function public.sync_pull_watched_items_delta(p_profile_id integer,p_since_event_id bigint,p_limit integer)
returns table(event_id bigint,operation text,content_id text,content_type text,title text,season integer,episode integer,watched_at bigint)
language plpgsql security definer set search_path=public,pg_temp as $$
begin perform public.ka_assert_profile_access(p_profile_id); if p_limit<1 or p_limit>1000 then raise exception 'invalid limit' using errcode='22023'; end if; return query select e.event_id,e.operation,e.content_id,e.content_type,e.payload->>'title',nullif((e.payload->>'season_num')::int,-1),nullif((e.payload->>'episode_num')::int,-1),(e.payload->>'watched_at')::bigint from public.ka_watched_events e where e.user_id=auth.uid() and e.profile_index=p_profile_id and e.event_id>p_since_event_id order by e.event_id limit p_limit; end $$;

create or replace function public.sync_push_watched_items(p_profile_id integer,p_items jsonb,p_origin_client_id text default null)
returns void language plpgsql security definer set search_path=public,pg_temp as $$
declare x record; changed boolean; payload jsonb;
begin perform public.ka_assert_profile_access(p_profile_id); if jsonb_typeof(p_items)<>'array' or jsonb_array_length(p_items)>1000 then raise exception 'invalid items' using errcode='22023'; end if;
 for x in select * from jsonb_to_recordset(p_items) as r(content_id text,content_type text,title text,season int,episode int,watched_at bigint) loop
  insert into public.ka_watched_items(user_id,profile_index,content_id,content_type,title,season_num,episode_num,watched_at) values(auth.uid(),p_profile_id,x.content_id,x.content_type,coalesce(x.title,''),coalesce(x.season,-1),coalesce(x.episode,-1),coalesce(x.watched_at,0)) on conflict(user_id,profile_index,content_id,content_type,season_num,episode_num) do update set title=excluded.title,watched_at=excluded.watched_at,updated_at=now() where excluded.watched_at>=public.ka_watched_items.watched_at and (public.ka_watched_items.title,public.ka_watched_items.watched_at) is distinct from (excluded.title,excluded.watched_at) returning true into changed;
  if changed then select to_jsonb(w) into payload from public.ka_watched_items w where w.user_id=auth.uid() and w.profile_index=p_profile_id and w.content_id=x.content_id and w.content_type=x.content_type and w.season_num=coalesce(x.season,-1) and w.episode_num=coalesce(x.episode,-1); insert into public.ka_watched_events(user_id,profile_index,operation,content_id,content_type,payload,origin_client_id) values(auth.uid(),p_profile_id,'upsert',x.content_id,x.content_type,payload,p_origin_client_id); end if; changed:=false;
 end loop;
end $$;

create or replace function public.sync_delete_watched_items(p_profile_id integer,p_keys jsonb,p_origin_client_id text default null)
returns void language plpgsql security definer set search_path=public,pg_temp as $$
declare x record; old jsonb; s int; e int;
begin perform public.ka_assert_profile_access(p_profile_id); for x in select * from jsonb_to_recordset(p_keys) as r(content_id text,season int,episode int) loop s:=coalesce(x.season,-1); e:=coalesce(x.episode,-1); select to_jsonb(w) into old from public.ka_watched_items w where w.user_id=auth.uid() and w.profile_index=p_profile_id and w.content_id=x.content_id and w.season_num=s and w.episode_num=e; delete from public.ka_watched_items where user_id=auth.uid() and profile_index=p_profile_id and content_id=x.content_id and season_num=s and episode_num=e; if old is not null then insert into public.ka_watched_events(user_id,profile_index,operation,content_id,content_type,payload,origin_client_id) values(auth.uid(),p_profile_id,'delete',x.content_id,coalesce(old->>'content_type',''),old,p_origin_client_id); end if; end loop; end $$;

create or replace function public.sync_get_watch_progress_delta_cursor(p_profile_id integer)
returns bigint language plpgsql security definer set search_path=public,pg_temp as $$ begin perform public.ka_assert_profile_access(p_profile_id); return coalesce((select max(event_id) from public.ka_watch_progress_events where user_id=auth.uid() and profile_index=p_profile_id),0); end $$;

create or replace function public.sync_pull_watch_progress(p_profile_id integer,p_since_last_watched bigint default null,p_limit integer default null)
returns table(content_id text,content_type text,video_id text,season integer,episode integer,"position" bigint,duration bigint,last_watched bigint,progress_key text)
language plpgsql security definer set search_path=public,pg_temp as $$
begin perform public.ka_assert_profile_access(p_profile_id); if coalesce(p_limit,1000)<1 or coalesce(p_limit,1000)>1000 then raise exception 'invalid limit' using errcode='22023'; end if; return query select w.content_id,w.content_type,w.video_id,nullif(w.season_num,-1),nullif(w.episode_num,-1),w.position,w.duration,w.last_watched,w.progress_key from public.ka_watch_progress w where w.user_id=auth.uid() and w.profile_index=p_profile_id and (p_since_last_watched is null or w.last_watched>p_since_last_watched) order by w.last_watched desc,w.progress_key limit coalesce(p_limit,1000); end $$;

create or replace function public.sync_pull_watch_progress_delta(p_profile_id integer,p_since_event_id bigint,p_limit integer)
returns table(event_id bigint,operation text,progress_key text,content_id text,content_type text,video_id text,season integer,episode integer,"position" bigint,duration bigint,last_watched bigint)
language plpgsql security definer set search_path=public,pg_temp as $$
begin perform public.ka_assert_profile_access(p_profile_id); if p_limit<1 or p_limit>1000 then raise exception 'invalid limit' using errcode='22023'; end if; return query select e.event_id,e.operation,e.progress_key,e.payload->>'content_id',e.payload->>'content_type',e.payload->>'video_id',nullif((e.payload->>'season_num')::int,-1),nullif((e.payload->>'episode_num')::int,-1),(e.payload->>'position')::bigint,(e.payload->>'duration')::bigint,(e.payload->>'last_watched')::bigint from public.ka_watch_progress_events e where e.user_id=auth.uid() and e.profile_index=p_profile_id and e.event_id>p_since_event_id order by e.event_id limit p_limit; end $$;

create or replace function public.sync_push_watch_progress(p_profile_id integer,p_entries jsonb,p_origin_client_id text default null)
returns void language plpgsql security definer set search_path=public,pg_temp as $$
declare x record; changed boolean; payload jsonb;
begin perform public.ka_assert_profile_access(p_profile_id); if jsonb_typeof(p_entries)<>'array' or jsonb_array_length(p_entries)>1000 then raise exception 'invalid entries' using errcode='22023'; end if;
 for x in select * from jsonb_to_recordset(p_entries) as r(content_id text,content_type text,video_id text,season int,episode int,"position" bigint,duration bigint,last_watched bigint,progress_key text) loop
  if nullif(x.progress_key,'') is null or nullif(x.video_id,'') is null then raise exception 'progress_key and video_id required' using errcode='22023'; end if;
  insert into public.ka_watch_progress(user_id,profile_index,progress_key,content_id,content_type,video_id,season_num,episode_num,position,duration,last_watched) values(auth.uid(),p_profile_id,x.progress_key,x.content_id,x.content_type,x.video_id,coalesce(x.season,-1),coalesce(x.episode,-1),greatest(coalesce(x.position,0),0),greatest(coalesce(x.duration,0),0),coalesce(x.last_watched,0)) on conflict(user_id,profile_index,progress_key) do update set content_id=excluded.content_id,content_type=excluded.content_type,video_id=excluded.video_id,season_num=excluded.season_num,episode_num=excluded.episode_num,position=excluded.position,duration=excluded.duration,last_watched=excluded.last_watched,updated_at=now() where excluded.last_watched>=public.ka_watch_progress.last_watched and (public.ka_watch_progress.content_id,public.ka_watch_progress.content_type,public.ka_watch_progress.video_id,public.ka_watch_progress.season_num,public.ka_watch_progress.episode_num,public.ka_watch_progress.position,public.ka_watch_progress.duration,public.ka_watch_progress.last_watched) is distinct from (excluded.content_id,excluded.content_type,excluded.video_id,excluded.season_num,excluded.episode_num,excluded.position,excluded.duration,excluded.last_watched) returning true into changed;
  if changed then select to_jsonb(w) into payload from public.ka_watch_progress w where w.user_id=auth.uid() and w.profile_index=p_profile_id and w.progress_key=x.progress_key; insert into public.ka_watch_progress_events(user_id,profile_index,operation,progress_key,payload,origin_client_id) values(auth.uid(),p_profile_id,'upsert',x.progress_key,payload,p_origin_client_id); end if; changed:=false;
 end loop;
end $$;

create or replace function public.sync_delete_watch_progress(p_profile_id integer,p_keys jsonb,p_origin_client_id text default null)
returns void language plpgsql security definer set search_path=public,pg_temp as $$
declare x record; old jsonb;
begin perform public.ka_assert_profile_access(p_profile_id); for x in select value::text as progress_key from jsonb_array_elements_text(p_keys) loop select to_jsonb(w) into old from public.ka_watch_progress w where w.user_id=auth.uid() and w.profile_index=p_profile_id and w.progress_key=x.progress_key; delete from public.ka_watch_progress where user_id=auth.uid() and profile_index=p_profile_id and progress_key=x.progress_key; if old is not null then insert into public.ka_watch_progress_events(user_id,profile_index,operation,progress_key,payload,origin_client_id) values(auth.uid(),p_profile_id,'delete',x.progress_key,old,p_origin_client_id); end if; end loop; end $$;

-- Only authenticated clients may call these gateways.
revoke all on function public.sync_get_library_delta_cursor(integer) from public;
revoke all on function public.sync_pull_library(integer,integer,integer) from public;
revoke all on function public.sync_pull_library_delta(integer,bigint,integer) from public;
revoke all on function public.sync_push_library_items(integer,jsonb,text) from public;
revoke all on function public.sync_delete_library_items(integer,jsonb,text) from public;
revoke all on function public.sync_get_watched_items_delta_cursor(integer) from public;
revoke all on function public.sync_pull_watched_items(integer,integer,integer) from public;
revoke all on function public.sync_pull_watched_items_delta(integer,bigint,integer) from public;
revoke all on function public.sync_push_watched_items(integer,jsonb,text) from public;
revoke all on function public.sync_delete_watched_items(integer,jsonb,text) from public;
revoke all on function public.sync_get_watch_progress_delta_cursor(integer) from public;
revoke all on function public.sync_pull_watch_progress(integer,bigint,integer) from public;
revoke all on function public.sync_pull_watch_progress_delta(integer,bigint,integer) from public;
revoke all on function public.sync_push_watch_progress(integer,jsonb,text) from public;
revoke all on function public.sync_delete_watch_progress(integer,jsonb,text) from public;
grant execute on function public.sync_get_library_delta_cursor(integer) to authenticated;
grant execute on function public.sync_pull_library(integer,integer,integer) to authenticated;
grant execute on function public.sync_pull_library_delta(integer,bigint,integer) to authenticated;
grant execute on function public.sync_push_library_items(integer,jsonb,text) to authenticated;
grant execute on function public.sync_delete_library_items(integer,jsonb,text) to authenticated;
grant execute on function public.sync_get_watched_items_delta_cursor(integer) to authenticated;
grant execute on function public.sync_pull_watched_items(integer,integer,integer) to authenticated;
grant execute on function public.sync_pull_watched_items_delta(integer,bigint,integer) to authenticated;
grant execute on function public.sync_push_watched_items(integer,jsonb,text) to authenticated;
grant execute on function public.sync_delete_watched_items(integer,jsonb,text) to authenticated;
grant execute on function public.sync_get_watch_progress_delta_cursor(integer) to authenticated;
grant execute on function public.sync_pull_watch_progress(integer,bigint,integer) to authenticated;
grant execute on function public.sync_pull_watch_progress_delta(integer,bigint,integer) to authenticated;
grant execute on function public.sync_push_watch_progress(integer,jsonb,text) to authenticated;
grant execute on function public.sync_delete_watch_progress(integer,jsonb,text) to authenticated;
