# Plano de correção definitiva da sincronização — KA Play

Data: 2026-10-30
Repositório: `franciscoalro/kaplay-app`
Branch: `kaplay-rebrand`
Commit da inspeção Android: `4cfbffed`
APK observado: `com.kaplay.app` `0.9.18` (`versionCode=191`)
Projeto Supabase: `iibureqvpurmbgqupnvl`

## 1. Escopo e veredito atual

Este documento prepara a correção, mas **não executa SQL, migration, DDL, alteração de RLS, alteração de Auth, recompensa ou escrita em produção**.

A inspeção da Etapa 13 confirmou que o APK chama RPCs de sincronização inexistentes no backend de produção e recebe `PGRST202`. A sincronização de biblioteca, histórico e progresso permanece **REPROVADA para homologação**.

Veredito deste plano: **PARCIAL — pronto para revisão técnica, não pronto para implantação**.

Fontes usadas:

- `RELATORIO_ADB_PRODUCAO_KA_PLAY.md`;
- `evidence/ka-play-prod-20261030/logcat-sanitized.txt`;
- contratos Kotlin em `composeApp/src/commonMain`;
- migrations locais propostas A/B/C;
- `supabase/proposed/INTROSPECCAO_SYNC_KA_PLAY.sql`.

## 2. Comparativo dos contratos RPC

| RPC esperada pelo APK | Parâmetros enviados pelo Kotlin | Retorno/uso esperado | Candidata local | Produção observada |
|---|---|---|---|---|
| `sync_get_library_delta_cursor` | `p_profile_id` | `bigint` | Migration C | `PGRST202` |
| `sync_pull_library` | `p_profile_id`, `p_limit`, `p_offset` | itens de biblioteca | Migration C | não capturada nesta janela |
| `sync_pull_library_delta` | `p_profile_id`, `p_since_event_id`, `p_limit` | eventos delta | Migration C | não capturada nesta janela |
| `sync_push_library_items` | `p_profile_id`, `p_items`, `p_origin_client_id` | void | Migration C | não testada em produção |
| `sync_delete_library_items` | `p_profile_id`, `p_keys`, `p_origin_client_id` | void | Migration C | não testada em produção |
| `sync_get_watched_items_delta_cursor` | `p_profile_id` | `bigint` | Migration C | não conclusivo na janela |
| `sync_pull_watched_items` | `p_profile_id`, `p_page`, `p_page_size` | itens assistidos | Migration C | `PGRST202` |
| `sync_pull_watched_items_delta` | `p_profile_id`, `p_since_event_id`, `p_limit` | eventos delta | Migration C | não conclusivo na janela |
| `sync_push_watched_items` | `p_profile_id`, `p_items`, `p_origin_client_id` | void | Migration C | não testada em produção |
| `sync_delete_watched_items` | `p_profile_id`, `p_keys`, `p_origin_client_id` | void | Migration C | não testada em produção |
| `sync_get_watch_progress_delta_cursor` | `p_profile_id` | `bigint` | Migration C | `PGRST202` |
| `sync_pull_watch_progress` | `p_profile_id`; parâmetros opcionais omitidos quando nulos | progresso | Migration C | `PGRST202` |
| `sync_pull_watch_progress_delta` | `p_profile_id`, `p_since_event_id`, `p_limit` | eventos delta | Migration C | não conclusivo na janela |
| `sync_push_watch_progress` | `p_profile_id`, `p_entries`, `p_origin_client_id` | void | Migration C | não testada em produção |
| `sync_delete_watch_progress` | `p_profile_id`, `p_keys`, `p_origin_client_id` | void | Migration C | não testada em produção |
| `get_avatar_catalog` | nenhum | catálogo padrão de avatares | não implementada em A/B/C | `PGRST202` |
| `get_my_member_access` | nenhum | uma linha `tier`, `entitlements[]` | não implementada em A/B/C | `PGRST202` |
| `get_member_profile_avatar_catalog` | nenhum, somente se houver entitlement | catálogo premium | não implementada em A/B/C | não conclusivo |

### Inventário global de RPCs Kotlin

A busca estática encontrou 46 nomes literais de RPC no código Kotlin. Apenas 17 possuem alguma definição SQL versionada neste checkout. Isso **não prova ausência em produção**; prova que o repositório ainda não contém uma fonte SQL completa e autoritativa para todos os contratos.

Além das RPCs de sync deste plano, faltam definições SQL versionadas no checkout para famílias de PIN/perfil, device login, watch party, collections, configurações de perfil, credenciais de provedores, catálogo Home, media servers, addons/plugins e membership. Elas devem permanecer fora da migration de recuperação de sync até a introspecção confirmar suas definições atuais. Não recriar funções existentes com contratos inferidos.

