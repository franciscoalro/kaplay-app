# KA Play — Plano de implementação de sincronização validado

Status: **preparado para revisão; nenhum SQL executado**  
Projeto Supabase: `iibureqvpurmbgqupnvl`  
APK de referência: `0.9.18` / `versionCode 191`

## 1. Resultado da validação administrativa

A introspecção foi executada somente para leitura pela Management API autenticada do projeto. O projeto respondeu `HTTP 200` e está `ACTIVE_HEALTHY`.

### Objetos confirmados

- `ka_profiles`
- `user_favorites`
- `watch_history`
- `ka_ad_reward_balances`
- `ka_ad_reward_events`
- estruturas auxiliares de recompensas

### Funções confirmadas

- `sync_pull_profiles()`
- `sync_push_profiles(...)`
- `ka_claim_ad_reward(integer, uuid)`

A busca ampla por `sync_%` não encontrou RPCs de biblioteca, histórico ou progresso. Portanto, não basta criar somente as três funções inicialmente destacadas: o adapter Kotlin também chama funções de snapshot, delta, push e delete que estão ausentes.

## 2. Contratos Kotlin validados

### Biblioteca

`SupabaseLibrarySyncAdapter` usa:

```text
sync_pull_library(p_profile_id, p_limit, p_offset)
sync_get_library_delta_cursor(p_profile_id) -> Long
sync_pull_library_delta(p_profile_id, p_since_event_id, p_limit)
sync_push_library_items(p_profile_id, p_items, p_origin_client_id)
sync_delete_library_items(p_profile_id, p_keys, p_origin_client_id)
```

Item JSON:

```json
{
  "content_id": "string",
  "content_type": "string",
  "name": "string",
  "poster": "string|null",
  "poster_shape": "POSTER|SQUARE|LANDSCAPE",
  "background": "string|null",
  "logo": "string|null",
  "description": "string|null",
  "release_info": "string|null",
  "imdb_rating": "number|null",
  "genres": ["string"],
  "addon_base_url": "string|null",
  "added_at": "long"
}
```

### Histórico

`SupabaseWatchedSyncAdapter` usa:

```text
sync_get_watched_items_delta_cursor(p_profile_id) -> Long
sync_pull_watched_items_delta(p_profile_id, p_since_event_id, p_limit)
sync_pull_watched_items(p_profile_id, p_page, p_page_size)
sync_push_watched_items(p_profile_id, p_items, p_origin_client_id)
sync_delete_watched_items(p_profile_id, p_keys, p_origin_client_id)
```

Item JSON:

```json
{
  "content_id": "string",
  "content_type": "string",
  "title": "string",
  "season": "integer|null",
  "episode": "integer|null",
  "watched_at": "long"
}
```

### Progresso

`SupabaseProgressSyncAdapter` usa:

```text
sync_get_watch_progress_delta_cursor(p_profile_id) -> Long
sync_pull_watch_progress_delta(p_profile_id, p_since_event_id, p_limit)
sync_pull_watch_progress(p_profile_id, p_since_last_watched?, p_limit?)
sync_push_watch_progress(p_profile_id, p_entries, p_origin_client_id)
sync_delete_watch_progress(p_profile_id, p_keys, p_origin_client_id)
```

Item JSON:

```json
{
  "content_id": "string",
  "content_type": "string",
  "video_id": "string",
  "season": "integer|null",
  "episode": "integer|null",
  "position": "long",
  "duration": "long",
  "last_watched": "long",
  "progress_key": "string"
}
```

A identidade local é:

```text
progressKey = contentId_s{season}e{episode}
```

ou `contentId` quando não há temporada/episódio. `video_id` identifica o vídeo reproduzível e não pode ser substituído silenciosamente por `media_id`.

## 3. Identidade de perfil

O mapeamento está confirmado:

```text
ProfileRepository.activeProfileId
        ↕
KAPlayProfile.profileIndex
        ↕
ka_profiles.profile_index
```

`ka_profiles` possui unicidade `(user_id, profile_index)` e limite de perfis de 1 a 6.

O armazenamento local já é separado por perfil:

```text
kaplay_library/library_payload_<profileId>
kaplay_watched/watched_payload_<profileId>
kaplay_watch_progress/watch_progress_payload_<profileId>
```

## 4. Incompatibilidade do schema atual

### Favoritos

`user_favorites` atualmente contém:

```text
user_id, media_id, title, poster_url, media_type, status, updated_at
```

Chave atual:

```text
(user_id, media_id)
```

Não há `profile_index`.

### Histórico

`watch_history` atualmente contém:

```text
user_id, media_id, title, episode_num, season_num,
position_sec, duration_sec, poster_url, updated_at
```

Chave atual:

```text
(user_id, media_id, season_num, episode_num)
```

Não há `profile_index`, `content_type`, `video_id` ou `progress_key`.

### Progresso

Não existe tabela pública `watch_progress`. `position_sec` e `duration_sec` no histórico não carregam a identidade `video_id` exigida pelo contrato Kotlin.

