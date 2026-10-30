# KA Play — Migração de sincronização (preparação)

Status: **rascunho bloqueado para execução**  
Projeto: `iibureqvpurmbgqupnvl`  
Nenhum SQL foi executado.

## Resultado desta etapa

Foi preparado o rascunho:

```text
supabase/proposed/20261030000000_ka_sync_rpc_recovery.draft.sql
```

Ele contém as assinaturas exatas exigidas pelo Kotlin, requisitos de segurança e preflight, mas aborta deliberadamente com exceção. Não é uma migration aplicável até que o snapshot administrativo das tabelas/colunas/policies seja incorporado.

## Contratos confirmados no Kotlin

| Função | Parâmetros | Retorno esperado |
|---|---|---|
| `get_avatar_catalog()` | nenhum | lista de `id`, `display_name`, `storage_path`, `category`, `sort_order`, `is_active`, `bg_color` |
| `get_my_member_access()` | nenhum | lista com `tier` e `entitlements: string[]` |
| `sync_get_library_delta_cursor(integer)` | `p_profile_id` | `bigint` escalar |
| `sync_pull_watched_items(integer, integer, integer)` | `p_profile_id`, `p_page`, `p_page_size` | lista de histórico com `content_id`, `content_type`, `title`, `season`, `episode`, `watched_at` |
| `sync_pull_watch_progress(integer, bigint?, integer?)` | `p_profile_id`, `p_since_last_watched`, `p_limit` | lista com `content_id`, `content_type`, `video_id`, `season`, `episode`, `position`, `duration`, `last_watched`, `progress_key` |

Os tipos acima refletem os DTOs serializáveis usados por:

```text
AvatarRepository.kt
MemberAccessRemoteDataSource.kt
SupabaseLibrarySyncAdapter.kt
SupabaseWatchedSyncAdapter.kt
SupabaseProgressSyncAdapter.kt
```

## Verificações realizadas

- Projeto Supabase confirmado pelo runtime do APK.
- As cinco funções foram confirmadas como fisicamente ausentes pelo diagnóstico administrativo informado.
- Migrations locais não contêm essas funções.
- `ka_profiles` usa `profile_index` e `user_id`; localmente o valor é transportado como `profileId`.
- Armazenamento local de biblioteca, histórico e progresso usa chaves por perfil:
  - `library_payload_<profileId>`
  - `watched_payload_<profileId>`
  - `watch_progress_payload_<profileId>`
- Recompensa é independente e usa `ka_claim_ad_reward`; não será alterada.
- O endpoint público retornou `HTTP 200` vazio para `user_favorites` e `watch_history` sem autenticação durante a sondagem. Isso não prova exposição de linhas, mas exige confirmação explícita das policies RLS antes de qualquer mudança.
- O endpoint retornou `PGRST205` para `watch_progress`; a estrutura remota de progresso não foi identificada com segurança por acesso público.

## Snapshot administrativo obtido

A introspecção somente leitura foi executada pelo endpoint de Management API do projeto, sem aplicar SQL. Resultado relevante:

- `user_favorites`: `user_id`, `media_id`, `title`, `poster_url`, `media_type`, `status`, `updated_at`.
- `watch_history`: `user_id`, `media_id`, `title`, `episode_num`, `season_num`, `position_sec`, `duration_sec`, `poster_url`, `updated_at`.
- Nenhuma das duas tabelas possui `profile_index`.
- Não existe tabela pública `watch_progress`.
- `ka_profiles` possui `user_id`, `profile_index` e índice único `(user_id, profile_index)`.
- Chaves atuais: favoritos `(user_id, media_id)`; histórico `(user_id, media_id, season_num, episode_num)`.
- RLS está habilitado nas tabelas; as policies atuais de favoritos/histórico autorizam por `auth.uid() = user_id`, mas não distinguem perfis.
- A única função entre as RPCs de recompensas pesquisadas que existe é `ka_claim_ad_reward(integer, uuid)`, `SECURITY DEFINER`; ela foi preservada.
- A consulta ampla por `sync_%` encontrou apenas `sync_pull_profiles()` e `sync_push_profiles(...)`; os RPCs relacionados de biblioteca, histórico e progresso também não estão presentes.
- Não foram encontradas tabelas públicas de catálogo de avatares ou membership necessárias para implementar `get_avatar_catalog()` e `get_my_member_access()` sem criar uma nova fonte de dados.