### Observação sobre assinatura de progresso

A migration C define `sync_pull_watch_progress(integer, bigint default null, integer default null)`, enquanto o Kotlin omite os parâmetros opcionais quando nulos. Antes da implantação, esse contrato precisa ser testado no PostgREST com o JSON exato do APK. A opção preferida é tornar a chamada explícita no Kotlin, enviando `p_since_last_watched: null` e `p_limit: null`, ou criar uma assinatura/wrapper inequívoco revisado no banco. Não deve haver overload ambíguo.

## 3. Migrations A/B/C e preservação de dados

### Migration A — risco de atribuição implícita

A versão local adiciona `profile_index integer not null default 1` em tabelas legadas. Isso não é seguro para produção sem prova de que todos os registros legados pertencem ao perfil 1.

Plano obrigatório antes de qualquer migration:

1. snapshot somente leitura do schema e contagens;
2. backup restaurável verificado;
3. inventário por `user_id`, chaves e timestamps;
4. classificação de registros sem perfil determinístico;
5. nenhuma atribuição automática a profile 1 sem evidência de origem;
6. tabela de exceções para registros órfãos, se existirem;
7. migração idempotente com `profile_index` nullable durante backfill, se necessário;
8. tornar obrigatório somente após todos os registros terem mapeamento auditado;
9. rollback documentado para cada etapa reversível.

### Migration B — storage/eventos

As tabelas `ka_*` são novas e isoladas, mas ainda exigem confirmação de:

- nomes que não colidam com tabelas existentes;
- ownership de `auth.users`;
- FK composta de `(user_id, profile_index)` para `ka_profiles`; sem ela, excluir e recriar o mesmo índice de perfil pode reexpor dados antigos órfãos;
- comportamento de cascade/restrict para exclusão de perfil, validado sem perda acidental;
- índices e retenção de eventos;
- RLS em todas as tabelas expostas;
- grants explícitos somente para `authenticated`;
- ausência de acesso `anon`/`PUBLIC`.

### Migration C — RPCs

A implementação local cobre três famílias de sync — library, watched e watch progress — incluindo cursores, delta, push e delete. Ela **ainda não está pronta para produção**. Uma revisão independente encontrou defeitos bloqueadores:

1. **Delete de watched incompatível:** a PK SQL inclui `content_type`, mas o DTO Kotlin de exclusão não o envia e a RPC apaga por `content_id/season/episode`. Isso pode apagar mais de uma linha ou tornar o `SELECT INTO` não determinístico. O contrato deve incluir `content_type` de ponta a ponta.
2. **Snapshot de progresso truncado:** o Kotlin realiza uma chamada sem `p_limit`; a RPC limita silenciosamente a 1000. É necessária paginação determinística ou snapshot completo com contrato explícito.
3. **Push sem lote consistente:** watched/progress podem enviar coleções acima de 1000, enquanto o SQL rejeita lotes maiores. O cliente deve lotear ou o contrato deve definir limite único.
4. **Deletes sem limites:** watched/progress não validam tipo JSON nem tamanho do batch.
5. **Paginação instável:** `ORDER BY` de library e watched não inclui toda a chave única; empates podem duplicar ou omitir linhas. Mesmo com desempate total, paginação por offset pode omitir itens sob mutações concorrentes. Usar keyset/cursor ou snapshot consistente, incluindo todos os componentes da chave.
6. **Payload sem limites:** faltam limites para strings, arrays, JSON total e `origin_client_id`.
7. **Segurança privilegiada:** todas as RPCs usam `SECURITY DEFINER`; RLS está habilitado sem policies e o acesso efetivo depende integralmente das funções.

Requisitos mínimos antes de nova validação:

- `auth.uid()` obrigatório e validação de ownership por `ka_profiles`;
- decisão explícita sobre usuários Supabase anônimos: a role JWT também é `authenticated`, então `GRANT TO authenticated` não os exclui sozinho;
- `SET search_path` fixo e mínimo;
- owner controlado e default privileges auditados;
- `REVOKE EXECUTE FROM PUBLIC` e grants mínimos;
- limites de paginação, batch, strings e JSON;
- paginação keyset/cursor ou snapshot consistente, com ordenação total e estável;
- FK de ownership entre storage/eventos e `ka_profiles`, com política segura para exclusão/recriação de perfil;
- nenhuma leitura ou escrita nas tabelas de recompensa;
- teste de stale `last_watched`;
- teste de idempotência e exclusão por chave completa.

## 4. Avatares e membro

`get_avatar_catalog` e `get_my_member_access` não podem receber respostas fictícias.

### `get_avatar_catalog`

Investigar separadamente:

- tabela/view fonte do catálogo padrão;
- campos exigidos pelo `AvatarCatalogItem`;
- semântica de `is_active`, `category`, `sort_order`, `bg_color`;
- política de leitura para usuários autenticados;
- se o catálogo já existe ou precisa ser criado por mudança de produto separada.

### `get_my_member_access`

Investigar separadamente:

- fonte oficial da assinatura/membership;
- contrato exato `tier` e `entitlements[]`;
- vínculo do usuário por `auth.uid()`;
- cache e validade do JWT;
- não usar `user_metadata` para autorização;
- não alterar recompensas nem `ka_claim_ad_reward`;
- usuário sem registro deve receber apenas o comportamento já definido pelo produto, nunca um entitlement inventado.

## 5. Correção proposta do SyncManager

Arquivo principal:

```text
composeApp/src/commonMain/kotlin/com/kaplay/app/core/sync/SyncManager.kt
```

O código atual tenta proteger o freshness com `ProfilePullFreshness.recordIfSuccessful`, mas essa proteção só funciona quando a falha chega ao `ProfileSyncResult`. `LibraryRepository.pullFromServer` captura `Throwable`, registra o erro e retorna normalmente; portanto, o `SyncManager` pode considerar a etapa bem-sucedida e avançar freshness mesmo após falha da biblioteca. Além disso, existe um log final incondicional:

```kotlin
if (!syncResult.succeeded) {
    log.w { "Activity sync incomplete ..." }
}
log.i { "Activity sync completed ..." }
```

Correção proposta, ainda não aplicada:

1. introduzir estado explícito por perfil:
   - `SYNCING`;
   - `SYNCED`;
   - `PENDING`;
   - `OFFLINE`;
   - `ERROR`;
2. publicar esse estado em `StateFlow` para a UI;
3. fazer cada serviço obrigatório retornar resultado estruturado (`Success`, `Offline`, `ContractError`, `TransientError`) ou propagar erro; nenhum serviço pode capturar falha e retornar `Unit` silenciosamente;
4. registrar `SYNCED` somente quando todas as etapas obrigatórias retornarem sucesso;
5. registrar `ERROR` quando etapa obrigatória falhar;
6. registrar `PENDING` quando houver dados locais não enviados;
7. registrar `OFFLINE` quando a falha for de transporte sem apagar dados locais;
8. não avançar freshness nem cursor de uma etapa que falhou;
9. classificar `PGRST202` como erro de contrato/configuração, não como retry infinito;
10. usar backoff limitado somente para erros transitórios;
11. manter a mensagem sanitizada com etapa, perfil e código HTTP, nunca token;
12. substituir o log `completed` por `succeeded` ou `finished status=ERROR`.

A exceção lançada em `refreshActiveWatchSource` deve continuar propagando o resultado incompleto. Library e demais serviços chamados por `ProfileSyncOperations` também precisam deixar a falha observável pelo `ProfileSyncResult`; corrigir somente o log não elimina o falso sucesso.

## 6. Estratégia de validação antes da produção

### Backend isolado

1. corrigir primeiro os candidatos A/B/C; não reutilizar a Migration C atual sem as correções bloqueadoras;
2. reaplicar as versões revisadas somente em banco isolado descartável;
3. adicionar contratos de avatar/membership somente com fontes reais identificadas;
4. testar chamadas com o JSON exatamente igual ao Kotlin;
5. testar mais de 1000 registros de progresso e empates nas chaves de ordenação;
6. testar delete de watched com mesmo `content_id` em `content_type` diferentes;
7. testar batches acima/abaixo do limite e payloads malformados;
8. testar dois usuários, quatro perfis e usuário Auth anônimo se esse recurso estiver habilitado;
9. testar RLS/ownership, perfil inexistente, stale-progress, idempotência e delete;
10. testar schema cache do PostgREST após criação das funções;
11. executar APK de integração, não APK BUILD ONLY;
12. confirmar logs sem `PGRST202` e sem falso sucesso;
13. confirmar UI `SYNCED` somente após todas as etapas obrigatórias.

### Produção

Somente após aprovação formal:

1. backup restaurável;
2. diff SQL revisado;
3. janela de implantação;
4. migration incremental;
5. refresh do schema cache;
6. smoke test somente leitura;
7. monitoramento de erros;
8. rollback conforme plano.

## 7. SQL preparado para revisão — não executar

O SQL de introspecção somente leitura já preparado é:

```text
supabase/proposed/INTROSPECCAO_SYNC_KA_PLAY.sql
```

Ele consulta funções, assinaturas, resultados, `prosecdef`, `search_path`, tabelas, constraints, índices, RLS, policies e grants. Deve ser executado somente após confirmar explicitamente o projeto e com saída sanitizada.