### Consequência

Não é seguro criar as RPCs diretamente sobre `user_favorites`/`watch_history`:

- perfis do mesmo usuário compartilhariam dados;
- biblioteca exige campos que favoritos não possui;
- histórico não consegue reconstruir `video_id` com exatidão;
- progresso antigo poderia ser sobrescrito ou associado ao vídeo errado.

## 5. Migrations propostas

As migrations abaixo são **propostas de desenho**, não foram aplicadas. O draft bloqueado existente permanece em:

```text
supabase/proposed/20261030000000_ka_sync_rpc_recovery.draft.sql
```

### Migration A — escopo por perfil

Nome proposto:

```text
20261030000001_ka_profile_scope_legacy_data.sql
```

Operações:

1. Criar backup lógico verificável antes da migration.
2. Confirmar que os registros atuais representam o perfil original e devem ir para `profile_index = 1`.
3. Adicionar `profile_index integer not null default 1` em `user_favorites` e `watch_history`.
4. Validar faixa `1..6` e existência do perfil correspondente em `ka_profiles`.
5. Recriar chaves únicas como:
   - favoritos: `(user_id, profile_index, media_id)`;
   - histórico: `(user_id, profile_index, media_id, season_num, episode_num)`.
6. Adicionar índices por `(user_id, profile_index, updated_at)`.
7. Recriar policies com alvo `authenticated`, ownership por `auth.uid()`, sem ampliar acesso.
8. Revogar acesso direto de `anon`; manter RLS habilitado.

A atribuição automática ao perfil 1 permanece **condicionada à confirmação do proprietário do produto**. A migration não deve mover dados para outro perfil sem essa confirmação.

### Migration B — armazenamento de sincronização

Nome proposto:

```text
20261030000002_ka_sync_storage.sql
```

Por causa da incompatibilidade dos campos legados, a opção segura é criar armazenamento dedicado e não corromper as tabelas antigas:

- `ka_library_items`
- `ka_library_events`
- `ka_watched_items`
- `ka_watched_events`
- `ka_watch_progress`
- `ka_watch_progress_events`

Todas devem conter, no mínimo:

```text
user_id uuid not null
profile_index integer not null
```

E devem ter unicidade composta por usuário, perfil e identidade lógica. O progresso deve usar `progress_key` como chave lógica e preservar `video_id`, `position`, `duration` e `last_watched`.

A migração de histórico legado não deve inventar `video_id`. Registros antigos sem identidade reproduzível devem permanecer no histórico legado e ser marcados para reconciliação, ou receber uma política de fallback aprovada separadamente.

### Migration C — RPCs de sincronização

Nome proposto:

```text
20261030000003_ka_sync_rpc_functions.sql
```

Deve criar o conjunto completo usado pelos adapters, não apenas três funções isoladas:

- biblioteca: snapshot, cursor, delta, push e delete;
- histórico: snapshot, cursor, delta, push e delete;
- progresso: snapshot, cursor, delta, push e delete.

Requisitos:

- `SECURITY INVOKER` se as tabelas forem acessíveis somente pelo contexto seguro aprovado;
- caso o acesso direto seja revogado e as funções precisem operar sobre tabelas privadas, `SECURITY DEFINER` poderá ser usado somente com justificativa, `search_path` fixo, `auth.uid()` obrigatório e grants mínimos;
- nunca `GRANT EXECUTE TO PUBLIC`;
- nunca aceitar `p_profile_id` sem verificar a associação em `ka_profiles`;
- limitar paginação e tamanho de lote;
- usar eventos monotônicos para cursor/delta;
- usar upsert idempotente;
- rejeitar atualização de progresso com `last_watched` mais antigo;
- registrar tombstones para deletes;
- manter `origin_client_id` para evitar eco de eventos.

As funções de recompensa não fazem parte dessa migration.

## 6. RLS e privilégios

Estado confirmado:

- RLS habilitado em `ka_profiles`, `user_favorites`, `watch_history` e saldo de recompensas.
- `ka_profiles`: policy autenticada com `auth.uid() = user_id`.
- favoritos/histórico: policy atual usa role `public` e `auth.uid() = user_id`; deve ser revisada para `authenticated` e grants mínimos.
- recompensas: leitura autenticada do próprio `user_id`; não alterar.

Para o novo armazenamento:

- RLS habilitado antes de qualquer grant.
- acesso direto a tabelas de sync não deve ser exposto ao `anon`.
- se o acesso direto permitir leitura de todos os perfis do mesmo usuário, revogar Data API direto e usar RPCs validadas por `p_profile_id`.
- nenhuma policy deve confiar em `user_metadata` ou em claims editáveis.

## 7. Dados legados, duplicidade e backup

Antes da Migration A:

1. Exportar `user_favorites` e `watch_history` com hash e contagem.
2. Verificar duplicidades pelas chaves atuais.
3. Verificar registros órfãos em relação a `auth.users`.
4. Confirmar que todos os registros pertencem ao perfil original 1.
5. Registrar contagem por usuário.
6. Executar a migration apenas em transação após backup verificável.
7. Comparar contagem e hashes pós-migration.

