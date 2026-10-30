# Plano de correção da sincronização — KA Play

Status: **aguardando aprovação — nenhuma alteração SQL executada**  
Projeto Supabase: `iibureqvpurmbgqupnvl`  
APK/runtime de referência: `0.9.18` / `versionCode 191`

## 1. Diagnóstico executivo

O cliente Kotlin chama cinco RPCs que o endpoint PostgREST remoto respondeu como `HTTP 404 / PGRST202`: a função não foi encontrada no schema cache `public`.

A evidência não permite distinguir sem acesso administrativo entre:

1. função inexistente no banco;
2. função criada em outro schema;
3. função existente, mas não publicada no cache do PostgREST;
4. assinatura diferente da chamada Kotlin.

As migrations versionadas locais não declaram nenhuma das cinco funções. Elas declaram apenas as estruturas de perfis e recompensas observadas no checkout.

**Regra de segurança:** não criar funções, executar SQL em produção, modificar RLS, apagar dados ou substituir RPCs antes da aprovação deste plano e da confirmação administrativa do schema real.

## 2. Inventário das RPCs

### 2.1 `get_avatar_catalog`

**Status:** confirmado como chamada do app; ausente no schema cache remoto; dependência de tabela remota pendente.

- Chamada: `AvatarRepository.fetchStandardCatalog()`.
- Arquivo: `composeApp/src/commonMain/kotlin/com/kaplay/app/features/profiles/AvatarRepository.kt`.
- Parâmetros: nenhum.
- Decodificação esperada: lista JSON de `AvatarCatalogItem`.
- Campos esperados:
  - `id: string`
  - `display_name: string`
  - `storage_path: string`
  - `category: string`
  - `sort_order: integer`
  - `is_active: boolean`
  - `bg_color: string|null`
- Pós-processamento: filtra `is_active`, ordena por `category` e `sort_order`, salva cache local.
- Dependência funcional: catálogo padrão de avatares. O acesso de membro também controla catálogo adicional por outra RPC (`get_member_profile_avatar_catalog`).

**Alternativa existente:** não localizada para o catálogo padrão. O cache local é fallback, não substitui a primeira sincronização.

### 2.2 `get_my_member_access`

**Status:** confirmado como chamada; ausente no schema cache remoto; impacto em cosméticos de membro confirmado; dependência direta do botão de recompensa não confirmada.

- Chamada: `MemberAccessRemoteDataSource.getMemberAccess()`.
- Arquivo: `composeApp/src/commonMain/kotlin/com/kaplay/app/features/membership/MemberAccessRemoteDataSource.kt`.
- Parâmetros: nenhum.
- Decodificação esperada: primeira linha de uma lista JSON:
  ```json
  {
    "tier": "SUPPORTER|SUPPORTER_PLUS",
    "entitlements": ["GOLD_THEME", "PROFILE_AVATARS", "PROFILE_BACKGROUNDS"]
  }
  ```
- Pós-processamento: valores desconhecidos são ignorados; ausência de linha vira `MemberAccess.None`.
- Dependências: temas, avatares e backgrounds de membro.
- A Home/recompensa usa `AdRewardRepository` e a RPC existente `ka_claim_ad_reward`; não deve ser substituída por esta função.

**Alternativa existente:** cache de acesso no `MemberAccessRepository`; é fallback temporário e não confirma permissões atuais.

### 2.3 `sync_get_library_delta_cursor`

**Status:** confirmado como chamada; ausente no schema cache remoto; bloqueia sincronização incremental da Minha lista.

- Chamada: `SupabaseLibrarySyncAdapter.getDeltaCursor(profileId)`.
- Arquivo: `composeApp/src/commonMain/kotlin/com/kaplay/app/features/library/sync/SupabaseLibrarySyncAdapter.kt`.
- Parâmetro:
  ```json
  { "p_profile_id": integer }
  ```
- Retorno esperado: escalar `bigint`/`long`, decodificado por `decodeAs<Long>()`.
- Alternativa: `sync_pull_library` faz snapshot paginado, mas o adapter usa o cursor para o caminho incremental. Não substituir sem medir custo e sem confirmar contratos existentes.

