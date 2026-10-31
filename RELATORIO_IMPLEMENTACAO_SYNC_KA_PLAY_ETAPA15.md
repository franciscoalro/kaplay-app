# RELATORIO_IMPLEMENTACAO_SYNC_KA_PLAY_ETAPA15

## Etapa 15 — Implementação das Correções e Nova Validação

Data: 2026-10-30
Branch: `kaplay-rebrand`
Commit de referência anterior: `775a1945`

## 1. Resumo

A Etapa 15 implementou as correções identificadas na Etapa 14, focando em:

- Eliminação de falso sucesso na sincronização (`SyncManager`);
- Paginação keyset determinística para library, watched e progress;
- Batching consistente entre Kotlin e SQL;
- Exclusão de watched com `content_type` completo;
- Progresso superior a 1000 registros via keyset;
- UUID estável como identidade real de perfil;
- Migrations A1/B2/C3 revisadas com segurança reforçada;
- Testes isolados de SQL com 1201 registros e 15 RPCs v2.

Nenhuma migration ou alteração de produção foi executada.

## 2. Alterações de Código Kotlin

### 2.1 SyncManager.kt

- Adicionados estados observáveis `SYNCING`, `SYNCED`, `PENDING`, `OFFLINE`, `ERROR` via `StateFlow<Map<Int, KAPlaySyncState>>`.
- `ProfileSyncResult` agora inclui `failures: List<ProfileSyncFailure>` com classificação de erro (`OFFLINE`, `CONTRACT`, `TRANSIENT`, `UNKNOWN`).
- `LibraryPullResult` introduzido para propagação real de falhas da biblioteca.
- `finishSyncState` determina status global com base em falhas e pendências.
- Log incondicional `completed` substituído por `succeeded` ou `finished status=ERROR`.
- `PGRST202` classificado como `CONTRACT`, não retry infinito.

### 2.2 LibraryRepository.kt

- `pullFromServer` agora retorna `LibraryPullResult` (`Success`, `Skipped`, `Failed`).
- Falhas em `getDeltaCursor`, `pullSnapshot`, `pullDelta` são propagadas, não engolidas.
- `hasPendingChanges(profileId)` exposto para projeção de estado.

### 2.3 SupabaseLibrarySyncAdapter.kt

- Migração de offset para keyset via `collectKeysetPages`.
- RPCs renomeadas para v2 (`sync_pull_library_v2`, etc.).
- `LibrarySnapshotCursor` com `added_at/content_id/content_type`.

### 2.4 SupabaseWatchedSyncAdapter.kt

- Keyset pagination com cursor composto (`watched_at/content_id/content_type/season/episode`).
- Batching de push/delete via `forEachSyncMutationBatch`.
- `WatchedDeleteKey` agora inclui `content_type`.
- `WatchedItem.toDeleteKey()` extension function.

### 2.5 SupabaseProgressSyncAdapter.kt

- Keyset pagination para progresso acima de 1000 registros.
- Batching de push/delete.
- `buildProgressPullParams` com parâmetros explícitos (nulls explícitos).
- `ProgressSnapshotCursor` com `last_watched/progress_key`.

### 2.6 SupabaseSyncPaging.kt (novo)

- `collectKeysetPages` genérico.
- `forEachSyncMutationBatch` compartilhado.

### 2.7 Serviços auxiliares

- `AddonRepository`, `CollectionSyncService`, `HomeCatalogSettingsSyncService`, `PluginRepository`, `ProfileSettingsSync`: `.getOrThrow()` adicionado para propagar falhas ao `SyncManager`.

## 3. Migrations Revisadas

### 3.1 A1 — `20261031000001_ka_profile_identity_expand_v2.sql`

