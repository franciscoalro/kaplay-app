-- KA Play: introspecção somente leitura para fechar contratos de sync.
-- Execute no SQL Editor do projeto iibureqvpurmbgqupnvl.
-- Não altera tabelas, funções, RLS ou dados.

-- 1) Funções alvo e relacionadas
select
  n.nspname as schema_name,
  p.proname as function_name,
  pg_get_function_identity_arguments(p.oid) as identity_arguments,
  pg_get_function_result(p.oid) as return_type,
  p.provolatile,
  p.prosecdef,
  coalesce(nullif(array_to_string(p.proconfig, ', '), ''), '') as config,
  pg_get_functiondef(p.oid) as definition
from pg_proc p
join pg_namespace n on n.oid = p.pronamespace
where n.nspname = 'public'
  and p.proname in (
    'get_avatar_catalog',
    'get_my_member_access',
    'sync_get_library_delta_cursor',
    'sync_pull_watched_items',
    'sync_pull_watch_progress',
    'sync_pull_library',
    'sync_get_watched_items_delta_cursor',
    'sync_pull_watched_items_delta',
    'sync_get_watch_progress_delta_cursor',
    'sync_pull_watch_progress_delta',
    'sync_push_library_items',
    'sync_delete_library_items',
    'sync_push_watched_items',
    'sync_delete_watched_items',
    'sync_push_watch_progress',
    'sync_delete_watch_progress',
    'get_member_profile_avatar_catalog'
  )
order by p.proname, identity_arguments;

-- 2) Tabelas e colunas candidatas
select
  c.table_schema,
  c.table_name,
  c.ordinal_position,
  c.column_name,
  c.data_type,
  c.udt_schema,
  c.udt_name,
  c.is_nullable,
  c.column_default
from information_schema.columns c
where c.table_schema = 'public'
  and c.table_name in (
    'ka_profiles',
    'user_favorites',
    'watch_history',
    'watch_progress',
    'library',
    'library_events',
    'watch_history_events',
    'watch_progress_events',
    'avatar_catalog',
    'membership',
    'member_access',
    'ka_ad_reward_balances',
    'ka_ad_reward_claims'
  )
order by c.table_name, c.ordinal_position;

-- 3) Constraints e relacionamentos
select
  tc.table_schema,
  tc.table_name,
  tc.constraint_name,
  tc.constraint_type,
  kcu.column_name,
  ccu.table_schema as foreign_table_schema,
  ccu.table_name as foreign_table_name,
  ccu.column_name as foreign_column_name
from information_schema.table_constraints tc
left join information_schema.key_column_usage kcu
  on kcu.constraint_schema = tc.constraint_schema
 and kcu.constraint_name = tc.constraint_name
 and kcu.table_schema = tc.table_schema
 and kcu.table_name = tc.table_name
left join information_schema.constraint_column_usage ccu
  on ccu.constraint_schema = tc.constraint_schema
 and ccu.constraint_name = tc.constraint_name
where tc.table_schema = 'public'
  and tc.table_name in (
    'ka_profiles', 'user_favorites', 'watch_history', 'watch_progress',
    'library', 'library_events', 'watch_history_events',
    'watch_progress_events', 'avatar_catalog', 'membership', 'member_access'
  )
order by tc.table_name, tc.constraint_name, kcu.ordinal_position;

-- 4) Índices
select
  schemaname,
  tablename,
  indexname,
  indexdef
from pg_indexes
where schemaname = 'public'
  and tablename in (
    'ka_profiles', 'user_favorites', 'watch_history', 'watch_progress',
    'library', 'library_events', 'watch_history_events',
    'watch_progress_events', 'avatar_catalog', 'membership', 'member_access'
  )
order by tablename, indexname;

-- 5) RLS e policies
select
  schemaname,
  tablename,
  rowsecurity,
  forcerowsecurity
from pg_tables
where schemaname = 'public'
  and tablename in (
    'ka_profiles', 'user_favorites', 'watch_history', 'watch_progress',
    'library', 'library_events', 'watch_history_events',
    'watch_progress_events', 'avatar_catalog', 'membership', 'member_access'
  )
order by tablename;

select
  schemaname,
  tablename,
  policyname,
  permissive,
  roles,
  cmd,
  qual,
  with_check
from pg_policies
where schemaname = 'public'
  and tablename in (
    'ka_profiles', 'user_favorites', 'watch_history', 'watch_progress',
    'library', 'library_events', 'watch_history_events',
    'watch_progress_events', 'avatar_catalog', 'membership', 'member_access'
  )
order by tablename, policyname;

-- 6) Grants de tabela e função
select grantee, table_schema, table_name, privilege_type
from information_schema.role_table_grants
where table_schema = 'public'
  and table_name in (
    'ka_profiles', 'user_favorites', 'watch_history', 'watch_progress',
    'library', 'library_events', 'watch_history_events',
    'watch_progress_events', 'avatar_catalog', 'membership', 'member_access'
  )
order by table_name, grantee, privilege_type;

select
  routine_schema,
  routine_name,
  specific_name,
  grantee,
  privilege_type
from information_schema.routine_privileges
where routine_schema = 'public'
  and routine_name in (
    'get_avatar_catalog',
    'get_my_member_access',
    'sync_get_library_delta_cursor',
    'sync_pull_watched_items',
    'sync_pull_watch_progress',
    'ka_claim_ad_reward'
  )
order by routine_name, specific_name, grantee;
