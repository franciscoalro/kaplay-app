# Testes de sincronização — KA Play

Status: **roteiro preparado; execução contra o banco real pendente**

## 1. Pré-condições

- Projeto de teste separado ou janela aprovada no projeto `iibureqvpurmbgqupnvl`.
- Dois usuários autenticados: `user_a` e `user_b`.
- Dois perfis por usuário: `profile_a1`, `profile_a2`, `profile_b1`.
- Dois clientes/dispositivos para testes de concorrência.
- Snapshot pré-teste de funções, grants, RLS, índices e contagens.
- Logs com tokens, cookies e payloads sensíveis mascarados.

Não usar `service_role` no APK e não desabilitar RLS.

## 2. Testes de contrato das RPCs

Para cada função, confirmar:

- nome e assinatura no `pg_proc`;
- schema `public`;
- `provolatile` compatível com leitura/escrita;
- `prosecdef = false` salvo justificativa aprovada;
- `search_path` fixo;
- `EXECUTE` para `authenticated`;
- ausência de `EXECUTE` para `anon`/`public`;
- retorno JSON compatível com os DTOs Kotlin.

Funções:

```text
get_avatar_catalog()
get_my_member_access()
sync_get_library_delta_cursor(integer)
sync_pull_watched_items(integer, integer, integer)
sync_pull_watch_progress(integer, bigint, integer)
```

### Casos negativos

- chamada sem JWT → negar;
- JWT de usuário A com `profileId` do usuário B → negar ou retornar conjunto vazio conforme contrato aprovado;
- `profileId` inexistente → resultado vazio/erro controlado, sem vazamento;
- página zero/negativa → rejeitar;
- `page_size`/`limit` acima do limite → rejeitar ou limitar de forma documentada;
- parâmetros nulos somente onde a assinatura permite.

## 3. Isolamento de usuários e perfis

1. Inserir favorito/histórico/progresso em `profile_a1`.
2. Ler com `profile_a1` autenticado como `user_a` → deve retornar o registro.
3. Ler com `profile_a2` autenticado como `user_a` → não deve retornar dados de `profile_a1` se o contrato for por perfil.
4. Ler com `profile_b1` autenticado como `user_b` → não deve retornar dados de `user_a`.
5. Alterar manualmente `p_profile_id` no request → não pode atravessar a fronteira de `auth.uid()`.
6. Repetir o teste via REST/PostgREST e via APK.

**Critério:** zero registros cruzados e zero mutações cruzadas.

## 4. Biblioteca/favoritos

- Pull inicial completo.
- Cursor inicial igual a zero.
- Inclusão de item e novo cursor monotônico.
- Exclusão de item e evento correspondente.
- Paginação sem duplicação nem salto.
- Dois pushes idênticos → estado final idempotente.
- Dois clientes adicionando itens diferentes → ambos preservados.
- Dois clientes alterando o mesmo item → política de conflito documentada e reproduzível.
- Fechar/reabrir APK → estado persiste.
- Trocar perfil → biblioteca correta, sem dados do perfil anterior.

## 5. Histórico assistido

- Inserir episódio com `content_id`, tipo, título, temporada, episódio e `watched_at`.
- Pull em página única.
- Pull em múltiplas páginas.
- Repetir push da mesma chave → sem duplicação.
- Marcar e remover episódio.
- Validar ordenação por `watched_at`.
- Verificar que um timestamp antigo não regride estado mais recente.
- Offline: manter histórico local.
- Reconectar: reconciliar sem apagar alterações locais confirmadas.

## 6. Progresso e retomada

- Salvar `position`, `duration`, `last_watched` e `progress_key`.
- Fechar e reabrir o app → posição local preservada.
- Dispositivo A grava posição mais nova; dispositivo B envia posição antiga → a mais nova deve prevalecer.
- Mesmo `progress_key` repetido → operação idempotente.
- `video_id` diferente para o mesmo conteúdo → não misturar progresso.
- `limit` e `since_last_watched` → sem duplicatas nem omissões.
- Episódio concluído → validar regra de retomada existente.
- Offline durante push → erro isolado; player e Home continuam operacionais.

## 7. Falha parcial e recuperação

- Derrubar a rede durante pull de biblioteca.
- Derrubar a rede durante histórico.
- Derrubar a rede durante progresso.
- Confirmar que `SyncManager` registra somente a etapa falha.
- Confirmar que addons, Home, player, autenticação e recompensa não são bloqueados.
- Reconectar e repetir sync → resultado convergente.
- Repetir sync várias vezes → sem duplicação.

## 8. Recompensa — teste de não regressão

Sem alterar as RPCs existentes:

- usuário autenticado consulta saldo;
- `ka_claim_ad_reward(p_profile_index, p_event_id)` continua aceitando evento válido;
- repetição do mesmo `event_id` é idempotente;
- usuário não autenticado é rejeitado;
- perfil inválido é rejeitado;
- limite diário permanece ativo;
- migration de sync não concede acesso às tabelas de recompensa;
- falha nas RPCs de histórico/progresso não remove saldo local nem bloqueia o botão.

## 9. Testes Kotlin/integração

Adicionar testes depois de fechar o schema real:

- decoder de cada DTO com JSON real do PostgREST;
- parâmetros opcionais de progresso;
- paginação e cursor;
- merge que preserva maior `last_watched`;
- idempotência de eventos;
- isolamento por `profileId` no storage local;
- `SyncManager` com falha isolada em cada etapa.

## 10. Evidências obrigatórias

Para cada teste registrar:

- usuário/perfil anonimizados;
- função chamada;
- request sem credenciais;
- status HTTP/Postgres;
- quantidade de linhas antes/depois;
- hash ou chave técnica do item, sem conteúdo privado;
- resultado esperado/obtido;
- log redigido;
- screenshot somente quando necessário.

## 11. Critérios de aprovação

A sincronização só será aprovada quando:

- as cinco RPCs responderem com contratos compatíveis;
- RLS impedir qualquer leitura/escrita cruzada;
- perfil local e perfil remoto estiverem mapeados explicitamente;
- progresso mais novo nunca for sobrescrito por antigo;
- pushes repetidos forem idempotentes;
- offline/reconexão convergir;
- recompensa passar sem alteração;
- build e APK passarem os fluxos de autenticação, biblioteca, histórico, progresso e troca de perfis.