### Consequência

As três RPCs de sincronização de dados existentes podem ser preparadas apenas depois de uma migração de compatibilidade que acrescente o escopo de perfil às tabelas atuais. Sem essa etapa, qualquer RPC que aceite `p_profile_id` teria de compartilhar favoritos/histórico entre todos os perfis do mesmo usuário, contrariando o requisito de isolamento.

`sync_pull_watch_progress` também não pode ser implementada fielmente apenas com as colunas atuais: faltam `video_id`, `progress_key` e `content_type` no histórico. `position_sec`/`duration_sec` podem servir como origem de dados legados, mas não substituem esses campos sem uma decisão de contrato.

`get_avatar_catalog()` e `get_my_member_access()` permanecem bloqueadas por falta de tabelas-fonte e dados de negócio confirmados. Não será criado catálogo vazio nem entitlement fictício.

## Bloqueio para uma migration concreta

A confirmação de que as RPCs estão fisicamente ausentes agora foi complementada pelo snapshot real. Ainda faltam, por função/tabela:

- nome exato e tipo da coluna de perfil;
- vínculo entre `profileId` local e `profile_index` remoto;
- chaves naturais e constraints de unicidade;
- nomes das colunas de conteúdo, episódio e progresso;
- tabelas de eventos/cursor da biblioteca;
- policies RLS e grants atuais;
- índices existentes;
- funções relacionadas já existentes;
- estratégia de conflito aplicada pelos writes atuais.

Criar SQL concreto sem esses dados poderia produzir uma RPC que compila, mas mistura perfis, perde progresso recente ou retorna JSON incompatível. Por isso o arquivo SQL permanece bloqueado e não usa nomes inventados.

## Desenho obrigatório da migration final

Quando o snapshot for disponibilizado e revisado:

1. `BEGIN`/`COMMIT` em uma migration nova.
2. Preflight que falha se tabela, coluna, tipo ou constraint esperada não existir.
3. `SECURITY INVOKER` por padrão.
4. `search_path` fixo e mínimo.
5. `REVOKE EXECUTE` de `public`/`anon`; `GRANT EXECUTE` apenas a `authenticated`.
6. Validação de `auth.uid()` e associação de `p_profile_id` a `ka_profiles.user_id`.
7. Paginação limitada e rejeição de valores negativos/absurdamente grandes.
8. Cursor baseado em evento monotônico confirmado no schema.
9. Progress merge protegido por `last_watched`/versão, sem substituir dado mais novo por dado antigo.
10. Idempotência por chave natural/evento e `origin_client_id`, quando suportado pelo schema atual.
11. Nenhuma referência ou alteração em `ka_claim_ad_reward`, `ka_ad_reward_balances` ou `ka_ad_reward_claims`.
12. Refresh do schema cache apenas após commit aprovado.

## Rollback

- Não apagar tabelas, dados, policies ou migrations existentes.
- Remover somente as funções criadas pela nova migration, usando assinaturas exatas.
- Reverter grants adicionados pela migration.
- Restaurar a versão anterior do APK caso os contratos de retorno falhem.
- Não executar `DROP ... CASCADE`.
- Antes da aplicação, salvar snapshot de `pg_proc`, grants, policies, índices e contagens.

## Pronto para aplicação

- Contratos Kotlin documentados.
- Arquivo SQL draft criado e deliberadamente não executável.
- Requisitos de segurança e rollback definidos.
- Arquivo de testes criado em `TESTES_SYNC_KA_PLAY.md`.

## Ainda dependente do banco real

- Validar os nomes/tipos reais de todas as colunas.
- Validar RLS e grants com usuários de teste.
- Validar chamadas autenticadas e JSON real.
- Validar conflito entre dois dispositivos.
- Validar offline/reconexão e persistência após reabertura.
- Validar que recompensa continua operando sem qualquer alteração.

**Aguardando aprovação e snapshot administrativo antes de converter o draft em migration aplicável.**