O rascunho deliberadamente não executável é:

```text
supabase/proposed/20261030000000_ka_sync_rpc_recovery.draft.sql
```

Ele contém apenas contratos e guardas de revisão; termina em `rollback` e não deve ser renomeado para migration.

Mapeamento dos candidatos locais:

```text
A: supabase/proposed/20261030000001_ka_profile_scope_local.sql
B: supabase/proposed/20261030000002_ka_sync_storage_local.sql
C: supabase/proposed/20261030000003_ka_sync_rpcs_local.sql
```

Existe também `20261030000001_ka_sync_profile_storage.draft.sql` com o mesmo prefixo temporal de A. Antes de promover qualquer arquivo, os nomes/timestamps devem ser normalizados para evitar ordem ambígua. A candidata C contém os bloqueios documentados neste plano e **não é o SQL final de produção**.

A sequência SQL de revisão, ainda não executada, é:

```sql
-- SOMENTE REVISÃO. NÃO EXECUTAR AUTOMATICAMENTE.
-- 1. Capturar o snapshot sem alterações:
-- \i supabase/proposed/INTROSPECCAO_SYNC_KA_PLAY.sql
--
-- 2. Comparar assinaturas com o contrato Kotlin.
-- 3. Revisar o SQL candidato A/B/C em banco isolado.
-- 4. Somente depois gerar migration final com supabase migration new.
-- 5. Em produção, não aplicar sem backup, aprovação e rollback.
```

Nenhum SQL final de produção é declarado pronto enquanto as fontes legadas de avatar, membership e perfil não forem identificadas e enquanto o mapeamento dos dados legados não for auditado.

## 8. Checklist de segurança

- [ ] projeto confirmado antes de qualquer conexão;
- [ ] nenhum `service_role` no APK;
- [ ] nenhum JWT ou header em logs;
- [ ] ownership baseado em `auth.uid()`;
- [ ] nunca usar `user_metadata` para autorização;
- [ ] RLS habilitado nas tabelas expostas;
- [ ] policies com ownership e papéis explicitamente revisados;
- [ ] decidir e testar acesso de usuários Auth anônimos, que também podem portar role `authenticated`;
- [ ] `UPDATE` com `USING` e `WITH CHECK` quando houver acesso direto por tabela;
- [ ] funções `SECURITY DEFINER` revisadas independentemente;
- [ ] owner das funções e default privileges controlados;
- [ ] `search_path` fixo;
- [ ] `REVOKE PUBLIC` e grants explícitos;
- [ ] limites de batch, paginação, strings, arrays, JSON total e `origin_client_id`;
- [ ] ordenação total e estável em todas as páginas;
- [ ] recompensas fora do escopo;
- [ ] backup restaurável comprovado;
- [ ] rollback ensaiado no isolado;
- [ ] schema cache atualizado somente após deploy aprovado.

## 9. Alterações realizadas nesta etapa

Nenhum código de produção, migration, schema, Auth, RLS ou recompensa foi alterado.

Foi criado somente este plano de correção. O APK de produção não foi reinstalado, limpo ou modificado.

## 10. Bloqueios restantes

1. Snapshot autorizado e sanitizado do schema de produção ainda não foi obtido.
2. Não se conhece a fonte oficial de avatar padrão.
3. Não se conhece a fonte oficial de membership/entitlements.
4. Migration A ainda não pode atribuir registros legados ao perfil 1.
5. Assinaturas de PostgREST precisam ser comparadas com payloads reais do APK.
6. Delete de watched precisa incorporar `content_type` de ponta a ponta.
7. Snapshot de progresso precisa de paginação acima de 1000 itens.
8. Push/delete precisam de batching, validação e limites coerentes.
9. Paginação precisa migrar de offset para keyset/cursor ou snapshot consistente e incluir toda a chave única.
10. Storage/eventos precisam de vínculo seguro com `ka_profiles` para impedir reexposição após exclusão/recriação de perfil.
11. Todos os serviços obrigatórios precisam propagar resultado ao `SyncManager`; Library atualmente engole falhas.
12. Política para usuários Auth anônimos ainda não foi definida/testada.
13. Correção de estado do `SyncManager` ainda não foi implementada.
14. Nenhuma migration foi aprovada para produção.

## 11. Próximo passo recomendado

Executar somente a introspecção read-only no projeto confirmado, salvar o snapshot sanitizado e revisar as assinaturas com o proprietário. Em paralelo, preparar testes unitários do `SyncManager` para garantir que falha obrigatória nunca produza status global de sucesso.

Depois disso, corrigir o backend isolado e o `SyncManager`, validar o APK de integração e somente então discutir uma implantação incremental em produção.