### 2.4 `sync_pull_watched_items`

**Status:** confirmado como chamada; ausente no schema cache remoto; bloqueia pull do histórico remoto.

- Chamada: `SupabaseWatchedSyncAdapter.pull(profileId, pageSize)`.
- Arquivo: `composeApp/src/commonMain/kotlin/com/kaplay/app/features/watching/sync/SupabaseWatchedSyncAdapter.kt`.
- Parâmetros:
  ```json
  {
    "p_profile_id": integer,
    "p_page": integer,
    "p_page_size": integer
  }
  ```
- Retorno: lista JSON com:
  ```json
  {
    "content_id": string,
    "content_type": string,
    "title": string,
    "season": integer|null,
    "episode": integer|null,
    "watched_at": long
  }
  ```
- Paginação termina quando o tamanho retornado é menor que `p_page_size`.
- Operações relacionadas que também precisam ser verificadas: `sync_get_watched_items_delta_cursor`, `sync_pull_watched_items_delta`, `sync_push_watched_items` e `sync_delete_watched_items`.

### 2.5 `sync_pull_watch_progress`

**Status:** confirmado como chamada; ausente no schema cache remoto; bloqueia retomada/progresso remoto.

- Chamada: `SupabaseProgressSyncAdapter.pull(profileId, sinceLastWatched, limit)`.
- Arquivo: `composeApp/src/commonMain/kotlin/com/kaplay/app/features/watching/sync/SupabaseProgressSyncAdapter.kt`.
- Parâmetros:
  ```json
  {
    "p_profile_id": integer,
    "p_since_last_watched": long|null,
    "p_limit": integer|null
  }
  ```
- Retorno: lista JSON com:
  ```json
  {
    "content_id": string,
    "content_type": string,
    "video_id": string,
    "season": integer|null,
    "episode": integer|null,
    "position": long,
    "duration": long,
    "last_watched": long,
    "progress_key": string
  }
  ```
- Operações relacionadas que também precisam ser verificadas: `sync_get_watch_progress_delta_cursor`, `sync_pull_watch_progress_delta`, `sync_push_watch_progress` e `sync_delete_watch_progress`.

## 3. Favoritos, histórico, progresso e perfis

### Armazenamento local confirmado

O cliente separa os dados por `profileId` em SharedPreferences:

- `kaplay_library` → `library_payload_<profileId>`
- `kaplay_watched` → `watched_payload_<profileId>`
- `kaplay_watch_progress` → `watch_progress_payload_<profileId>`

Os adaptadores remotos também recebem `p_profile_id` explicitamente. Isso reduz o risco de mistura local, mas não prova isolamento no banco.

### Armazenamento remoto

As tabelas informadas (`user_favorites`, `watch_history` e estruturas de progresso) não possuem migrations correspondentes neste checkout. O schema, chaves, foreign keys, índices, RLS e nomes exatos de colunas precisam ser obtidos por introspecção administrativa somente leitura.

**Classificação:** pendente / não verificável com a chave pública.

### Sobrescrita entre perfis

- Localmente, o namespace inclui o `profileId`.
- Remotamente, o cliente envia o `profileId`, mas a autorização efetiva depende das RPCs e políticas.
- A migration de `ka_profiles` usa `auth.uid()` para limitar perfis do usuário, mas não cobre as tabelas de favoritos/histórico/progresso.

**Risco:** alto se uma RPC aceitar `p_profile_id` pertencente a outro usuário sem validar a associação.

**Regra de implementação:** cada RPC de leitura/escrita deve validar, dentro da mesma transação, que o perfil pertence a `auth.uid()`. RLS deve permanecer habilitado; nenhuma função deve confiar apenas no `profileId` enviado pelo cliente.

## 4. Recompensas e não regressão

A recompensa não depende de `get_my_member_access`.

O fluxo existente usa:

- tabela `ka_ad_reward_balances`;
- RPC `ka_claim_ad_reward(p_profile_index integer, p_event_id uuid)`;
- leitura autenticada da própria linha de saldo;
- função `SECURITY DEFINER` com `search_path = public`, validação de autenticação, perfil e idempotência por `event_id`.

