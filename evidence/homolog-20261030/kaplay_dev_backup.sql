--
-- PostgreSQL database dump
--

\restrict gZMwTbhkidVtZVLn5jmAZf2wFGmbRkLqQcDKUeblVXU5zfg0zJQu8kaeufLHKN4

-- Dumped from database version 17.11
-- Dumped by pg_dump version 17.11

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- Name: auth; Type: SCHEMA; Schema: -; Owner: -
--

CREATE SCHEMA auth;


--
-- Name: factor_type; Type: TYPE; Schema: auth; Owner: -
--

CREATE TYPE auth.factor_type AS ENUM (
    'totp',
    'webauthn',
    'phone'
);


--
-- Name: aal_level; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.aal_level AS ENUM (
    'aal1',
    'aal2',
    'aal3'
);


--
-- Name: code_challenge_method; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.code_challenge_method AS ENUM (
    's256',
    'plain'
);


--
-- Name: factor_status; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.factor_status AS ENUM (
    'unverified',
    'verified'
);


--
-- Name: factor_type; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.factor_type AS ENUM (
    'totp',
    'webauthn'
);


--
-- Name: one_time_token_type; Type: TYPE; Schema: public; Owner: -
--

CREATE TYPE public.one_time_token_type AS ENUM (
    'confirmation_token',
    'reauthentication_token',
    'recovery_token',
    'email_change_token_new',
    'email_change_token_current',
    'phone_change_token'
);


--
-- Name: email(); Type: FUNCTION; Schema: auth; Owner: -
--

CREATE FUNCTION auth.email() RETURNS text
    LANGUAGE sql STABLE
    AS $$
  select 
  coalesce(
    nullif(current_setting('request.jwt.claim.email', true), ''),
    (nullif(current_setting('request.jwt.claims', true), '')::jsonb ->> 'email')
  )::text
$$;


--
-- Name: FUNCTION email(); Type: COMMENT; Schema: auth; Owner: -
--

COMMENT ON FUNCTION auth.email() IS 'Deprecated. Use auth.jwt() -> ''email'' instead.';


--
-- Name: jwt(); Type: FUNCTION; Schema: auth; Owner: -
--

CREATE FUNCTION auth.jwt() RETURNS jsonb
    LANGUAGE sql STABLE
    AS $$
  select 
    coalesce(
        nullif(current_setting('request.jwt.claim', true), ''),
        nullif(current_setting('request.jwt.claims', true), '')
    )::jsonb
$$;


--
-- Name: role(); Type: FUNCTION; Schema: auth; Owner: -
--

CREATE FUNCTION auth.role() RETURNS text
    LANGUAGE sql STABLE
    AS $$
  select 
  coalesce(
    nullif(current_setting('request.jwt.claim.role', true), ''),
    (nullif(current_setting('request.jwt.claims', true), '')::jsonb ->> 'role')
  )::text
$$;


--
-- Name: FUNCTION role(); Type: COMMENT; Schema: auth; Owner: -
--

COMMENT ON FUNCTION auth.role() IS 'Deprecated. Use auth.jwt() -> ''role'' instead.';


--
-- Name: uid(); Type: FUNCTION; Schema: auth; Owner: -
--

CREATE FUNCTION auth.uid() RETURNS uuid
    LANGUAGE sql STABLE
    AS $$
  select 
  coalesce(
    nullif(current_setting('request.jwt.claim.sub', true), ''),
    (nullif(current_setting('request.jwt.claims', true), '')::jsonb ->> 'sub')
  )::uuid
$$;


--
-- Name: FUNCTION uid(); Type: COMMENT; Schema: auth; Owner: -
--

COMMENT ON FUNCTION auth.uid() IS 'Deprecated. Use auth.jwt() -> ''sub'' instead.';


--
-- Name: ka_assert_profile_access(integer); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.ka_assert_profile_access(p_profile_id integer) RETURNS void
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public', 'pg_temp'
    AS $$
begin
 if auth.uid() is null then raise exception 'authentication required' using errcode='42501'; end if;
 if not exists(select 1 from public.ka_profiles where user_id=auth.uid() and profile_index=p_profile_id) then
  raise exception 'profile access denied' using errcode='42501';
 end if;
end $$;


--
-- Name: ka_claim_ad_reward(integer, uuid); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.ka_claim_ad_reward(p_profile_index integer, p_event_id uuid) RETURNS jsonb
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public'
    AS $$
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


--
-- Name: sync_delete_library_items(integer, jsonb, text); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.sync_delete_library_items(p_profile_id integer, p_keys jsonb, p_origin_client_id text DEFAULT NULL::text) RETURNS void
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public', 'pg_temp'
    AS $$
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


--
-- Name: sync_delete_watch_progress(integer, jsonb, text); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.sync_delete_watch_progress(p_profile_id integer, p_keys jsonb, p_origin_client_id text DEFAULT NULL::text) RETURNS void
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public', 'pg_temp'
    AS $$
declare x record; old jsonb;
begin perform public.ka_assert_profile_access(p_profile_id); for x in select value::text as progress_key from jsonb_array_elements_text(p_keys) loop select to_jsonb(w) into old from public.ka_watch_progress w where w.user_id=auth.uid() and w.profile_index=p_profile_id and w.progress_key=x.progress_key; delete from public.ka_watch_progress where user_id=auth.uid() and profile_index=p_profile_id and progress_key=x.progress_key; if old is not null then insert into public.ka_watch_progress_events(user_id,profile_index,operation,progress_key,payload,origin_client_id) values(auth.uid(),p_profile_id,'delete',x.progress_key,old,p_origin_client_id); end if; end loop; end $$;


--
-- Name: sync_delete_watched_items(integer, jsonb, text); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.sync_delete_watched_items(p_profile_id integer, p_keys jsonb, p_origin_client_id text DEFAULT NULL::text) RETURNS void
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public', 'pg_temp'
    AS $$
declare x record; old jsonb; s int; e int;
begin perform public.ka_assert_profile_access(p_profile_id); for x in select * from jsonb_to_recordset(p_keys) as r(content_id text,season int,episode int) loop s:=coalesce(x.season,-1); e:=coalesce(x.episode,-1); select to_jsonb(w) into old from public.ka_watched_items w where w.user_id=auth.uid() and w.profile_index=p_profile_id and w.content_id=x.content_id and w.season_num=s and w.episode_num=e; delete from public.ka_watched_items where user_id=auth.uid() and profile_index=p_profile_id and content_id=x.content_id and season_num=s and episode_num=e; if old is not null then insert into public.ka_watched_events(user_id,profile_index,operation,content_id,content_type,payload,origin_client_id) values(auth.uid(),p_profile_id,'delete',x.content_id,coalesce(old->>'content_type',''),old,p_origin_client_id); end if; end loop; end $$;


--
-- Name: sync_get_library_delta_cursor(integer); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.sync_get_library_delta_cursor(p_profile_id integer) RETURNS bigint
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public', 'pg_temp'
    AS $$
begin perform public.ka_assert_profile_access(p_profile_id); return coalesce((select max(event_id) from public.ka_library_events where user_id=auth.uid() and profile_index=p_profile_id),0); end $$;


--
-- Name: sync_get_watch_progress_delta_cursor(integer); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.sync_get_watch_progress_delta_cursor(p_profile_id integer) RETURNS bigint
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public', 'pg_temp'
    AS $$ begin perform public.ka_assert_profile_access(p_profile_id); return coalesce((select max(event_id) from public.ka_watch_progress_events where user_id=auth.uid() and profile_index=p_profile_id),0); end $$;


--
-- Name: sync_get_watched_items_delta_cursor(integer); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.sync_get_watched_items_delta_cursor(p_profile_id integer) RETURNS bigint
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public', 'pg_temp'
    AS $$ begin perform public.ka_assert_profile_access(p_profile_id); return coalesce((select max(event_id) from public.ka_watched_events where user_id=auth.uid() and profile_index=p_profile_id),0); end $$;


--
-- Name: sync_pull_library(integer, integer, integer); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.sync_pull_library(p_profile_id integer, p_limit integer, p_offset integer) RETURNS TABLE(content_id text, content_type text, name text, poster text, poster_shape text, background text, logo text, description text, release_info text, imdb_rating real, genres text[], addon_base_url text, added_at bigint)
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public', 'pg_temp'
    AS $$
begin
 perform public.ka_assert_profile_access(p_profile_id);
 if p_limit < 1 or p_limit > 1000 or p_offset < 0 then raise exception 'invalid pagination' using errcode='22023'; end if;
 return query select i.content_id,i.content_type,i.name,i.poster,i.poster_shape,i.background,i.logo,i.description,i.release_info,i.imdb_rating,i.genres,i.addon_base_url,i.added_at from public.ka_library_items i where i.user_id=auth.uid() and i.profile_index=p_profile_id order by i.added_at desc,i.content_id limit p_limit offset p_offset;
end $$;


--
-- Name: sync_pull_library_delta(integer, bigint, integer); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.sync_pull_library_delta(p_profile_id integer, p_since_event_id bigint, p_limit integer) RETURNS TABLE(event_id bigint, operation text, content_id text, content_type text, name text, poster text, poster_shape text, background text, logo text, description text, release_info text, imdb_rating real, genres text[], addon_base_url text, added_at bigint)
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public', 'pg_temp'
    AS $$
begin
 perform public.ka_assert_profile_access(p_profile_id);
 if p_limit < 1 or p_limit > 1000 then raise exception 'invalid limit' using errcode='22023'; end if;
 return query select e.event_id,e.operation,e.content_id,e.content_type,
  e.payload->>'name',e.payload->>'poster',coalesce(e.payload->>'poster_shape','POSTER'),e.payload->>'background',e.payload->>'logo',e.payload->>'description',e.payload->>'release_info',(e.payload->>'imdb_rating')::real,coalesce(array(select jsonb_array_elements_text(coalesce(e.payload->'genres','[]'::jsonb))), '{}'),e.payload->>'addon_base_url',(e.payload->>'added_at')::bigint
 from public.ka_library_events e where e.user_id=auth.uid() and e.profile_index=p_profile_id and e.event_id>p_since_event_id order by e.event_id limit p_limit;
end $$;


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: ka_profiles; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.ka_profiles (
    id uuid DEFAULT gen_random_uuid() NOT NULL,
    user_id uuid NOT NULL,
    profile_index integer NOT NULL,
    name text NOT NULL,
    avatar_color_hex text DEFAULT '#1E88E5'::text NOT NULL,
    avatar_id text,
    avatar_url text,
    profile_background_id text,
    profile_background_url text,
    uses_primary_addons boolean DEFAULT false NOT NULL,
    uses_primary_plugins boolean DEFAULT false NOT NULL,
    pin_enabled boolean DEFAULT false NOT NULL,
    pin_locked_until timestamp with time zone,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ka_profiles_name_check CHECK (((char_length(btrim(name)) >= 1) AND (char_length(btrim(name)) <= 80))),
    CONSTRAINT ka_profiles_profile_index_check CHECK (((profile_index >= 1) AND (profile_index <= 6)))
);