A migration deve abortar se encontrar duplicidades incompatíveis ou perfis inexistentes.

## 8. Testes planejados

### Isolamento

- usuário A não lê/escreve dados de B;
- perfil A1 não lê/escreve dados de A2;
- `p_profile_id` adulterado não atravessa ownership;
- `anon` não executa RPC nem lê tabela;
- troca de perfil recarrega namespace correto.

### Favoritos/biblioteca

- snapshot vazio e com itens;
- push repetido idempotente;
- delete gera tombstone;
- cursor monotônico;
- delta sem duplicatas;
- dois clientes adicionando itens diferentes preservam ambos;
- conflito do mesmo item segue regra definida.

### Histórico

- paginação completa;
- marcação e remoção;
- episódios com e sem temporada;
- dados antigos preservados;
- perfil 1 recebe legado somente após confirmação;
- histórico de A1 não aparece em A2.

### Progresso

- `video_id` e `progress_key` distintos;
- posição mais nova vence;
- update antigo não regride posição;
- mesma chave repetida é idempotente;
- retomada após reabrir app;
- offline → push após reconexão;
- delete por `progress_key` não remove outro vídeo.

### Não regressão

- login e troca de perfil;
- RPCs de perfil existentes;
- recompensas e `ka_claim_ad_reward` sem alteração;
- Home, player e catálogo continuam funcionando quando sync falha;
- reinstall/reopen preserva dados conforme política do produto.

## 9. Backup e rollback

### Backup

- snapshot DDL de funções, policies, grants e índices;
- export lógico das quatro tabelas afetadas;
- contagens e hashes por `user_id`;
- backup separado das estruturas de recompensa;
- teste de restauração em ambiente isolado.

### Rollback

- rollback de functions por assinatura exata;
- remoção apenas de tabelas novas criadas pela migration, se estiverem vazias ou restauradas;
- reversão de grants/policies criados pelo conjunto;
- nunca `DROP ... CASCADE`;
- nunca apagar ou reclassificar dados legados automaticamente;
- APK só deve ativar adapters após a validação do schema.

## 10. Avatares

### Confirmado

- `get_avatar_catalog()` é chamado sem parâmetros.
- O retorno exige `id`, `display_name`, `storage_path`, `category`, `sort_order`, `is_active` e `bg_color`.
- O cliente monta URLs públicas no bucket `avatars` quando recebe `storage_path`.
- O repositório possui cache local.

### Pendente

- Não há tabela pública de catálogo de avatares na introspecção.
- Não foi encontrada fonte oficial de registros para preencher a função.
- Não criar tabela vazia ou catálogo artificial nesta etapa.

**Resultado:** RPC não pronta para implementação; requer decisão sobre fonte oficial.

## 11. Membership

### Confirmado

- `MemberAccessRemoteDataSource` espera `tier` e `entitlements`.
- Recursos dependentes: temas, avatares e backgrounds premium.
- Recompensas não dependem de `get_my_member_access`.

### Pendente

- Não foi encontrada tabela pública de membership/autorização na introspecção.
- Não é possível conceder entitlement por fallback local.
- Não alterar `ka_claim_ad_reward` nem estruturas de recompensa.

**Resultado:** RPC não pronta para implementação; requer identificação do sistema de assinatura/autorização.

## 12. Classificação

| Item | Resultado |
|---|---|
| `profileId` local ↔ `profile_index` remoto | **CONFIRMADO** |
| Separação local por perfil | **CONFIRMADO** |
| Dados legados em favoritos/histórico | **CONFIRMADO** |
| Escopo remoto atual por perfil | **AUSENTE** |
| Migração automática para perfil 1 | **PENDENTE DE CONFIRMAÇÃO DO PRODUTO** |
| Progresso com `video_id`/`progress_key` | **AUSENTE NO SCHEMA ATUAL** |
| RPCs completas de sync | **AUSENTES** |
| Segurança por usuário | **PARCIALMENTE CONFIRMADA** |
| Segurança por perfil | **PENDENTE** |
| Recompensa existente | **CONFIRMADA E PRESERVADA** |
| Fonte de avatares | **NÃO VERIFICÁVEL/AUSENTE** |
| Fonte de membership | **NÃO VERIFICÁVEL/AUSENTE** |
| Banco de produção alterado | **NÃO** |
| Redesign visual iniciado | **NÃO** |

## 13. Decisões ainda necessárias

1. Confirmar formalmente que registros legados devem ser atribuídos ao perfil 1.
2. Aprovar criação de armazenamento dedicado de biblioteca, histórico e progresso, sem apagar tabelas legadas.
3. Definir política para registros antigos sem `video_id`.
4. Identificar a fonte oficial de catálogo de avatares.
5. Identificar a fonte oficial de membership.
6. Aprovar a migration concreta após revisão de segurança e backup.

Até essas decisões, os SQLs continuam somente como drafts e nenhuma alteração será executada no banco.