**Decisão:** não alterar essas RPCs nem suas tabelas/policies neste plano. A correção de sincronização deve ser independente do módulo de recompensas.

## 5. Projeto técnico preliminar

### 5.1 Ordem de confirmação antes do SQL

1. Obter dump/introspecção somente leitura de:
   - `pg_proc`/`pg_namespace` para todas as assinaturas;
   - `information_schema.routine_privileges`;
   - colunas, tipos, chaves e índices das tabelas;
   - policies RLS e grants.
2. Comparar com os contratos acima.
3. Confirmar se há funções equivalentes com nomes diferentes.
4. Confirmar se o cache PostgREST está desatualizado.
5. Somente então escolher entre refresh de schema cache, migration de função ausente ou adaptação do cliente.

### 5.2 SQL preliminar — não executável ainda

Os blocos abaixo são contratos de referência, não migrations prontas. Os nomes de tabelas/colunas e os `returns` só podem ser preenchidos após introspecção. Não aplicar estes blocos diretamente.

```sql
-- PLACEHOLDER: revisar tabela de catálogo e retorno real antes de gerar migration.
-- Não executar.
create or replace function public.get_avatar_catalog()
returns setof public.<avatar_catalog_table>
language sql
stable
security invoker
set search_path = public
as $$
  select id, display_name, storage_path, category, sort_order, is_active, bg_color
  from public.<avatar_catalog_table>
  where is_active = true
  order by category, sort_order;
$$;
-- grant execute on function public.get_avatar_catalog() to authenticated;
```

```sql
-- PLACEHOLDER: fonte de membership e nomes de entitlement ainda pendentes.
-- Não executar.
create or replace function public.get_my_member_access()
returns table (tier text, entitlements text[])
language sql
stable
security invoker
set search_path = public
as $$
  select <tier_expression>, <entitlements_expression>
  from public.<membership_table>
  where user_id = (select auth.uid());
$$;
-- grant execute on function public.get_my_member_access() to authenticated;
```

```sql
-- PLACEHOLDER: nomes e chaves da tabela de eventos ainda pendentes.
-- Não executar.
create or replace function public.sync_get_library_delta_cursor(p_profile_id integer)
returns bigint
language sql
stable
security invoker
set search_path = public
as $$
  select coalesce(max(event_id), 0)::bigint
  from public.<library_events_table> e
  where e.profile_id = p_profile_id
    and exists (
      select 1 from public.ka_profiles p
      where p.profile_index = p_profile_id
        and p.user_id = (select auth.uid())
    );
$$;
```

```sql
-- PLACEHOLDER: validar tabela, paginação, colunas e autorização antes de gerar migration.
-- Não executar.
create or replace function public.sync_pull_watched_items(
  p_profile_id integer,
  p_page integer,
  p_page_size integer
)
returns setof record
language sql
stable
security invoker
set search_path = public
as $$
  select <watched_columns>
  from public.<watch_history_table> h
  where h.profile_id = p_profile_id
    and exists (<profile_belongs_to_auth_uid>)
  order by h.watched_at desc
  limit p_page_size offset ((p_page - 1) * p_page_size);
$$;
```

```sql
-- PLACEHOLDER: validar tabela, conflito e autorização antes de gerar migration.
-- Não executar.
create or replace function public.sync_pull_watch_progress(
  p_profile_id integer,
  p_since_last_watched bigint default null,
  p_limit integer default null
)
returns setof record
language sql
stable
security invoker
set search_path = public
as $$
  select <progress_columns>
  from public.<watch_progress_table> w
  where w.profile_id = p_profile_id
    and (<since_filter>)
    and exists (<profile_belongs_to_auth_uid>)
  order by w.last_watched desc
  limit coalesce(p_limit, 1000);
$$;
```

### 5.3 Permissões e RLS propostas

- `REVOKE ALL` de `anon` para dados pessoais.
- `GRANT EXECUTE` somente a `authenticated`, com assinatura exata.
- Preferir `SECURITY INVOKER` quando RLS puder expressar a autorização.
- Usar `SECURITY DEFINER` somente se indispensável, com `search_path` fixo, validação explícita de `auth.uid()` e revisão de grants.
- Nunca autorizar por `p_profile_id` sem verificar a relação com `auth.uid()`.
- Não conceder `service_role` ao APK.