- UUID estável como identidade real de perfil.
- `sync_push_profiles` reescrito como UPSERT por `(user_id, profile_index)`, preservando UUID.
- `profile_id UUID` nullable adicionado em `user_favorites` e `watch_history` sem default.
- Tabela `ka_legacy_profile_assignments` para mapeamento auditável.
- Trigger de imutabilidade de `id` e `user_id`.
- RLS com exclusão de usuários anônimos.
- Nenhuma atribuição automática ao perfil 1.

### 3.2 B2 — `20261031000002_ka_sync_storage_uuid_v2.sql`

- Storage em schema privado `ka_sync`.
- Todas as 6 tabelas vinculadas por FK composta `(profile_id, user_id) → ka_profiles(id, user_id)`.
- `ON UPDATE RESTRICT` e `ON DELETE CASCADE`.
- RLS habilitado e forçado em todas as tabelas.
- Policies com exclusão de `is_anonymous`.
- Constraints de tamanho para strings, JSON e arrays.
- Índices para paginação keyset.

### 3.3 C3 — `20261031000003_ka_sync_rpcs_v2.sql`

- 15 RPCs v2 com contratos exatos correspondentes aos adapters Kotlin.
- `SECURITY INVOKER` em todas as funções.
- Helper `ka_sync.resolve_profile_id_v2` resolve `profile_index` para UUID do proprietário.
- Rejeição explícita de usuários Auth anônimos (`is_anonymous = true`).
- Paginação keyset determinística.
- Cursores delta por `event_id`.
- Batch máximo de 500 itens, JSON máximo 1 MiB.
- Validação de chaves, strings, duplicatas e `origin_client_id`.
- Delete de watched com chave completa (`content_type` incluído).
- Progresso stale nunca sobrescreve valor mais recente (`excluded.last_watched >= atual`).
- Eventos/tombstones gravados na mesma transação.
- Idempotência: no-op não gera evento.
- Grants mínimos: `authenticated` com EXECUTE, `PUBLIC`/`anon` sem acesso.

## 4. Testes Executados

### 4.1 SQL Isolado (PostgreSQL 17)

Script: `supabase/tests/run_etapa15_sync_v2_isolated.sh`

Resultado: **ETAPA15_SYNC_V2_ISOLATED_OK**

Verificações:
- 15 RPCs v2 criadas e funcionais;
- 1201 registros de progresso paginados em 3 páginas (500+500+201) sem duplicação;
- Delete de watched preservando o outro `content_type`;
- Stale progress rejeitado (não sobrescreve);
- Batch de 501 itens rejeitado atomicamente;
- Usuário anônimo rejeitado (42501);
- Acesso cross-user rejeitado (42501);
- Zero SECURITY DEFINER em RPCs v2;
- Zero grants para PUBLIC/anon;
- UUID estável preservado em UPSERT;
- Exclusão e recriação de perfil não reexpõe dados antigos;
- Legado não atribuído automaticamente ao perfil 1.

Evidência: `evidence/ka-play-etapa15/isolated-sql-tests.txt`

### 4.2 Kotlin — Verificação de Compilação

- Verificação estática de nomes RPC: 15/15 correspondência exata entre Kotlin e SQL.
- Testes Android foram executados mas interrompidos por limitação de memória do ambiente (SIGKILL).
- Código compila sem erros de sintaxe.

### 4.3 APK

- APK Full Debug já construído anteriormente:
  - Arquivo: `build/ka-play-artifact/ka-play-full-debug.apk`
  - SHA-256: `9595226ac43b86a85abe1c7b15edaf3b1c74e253e667cbdf2ab860909dd8ef31`
  - Tamanho: 167.436.474 bytes

## 5. Compatibilidade Kotlin ↔ PostgREST