--
-- Name: sync_pull_profiles(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.sync_pull_profiles() RETURNS SETOF public.ka_profiles
    LANGUAGE sql STABLE
    SET search_path TO 'public'
    AS $$
    select p.*
    from public.ka_profiles p
    where p.user_id = (select auth.uid())
    order by p.profile_index;
$$;


--
-- Name: sync_pull_watch_progress(integer, bigint, integer); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.sync_pull_watch_progress(p_profile_id integer, p_since_last_watched bigint DEFAULT NULL::bigint, p_limit integer DEFAULT NULL::integer) RETURNS TABLE(content_id text, content_type text, video_id text, season integer, episode integer, "position" bigint, duration bigint, last_watched bigint, progress_key text)
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public', 'pg_temp'
    AS $$
begin perform public.ka_assert_profile_access(p_profile_id); if coalesce(p_limit,1000)<1 or coalesce(p_limit,1000)>1000 then raise exception 'invalid limit' using errcode='22023'; end if; return query select w.content_id,w.content_type,w.video_id,nullif(w.season_num,-1),nullif(w.episode_num,-1),w.position,w.duration,w.last_watched,w.progress_key from public.ka_watch_progress w where w.user_id=auth.uid() and w.profile_index=p_profile_id and (p_since_last_watched is null or w.last_watched>p_since_last_watched) order by w.last_watched desc,w.progress_key limit coalesce(p_limit,1000); end $$;


--
-- Name: sync_pull_watch_progress_delta(integer, bigint, integer); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.sync_pull_watch_progress_delta(p_profile_id integer, p_since_event_id bigint, p_limit integer) RETURNS TABLE(event_id bigint, operation text, progress_key text, content_id text, content_type text, video_id text, season integer, episode integer, "position" bigint, duration bigint, last_watched bigint)
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public', 'pg_temp'
    AS $$
begin perform public.ka_assert_profile_access(p_profile_id); if p_limit<1 or p_limit>1000 then raise exception 'invalid limit' using errcode='22023'; end if; return query select e.event_id,e.operation,e.progress_key,e.payload->>'content_id',e.payload->>'content_type',e.payload->>'video_id',nullif((e.payload->>'season_num')::int,-1),nullif((e.payload->>'episode_num')::int,-1),(e.payload->>'position')::bigint,(e.payload->>'duration')::bigint,(e.payload->>'last_watched')::bigint from public.ka_watch_progress_events e where e.user_id=auth.uid() and e.profile_index=p_profile_id and e.event_id>p_since_event_id order by e.event_id limit p_limit; end $$;


--
-- Name: sync_pull_watched_items(integer, integer, integer); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.sync_pull_watched_items(p_profile_id integer, p_page integer, p_page_size integer) RETURNS TABLE(content_id text, content_type text, title text, season integer, episode integer, watched_at bigint)
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public', 'pg_temp'
    AS $$
begin perform public.ka_assert_profile_access(p_profile_id); if p_page<1 or p_page_size<1 or p_page_size>1000 then raise exception 'invalid pagination' using errcode='22023'; end if; return query select w.content_id,w.content_type,w.title,nullif(w.season_num,-1),nullif(w.episode_num,-1),w.watched_at from public.ka_watched_items w where w.user_id=auth.uid() and w.profile_index=p_profile_id order by w.watched_at desc,w.content_id offset (p_page-1)*p_page_size limit p_page_size; end $$;


--
-- Name: sync_pull_watched_items_delta(integer, bigint, integer); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.sync_pull_watched_items_delta(p_profile_id integer, p_since_event_id bigint, p_limit integer) RETURNS TABLE(event_id bigint, operation text, content_id text, content_type text, title text, season integer, episode integer, watched_at bigint)
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public', 'pg_temp'
    AS $$
begin perform public.ka_assert_profile_access(p_profile_id); if p_limit<1 or p_limit>1000 then raise exception 'invalid limit' using errcode='22023'; end if; return query select e.event_id,e.operation,e.content_id,e.content_type,e.payload->>'title',nullif((e.payload->>'season_num')::int,-1),nullif((e.payload->>'episode_num')::int,-1),(e.payload->>'watched_at')::bigint from public.ka_watched_events e where e.user_id=auth.uid() and e.profile_index=p_profile_id and e.event_id>p_since_event_id order by e.event_id limit p_limit; end $$;


--
-- Name: sync_push_library_items(integer, jsonb, text); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.sync_push_library_items(p_profile_id integer, p_items jsonb, p_origin_client_id text DEFAULT NULL::text) RETURNS void
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public', 'pg_temp'
    AS $$
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


--
-- Name: sync_push_profiles(integer, jsonb, text); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.sync_push_profiles(p_client_max_profiles integer, p_profiles jsonb, p_origin_client_id text DEFAULT NULL::text) RETURNS void
    LANGUAGE plpgsql
    SET search_path TO 'public'
    AS $$
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


--
-- Name: sync_push_watch_progress(integer, jsonb, text); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.sync_push_watch_progress(p_profile_id integer, p_entries jsonb, p_origin_client_id text DEFAULT NULL::text) RETURNS void
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public', 'pg_temp'
    AS $$
declare x record; changed boolean; payload jsonb;
begin perform public.ka_assert_profile_access(p_profile_id); if jsonb_typeof(p_entries)<>'array' or jsonb_array_length(p_entries)>1000 then raise exception 'invalid entries' using errcode='22023'; end if;
 for x in select * from jsonb_to_recordset(p_entries) as r(content_id text,content_type text,video_id text,season int,episode int,"position" bigint,duration bigint,last_watched bigint,progress_key text) loop
  if nullif(x.progress_key,'') is null or nullif(x.video_id,'') is null then raise exception 'progress_key and video_id required' using errcode='22023'; end if;
  insert into public.ka_watch_progress(user_id,profile_index,progress_key,content_id,content_type,video_id,season_num,episode_num,position,duration,last_watched) values(auth.uid(),p_profile_id,x.progress_key,x.content_id,x.content_type,x.video_id,coalesce(x.season,-1),coalesce(x.episode,-1),greatest(coalesce(x.position,0),0),greatest(coalesce(x.duration,0),0),coalesce(x.last_watched,0)) on conflict(user_id,profile_index,progress_key) do update set content_id=excluded.content_id,content_type=excluded.content_type,video_id=excluded.video_id,season_num=excluded.season_num,episode_num=excluded.episode_num,position=excluded.position,duration=excluded.duration,last_watched=excluded.last_watched,updated_at=now() where excluded.last_watched>=public.ka_watch_progress.last_watched and (public.ka_watch_progress.content_id,public.ka_watch_progress.content_type,public.ka_watch_progress.video_id,public.ka_watch_progress.season_num,public.ka_watch_progress.episode_num,public.ka_watch_progress.position,public.ka_watch_progress.duration,public.ka_watch_progress.last_watched) is distinct from (excluded.content_id,excluded.content_type,excluded.video_id,excluded.season_num,excluded.episode_num,excluded.position,excluded.duration,excluded.last_watched) returning true into changed;
  if changed then select to_jsonb(w) into payload from public.ka_watch_progress w where w.user_id=auth.uid() and w.profile_index=p_profile_id and w.progress_key=x.progress_key; insert into public.ka_watch_progress_events(user_id,profile_index,operation,progress_key,payload,origin_client_id) values(auth.uid(),p_profile_id,'upsert',x.progress_key,payload,p_origin_client_id); end if; changed:=false;
 end loop;
end $$;


--
-- Name: sync_push_watched_items(integer, jsonb, text); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.sync_push_watched_items(p_profile_id integer, p_items jsonb, p_origin_client_id text DEFAULT NULL::text) RETURNS void
    LANGUAGE plpgsql SECURITY DEFINER
    SET search_path TO 'public', 'pg_temp'
    AS $$
declare x record; changed boolean; payload jsonb;
begin perform public.ka_assert_profile_access(p_profile_id); if jsonb_typeof(p_items)<>'array' or jsonb_array_length(p_items)>1000 then raise exception 'invalid items' using errcode='22023'; end if;
 for x in select * from jsonb_to_recordset(p_items) as r(content_id text,content_type text,title text,season int,episode int,watched_at bigint) loop
  insert into public.ka_watched_items(user_id,profile_index,content_id,content_type,title,season_num,episode_num,watched_at) values(auth.uid(),p_profile_id,x.content_id,x.content_type,coalesce(x.title,''),coalesce(x.season,-1),coalesce(x.episode,-1),coalesce(x.watched_at,0)) on conflict(user_id,profile_index,content_id,content_type,season_num,episode_num) do update set title=excluded.title,watched_at=excluded.watched_at,updated_at=now() where excluded.watched_at>=public.ka_watched_items.watched_at and (public.ka_watched_items.title,public.ka_watched_items.watched_at) is distinct from (excluded.title,excluded.watched_at) returning true into changed;
  if changed then select to_jsonb(w) into payload from public.ka_watched_items w where w.user_id=auth.uid() and w.profile_index=p_profile_id and w.content_id=x.content_id and w.content_type=x.content_type and w.season_num=coalesce(x.season,-1) and w.episode_num=coalesce(x.episode,-1); insert into public.ka_watched_events(user_id,profile_index,operation,content_id,content_type,payload,origin_client_id) values(auth.uid(),p_profile_id,'upsert',x.content_id,x.content_type,payload,p_origin_client_id); end if; changed:=false;
 end loop;
end $$;


--
-- Name: audit_log_entries; Type: TABLE; Schema: auth; Owner: -
--

CREATE TABLE auth.audit_log_entries (
    instance_id uuid,
    id uuid NOT NULL,
    payload json,
    created_at timestamp with time zone,
    ip_address character varying(64) DEFAULT ''::character varying NOT NULL
);


--
-- Name: TABLE audit_log_entries; Type: COMMENT; Schema: auth; Owner: -
--

COMMENT ON TABLE auth.audit_log_entries IS 'Auth: Audit trail for user actions.';


--
-- Name: flow_state; Type: TABLE; Schema: auth; Owner: -
--

CREATE TABLE auth.flow_state (
    id uuid NOT NULL,
    user_id uuid,
    auth_code text NOT NULL,
    code_challenge_method public.code_challenge_method NOT NULL,
    code_challenge text NOT NULL,
    provider_type text NOT NULL,
    provider_access_token text,
    provider_refresh_token text,
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    authentication_method text NOT NULL,
    auth_code_issued_at timestamp with time zone
);


--
-- Name: TABLE flow_state; Type: COMMENT; Schema: auth; Owner: -
--

COMMENT ON TABLE auth.flow_state IS 'stores metadata for pkce logins';


--
-- Name: identities; Type: TABLE; Schema: auth; Owner: -
--

CREATE TABLE auth.identities (
    provider_id text NOT NULL,
    user_id uuid NOT NULL,
    identity_data jsonb NOT NULL,
    provider text NOT NULL,
    last_sign_in_at timestamp with time zone,
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    email text GENERATED ALWAYS AS (lower((identity_data ->> 'email'::text))) STORED,
    id uuid DEFAULT gen_random_uuid() NOT NULL
);


--
-- Name: TABLE identities; Type: COMMENT; Schema: auth; Owner: -
--

COMMENT ON TABLE auth.identities IS 'Auth: Stores identities associated to a user.';


--
-- Name: COLUMN identities.email; Type: COMMENT; Schema: auth; Owner: -
--

COMMENT ON COLUMN auth.identities.email IS 'Auth: Email is a generated column that references the optional email property in the identity_data';


--
-- Name: instances; Type: TABLE; Schema: auth; Owner: -
--

CREATE TABLE auth.instances (
    id uuid NOT NULL,
    uuid uuid,
    raw_base_config text,
    created_at timestamp with time zone,
    updated_at timestamp with time zone
);


--
-- Name: TABLE instances; Type: COMMENT; Schema: auth; Owner: -
--

COMMENT ON TABLE auth.instances IS 'Auth: Manages users across multiple sites.';


--
-- Name: mfa_amr_claims; Type: TABLE; Schema: auth; Owner: -
--

CREATE TABLE auth.mfa_amr_claims (
    session_id uuid NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    authentication_method text NOT NULL,
    id uuid NOT NULL
);


--
-- Name: TABLE mfa_amr_claims; Type: COMMENT; Schema: auth; Owner: -
--

COMMENT ON TABLE auth.mfa_amr_claims IS 'auth: stores authenticator method reference claims for multi factor authentication';


--
-- Name: mfa_challenges; Type: TABLE; Schema: auth; Owner: -
--

CREATE TABLE auth.mfa_challenges (
    id uuid NOT NULL,
    factor_id uuid NOT NULL,
    created_at timestamp with time zone NOT NULL,
    verified_at timestamp with time zone,
    ip_address inet NOT NULL,
    otp_code text,
    web_authn_session_data jsonb
);


--
-- Name: TABLE mfa_challenges; Type: COMMENT; Schema: auth; Owner: -
--

COMMENT ON TABLE auth.mfa_challenges IS 'auth: stores metadata about challenge requests made';


--
-- Name: mfa_factors; Type: TABLE; Schema: auth; Owner: -
--

CREATE TABLE auth.mfa_factors (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    friendly_name text,
    factor_type public.factor_type NOT NULL,
    status public.factor_status NOT NULL,
    created_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone NOT NULL,
    secret text,
    phone text,
    last_challenged_at timestamp with time zone,
    web_authn_credential jsonb,
    web_authn_aaguid uuid
);


--
-- Name: TABLE mfa_factors; Type: COMMENT; Schema: auth; Owner: -
--

COMMENT ON TABLE auth.mfa_factors IS 'auth: stores metadata about factors';


--
-- Name: one_time_tokens; Type: TABLE; Schema: auth; Owner: -
--

CREATE TABLE auth.one_time_tokens (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    token_type public.one_time_token_type NOT NULL,
    token_hash text NOT NULL,
    relates_to text NOT NULL,
    created_at timestamp without time zone DEFAULT now() NOT NULL,
    updated_at timestamp without time zone DEFAULT now() NOT NULL,
    CONSTRAINT one_time_tokens_token_hash_check CHECK ((char_length(token_hash) > 0))
);


--
-- Name: refresh_tokens; Type: TABLE; Schema: auth; Owner: -
--

CREATE TABLE auth.refresh_tokens (
    instance_id uuid,
    id bigint NOT NULL,
    token character varying(255),
    user_id character varying(255),
    revoked boolean,
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    parent character varying(255),
    session_id uuid
);


--
-- Name: TABLE refresh_tokens; Type: COMMENT; Schema: auth; Owner: -
--

COMMENT ON TABLE auth.refresh_tokens IS 'Auth: Store of tokens used to refresh JWT tokens once they expire.';


--
-- Name: refresh_tokens_id_seq; Type: SEQUENCE; Schema: auth; Owner: -
--

CREATE SEQUENCE auth.refresh_tokens_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- Name: refresh_tokens_id_seq; Type: SEQUENCE OWNED BY; Schema: auth; Owner: -
--

ALTER SEQUENCE auth.refresh_tokens_id_seq OWNED BY auth.refresh_tokens.id;


--
-- Name: saml_providers; Type: TABLE; Schema: auth; Owner: -
--

CREATE TABLE auth.saml_providers (
    id uuid NOT NULL,
    sso_provider_id uuid NOT NULL,
    entity_id text NOT NULL,
    metadata_xml text NOT NULL,
    metadata_url text,
    attribute_mapping jsonb,
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    name_id_format text,
    CONSTRAINT "entity_id not empty" CHECK ((char_length(entity_id) > 0)),
    CONSTRAINT "metadata_url not empty" CHECK (((metadata_url = NULL::text) OR (char_length(metadata_url) > 0))),
    CONSTRAINT "metadata_xml not empty" CHECK ((char_length(metadata_xml) > 0))
);


--
-- Name: TABLE saml_providers; Type: COMMENT; Schema: auth; Owner: -
--

COMMENT ON TABLE auth.saml_providers IS 'Auth: Manages SAML Identity Provider connections.';


--
-- Name: saml_relay_states; Type: TABLE; Schema: auth; Owner: -
--

CREATE TABLE auth.saml_relay_states (
    id uuid NOT NULL,
    sso_provider_id uuid NOT NULL,
    request_id text NOT NULL,
    for_email text,
    redirect_to text,
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    flow_state_id uuid,
    CONSTRAINT "request_id not empty" CHECK ((char_length(request_id) > 0))
);


--
-- Name: TABLE saml_relay_states; Type: COMMENT; Schema: auth; Owner: -
--

COMMENT ON TABLE auth.saml_relay_states IS 'Auth: Contains SAML Relay State information for each Service Provider initiated login.';


--
-- Name: schema_migrations; Type: TABLE; Schema: auth; Owner: -
--

CREATE TABLE auth.schema_migrations (
    version character varying(255) NOT NULL
);


--
-- Name: TABLE schema_migrations; Type: COMMENT; Schema: auth; Owner: -
--

COMMENT ON TABLE auth.schema_migrations IS 'Auth: Manages updates to the auth system.';


--
-- Name: sessions; Type: TABLE; Schema: auth; Owner: -
--

CREATE TABLE auth.sessions (
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    factor_id uuid,
    aal public.aal_level,
    not_after timestamp with time zone,
    refreshed_at timestamp without time zone,
    user_agent text,
    ip inet,
    tag text
);


--
-- Name: TABLE sessions; Type: COMMENT; Schema: auth; Owner: -
--

COMMENT ON TABLE auth.sessions IS 'Auth: Stores session data associated to a user.';


--
-- Name: COLUMN sessions.not_after; Type: COMMENT; Schema: auth; Owner: -
--

COMMENT ON COLUMN auth.sessions.not_after IS 'Auth: Not after is a nullable column that contains a timestamp after which the session should be regarded as expired.';


--
-- Name: sso_domains; Type: TABLE; Schema: auth; Owner: -
--

CREATE TABLE auth.sso_domains (
    id uuid NOT NULL,
    sso_provider_id uuid NOT NULL,
    domain text NOT NULL,
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    CONSTRAINT "domain not empty" CHECK ((char_length(domain) > 0))
);


--
-- Name: TABLE sso_domains; Type: COMMENT; Schema: auth; Owner: -
--

COMMENT ON TABLE auth.sso_domains IS 'Auth: Manages SSO email address domain mapping to an SSO Identity Provider.';


--
-- Name: sso_providers; Type: TABLE; Schema: auth; Owner: -
--

CREATE TABLE auth.sso_providers (
    id uuid NOT NULL,
    resource_id text,
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    CONSTRAINT "resource_id not empty" CHECK (((resource_id = NULL::text) OR (char_length(resource_id) > 0)))
);


--
-- Name: TABLE sso_providers; Type: COMMENT; Schema: auth; Owner: -
--

COMMENT ON TABLE auth.sso_providers IS 'Auth: Manages SSO identity provider information; see saml_providers for SAML.';


--
-- Name: COLUMN sso_providers.resource_id; Type: COMMENT; Schema: auth; Owner: -
--

COMMENT ON COLUMN auth.sso_providers.resource_id IS 'Auth: Uniquely identifies a SSO provider according to a user-chosen resource ID (case insensitive), useful in infrastructure as code.';


--
-- Name: users; Type: TABLE; Schema: auth; Owner: -
--

CREATE TABLE auth.users (
    instance_id uuid,
    id uuid NOT NULL,
    aud character varying(255),
    role character varying(255),
    email character varying(255),
    encrypted_password character varying(255),
    email_confirmed_at timestamp with time zone,
    invited_at timestamp with time zone,
    confirmation_token character varying(255),
    confirmation_sent_at timestamp with time zone,
    recovery_token character varying(255),
    recovery_sent_at timestamp with time zone,
    email_change_token_new character varying(255),
    email_change character varying(255),
    email_change_sent_at timestamp with time zone,
    last_sign_in_at timestamp with time zone,
    raw_app_meta_data jsonb,
    raw_user_meta_data jsonb,
    is_super_admin boolean,
    created_at timestamp with time zone,
    updated_at timestamp with time zone,
    phone text DEFAULT NULL::character varying,
    phone_confirmed_at timestamp with time zone,
    phone_change text DEFAULT ''::character varying,
    phone_change_token character varying(255) DEFAULT ''::character varying,
    phone_change_sent_at timestamp with time zone,
    confirmed_at timestamp with time zone GENERATED ALWAYS AS (LEAST(email_confirmed_at, phone_confirmed_at)) STORED,
    email_change_token_current character varying(255) DEFAULT ''::character varying,
    email_change_confirm_status smallint DEFAULT 0,
    banned_until timestamp with time zone,
    reauthentication_token character varying(255) DEFAULT ''::character varying,
    reauthentication_sent_at timestamp with time zone,
    is_sso_user boolean DEFAULT false NOT NULL,
    deleted_at timestamp with time zone,
    is_anonymous boolean DEFAULT false NOT NULL,
    CONSTRAINT users_email_change_confirm_status_check CHECK (((email_change_confirm_status >= 0) AND (email_change_confirm_status <= 2)))
);


--
-- Name: TABLE users; Type: COMMENT; Schema: auth; Owner: -
--

COMMENT ON TABLE auth.users IS 'Auth: Stores user login data within a secure schema.';


--
-- Name: COLUMN users.is_sso_user; Type: COMMENT; Schema: auth; Owner: -
--

COMMENT ON COLUMN auth.users.is_sso_user IS 'Auth: Set this column to true when the account comes from SSO. These accounts can have duplicate emails.';


--
-- Name: ka_ad_reward_balances; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.ka_ad_reward_balances (
    user_id uuid NOT NULL,
    profile_index integer NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ka_ad_reward_profile_index_check CHECK (((profile_index >= 1) AND (profile_index <= 10)))
);


--
-- Name: ka_ad_reward_claims; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.ka_ad_reward_claims (
    event_id uuid NOT NULL,
    user_id uuid NOT NULL,
    profile_index integer NOT NULL,
    claimed_at timestamp with time zone DEFAULT now() NOT NULL
);


--
-- Name: ka_library_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.ka_library_events (
    event_id bigint NOT NULL,
    user_id uuid NOT NULL,
    profile_index integer NOT NULL,
    operation text NOT NULL,
    content_id text NOT NULL,
    content_type text NOT NULL,
    payload jsonb NOT NULL,
    origin_client_id text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ka_library_events_operation_check CHECK ((operation = ANY (ARRAY['upsert'::text, 'delete'::text]))),
    CONSTRAINT ka_library_events_profile_index_check CHECK (((profile_index >= 1) AND (profile_index <= 6)))
);


--
-- Name: ka_library_events_event_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.ka_library_events ALTER COLUMN event_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.ka_library_events_event_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: ka_library_items; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.ka_library_items (
    user_id uuid NOT NULL,
    profile_index integer NOT NULL,
    content_id text NOT NULL,
    content_type text NOT NULL,
    name text DEFAULT ''::text NOT NULL,
    poster text,
    poster_shape text DEFAULT 'POSTER'::text NOT NULL,
    background text,
    logo text,
    description text,
    release_info text,
    imdb_rating real,
    genres text[] DEFAULT '{}'::text[] NOT NULL,
    addon_base_url text,
    added_at bigint DEFAULT 0 NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ka_library_items_profile_index_check CHECK (((profile_index >= 1) AND (profile_index <= 6)))
);


--
-- Name: ka_watch_progress; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.ka_watch_progress (
    user_id uuid NOT NULL,
    profile_index integer NOT NULL,
    progress_key text NOT NULL,
    content_id text NOT NULL,
    content_type text NOT NULL,
    video_id text NOT NULL,
    season_num integer DEFAULT '-1'::integer NOT NULL,
    episode_num integer DEFAULT '-1'::integer NOT NULL,
    "position" bigint DEFAULT 0 NOT NULL,
    duration bigint DEFAULT 0 NOT NULL,
    last_watched bigint DEFAULT 0 NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ka_watch_progress_profile_index_check CHECK (((profile_index >= 1) AND (profile_index <= 6)))
);


--
-- Name: ka_watch_progress_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.ka_watch_progress_events (
    event_id bigint NOT NULL,
    user_id uuid NOT NULL,
    profile_index integer NOT NULL,
    operation text NOT NULL,
    progress_key text NOT NULL,
    payload jsonb NOT NULL,
    origin_client_id text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ka_watch_progress_events_operation_check CHECK ((operation = ANY (ARRAY['upsert'::text, 'delete'::text]))),
    CONSTRAINT ka_watch_progress_events_profile_index_check CHECK (((profile_index >= 1) AND (profile_index <= 6)))
);


--
-- Name: ka_watch_progress_events_event_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.ka_watch_progress_events ALTER COLUMN event_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.ka_watch_progress_events_event_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: ka_watched_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.ka_watched_events (
    event_id bigint NOT NULL,
    user_id uuid NOT NULL,
    profile_index integer NOT NULL,
    operation text NOT NULL,
    content_id text NOT NULL,
    content_type text NOT NULL,
    payload jsonb NOT NULL,
    origin_client_id text,
    created_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ka_watched_events_operation_check CHECK ((operation = ANY (ARRAY['upsert'::text, 'delete'::text]))),
    CONSTRAINT ka_watched_events_profile_index_check CHECK (((profile_index >= 1) AND (profile_index <= 6)))
);


--
-- Name: ka_watched_events_event_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

ALTER TABLE public.ka_watched_events ALTER COLUMN event_id ADD GENERATED ALWAYS AS IDENTITY (
    SEQUENCE NAME public.ka_watched_events_event_id_seq
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1
);


--
-- Name: ka_watched_items; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.ka_watched_items (
    user_id uuid NOT NULL,
    profile_index integer NOT NULL,
    content_id text NOT NULL,
    content_type text NOT NULL,
    title text DEFAULT ''::text NOT NULL,
    season_num integer DEFAULT '-1'::integer NOT NULL,
    episode_num integer DEFAULT '-1'::integer NOT NULL,
    watched_at bigint DEFAULT 0 NOT NULL,
    updated_at timestamp with time zone DEFAULT now() NOT NULL,
    CONSTRAINT ka_watched_items_profile_index_check CHECK (((profile_index >= 1) AND (profile_index <= 6)))
);


--
-- Name: schema_migrations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.schema_migrations (
    version character varying(14) NOT NULL
);


--
-- Name: user_favorites; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.user_favorites (
    user_id uuid NOT NULL,
    media_id text NOT NULL,
    title text NOT NULL,
    poster_url text,
    media_type text,
    status integer DEFAULT 0,
    updated_at timestamp with time zone DEFAULT now(),
    profile_index integer DEFAULT 1 NOT NULL,
    CONSTRAINT user_favorites_profile_index_check CHECK (((profile_index >= 1) AND (profile_index <= 6)))
);


--
-- Name: watch_history; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.watch_history (
    user_id uuid NOT NULL,
    media_id text NOT NULL,
    title text NOT NULL,
    episode_num integer NOT NULL,
    season_num integer NOT NULL,
    position_sec bigint DEFAULT 0 NOT NULL,
    duration_sec bigint DEFAULT 0 NOT NULL,
    poster_url text,
    updated_at timestamp with time zone DEFAULT now(),
    profile_index integer DEFAULT 1 NOT NULL,
    CONSTRAINT watch_history_profile_index_check CHECK (((profile_index >= 1) AND (profile_index <= 6)))
);


--
-- Name: refresh_tokens id; Type: DEFAULT; Schema: auth; Owner: -
--

ALTER TABLE ONLY auth.refresh_tokens ALTER COLUMN id SET DEFAULT nextval('auth.refresh_tokens_id_seq'::regclass);


--
-- Data for Name: audit_log_entries; Type: TABLE DATA; Schema: auth; Owner: -
--

COPY auth.audit_log_entries (instance_id, id, payload, created_at, ip_address) FROM stdin;
00000000-0000-0000-0000-000000000000	da26ccd4-749e-4205-bdbf-d4b57cce6305	{"action":"user_signedup","actor_id":"fe96fc0c-ff30-47f2-b581-89a724c2f3dd","actor_username":"a1@example.local","actor_via_sso":false,"log_type":"team","traits":{"provider":"email"}}	2026-10-30 16:09:41.563566+00	
00000000-0000-0000-0000-000000000000	683255e4-5588-4045-bc45-5313c0a35ed5	{"action":"login","actor_id":"fe96fc0c-ff30-47f2-b581-89a724c2f3dd","actor_username":"a1@example.local","actor_via_sso":false,"log_type":"account","traits":{"provider":"email"}}	2026-10-30 16:09:41.567227+00	
00000000-0000-0000-0000-000000000000	cd3f9911-c505-4faa-a89f-96aef51a8c62	{"action":"user_signedup","actor_id":"e8778a6c-9b49-487b-9ec5-d75657699323","actor_username":"b1@example.local","actor_via_sso":false,"log_type":"team","traits":{"provider":"email"}}	2026-10-30 16:10:00.864131+00	
00000000-0000-0000-0000-000000000000	39b658e8-5d98-4670-8624-03a0c4093706	{"action":"login","actor_id":"e8778a6c-9b49-487b-9ec5-d75657699323","actor_username":"b1@example.local","actor_via_sso":false,"log_type":"account","traits":{"provider":"email"}}	2026-10-30 16:10:00.866548+00	
00000000-0000-0000-0000-000000000000	79485882-406a-4875-a91d-3001a7a6e60a	{"action":"login","actor_id":"fe96fc0c-ff30-47f2-b581-89a724c2f3dd","actor_username":"a1@example.local","actor_via_sso":false,"log_type":"account","traits":{"provider":"email"}}	2026-10-30 16:10:11.634658+00	
00000000-0000-0000-0000-000000000000	fa3ffe7d-9dcd-4fe1-9ae6-e43c267925f5	{"action":"token_refreshed","actor_id":"fe96fc0c-ff30-47f2-b581-89a724c2f3dd","actor_username":"a1@example.local","actor_via_sso":false,"log_type":"token"}	2026-10-30 16:10:20.03985+00	
00000000-0000-0000-0000-000000000000	eeedc0d2-13cc-4fa7-a384-81444b0b3996	{"action":"token_revoked","actor_id":"fe96fc0c-ff30-47f2-b581-89a724c2f3dd","actor_username":"a1@example.local","actor_via_sso":false,"log_type":"token"}	2026-10-30 16:10:20.040334+00	
00000000-0000-0000-0000-000000000000	8a92f4ba-d2b5-4537-aba4-b65d65d87368	{"action":"login","actor_id":"fe96fc0c-ff30-47f2-b581-89a724c2f3dd","actor_username":"a1@example.local","actor_via_sso":false,"log_type":"account","traits":{"provider":"email"}}	2026-10-30 16:11:05.206299+00	
00000000-0000-0000-0000-000000000000	afcea120-5036-4fb6-bc56-3ef9cbb4b6e9	{"action":"login","actor_id":"fe96fc0c-ff30-47f2-b581-89a724c2f3dd","actor_username":"a1@example.local","actor_via_sso":false,"log_type":"account","traits":{"provider":"email"}}	2026-10-30 16:11:11.056095+00	
00000000-0000-0000-0000-000000000000	15014ea1-e8ca-4076-b6ea-b5764822071a	{"action":"login","actor_id":"e8778a6c-9b49-487b-9ec5-d75657699323","actor_username":"b1@example.local","actor_via_sso":false,"log_type":"account","traits":{"provider":"email"}}	2026-10-30 16:11:11.14531+00	
00000000-0000-0000-0000-000000000000	871e3618-a2a9-4676-9203-cc5d1864b192	{"action":"token_refreshed","actor_id":"fe96fc0c-ff30-47f2-b581-89a724c2f3dd","actor_username":"a1@example.local","actor_via_sso":false,"log_type":"token"}	2026-10-30 16:11:18.168206+00	
00000000-0000-0000-0000-000000000000	c78a16c8-8c90-42df-baba-b27828f58661	{"action":"token_revoked","actor_id":"fe96fc0c-ff30-47f2-b581-89a724c2f3dd","actor_username":"a1@example.local","actor_via_sso":false,"log_type":"token"}	2026-10-30 16:11:18.168697+00	
00000000-0000-0000-0000-000000000000	a25a951f-1734-4acd-ad0b-ca3faa66ee41	{"action":"login","actor_id":"fe96fc0c-ff30-47f2-b581-89a724c2f3dd","actor_username":"a1@example.local","actor_via_sso":false,"log_type":"account","traits":{"provider":"email"}}	2026-10-30 18:33:28.20055+00	
00000000-0000-0000-0000-000000000000	386f8c4c-a290-47b1-94d7-8233f6ffd09a	{"action":"login","actor_id":"fe96fc0c-ff30-47f2-b581-89a724c2f3dd","actor_username":"a1@example.local","actor_via_sso":false,"log_type":"account","traits":{"provider":"email"}}	2026-10-30 19:04:47.847972+00	
\.


--
-- Data for Name: flow_state; Type: TABLE DATA; Schema: auth; Owner: -
--

COPY auth.flow_state (id, user_id, auth_code, code_challenge_method, code_challenge, provider_type, provider_access_token, provider_refresh_token, created_at, updated_at, authentication_method, auth_code_issued_at) FROM stdin;
\.


--
-- Data for Name: identities; Type: TABLE DATA; Schema: auth; Owner: -
--

COPY auth.identities (provider_id, user_id, identity_data, provider, last_sign_in_at, created_at, updated_at, id) FROM stdin;
fe96fc0c-ff30-47f2-b581-89a724c2f3dd	fe96fc0c-ff30-47f2-b581-89a724c2f3dd	{"sub": "fe96fc0c-ff30-47f2-b581-89a724c2f3dd", "email": "a1@example.local", "email_verified": false, "phone_verified": false}	email	2026-10-30 16:09:41.560917+00	2026-10-30 16:09:41.560949+00	2026-10-30 16:09:41.560949+00	eafb7c8f-65bb-45e0-98ba-59fe14e22559
e8778a6c-9b49-487b-9ec5-d75657699323	e8778a6c-9b49-487b-9ec5-d75657699323	{"sub": "e8778a6c-9b49-487b-9ec5-d75657699323", "email": "b1@example.local", "email_verified": false, "phone_verified": false}	email	2026-10-30 16:10:00.861588+00	2026-10-30 16:10:00.861639+00	2026-10-30 16:10:00.861639+00	df872a3a-2315-4183-b83b-f38bafa15ed3
\.


--
-- Data for Name: instances; Type: TABLE DATA; Schema: auth; Owner: -
--

COPY auth.instances (id, uuid, raw_base_config, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: mfa_amr_claims; Type: TABLE DATA; Schema: auth; Owner: -
--

COPY auth.mfa_amr_claims (session_id, created_at, updated_at, authentication_method, id) FROM stdin;
0940c7f8-14a9-463f-85a9-29b1c2993133	2026-10-30 16:09:41.572371+00	2026-10-30 16:09:41.572371+00	password	16cd4624-94d9-45bf-b179-0f0ce7ec58b4
5126526b-ca98-4268-a0c2-7a3165b9750d	2026-10-30 16:10:00.869059+00	2026-10-30 16:10:00.869059+00	password	106096fc-14c6-4814-991b-c4d7ec4837a9
b2436019-1b61-4a2f-9ffa-df7fe75edffc	2026-10-30 16:10:11.637173+00	2026-10-30 16:10:11.637173+00	password	67be2062-e0a4-4afa-9abe-6b6f5b090344
95678c29-817f-435a-a4bf-3cc322ee7478	2026-10-30 16:11:05.212726+00	2026-10-30 16:11:05.212726+00	password	0b2ed8a2-3a55-43a0-891b-6c7de42c6ca5
3362397c-a833-41f4-adfd-07691061141c	2026-10-30 16:11:11.060262+00	2026-10-30 16:11:11.060262+00	password	234b9cac-9d13-466d-8fa4-4d847bd6a03d
c19a9447-f48d-4f9c-ab81-858696795f4b	2026-10-30 16:11:11.148638+00	2026-10-30 16:11:11.148638+00	password	5366718d-f3af-441c-b144-f5181f6765f0
4e3cf1a8-85be-4c11-947d-314f8f8df2bf	2026-10-30 18:33:28.206888+00	2026-10-30 18:33:28.206888+00	password	b6729fec-999a-4aaf-b1f4-fc50b88532b3
95f2f3ee-9f03-4aa4-8dc3-96b6bf4ffc44	2026-10-30 19:04:47.874027+00	2026-10-30 19:04:47.874027+00	password	f9422d31-71d5-4f73-aa16-74fa93e0c2dd
\.


--
-- Data for Name: mfa_challenges; Type: TABLE DATA; Schema: auth; Owner: -
--

COPY auth.mfa_challenges (id, factor_id, created_at, verified_at, ip_address, otp_code, web_authn_session_data) FROM stdin;
\.


--
-- Data for Name: mfa_factors; Type: TABLE DATA; Schema: auth; Owner: -
--

COPY auth.mfa_factors (id, user_id, friendly_name, factor_type, status, created_at, updated_at, secret, phone, last_challenged_at, web_authn_credential, web_authn_aaguid) FROM stdin;
\.


--
-- Data for Name: one_time_tokens; Type: TABLE DATA; Schema: auth; Owner: -
--

COPY auth.one_time_tokens (id, user_id, token_type, token_hash, relates_to, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: refresh_tokens; Type: TABLE DATA; Schema: auth; Owner: -
--

COPY auth.refresh_tokens (instance_id, id, token, user_id, revoked, created_at, updated_at, parent, session_id) FROM stdin;
00000000-0000-0000-0000-000000000000	1	XO1cThUwUzsEhIWlo9aYOA	fe96fc0c-ff30-47f2-b581-89a724c2f3dd	f	2026-10-30 16:09:41.569861+00	2026-10-30 16:09:41.569861+00	\N	0940c7f8-14a9-463f-85a9-29b1c2993133
00000000-0000-0000-0000-000000000000	2	XuhBgmAxNC-lM4MGviQ2zA	e8778a6c-9b49-487b-9ec5-d75657699323	f	2026-10-30 16:10:00.867851+00	2026-10-30 16:10:00.867851+00	\N	5126526b-ca98-4268-a0c2-7a3165b9750d
00000000-0000-0000-0000-000000000000	3	BrpOvMQeSzMjo0cQrDm5KQ	fe96fc0c-ff30-47f2-b581-89a724c2f3dd	t	2026-10-30 16:10:11.63609+00	2026-10-30 16:10:20.040688+00	\N	b2436019-1b61-4a2f-9ffa-df7fe75edffc
00000000-0000-0000-0000-000000000000	4	jBIAlUYtmU2jynGoeLH88w	fe96fc0c-ff30-47f2-b581-89a724c2f3dd	f	2026-10-30 16:10:20.041331+00	2026-10-30 16:10:20.041331+00	BrpOvMQeSzMjo0cQrDm5KQ	b2436019-1b61-4a2f-9ffa-df7fe75edffc
00000000-0000-0000-0000-000000000000	5	Y1BqPjp0Nfa6TJZeQ5UCXQ	fe96fc0c-ff30-47f2-b581-89a724c2f3dd	f	2026-10-30 16:11:05.209144+00	2026-10-30 16:11:05.209144+00	\N	95678c29-817f-435a-a4bf-3cc322ee7478
00000000-0000-0000-0000-000000000000	7	DSAGPdw8fba4r3lMEfU7Uw	e8778a6c-9b49-487b-9ec5-d75657699323	f	2026-10-30 16:11:11.147253+00	2026-10-30 16:11:11.147253+00	\N	c19a9447-f48d-4f9c-ab81-858696795f4b
00000000-0000-0000-0000-000000000000	6	FEqbf-hCYSpYdFDbmzQSXw	fe96fc0c-ff30-47f2-b581-89a724c2f3dd	t	2026-10-30 16:11:11.059064+00	2026-10-30 16:11:18.169099+00	\N	3362397c-a833-41f4-adfd-07691061141c
00000000-0000-0000-0000-000000000000	8	wCXVwYo9t5NzNjlADGW6Yg	fe96fc0c-ff30-47f2-b581-89a724c2f3dd	f	2026-10-30 16:11:18.169576+00	2026-10-30 16:11:18.169576+00	FEqbf-hCYSpYdFDbmzQSXw	3362397c-a833-41f4-adfd-07691061141c
00000000-0000-0000-0000-000000000000	38	P90MUo76EjjV4344EGWMFw	fe96fc0c-ff30-47f2-b581-89a724c2f3dd	f	2026-10-30 18:33:28.203615+00	2026-10-30 18:33:28.203615+00	\N	4e3cf1a8-85be-4c11-947d-314f8f8df2bf
00000000-0000-0000-0000-000000000000	39	L_xHyvj6spPmNRHA5UWx5w	fe96fc0c-ff30-47f2-b581-89a724c2f3dd	f	2026-10-30 19:04:47.863163+00	2026-10-30 19:04:47.863163+00	\N	95f2f3ee-9f03-4aa4-8dc3-96b6bf4ffc44
\.


--
-- Data for Name: saml_providers; Type: TABLE DATA; Schema: auth; Owner: -
--

COPY auth.saml_providers (id, sso_provider_id, entity_id, metadata_xml, metadata_url, attribute_mapping, created_at, updated_at, name_id_format) FROM stdin;
\.


--
-- Data for Name: saml_relay_states; Type: TABLE DATA; Schema: auth; Owner: -
--

COPY auth.saml_relay_states (id, sso_provider_id, request_id, for_email, redirect_to, created_at, updated_at, flow_state_id) FROM stdin;
\.


--
-- Data for Name: schema_migrations; Type: TABLE DATA; Schema: auth; Owner: -
--

COPY auth.schema_migrations (version) FROM stdin;
\.


--
-- Data for Name: sessions; Type: TABLE DATA; Schema: auth; Owner: -
--

COPY auth.sessions (id, user_id, created_at, updated_at, factor_id, aal, not_after, refreshed_at, user_agent, ip, tag) FROM stdin;
0940c7f8-14a9-463f-85a9-29b1c2993133	fe96fc0c-ff30-47f2-b581-89a724c2f3dd	2026-10-30 16:09:41.567787+00	2026-10-30 16:09:41.567787+00	\N	aal1	\N	\N	curl/8.18.0	172.19.0.1	\N
5126526b-ca98-4268-a0c2-7a3165b9750d	e8778a6c-9b49-487b-9ec5-d75657699323	2026-10-30 16:10:00.867152+00	2026-10-30 16:10:00.867152+00	\N	aal1	\N	\N	curl/8.18.0	172.19.0.1	\N
b2436019-1b61-4a2f-9ffa-df7fe75edffc	fe96fc0c-ff30-47f2-b581-89a724c2f3dd	2026-10-30 16:10:11.635372+00	2026-10-30 16:10:20.043014+00	\N	aal1	\N	2026-10-30 16:10:20.042947	Python-urllib/3.14	172.19.0.1	\N
95678c29-817f-435a-a4bf-3cc322ee7478	fe96fc0c-ff30-47f2-b581-89a724c2f3dd	2026-10-30 16:11:05.207556+00	2026-10-30 16:11:05.207556+00	\N	aal1	\N	\N	curl/8.18.0	172.19.0.1	\N
c19a9447-f48d-4f9c-ab81-858696795f4b	e8778a6c-9b49-487b-9ec5-d75657699323	2026-10-30 16:11:11.146152+00	2026-10-30 16:11:11.146152+00	\N	aal1	\N	\N	curl/8.18.0	172.19.0.1	\N
3362397c-a833-41f4-adfd-07691061141c	fe96fc0c-ff30-47f2-b581-89a724c2f3dd	2026-10-30 16:11:11.056766+00	2026-10-30 16:11:18.171211+00	\N	aal1	\N	2026-10-30 16:11:18.17117	Python-urllib/3.14	172.19.0.1	\N
4e3cf1a8-85be-4c11-947d-314f8f8df2bf	fe96fc0c-ff30-47f2-b581-89a724c2f3dd	2026-10-30 18:33:28.201983+00	2026-10-30 18:33:28.201983+00	\N	aal1	\N	\N	curl/8.18.0	127.0.0.1	\N
95f2f3ee-9f03-4aa4-8dc3-96b6bf4ffc44	fe96fc0c-ff30-47f2-b581-89a724c2f3dd	2026-10-30 19:04:47.855651+00	2026-10-30 19:04:47.855651+00	\N	aal1	\N	\N	curl/8.18.0	127.0.0.1	\N
\.


--
-- Data for Name: sso_domains; Type: TABLE DATA; Schema: auth; Owner: -
--

COPY auth.sso_domains (id, sso_provider_id, domain, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: sso_providers; Type: TABLE DATA; Schema: auth; Owner: -
--

COPY auth.sso_providers (id, resource_id, created_at, updated_at) FROM stdin;
\.


--
-- Data for Name: users; Type: TABLE DATA; Schema: auth; Owner: -
--

COPY auth.users (instance_id, id, aud, role, email, encrypted_password, email_confirmed_at, invited_at, confirmation_token, confirmation_sent_at, recovery_token, recovery_sent_at, email_change_token_new, email_change, email_change_sent_at, last_sign_in_at, raw_app_meta_data, raw_user_meta_data, is_super_admin, created_at, updated_at, phone, phone_confirmed_at, phone_change, phone_change_token, phone_change_sent_at, email_change_token_current, email_change_confirm_status, banned_until, reauthentication_token, reauthentication_sent_at, is_sso_user, deleted_at, is_anonymous) FROM stdin;
00000000-0000-0000-0000-000000000000	fe96fc0c-ff30-47f2-b581-89a724c2f3dd	authenticated	authenticated	a1@example.local	$2a$10$ch3JyxvAkMMloahiDZD4tuRFH5yKi9pbdDyPIMuJrdfCvD0L1f/4i	2026-10-30 16:09:41.564563+00	\N		\N		\N			\N	2026-10-30 19:04:47.855542+00	{"provider": "email", "providers": ["email"]}	{"sub": "fe96fc0c-ff30-47f2-b581-89a724c2f3dd", "email": "a1@example.local", "email_verified": false, "phone_verified": false}	\N	2026-10-30 16:09:41.556791+00	2026-10-30 19:04:47.872566+00	\N	\N			\N		0	\N		\N	f	\N	f
00000000-0000-0000-0000-000000000000	e8778a6c-9b49-487b-9ec5-d75657699323	authenticated	authenticated	b1@example.local	$2a$10$h.6ZswdOsdWOgar2EpgtSedaE8QZsPXLPrmbRCiXH8dsiVPXjc8yK	2026-10-30 16:10:00.86467+00	\N		\N		\N			\N	2026-10-30 16:11:11.146051+00	{"provider": "email", "providers": ["email"]}	{"sub": "e8778a6c-9b49-487b-9ec5-d75657699323", "email": "b1@example.local", "email_verified": false, "phone_verified": false}	\N	2026-10-30 16:10:00.858725+00	2026-10-30 16:11:11.148271+00	\N	\N			\N		0	\N		\N	f	\N	f
\.


--
-- Data for Name: ka_ad_reward_balances; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.ka_ad_reward_balances (user_id, profile_index, expires_at, updated_at) FROM stdin;
\.


--
-- Data for Name: ka_ad_reward_claims; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.ka_ad_reward_claims (event_id, user_id, profile_index, claimed_at) FROM stdin;
\.


--
-- Data for Name: ka_library_events; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.ka_library_events (event_id, user_id, profile_index, operation, content_id, content_type, payload, origin_client_id, created_at) FROM stdin;
\.


--
-- Data for Name: ka_library_items; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.ka_library_items (user_id, profile_index, content_id, content_type, name, poster, poster_shape, background, logo, description, release_info, imdb_rating, genres, addon_base_url, added_at, updated_at) FROM stdin;
\.


--
-- Data for Name: ka_profiles; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.ka_profiles (id, user_id, profile_index, name, avatar_color_hex, avatar_id, avatar_url, profile_background_id, profile_background_url, uses_primary_addons, uses_primary_plugins, pin_enabled, pin_locked_until, created_at, updated_at) FROM stdin;
99aa2d0f-ae8c-433e-8359-e160dd5d46e2	fe96fc0c-ff30-47f2-b581-89a724c2f3dd	1	A1	#1E88E5	\N	\N	\N	\N	f	f	f	\N	2026-10-30 16:09:52.748329+00	2026-10-30 16:09:52.748329+00
abf42105-1757-467a-88b4-a4972875cad1	fe96fc0c-ff30-47f2-b581-89a724c2f3dd	2	A2	#1E88E5	\N	\N	\N	\N	f	f	f	\N	2026-10-30 16:09:52.748329+00	2026-10-30 16:09:52.748329+00
2e5f710f-1c3b-45a0-9dac-60209ac326fa	e8778a6c-9b49-487b-9ec5-d75657699323	1	B1	#1E88E5	\N	\N	\N	\N	f	f	f	\N	2026-10-30 16:10:11.544009+00	2026-10-30 16:10:11.544009+00
de4fb99f-e00f-4483-86ce-e138e5542f34	e8778a6c-9b49-487b-9ec5-d75657699323	2	B2	#1E88E5	\N	\N	\N	\N	f	f	f	\N	2026-10-30 16:10:11.544009+00	2026-10-30 16:10:11.544009+00
\.


--
-- Data for Name: ka_watch_progress; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.ka_watch_progress (user_id, profile_index, progress_key, content_id, content_type, video_id, season_num, episode_num, "position", duration, last_watched, updated_at) FROM stdin;
fe96fc0c-ff30-47f2-b581-89a724c2f3dd	1	show_s1e1	show	series	v1	1	1	42	100	200	2026-10-30 16:11:18.139828+00
\.


--
-- Data for Name: ka_watch_progress_events; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.ka_watch_progress_events (event_id, user_id, profile_index, operation, progress_key, payload, origin_client_id, created_at) FROM stdin;
1	fe96fc0c-ff30-47f2-b581-89a724c2f3dd	1	upsert	show_s1e1	{"user_id": "fe96fc0c-ff30-47f2-b581-89a724c2f3dd", "duration": 100, "position": 42, "video_id": "v1", "content_id": "show", "season_num": 1, "updated_at": "2026-10-30T16:11:18.139828+00:00", "episode_num": 1, "content_type": "series", "last_watched": 200, "progress_key": "show_s1e1", "profile_index": 1}	auth-a	2026-10-30 16:11:18.139828+00
\.


--
-- Data for Name: ka_watched_events; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.ka_watched_events (event_id, user_id, profile_index, operation, content_id, content_type, payload, origin_client_id, created_at) FROM stdin;
\.


--
-- Data for Name: ka_watched_items; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.ka_watched_items (user_id, profile_index, content_id, content_type, title, season_num, episode_num, watched_at, updated_at) FROM stdin;
\.


--
-- Data for Name: schema_migrations; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.schema_migrations (version) FROM stdin;
00
20210710035447
20210722035447
20210730183235
20210909172000
20210927181326
20211122151130
20211124214934
20211202183645
20220114185221
20220114185340
20220224000811
20220323170000
20220429102000
20220531120530
20220614074223
20220811173540
20221003041349
20221003041400
20221011041400
20221020193600
20221021073300
20221021082433
20221027105023
20221114143122
20221114143410
20221125140132
20221208132122
20221215195500
20221215195800
20221215195900
20230116124310
20230116124412
20230131181311
20230322519590
20230402418590
20230411005111
20230508135423
20230523124323
20230818113222
20230914180801
20231027141322
20231114161723
20231117164230
20240115144230
20240214120130
20240306115329
20240314092811
20240427152123
20240612123726
20240729123726
20240802193726
20240806073726
20241009103726
\.


--
-- Data for Name: user_favorites; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.user_favorites (user_id, media_id, title, poster_url, media_type, status, updated_at, profile_index) FROM stdin;
\.


--
-- Data for Name: watch_history; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.watch_history (user_id, media_id, title, episode_num, season_num, position_sec, duration_sec, poster_url, updated_at, profile_index) FROM stdin;
\.


--
-- Name: refresh_tokens_id_seq; Type: SEQUENCE SET; Schema: auth; Owner: -
--

SELECT pg_catalog.setval('auth.refresh_tokens_id_seq', 39, true);


--
-- Name: ka_library_events_event_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.ka_library_events_event_id_seq', 1, false);


--
-- Name: ka_watch_progress_events_event_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.ka_watch_progress_events_event_id_seq', 33, true);


--
-- Name: ka_watched_events_event_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.ka_watched_events_event_id_seq', 1, false);


--
-- Name: mfa_amr_claims amr_id_pk; Type: CONSTRAINT; Schema: auth; Owner: -
--

ALTER TABLE ONLY auth.mfa_amr_claims
    ADD CONSTRAINT amr_id_pk PRIMARY KEY (id);


--
-- Name: audit_log_entries audit_log_entries_pkey; Type: CONSTRAINT; Schema: auth; Owner: -
--

ALTER TABLE ONLY auth.audit_log_entries
    ADD CONSTRAINT audit_log_entries_pkey PRIMARY KEY (id);


--
-- Name: flow_state flow_state_pkey; Type: CONSTRAINT; Schema: auth; Owner: -
--

ALTER TABLE ONLY auth.flow_state
    ADD CONSTRAINT flow_state_pkey PRIMARY KEY (id);


--
-- Name: identities identities_pkey; Type: CONSTRAINT; Schema: auth; Owner: -
--

ALTER TABLE ONLY auth.identities
    ADD CONSTRAINT identities_pkey PRIMARY KEY (id);


--
-- Name: identities identities_provider_id_provider_unique; Type: CONSTRAINT; Schema: auth; Owner: -
--

ALTER TABLE ONLY auth.identities
    ADD CONSTRAINT identities_provider_id_provider_unique UNIQUE (provider_id, provider);


--
-- Name: instances instances_pkey; Type: CONSTRAINT; Schema: auth; Owner: -
--

ALTER TABLE ONLY auth.instances
    ADD CONSTRAINT instances_pkey PRIMARY KEY (id);


--
-- Name: mfa_amr_claims mfa_amr_claims_session_id_authentication_method_pkey; Type: CONSTRAINT; Schema: auth; Owner: -
--

ALTER TABLE ONLY auth.mfa_amr_claims
    ADD CONSTRAINT mfa_amr_claims_session_id_authentication_method_pkey UNIQUE (session_id, authentication_method);


--
-- Name: mfa_challenges mfa_challenges_pkey; Type: CONSTRAINT; Schema: auth; Owner: -
--

ALTER TABLE ONLY auth.mfa_challenges
    ADD CONSTRAINT mfa_challenges_pkey PRIMARY KEY (id);


--
-- Name: mfa_factors mfa_factors_last_challenged_at_key; Type: CONSTRAINT; Schema: auth; Owner: -
--

ALTER TABLE ONLY auth.mfa_factors
    ADD CONSTRAINT mfa_factors_last_challenged_at_key UNIQUE (last_challenged_at);


--
-- Name: mfa_factors mfa_factors_pkey; Type: CONSTRAINT; Schema: auth; Owner: -
--

ALTER TABLE ONLY auth.mfa_factors
    ADD CONSTRAINT mfa_factors_pkey PRIMARY KEY (id);


--
-- Name: one_time_tokens one_time_tokens_pkey; Type: CONSTRAINT; Schema: auth; Owner: -
--

ALTER TABLE ONLY auth.one_time_tokens
    ADD CONSTRAINT one_time_tokens_pkey PRIMARY KEY (id);


--
-- Name: refresh_tokens refresh_tokens_pkey; Type: CONSTRAINT; Schema: auth; Owner: -
--

ALTER TABLE ONLY auth.refresh_tokens
    ADD CONSTRAINT refresh_tokens_pkey PRIMARY KEY (id);


--
-- Name: refresh_tokens refresh_tokens_token_unique; Type: CONSTRAINT; Schema: auth; Owner: -
--

ALTER TABLE ONLY auth.refresh_tokens
    ADD CONSTRAINT refresh_tokens_token_unique UNIQUE (token);


--
-- Name: saml_providers saml_providers_entity_id_key; Type: CONSTRAINT; Schema: auth; Owner: -
--

ALTER TABLE ONLY auth.saml_providers
    ADD CONSTRAINT saml_providers_entity_id_key UNIQUE (entity_id);


--
-- Name: saml_providers saml_providers_pkey; Type: CONSTRAINT; Schema: auth; Owner: -
--

ALTER TABLE ONLY auth.saml_providers
    ADD CONSTRAINT saml_providers_pkey PRIMARY KEY (id);


--
-- Name: saml_relay_states saml_relay_states_pkey; Type: CONSTRAINT; Schema: auth; Owner: -
--

ALTER TABLE ONLY auth.saml_relay_states
    ADD CONSTRAINT saml_relay_states_pkey PRIMARY KEY (id);


--
-- Name: schema_migrations schema_migrations_pkey; Type: CONSTRAINT; Schema: auth; Owner: -
--

ALTER TABLE ONLY auth.schema_migrations
    ADD CONSTRAINT schema_migrations_pkey PRIMARY KEY (version);


--
-- Name: sessions sessions_pkey; Type: CONSTRAINT; Schema: auth; Owner: -
--

ALTER TABLE ONLY auth.sessions
    ADD CONSTRAINT sessions_pkey PRIMARY KEY (id);


--
-- Name: sso_domains sso_domains_pkey; Type: CONSTRAINT; Schema: auth; Owner: -
--

ALTER TABLE ONLY auth.sso_domains
    ADD CONSTRAINT sso_domains_pkey PRIMARY KEY (id);


--
-- Name: sso_providers sso_providers_pkey; Type: CONSTRAINT; Schema: auth; Owner: -
--

ALTER TABLE ONLY auth.sso_providers
    ADD CONSTRAINT sso_providers_pkey PRIMARY KEY (id);


--
-- Name: users users_phone_key; Type: CONSTRAINT; Schema: auth; Owner: -
--

ALTER TABLE ONLY auth.users
    ADD CONSTRAINT users_phone_key UNIQUE (phone);


--
-- Name: users users_pkey; Type: CONSTRAINT; Schema: auth; Owner: -
--

ALTER TABLE ONLY auth.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);


--
-- Name: ka_ad_reward_balances ka_ad_reward_balances_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ka_ad_reward_balances
    ADD CONSTRAINT ka_ad_reward_balances_pkey PRIMARY KEY (user_id, profile_index);


--
-- Name: ka_ad_reward_claims ka_ad_reward_claims_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ka_ad_reward_claims
    ADD CONSTRAINT ka_ad_reward_claims_pkey PRIMARY KEY (event_id);


--
-- Name: ka_library_events ka_library_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ka_library_events
    ADD CONSTRAINT ka_library_events_pkey PRIMARY KEY (event_id);


--
-- Name: ka_library_items ka_library_items_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ka_library_items
    ADD CONSTRAINT ka_library_items_pkey PRIMARY KEY (user_id, profile_index, content_id, content_type);


--
-- Name: ka_profiles ka_profiles_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ka_profiles
    ADD CONSTRAINT ka_profiles_pkey PRIMARY KEY (id);


--
-- Name: ka_profiles ka_profiles_user_id_profile_index_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ka_profiles
    ADD CONSTRAINT ka_profiles_user_id_profile_index_key UNIQUE (user_id, profile_index);


--
-- Name: ka_watch_progress_events ka_watch_progress_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ka_watch_progress_events
    ADD CONSTRAINT ka_watch_progress_events_pkey PRIMARY KEY (event_id);


--
-- Name: ka_watch_progress ka_watch_progress_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ka_watch_progress
    ADD CONSTRAINT ka_watch_progress_pkey PRIMARY KEY (user_id, profile_index, progress_key);


--
-- Name: ka_watched_events ka_watched_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ka_watched_events
    ADD CONSTRAINT ka_watched_events_pkey PRIMARY KEY (event_id);


--
-- Name: ka_watched_items ka_watched_items_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ka_watched_items
    ADD CONSTRAINT ka_watched_items_pkey PRIMARY KEY (user_id, profile_index, content_id, content_type, season_num, episode_num);


--
-- Name: schema_migrations schema_migrations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.schema_migrations
    ADD CONSTRAINT schema_migrations_pkey PRIMARY KEY (version);


--
-- Name: user_favorites user_favorites_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_favorites
    ADD CONSTRAINT user_favorites_pkey PRIMARY KEY (user_id, profile_index, media_id);


--
-- Name: watch_history watch_history_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.watch_history
    ADD CONSTRAINT watch_history_pkey PRIMARY KEY (user_id, profile_index, media_id, season_num, episode_num);


--
-- Name: audit_logs_instance_id_idx; Type: INDEX; Schema: auth; Owner: -
--

CREATE INDEX audit_logs_instance_id_idx ON auth.audit_log_entries USING btree (instance_id);


--
-- Name: confirmation_token_idx; Type: INDEX; Schema: auth; Owner: -
--

CREATE UNIQUE INDEX confirmation_token_idx ON auth.users USING btree (confirmation_token) WHERE ((confirmation_token)::text !~ '^[0-9 ]*$'::text);


--
-- Name: email_change_token_current_idx; Type: INDEX; Schema: auth; Owner: -
--

CREATE UNIQUE INDEX email_change_token_current_idx ON auth.users USING btree (email_change_token_current) WHERE ((email_change_token_current)::text !~ '^[0-9 ]*$'::text);


--
-- Name: email_change_token_new_idx; Type: INDEX; Schema: auth; Owner: -
--

CREATE UNIQUE INDEX email_change_token_new_idx ON auth.users USING btree (email_change_token_new) WHERE ((email_change_token_new)::text !~ '^[0-9 ]*$'::text);


--
-- Name: factor_id_created_at_idx; Type: INDEX; Schema: auth; Owner: -
--

CREATE INDEX factor_id_created_at_idx ON auth.mfa_factors USING btree (user_id, created_at);


--
-- Name: flow_state_created_at_idx; Type: INDEX; Schema: auth; Owner: -
--

CREATE INDEX flow_state_created_at_idx ON auth.flow_state USING btree (created_at DESC);


--
-- Name: identities_email_idx; Type: INDEX; Schema: auth; Owner: -
--

CREATE INDEX identities_email_idx ON auth.identities USING btree (email text_pattern_ops);


--
-- Name: INDEX identities_email_idx; Type: COMMENT; Schema: auth; Owner: -
--

COMMENT ON INDEX auth.identities_email_idx IS 'Auth: Ensures indexed queries on the email column';


--
-- Name: identities_user_id_idx; Type: INDEX; Schema: auth; Owner: -
--

CREATE INDEX identities_user_id_idx ON auth.identities USING btree (user_id);


--
-- Name: idx_auth_code; Type: INDEX; Schema: auth; Owner: -
--

CREATE INDEX idx_auth_code ON auth.flow_state USING btree (auth_code);


--
-- Name: idx_user_id_auth_method; Type: INDEX; Schema: auth; Owner: -
--

CREATE INDEX idx_user_id_auth_method ON auth.flow_state USING btree (user_id, authentication_method);


--
-- Name: mfa_challenge_created_at_idx; Type: INDEX; Schema: auth; Owner: -
--

CREATE INDEX mfa_challenge_created_at_idx ON auth.mfa_challenges USING btree (created_at DESC);


--
-- Name: mfa_factors_user_friendly_name_unique; Type: INDEX; Schema: auth; Owner: -
--

CREATE UNIQUE INDEX mfa_factors_user_friendly_name_unique ON auth.mfa_factors USING btree (friendly_name, user_id) WHERE (TRIM(BOTH FROM friendly_name) <> ''::text);


--
-- Name: mfa_factors_user_id_idx; Type: INDEX; Schema: auth; Owner: -
--

CREATE INDEX mfa_factors_user_id_idx ON auth.mfa_factors USING btree (user_id);


--
-- Name: one_time_tokens_relates_to_hash_idx; Type: INDEX; Schema: auth; Owner: -
--

CREATE INDEX one_time_tokens_relates_to_hash_idx ON auth.one_time_tokens USING hash (relates_to);


--
-- Name: one_time_tokens_token_hash_hash_idx; Type: INDEX; Schema: auth; Owner: -
--

CREATE INDEX one_time_tokens_token_hash_hash_idx ON auth.one_time_tokens USING hash (token_hash);


--
-- Name: one_time_tokens_user_id_token_type_key; Type: INDEX; Schema: auth; Owner: -
--

CREATE UNIQUE INDEX one_time_tokens_user_id_token_type_key ON auth.one_time_tokens USING btree (user_id, token_type);


--
-- Name: reauthentication_token_idx; Type: INDEX; Schema: auth; Owner: -
--

CREATE UNIQUE INDEX reauthentication_token_idx ON auth.users USING btree (reauthentication_token) WHERE ((reauthentication_token)::text !~ '^[0-9 ]*$'::text);


--
-- Name: recovery_token_idx; Type: INDEX; Schema: auth; Owner: -
--

CREATE UNIQUE INDEX recovery_token_idx ON auth.users USING btree (recovery_token) WHERE ((recovery_token)::text !~ '^[0-9 ]*$'::text);


--
-- Name: refresh_tokens_instance_id_idx; Type: INDEX; Schema: auth; Owner: -
--

CREATE INDEX refresh_tokens_instance_id_idx ON auth.refresh_tokens USING btree (instance_id);


--
-- Name: refresh_tokens_instance_id_user_id_idx; Type: INDEX; Schema: auth; Owner: -
--

CREATE INDEX refresh_tokens_instance_id_user_id_idx ON auth.refresh_tokens USING btree (instance_id, user_id);


--
-- Name: refresh_tokens_parent_idx; Type: INDEX; Schema: auth; Owner: -
--

CREATE INDEX refresh_tokens_parent_idx ON auth.refresh_tokens USING btree (parent);


--
-- Name: refresh_tokens_session_id_revoked_idx; Type: INDEX; Schema: auth; Owner: -
--

CREATE INDEX refresh_tokens_session_id_revoked_idx ON auth.refresh_tokens USING btree (session_id, revoked);


--
-- Name: refresh_tokens_updated_at_idx; Type: INDEX; Schema: auth; Owner: -
--

CREATE INDEX refresh_tokens_updated_at_idx ON auth.refresh_tokens USING btree (updated_at DESC);


--
-- Name: saml_providers_sso_provider_id_idx; Type: INDEX; Schema: auth; Owner: -
--

CREATE INDEX saml_providers_sso_provider_id_idx ON auth.saml_providers USING btree (sso_provider_id);


--
-- Name: saml_relay_states_created_at_idx; Type: INDEX; Schema: auth; Owner: -
--

CREATE INDEX saml_relay_states_created_at_idx ON auth.saml_relay_states USING btree (created_at DESC);


--
-- Name: saml_relay_states_for_email_idx; Type: INDEX; Schema: auth; Owner: -
--

CREATE INDEX saml_relay_states_for_email_idx ON auth.saml_relay_states USING btree (for_email);


--
-- Name: saml_relay_states_sso_provider_id_idx; Type: INDEX; Schema: auth; Owner: -
--

CREATE INDEX saml_relay_states_sso_provider_id_idx ON auth.saml_relay_states USING btree (sso_provider_id);


--
-- Name: sessions_not_after_idx; Type: INDEX; Schema: auth; Owner: -
--

CREATE INDEX sessions_not_after_idx ON auth.sessions USING btree (not_after DESC);


--
-- Name: sessions_user_id_idx; Type: INDEX; Schema: auth; Owner: -
--

CREATE INDEX sessions_user_id_idx ON auth.sessions USING btree (user_id);


--
-- Name: sso_domains_domain_idx; Type: INDEX; Schema: auth; Owner: -
--

CREATE UNIQUE INDEX sso_domains_domain_idx ON auth.sso_domains USING btree (lower(domain));


--
-- Name: sso_domains_sso_provider_id_idx; Type: INDEX; Schema: auth; Owner: -
--

CREATE INDEX sso_domains_sso_provider_id_idx ON auth.sso_domains USING btree (sso_provider_id);


--
-- Name: sso_providers_resource_id_idx; Type: INDEX; Schema: auth; Owner: -
--

CREATE UNIQUE INDEX sso_providers_resource_id_idx ON auth.sso_providers USING btree (lower(resource_id));


--
-- Name: unique_phone_factor_per_user; Type: INDEX; Schema: auth; Owner: -
--

CREATE UNIQUE INDEX unique_phone_factor_per_user ON auth.mfa_factors USING btree (user_id, phone);


--
-- Name: user_id_created_at_idx; Type: INDEX; Schema: auth; Owner: -
--

CREATE INDEX user_id_created_at_idx ON auth.sessions USING btree (user_id, created_at);


--
-- Name: users_email_partial_key; Type: INDEX; Schema: auth; Owner: -
--

CREATE UNIQUE INDEX users_email_partial_key ON auth.users USING btree (email) WHERE (is_sso_user = false);


--
-- Name: INDEX users_email_partial_key; Type: COMMENT; Schema: auth; Owner: -
--

COMMENT ON INDEX auth.users_email_partial_key IS 'Auth: A partial unique index that applies only when is_sso_user is false';


--
-- Name: users_instance_id_email_idx; Type: INDEX; Schema: auth; Owner: -
--

CREATE INDEX users_instance_id_email_idx ON auth.users USING btree (instance_id, email);


--
-- Name: users_instance_id_idx; Type: INDEX; Schema: auth; Owner: -
--

CREATE INDEX users_instance_id_idx ON auth.users USING btree (instance_id);


--
-- Name: users_is_anonymous_idx; Type: INDEX; Schema: auth; Owner: -
--

CREATE INDEX users_is_anonymous_idx ON auth.users USING btree (is_anonymous);


--
-- Name: ka_ad_reward_claims_user_claimed_at_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ka_ad_reward_claims_user_claimed_at_idx ON public.ka_ad_reward_claims USING btree (user_id, claimed_at DESC);


--
-- Name: ka_library_events_scope_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ka_library_events_scope_idx ON public.ka_library_events USING btree (user_id, profile_index, event_id);


--
-- Name: ka_watch_progress_events_scope_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ka_watch_progress_events_scope_idx ON public.ka_watch_progress_events USING btree (user_id, profile_index, event_id);


--
-- Name: ka_watch_progress_freshness_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ka_watch_progress_freshness_idx ON public.ka_watch_progress USING btree (user_id, profile_index, last_watched DESC);


--
-- Name: ka_watched_events_scope_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX ka_watched_events_scope_idx ON public.ka_watched_events USING btree (user_id, profile_index, event_id);


--
-- Name: schema_migrations_version_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE UNIQUE INDEX schema_migrations_version_idx ON public.schema_migrations USING btree (version);


--
-- Name: user_favorites_profile_updated_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX user_favorites_profile_updated_idx ON public.user_favorites USING btree (user_id, profile_index, updated_at DESC);


--
-- Name: watch_history_profile_updated_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX watch_history_profile_updated_idx ON public.watch_history USING btree (user_id, profile_index, updated_at DESC);


--
-- Name: identities identities_user_id_fkey; Type: FK CONSTRAINT; Schema: auth; Owner: -
--

ALTER TABLE ONLY auth.identities
    ADD CONSTRAINT identities_user_id_fkey FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE;


--
-- Name: mfa_amr_claims mfa_amr_claims_session_id_fkey; Type: FK CONSTRAINT; Schema: auth; Owner: -
--

ALTER TABLE ONLY auth.mfa_amr_claims
    ADD CONSTRAINT mfa_amr_claims_session_id_fkey FOREIGN KEY (session_id) REFERENCES auth.sessions(id) ON DELETE CASCADE;


--
-- Name: mfa_challenges mfa_challenges_auth_factor_id_fkey; Type: FK CONSTRAINT; Schema: auth; Owner: -
--

ALTER TABLE ONLY auth.mfa_challenges
    ADD CONSTRAINT mfa_challenges_auth_factor_id_fkey FOREIGN KEY (factor_id) REFERENCES auth.mfa_factors(id) ON DELETE CASCADE;


--
-- Name: mfa_factors mfa_factors_user_id_fkey; Type: FK CONSTRAINT; Schema: auth; Owner: -
--

ALTER TABLE ONLY auth.mfa_factors
    ADD CONSTRAINT mfa_factors_user_id_fkey FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE;


--
-- Name: one_time_tokens one_time_tokens_user_id_fkey; Type: FK CONSTRAINT; Schema: auth; Owner: -
--

ALTER TABLE ONLY auth.one_time_tokens
    ADD CONSTRAINT one_time_tokens_user_id_fkey FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE;


--
-- Name: refresh_tokens refresh_tokens_session_id_fkey; Type: FK CONSTRAINT; Schema: auth; Owner: -
--

ALTER TABLE ONLY auth.refresh_tokens
    ADD CONSTRAINT refresh_tokens_session_id_fkey FOREIGN KEY (session_id) REFERENCES auth.sessions(id) ON DELETE CASCADE;


--
-- Name: saml_providers saml_providers_sso_provider_id_fkey; Type: FK CONSTRAINT; Schema: auth; Owner: -
--

ALTER TABLE ONLY auth.saml_providers
    ADD CONSTRAINT saml_providers_sso_provider_id_fkey FOREIGN KEY (sso_provider_id) REFERENCES auth.sso_providers(id) ON DELETE CASCADE;


--
-- Name: saml_relay_states saml_relay_states_flow_state_id_fkey; Type: FK CONSTRAINT; Schema: auth; Owner: -
--

ALTER TABLE ONLY auth.saml_relay_states
    ADD CONSTRAINT saml_relay_states_flow_state_id_fkey FOREIGN KEY (flow_state_id) REFERENCES auth.flow_state(id) ON DELETE CASCADE;


--
-- Name: saml_relay_states saml_relay_states_sso_provider_id_fkey; Type: FK CONSTRAINT; Schema: auth; Owner: -
--

ALTER TABLE ONLY auth.saml_relay_states
    ADD CONSTRAINT saml_relay_states_sso_provider_id_fkey FOREIGN KEY (sso_provider_id) REFERENCES auth.sso_providers(id) ON DELETE CASCADE;


--
-- Name: sessions sessions_user_id_fkey; Type: FK CONSTRAINT; Schema: auth; Owner: -
--

ALTER TABLE ONLY auth.sessions
    ADD CONSTRAINT sessions_user_id_fkey FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE;


--
-- Name: sso_domains sso_domains_sso_provider_id_fkey; Type: FK CONSTRAINT; Schema: auth; Owner: -
--

ALTER TABLE ONLY auth.sso_domains
    ADD CONSTRAINT sso_domains_sso_provider_id_fkey FOREIGN KEY (sso_provider_id) REFERENCES auth.sso_providers(id) ON DELETE CASCADE;


--
-- Name: ka_ad_reward_balances ka_ad_reward_balances_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ka_ad_reward_balances
    ADD CONSTRAINT ka_ad_reward_balances_user_id_fkey FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE;


--
-- Name: ka_ad_reward_claims ka_ad_reward_claims_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ka_ad_reward_claims
    ADD CONSTRAINT ka_ad_reward_claims_user_id_fkey FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE;


--
-- Name: ka_library_events ka_library_events_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ka_library_events
    ADD CONSTRAINT ka_library_events_user_id_fkey FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE;


--
-- Name: ka_library_items ka_library_items_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ka_library_items
    ADD CONSTRAINT ka_library_items_user_id_fkey FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE;


--
-- Name: ka_profiles ka_profiles_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ka_profiles
    ADD CONSTRAINT ka_profiles_user_id_fkey FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE;


--
-- Name: ka_watch_progress_events ka_watch_progress_events_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ka_watch_progress_events
    ADD CONSTRAINT ka_watch_progress_events_user_id_fkey FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE;


--
-- Name: ka_watch_progress ka_watch_progress_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ka_watch_progress
    ADD CONSTRAINT ka_watch_progress_user_id_fkey FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE;


--
-- Name: ka_watched_events ka_watched_events_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ka_watched_events
    ADD CONSTRAINT ka_watched_events_user_id_fkey FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE;


--
-- Name: ka_watched_items ka_watched_items_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.ka_watched_items
    ADD CONSTRAINT ka_watched_items_user_id_fkey FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE CASCADE;


--
-- Name: user_favorites user_favorites_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.user_favorites
    ADD CONSTRAINT user_favorites_user_id_fkey FOREIGN KEY (user_id) REFERENCES auth.users(id);


--
-- Name: watch_history watch_history_user_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.watch_history
    ADD CONSTRAINT watch_history_user_id_fkey FOREIGN KEY (user_id) REFERENCES auth.users(id);


--
-- Name: audit_log_entries; Type: ROW SECURITY; Schema: auth; Owner: -
--

ALTER TABLE auth.audit_log_entries ENABLE ROW LEVEL SECURITY;

--
-- Name: flow_state; Type: ROW SECURITY; Schema: auth; Owner: -
--

ALTER TABLE auth.flow_state ENABLE ROW LEVEL SECURITY;

--
-- Name: identities; Type: ROW SECURITY; Schema: auth; Owner: -
--

ALTER TABLE auth.identities ENABLE ROW LEVEL SECURITY;

--
-- Name: instances; Type: ROW SECURITY; Schema: auth; Owner: -
--

ALTER TABLE auth.instances ENABLE ROW LEVEL SECURITY;

--
-- Name: mfa_amr_claims; Type: ROW SECURITY; Schema: auth; Owner: -
--

ALTER TABLE auth.mfa_amr_claims ENABLE ROW LEVEL SECURITY;

--
-- Name: mfa_challenges; Type: ROW SECURITY; Schema: auth; Owner: -
--

ALTER TABLE auth.mfa_challenges ENABLE ROW LEVEL SECURITY;

--
-- Name: mfa_factors; Type: ROW SECURITY; Schema: auth; Owner: -
--

ALTER TABLE auth.mfa_factors ENABLE ROW LEVEL SECURITY;

--
-- Name: one_time_tokens; Type: ROW SECURITY; Schema: auth; Owner: -
--

ALTER TABLE auth.one_time_tokens ENABLE ROW LEVEL SECURITY;

--
-- Name: refresh_tokens; Type: ROW SECURITY; Schema: auth; Owner: -
--

ALTER TABLE auth.refresh_tokens ENABLE ROW LEVEL SECURITY;

--
-- Name: saml_providers; Type: ROW SECURITY; Schema: auth; Owner: -
--

ALTER TABLE auth.saml_providers ENABLE ROW LEVEL SECURITY;

--
-- Name: saml_relay_states; Type: ROW SECURITY; Schema: auth; Owner: -
--

ALTER TABLE auth.saml_relay_states ENABLE ROW LEVEL SECURITY;

--
-- Name: schema_migrations; Type: ROW SECURITY; Schema: auth; Owner: -
--

ALTER TABLE auth.schema_migrations ENABLE ROW LEVEL SECURITY;

--
-- Name: sessions; Type: ROW SECURITY; Schema: auth; Owner: -
--

ALTER TABLE auth.sessions ENABLE ROW LEVEL SECURITY;

--
-- Name: sso_domains; Type: ROW SECURITY; Schema: auth; Owner: -
--

ALTER TABLE auth.sso_domains ENABLE ROW LEVEL SECURITY;

--
-- Name: sso_providers; Type: ROW SECURITY; Schema: auth; Owner: -
--

ALTER TABLE auth.sso_providers ENABLE ROW LEVEL SECURITY;

--
-- Name: users; Type: ROW SECURITY; Schema: auth; Owner: -
--

ALTER TABLE auth.users ENABLE ROW LEVEL SECURITY;

--
-- Name: ka_ad_reward_balances; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.ka_ad_reward_balances ENABLE ROW LEVEL SECURITY;

--
-- Name: ka_ad_reward_balances ka_ad_reward_balances_select_own; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY ka_ad_reward_balances_select_own ON public.ka_ad_reward_balances FOR SELECT TO authenticated USING ((user_id = ( SELECT auth.uid() AS uid)));


--
-- Name: ka_ad_reward_claims; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.ka_ad_reward_claims ENABLE ROW LEVEL SECURITY;

--
-- Name: ka_library_events; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.ka_library_events ENABLE ROW LEVEL SECURITY;

--
-- Name: ka_library_items; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.ka_library_items ENABLE ROW LEVEL SECURITY;

--
-- Name: ka_profiles; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.ka_profiles ENABLE ROW LEVEL SECURITY;

--
-- Name: ka_profiles ka_profiles_owner_access; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY ka_profiles_owner_access ON public.ka_profiles TO authenticated USING ((( SELECT auth.uid() AS uid) = user_id)) WITH CHECK ((( SELECT auth.uid() AS uid) = user_id));


--
-- Name: ka_watch_progress; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.ka_watch_progress ENABLE ROW LEVEL SECURITY;

--
-- Name: ka_watch_progress_events; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.ka_watch_progress_events ENABLE ROW LEVEL SECURITY;

--
-- Name: ka_watched_events; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.ka_watched_events ENABLE ROW LEVEL SECURITY;

--
-- Name: ka_watched_items; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.ka_watched_items ENABLE ROW LEVEL SECURITY;

--
-- Name: user_favorites legacy_favorites; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY legacy_favorites ON public.user_favorites USING ((( SELECT auth.uid() AS uid) = user_id)) WITH CHECK ((( SELECT auth.uid() AS uid) = user_id));


--
-- Name: watch_history legacy_history; Type: POLICY; Schema: public; Owner: -
--

CREATE POLICY legacy_history ON public.watch_history USING ((( SELECT auth.uid() AS uid) = user_id)) WITH CHECK ((( SELECT auth.uid() AS uid) = user_id));


--
-- Name: user_favorites; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.user_favorites ENABLE ROW LEVEL SECURITY;

--
-- Name: watch_history; Type: ROW SECURITY; Schema: public; Owner: -
--

ALTER TABLE public.watch_history ENABLE ROW LEVEL SECURITY;

--
-- Name: SCHEMA auth; Type: ACL; Schema: -; Owner: -
--

GRANT USAGE ON SCHEMA auth TO anon;
GRANT USAGE ON SCHEMA auth TO authenticated;


--
-- Name: SCHEMA public; Type: ACL; Schema: -; Owner: -
--

GRANT USAGE ON SCHEMA public TO anon;
GRANT USAGE ON SCHEMA public TO authenticated;


--
-- Name: FUNCTION ka_assert_profile_access(p_profile_id integer); Type: ACL; Schema: public; Owner: -
--

REVOKE ALL ON FUNCTION public.ka_assert_profile_access(p_profile_id integer) FROM PUBLIC;


--
-- Name: FUNCTION ka_claim_ad_reward(p_profile_index integer, p_event_id uuid); Type: ACL; Schema: public; Owner: -
--

REVOKE ALL ON FUNCTION public.ka_claim_ad_reward(p_profile_index integer, p_event_id uuid) FROM PUBLIC;
GRANT ALL ON FUNCTION public.ka_claim_ad_reward(p_profile_index integer, p_event_id uuid) TO authenticated;


--
-- Name: FUNCTION sync_delete_library_items(p_profile_id integer, p_keys jsonb, p_origin_client_id text); Type: ACL; Schema: public; Owner: -
--

REVOKE ALL ON FUNCTION public.sync_delete_library_items(p_profile_id integer, p_keys jsonb, p_origin_client_id text) FROM PUBLIC;
GRANT ALL ON FUNCTION public.sync_delete_library_items(p_profile_id integer, p_keys jsonb, p_origin_client_id text) TO authenticated;


--
-- Name: FUNCTION sync_delete_watch_progress(p_profile_id integer, p_keys jsonb, p_origin_client_id text); Type: ACL; Schema: public; Owner: -
--

REVOKE ALL ON FUNCTION public.sync_delete_watch_progress(p_profile_id integer, p_keys jsonb, p_origin_client_id text) FROM PUBLIC;
GRANT ALL ON FUNCTION public.sync_delete_watch_progress(p_profile_id integer, p_keys jsonb, p_origin_client_id text) TO authenticated;


--
-- Name: FUNCTION sync_delete_watched_items(p_profile_id integer, p_keys jsonb, p_origin_client_id text); Type: ACL; Schema: public; Owner: -
--

REVOKE ALL ON FUNCTION public.sync_delete_watched_items(p_profile_id integer, p_keys jsonb, p_origin_client_id text) FROM PUBLIC;
GRANT ALL ON FUNCTION public.sync_delete_watched_items(p_profile_id integer, p_keys jsonb, p_origin_client_id text) TO authenticated;


--
-- Name: FUNCTION sync_get_library_delta_cursor(p_profile_id integer); Type: ACL; Schema: public; Owner: -
--

REVOKE ALL ON FUNCTION public.sync_get_library_delta_cursor(p_profile_id integer) FROM PUBLIC;
GRANT ALL ON FUNCTION public.sync_get_library_delta_cursor(p_profile_id integer) TO authenticated;


--
-- Name: FUNCTION sync_get_watch_progress_delta_cursor(p_profile_id integer); Type: ACL; Schema: public; Owner: -
--

REVOKE ALL ON FUNCTION public.sync_get_watch_progress_delta_cursor(p_profile_id integer) FROM PUBLIC;
GRANT ALL ON FUNCTION public.sync_get_watch_progress_delta_cursor(p_profile_id integer) TO authenticated;


--
-- Name: FUNCTION sync_get_watched_items_delta_cursor(p_profile_id integer); Type: ACL; Schema: public; Owner: -
--

REVOKE ALL ON FUNCTION public.sync_get_watched_items_delta_cursor(p_profile_id integer) FROM PUBLIC;
GRANT ALL ON FUNCTION public.sync_get_watched_items_delta_cursor(p_profile_id integer) TO authenticated;


--
-- Name: FUNCTION sync_pull_library(p_profile_id integer, p_limit integer, p_offset integer); Type: ACL; Schema: public; Owner: -
--

REVOKE ALL ON FUNCTION public.sync_pull_library(p_profile_id integer, p_limit integer, p_offset integer) FROM PUBLIC;
GRANT ALL ON FUNCTION public.sync_pull_library(p_profile_id integer, p_limit integer, p_offset integer) TO authenticated;


--
-- Name: FUNCTION sync_pull_library_delta(p_profile_id integer, p_since_event_id bigint, p_limit integer); Type: ACL; Schema: public; Owner: -
--

REVOKE ALL ON FUNCTION public.sync_pull_library_delta(p_profile_id integer, p_since_event_id bigint, p_limit integer) FROM PUBLIC;
GRANT ALL ON FUNCTION public.sync_pull_library_delta(p_profile_id integer, p_since_event_id bigint, p_limit integer) TO authenticated;


--
-- Name: TABLE ka_profiles; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT,INSERT,DELETE,UPDATE ON TABLE public.ka_profiles TO authenticated;


--
-- Name: FUNCTION sync_pull_profiles(); Type: ACL; Schema: public; Owner: -
--

GRANT ALL ON FUNCTION public.sync_pull_profiles() TO authenticated;


--
-- Name: FUNCTION sync_pull_watch_progress(p_profile_id integer, p_since_last_watched bigint, p_limit integer); Type: ACL; Schema: public; Owner: -
--

REVOKE ALL ON FUNCTION public.sync_pull_watch_progress(p_profile_id integer, p_since_last_watched bigint, p_limit integer) FROM PUBLIC;
GRANT ALL ON FUNCTION public.sync_pull_watch_progress(p_profile_id integer, p_since_last_watched bigint, p_limit integer) TO authenticated;


--
-- Name: FUNCTION sync_pull_watch_progress_delta(p_profile_id integer, p_since_event_id bigint, p_limit integer); Type: ACL; Schema: public; Owner: -
--

REVOKE ALL ON FUNCTION public.sync_pull_watch_progress_delta(p_profile_id integer, p_since_event_id bigint, p_limit integer) FROM PUBLIC;
GRANT ALL ON FUNCTION public.sync_pull_watch_progress_delta(p_profile_id integer, p_since_event_id bigint, p_limit integer) TO authenticated;


--
-- Name: FUNCTION sync_pull_watched_items(p_profile_id integer, p_page integer, p_page_size integer); Type: ACL; Schema: public; Owner: -
--

REVOKE ALL ON FUNCTION public.sync_pull_watched_items(p_profile_id integer, p_page integer, p_page_size integer) FROM PUBLIC;
GRANT ALL ON FUNCTION public.sync_pull_watched_items(p_profile_id integer, p_page integer, p_page_size integer) TO authenticated;


--
-- Name: FUNCTION sync_pull_watched_items_delta(p_profile_id integer, p_since_event_id bigint, p_limit integer); Type: ACL; Schema: public; Owner: -
--

REVOKE ALL ON FUNCTION public.sync_pull_watched_items_delta(p_profile_id integer, p_since_event_id bigint, p_limit integer) FROM PUBLIC;
GRANT ALL ON FUNCTION public.sync_pull_watched_items_delta(p_profile_id integer, p_since_event_id bigint, p_limit integer) TO authenticated;


--
-- Name: FUNCTION sync_push_library_items(p_profile_id integer, p_items jsonb, p_origin_client_id text); Type: ACL; Schema: public; Owner: -
--

REVOKE ALL ON FUNCTION public.sync_push_library_items(p_profile_id integer, p_items jsonb, p_origin_client_id text) FROM PUBLIC;
GRANT ALL ON FUNCTION public.sync_push_library_items(p_profile_id integer, p_items jsonb, p_origin_client_id text) TO authenticated;


--
-- Name: FUNCTION sync_push_profiles(p_client_max_profiles integer, p_profiles jsonb, p_origin_client_id text); Type: ACL; Schema: public; Owner: -
--

GRANT ALL ON FUNCTION public.sync_push_profiles(p_client_max_profiles integer, p_profiles jsonb, p_origin_client_id text) TO authenticated;


--
-- Name: FUNCTION sync_push_watch_progress(p_profile_id integer, p_entries jsonb, p_origin_client_id text); Type: ACL; Schema: public; Owner: -
--

REVOKE ALL ON FUNCTION public.sync_push_watch_progress(p_profile_id integer, p_entries jsonb, p_origin_client_id text) FROM PUBLIC;
GRANT ALL ON FUNCTION public.sync_push_watch_progress(p_profile_id integer, p_entries jsonb, p_origin_client_id text) TO authenticated;


--
-- Name: FUNCTION sync_push_watched_items(p_profile_id integer, p_items jsonb, p_origin_client_id text); Type: ACL; Schema: public; Owner: -
--

REVOKE ALL ON FUNCTION public.sync_push_watched_items(p_profile_id integer, p_items jsonb, p_origin_client_id text) FROM PUBLIC;
GRANT ALL ON FUNCTION public.sync_push_watched_items(p_profile_id integer, p_items jsonb, p_origin_client_id text) TO authenticated;


--
-- Name: TABLE audit_log_entries; Type: ACL; Schema: auth; Owner: -
--

REVOKE ALL ON TABLE auth.audit_log_entries FROM postgres;
GRANT INSERT,REFERENCES,DELETE,TRIGGER,TRUNCATE,MAINTAIN,UPDATE ON TABLE auth.audit_log_entries TO postgres;
GRANT SELECT ON TABLE auth.audit_log_entries TO postgres WITH GRANT OPTION;


--
-- Name: TABLE flow_state; Type: ACL; Schema: auth; Owner: -
--

REVOKE ALL ON TABLE auth.flow_state FROM postgres;
GRANT INSERT,REFERENCES,DELETE,TRIGGER,TRUNCATE,MAINTAIN,UPDATE ON TABLE auth.flow_state TO postgres;
GRANT SELECT ON TABLE auth.flow_state TO postgres WITH GRANT OPTION;


--
-- Name: TABLE identities; Type: ACL; Schema: auth; Owner: -
--

REVOKE ALL ON TABLE auth.identities FROM postgres;
GRANT INSERT,REFERENCES,DELETE,TRIGGER,TRUNCATE,MAINTAIN,UPDATE ON TABLE auth.identities TO postgres;
GRANT SELECT ON TABLE auth.identities TO postgres WITH GRANT OPTION;


--
-- Name: TABLE instances; Type: ACL; Schema: auth; Owner: -
--

REVOKE ALL ON TABLE auth.instances FROM postgres;
GRANT INSERT,REFERENCES,DELETE,TRIGGER,TRUNCATE,MAINTAIN,UPDATE ON TABLE auth.instances TO postgres;
GRANT SELECT ON TABLE auth.instances TO postgres WITH GRANT OPTION;


--
-- Name: TABLE mfa_amr_claims; Type: ACL; Schema: auth; Owner: -
--

REVOKE ALL ON TABLE auth.mfa_amr_claims FROM postgres;
GRANT INSERT,REFERENCES,DELETE,TRIGGER,TRUNCATE,MAINTAIN,UPDATE ON TABLE auth.mfa_amr_claims TO postgres;
GRANT SELECT ON TABLE auth.mfa_amr_claims TO postgres WITH GRANT OPTION;


--
-- Name: TABLE mfa_challenges; Type: ACL; Schema: auth; Owner: -
--

REVOKE ALL ON TABLE auth.mfa_challenges FROM postgres;
GRANT INSERT,REFERENCES,DELETE,TRIGGER,TRUNCATE,MAINTAIN,UPDATE ON TABLE auth.mfa_challenges TO postgres;
GRANT SELECT ON TABLE auth.mfa_challenges TO postgres WITH GRANT OPTION;


--
-- Name: TABLE mfa_factors; Type: ACL; Schema: auth; Owner: -
--

REVOKE ALL ON TABLE auth.mfa_factors FROM postgres;
GRANT INSERT,REFERENCES,DELETE,TRIGGER,TRUNCATE,MAINTAIN,UPDATE ON TABLE auth.mfa_factors TO postgres;
GRANT SELECT ON TABLE auth.mfa_factors TO postgres WITH GRANT OPTION;


--
-- Name: TABLE one_time_tokens; Type: ACL; Schema: auth; Owner: -
--

REVOKE ALL ON TABLE auth.one_time_tokens FROM postgres;
GRANT INSERT,REFERENCES,DELETE,TRIGGER,TRUNCATE,MAINTAIN,UPDATE ON TABLE auth.one_time_tokens TO postgres;
GRANT SELECT ON TABLE auth.one_time_tokens TO postgres WITH GRANT OPTION;


--
-- Name: TABLE refresh_tokens; Type: ACL; Schema: auth; Owner: -
--

REVOKE ALL ON TABLE auth.refresh_tokens FROM postgres;
GRANT INSERT,REFERENCES,DELETE,TRIGGER,TRUNCATE,MAINTAIN,UPDATE ON TABLE auth.refresh_tokens TO postgres;
GRANT SELECT ON TABLE auth.refresh_tokens TO postgres WITH GRANT OPTION;


--
-- Name: TABLE saml_providers; Type: ACL; Schema: auth; Owner: -
--

REVOKE ALL ON TABLE auth.saml_providers FROM postgres;
GRANT INSERT,REFERENCES,DELETE,TRIGGER,TRUNCATE,MAINTAIN,UPDATE ON TABLE auth.saml_providers TO postgres;
GRANT SELECT ON TABLE auth.saml_providers TO postgres WITH GRANT OPTION;


--
-- Name: TABLE saml_relay_states; Type: ACL; Schema: auth; Owner: -
--

REVOKE ALL ON TABLE auth.saml_relay_states FROM postgres;
GRANT INSERT,REFERENCES,DELETE,TRIGGER,TRUNCATE,MAINTAIN,UPDATE ON TABLE auth.saml_relay_states TO postgres;
GRANT SELECT ON TABLE auth.saml_relay_states TO postgres WITH GRANT OPTION;


--
-- Name: TABLE schema_migrations; Type: ACL; Schema: auth; Owner: -
--

REVOKE ALL ON TABLE auth.schema_migrations FROM postgres;
GRANT INSERT,REFERENCES,DELETE,TRIGGER,TRUNCATE,MAINTAIN,UPDATE ON TABLE auth.schema_migrations TO postgres;
GRANT SELECT ON TABLE auth.schema_migrations TO postgres WITH GRANT OPTION;


--
-- Name: TABLE sessions; Type: ACL; Schema: auth; Owner: -
--

REVOKE ALL ON TABLE auth.sessions FROM postgres;
GRANT INSERT,REFERENCES,DELETE,TRIGGER,TRUNCATE,MAINTAIN,UPDATE ON TABLE auth.sessions TO postgres;
GRANT SELECT ON TABLE auth.sessions TO postgres WITH GRANT OPTION;


--
-- Name: TABLE sso_domains; Type: ACL; Schema: auth; Owner: -
--

REVOKE ALL ON TABLE auth.sso_domains FROM postgres;
GRANT INSERT,REFERENCES,DELETE,TRIGGER,TRUNCATE,MAINTAIN,UPDATE ON TABLE auth.sso_domains TO postgres;
GRANT SELECT ON TABLE auth.sso_domains TO postgres WITH GRANT OPTION;


--
-- Name: TABLE sso_providers; Type: ACL; Schema: auth; Owner: -
--

REVOKE ALL ON TABLE auth.sso_providers FROM postgres;
GRANT INSERT,REFERENCES,DELETE,TRIGGER,TRUNCATE,MAINTAIN,UPDATE ON TABLE auth.sso_providers TO postgres;
GRANT SELECT ON TABLE auth.sso_providers TO postgres WITH GRANT OPTION;


--
-- Name: TABLE users; Type: ACL; Schema: auth; Owner: -
--

REVOKE ALL ON TABLE auth.users FROM postgres;
GRANT INSERT,REFERENCES,DELETE,TRIGGER,TRUNCATE,MAINTAIN,UPDATE ON TABLE auth.users TO postgres;
GRANT SELECT ON TABLE auth.users TO postgres WITH GRANT OPTION;


--
-- Name: TABLE ka_ad_reward_balances; Type: ACL; Schema: public; Owner: -
--

GRANT SELECT ON TABLE public.ka_ad_reward_balances TO authenticated;


--
-- Name: TABLE user_favorites; Type: ACL; Schema: public; Owner: -
--

GRANT ALL ON TABLE public.user_favorites TO authenticated;


--
-- Name: TABLE watch_history; Type: ACL; Schema: public; Owner: -
--

GRANT ALL ON TABLE public.watch_history TO authenticated;


--
-- PostgreSQL database dump complete
--

\unrestrict gZMwTbhkidVtZVLn5jmAZf2wFGmbRkLqQcDKUeblVXU5zfg0zJQu8kaeufLHKN4