## 6. Estratégia de conflito e offline

1. Manter cache local por perfil como fonte de continuidade offline.
2. Pull remoto não deve apagar dados locais quando uma RPC falhar.
3. Push deve ser idempotente por chave natural/evento e origem de cliente.
4. Para progresso, preferir maior `last_watched` e validar `progress_key` antes de substituir.
5. Para histórico, usar operação explícita de merge/delete; não fazer overwrite global por usuário.
6. Para favoritos, preservar o conjunto local até confirmação do servidor.
7. Registrar falha por etapa no `SyncManager`; não bloquear Home, player ou recompensa.

O `SyncManager` já captura falhas por etapa e executa etapas independentes. O ponto a preservar é que uma falha de histórico/progresso não invalide addons, Home, recompensa ou catálogo.

## 7. Testes obrigatórios antes de aplicar produção

### Contrato e banco

- RPC inexistente retorna erro controlado e não altera dados.
- RPC criada responde com tipos exatamente compatíveis com os DTOs Kotlin.
- `anon` não consegue executar nem ler dados.
- Usuário autenticado só acessa seu próprio perfil.
- Perfil A não lê, altera ou remove dados do perfil B.
- Perfil de usuário A não acessa perfil de usuário B mesmo alterando manualmente `p_profile_id`.
- Repetição do mesmo push é idempotente.
- Concorrência de dois clientes não perde atualização válida.

### Funcionalidade

- Login → carregar perfil → favoritos → histórico → progresso.
- Fechar e reabrir app mantendo o perfil ativo.
- Trocar perfil e confirmar namespaces local/remoto independentes.
- Reproduzir episódio, salvar posição e retomar em outra sessão.
- Marcar/desmarcar favorito e verificar persistência.
- Histórico completo e paginação.
- Pull após falha de internet.
- Push offline e reconciliação após reconexão.
- Recompensa continua funcionando com as RPCs de sincronização indisponíveis.
- Recompensas não são alteradas pela migration de sync.

## 8. Plano de reversão

- Não executar SQL nesta etapa.
- Toda alteração futura deve ser uma migration nova, versionada e transacional.
- Antes de aplicar, registrar snapshot somente leitura de funções, grants, policies, índices e contagens.
- Para rollback, usar migration inversa que remova apenas objetos criados pela correção, nunca tabelas ou dados existentes.
- Se a alteração for apenas refresh de schema cache, registrar o comando e verificar novamente os contratos.
- APK só deve apontar para a nova assinatura após testes de compatibilidade concluídos.

## 9. Classificação final

| Item | Classificação |
|---|---|
| Chamadas Kotlin e parâmetros | **Confirmado** |
| Formatos JSON esperados | **Confirmado pelo código Kotlin** |
| Projeto Supabase utilizado | **Confirmado** |
| RPCs disponíveis no schema cache público | **Reprovado / ausentes** |
| Existência física das funções em `pg_proc` | **Pendente** |
| Estrutura remota exata de favoritos/histórico/progresso | **Não verificável com chave pública** |
| Separação local por perfil | **Confirmado** |
| Isolamento remoto entre perfis | **Pendente / crítico** |
| Dependência da recompensa em `get_my_member_access` | **Não depende; confirmado pelo código** |
| Alternativa equivalente às cinco RPCs | **Não localizada** |
| Necessidade de alterar recompensas | **Não necessária / deve ser evitada** |
| SQL de correção | **Preliminar, não executado** |
| Aprovação para produção | **Pendente** |

## Decisão solicitada

Aguardar aprovação para a próxima etapa, que deve ser somente uma destas opções:

1. fornecer introspecção administrativa somente leitura do projeto para fechar os contratos; ou
2. autorizar a preparação de migrations concretas após confirmação das tabelas, colunas, policies e assinaturas.

Até essa aprovação, não executar SQL, não alterar produção e não modificar a interface visual.
