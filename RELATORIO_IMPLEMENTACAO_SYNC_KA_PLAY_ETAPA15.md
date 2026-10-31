# KA Play — Relatório de Implementação Etapa 15

## Resumo Executivo

A Etapa 15 implementou as correções críticas identificadas na Etapa 14, focando em:
- Eliminação de falso sucesso no SyncManager
- Paginação keyset sem perda de dados
- Batching consistente entre Kotlin e SQL
- Delete de watched com chave completa (content_type)
- Progresso >1000 registros
- Migrations A1/B2/C3 com UUID estável de perfil

**Status: Implementação concluída. Compilação local bloqueada por policy VPS (GitHub Actions obrigatório). Testes SQL isolados validados com sucesso.**

## Alterações Kotlin

### SyncManager.kt
- Adicionado `KAPlaySyncState` com estados `SYNCING`, `SYNCED`, `PENDING`, `OFFLINE`, `ERROR`
- `ProfileSyncResult` agora inclui `failures: List<ProfileSyncFailure>` com kind e code
- `profileSyncStateFor()` garante que falhas impedem `SYNCED`
- `publishSyncState()` expõe `StateFlow<Map<Int, KAPlaySyncState>>`
- Corrigido log incondicional `completed` → `succeeded`/`finished status=...`
- `LibraryRepository.pullFromServer()` agora retorna `LibraryPullResult` (Success/Skipped/Failed)
- Todos os serviços obrigatórios propagam falhas via `.getOrThrow()`

### LibraryRepository.kt
- `LibraryPullResult` sealed interface (Success, Skipped, Failed)
- `pullFromServer()` retorna resultado estruturado em vez de engolir exceções
- `hasPendingChanges(profileId)` exposto para o SyncManager

### SupabaseLibrarySyncAdapter.kt
- Paginação convertida de offset para keyset (`collectKeysetPages`)
- RPCs renomeadas para v2

### SupabaseWatchedSyncAdapter.kt
- Paginação keyset
- `WatchedDeleteKey` agora inclui `content_type`
- `WatchedItem.toDeleteKey()` helper
- Push/delete com batching (`forEachSyncMutationBatch`)

### SupabaseProgressSyncAdapter.kt
- Paginação keyset para >1000 registros
- Push/delete com batching
- `buildProgressPullParams()` com parâmetros nulos explícitos
- RPCs renomeadas para v2

### LibrarySyncPaging.kt
- Adicionado `collectKeysetPages()` e `forEachSyncMutationBatch()`

## Migrations SQL

### A1: `20261031000001_ka_profile_identity_expand_v2.sql`
- UUID estável: UPSERT preserva `ka_profiles.id`
- Legado: `profile_id UUID NULL` sem default automático
- Tabela `ka_legacy_profile_assignments` para mapeamento auditado
- Políticas RLS com verificação de anonymous auth

### B2: `20261031000002_ka_sync_storage_uuid_v2.sql`
- Schema `ka_sync` privado
- 6 tabelas com FK composta `(profile_id, user_id) → ka_profiles(id, user_id)`
- `ON UPDATE RESTRICT ON DELETE CASCADE`
- RLS enabled + forced
- Checks de domínio (tamanho, ranges)

### C3: `20261031000003_ka_sync_rpcs_v2.sql`
- 15 RPCs SECURITY INVOKER
- Resolução de profile_index para UUID
- Rejeição de anonymous auth
- Paginação keyset determinística
- Batch ≤500, JSON ≤1MiB
- Delete watched com content_type
- Stale progress protection (WHERE excluded.last_watched >= current)
- Eventos/tombstones na mesma transação
- Idempotência (no-op não gera evento)
- Grants mínimos, revoke PUBLIC/anon

## Testes

### Kotlin (novos)
- `SyncManagerTest`: falha de Library estruturada, estados nunca SYNCED após falha
- `SupabaseSyncPagingTest`: keyset >1000, batching, cursor que não avança, delete com content_type, parâmetros nulos explícitos

### SQL Isolado (validado)
- Arquivo: `supabase/tests/run_etapa15_sync_v2_isolated.sh`
- Resultado: `ETAPA15_SYNC_V2_ISOLATED_OK` (1201 progress rows, 15 v2 RPCs)
- Testes:
  - UUID estável no upsert
  - Sem atribuição automática de legado
  - Sem reexposição ao recriar perfil
  - Delete watched preserva outro content_type
  - Paginação keyset 1201 registros sem perda/duplicação
  - Stale progress rejeitado
  - Batch >500 rejeitado atomicamente
  - Anonymous auth rejeitado (42501)
  - Cross-user rejeitado (42501)
  - Zero SECURITY DEFINER
  - Zero PUBLIC/anon execute

## Bloqueios

1. Compilação local bloqueada por policy VPS (GitHub Actions obrigatório)
2. Homologação Android não executada
3. APK Full Debug não gerado
4. Testes Kotlin unitários não executados (processos killed por OOM)

## Próximos Passos

1. Disparar GitHub Actions para compilação e testes
2. Validar APK gerado
3. Executar testes de homologação
4. Avaliar pacote de implantação em produção
