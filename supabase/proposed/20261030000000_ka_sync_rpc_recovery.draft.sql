-- KA Play sync RPC recovery — DRAFT ONLY
-- This file is intentionally NOT an executable migration.
-- Do not rename into supabase/migrations/ or apply to production until the
-- schema snapshot, ownership checks, RLS, and return contracts are reviewed.
-- No existing reward function is touched here.

begin;

do $$
begin
  raise exception using
    errcode = 'P0001',
    message = 'KA Play sync draft: obtain and review the authoritative table/column snapshot before applying';
end
$$;

-- Required final signatures, to be materialized only after replacing the
-- source-table contracts with the confirmed schema:
--
-- public.get_avatar_catalog()
--   returns setof (id text, display_name text, storage_path text,
--                 category text, sort_order integer, is_active boolean,
--                 bg_color text)
--
-- public.get_my_member_access()
--   returns setof (tier text, entitlements text[])
--
-- public.sync_get_library_delta_cursor(p_profile_id integer)
--   returns bigint
--
-- public.sync_pull_watched_items(
--   p_profile_id integer,
--   p_page integer,
--   p_page_size integer
-- )
--   returns setof (content_id text, content_type text, title text,
--                  season integer, episode integer, watched_at bigint)
--
-- public.sync_pull_watch_progress(
--   p_profile_id integer,
--   p_since_last_watched bigint default null,
--   p_limit integer default null
-- )
--   returns setof (content_id text, content_type text, video_id text,
--                  season integer, episode integer, position bigint,
--                  duration bigint, last_watched bigint, progress_key text)
--
-- Required security for each final function:
--   * SECURITY INVOKER unless a reviewed exception is documented.
--   * SET search_path = public (or a fixed, minimal schema list).
--   * GRANT EXECUTE only to authenticated; revoke from anon/public.
--   * Validate auth.uid() and ownership of p_profile_id through ka_profiles.
--   * Bound page/page_size/limit to prevent unbounded reads.
--   * Never mutate reward tables or ka_claim_ad_reward().
--
-- Required pre-application checks:
--   * Confirm profile_index is the remote profile key and maps to local profileId.
--   * Confirm source table columns, types, unique keys, foreign keys, indexes.
--   * Confirm RLS policies for every source table.
--   * Confirm existing related RPCs and avoid signature collisions.
--   * Confirm PostgREST schema cache refresh procedure.
--
-- The final implementation must be generated only after those checks.
rollback;