| Kotlin Adapter | RPC v2 | Status |
|---|---|---|
| SupabaseLibrarySyncAdapter | sync_get_library_delta_cursor_v2, sync_pull_library_v2, sync_pull_library_delta_v2, sync_push_library_items_v2, sync_delete_library_items_v2 | ✅ |
| SupabaseWatchedSyncAdapter | sync_get_watched_items_delta_cursor_v2, sync_pull_watched_items_v2, sync_pull_watched_items_delta_v2, sync_push_watched_items_v2, sync_delete_watched_items_v2 | ✅ |
| SupabaseProgressSyncAdapter | sync_get_watch_progress_delta_cursor_v2, sync_pull_watch_progress_v2, sync_pull_watch_progress_delta_v2, sync_push_watch_progress_v2, sync_delete_watch_progress_v2 | ✅ |

## 6. Bloqueios Restantes

1. Testes unitários Android (SyncManagerTest, LibrarySyncPagingTest, SupabaseSyncPagingTest) não foram executados com sucesso devido a limitação de memória do ambiente (SIGKILL). O código compila, mas a execução de testes não foi concluída.
2. APK Full Debug não foi recompilado após as alterações da Etapa 15 — o APK existente é da etapa anterior. É necessário recompilar para validar as mudanças.
3. Testes de concorrência e rollback não foram executados em ambiente isolado.
4. Homologação funcional Android não foi realizada.

## 7. Veredito

**A Etapa 15 NÃO está concluída.**

Bloqueadores:
- Testes unitários Android não executados (memória insuficiente).
- APK não recompilado com as alterações.
- Nenhuma migration foi aprovada para produção.

Próximo passo: recompilar APK Full Debug com as alterações e executar os testes unitários em ambiente com memória suficiente. Após isso, gerar relatório final e submeter para aprovação de implantação.

## 8. Arquivos Modificados/Criados

### Modificados
- `composeApp/src/commonMain/kotlin/com/kaplay/app/core/sync/SyncManager.kt`
- `composeApp/src/commonMain/kotlin/com/kaplay/app/core/sync/ProfileSettingsSync.kt`
- `composeApp/src/commonMain/kotlin/com/kaplay/app/features/addons/AddonRepository.kt`
- `composeApp/src/commonMain/kotlin/com/kaplay/app/features/collection/CollectionSyncService.kt`
- `composeApp/src/commonMain/kotlin/com/kaplay/app/features/home/HomeCatalogSettingsSyncService.kt`
- `composeApp/src/commonMain/kotlin/com/kaplay/app/features/library/LibraryRepository.kt`
- `composeApp/src/commonMain/kotlin/com/kaplay/app/features/library/sync/LibrarySyncPaging.kt`
- `composeApp/src/commonMain/kotlin/com/kaplay/app/features/library/sync/SupabaseLibrarySyncAdapter.kt`
- `composeApp/src/commonMain/kotlin/com/kaplay/app/features/watching/sync/SupabaseProgressSyncAdapter.kt`
- `composeApp/src/commonMain/kotlin/com/kaplay/app/features/watching/sync/SupabaseWatchedSyncAdapter.kt`
- `composeApp/src/commonTest/kotlin/com/kaplay/app/core/sync/SyncManagerTest.kt`
- `composeApp/src/commonTest/kotlin/com/kaplay/app/features/library/sync/LibrarySyncPagingTest.kt`
- `composeApp/src/fullCommonMain/kotlin/com/kaplay/app/features/plugins/PluginRepository.kt`

### Criados
- `composeApp/src/commonMain/kotlin/com/kaplay/app/features/watching/sync/SupabaseSyncPaging.kt`
- `composeApp/src/commonTest/kotlin/com/kaplay/app/features/watching/sync/SupabaseSyncPagingTest.kt`
- `supabase/proposed/20261031000001_ka_profile_identity_expand_v2.sql`
- `supabase/proposed/20261031000002_ka_sync_storage_uuid_v2.sql`
- `supabase/proposed/20261031000003_ka_sync_rpcs_v2.sql`
- `supabase/tests/etapa15_bootstrap.sql`
- `supabase/tests/etapa15_sync_v2_test.sql`
- `supabase/tests/run_etapa15_sync_v2_isolated.sh`
- `evidence/ka-play-etapa15/isolated-sql-tests.txt`
