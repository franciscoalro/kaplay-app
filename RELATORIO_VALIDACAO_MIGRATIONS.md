# Relatório de validação das migrations — KA Play

Data: 2026-10-30  
Commit avaliado: `721cd54a`  
APK de referência: `0.9.18` / `versionCode 191`  
Projeto Supabase: `iibureqvpurmbgqupnvl`

## 1. Escopo e método

Foram revisados integralmente:

```text
PLANO_IMPLEMENTACAO_SYNC_VALIDADO.md
supabase/proposed/20261030000001_ka_sync_profile_storage.draft.sql
supabase/proposed/20261030000000_ka_sync_rpc_recovery.draft.sql
```

Também foram comparados os contratos dos adapters Kotlin:

```text
SupabaseLibrarySyncAdapter.kt
SupabaseWatchedSyncAdapter.kt
SupabaseProgressSyncAdapter.kt
LibraryRepository.kt
WatchedRepository.kt
WatchProgressRepository.kt
WatchProgressIdentity.kt
ProfileRepository.kt
```

A introspecção administrativa somente leitura já havia confirmado o schema remoto. Nenhum SQL foi executado no Supabase de produção.

## 2. Resultado executivo

**Status: REPROVADO como migration pronta para implantação.**

Os arquivos avaliados são drafts seguros para revisão, mas ainda não constituem migrations aplicáveis:

- ambos abortam deliberadamente com `RAISE EXCEPTION`;
- as alterações de schema estão dentro de comentários;
- não há criação efetiva de tabelas de sync;
- não há criação efetiva das RPCs;
- não há policies/grants efetivos para os novos objetos;
- não há testes SQL executáveis;
- não há banco de desenvolvimento isolado disponível nesta sessão.

Essa condição é adequada para não arriscar produção, mas não permite declarar a recuperação pronta.

## 3. Validação estática do SQL

Verificação realizada sobre os dois drafts:

```text
active DDL statements: []
RAISE EXCEPTION guard: presente
ROLLBACK: presente
```

A estrutura ativa é essencialmente:

```sql
BEGIN;
DO $$ ... RAISE EXCEPTION ... $$;
ROLLBACK;
```

Portanto, mesmo se executados acidentalmente, os drafts não aplicam `ALTER TABLE`, `CREATE`, `GRANT`, `REVOKE`, `INSERT`, `UPDATE` ou `DELETE`.

### Compatibilidade PostgreSQL/Supabase

- `BEGIN`, bloco `DO`, `RAISE EXCEPTION` e `ROLLBACK` são sintaticamente compatíveis com PostgreSQL.
- Os blocos comentados não foram aceitos como migration executável porque não constituem SQL ativo.
- Não foi possível realizar `EXPLAIN`, `pgTAP` ou execução em PostgreSQL isolado: não há `psql`, `supabase`, `sqlfluff`, biblioteca PostgreSQL ou banco local disponíveis nesta sessão.

## 4. Problemas encontrados no desenho

### 4.1 Escopo por perfil ainda não implementado

O schema atual tem:

- favoritos: chave `(user_id, media_id)`;
- histórico: chave `(user_id, media_id, season_num, episode_num)`;
- nenhuma coluna `profile_index` nessas tabelas.

A proposta de adicionar `profile_index default 1` é coerente somente se o proprietário confirmar que todos os registros legados representam o perfil 1. A migration não pode assumir isso automaticamente.

### 4.2 Duplicidades e chaves

O draft inclui consultas de duplicidade, mas elas estão em comentário e não abortam de fato.

Na migration final, devem ser `DO` checks que lancem exceção quando:

- houver duplicidade na nova chave composta;
- `profile_index` não existir em `ka_profiles`;
- houver registros órfãos de `auth.users`;
- o perfil 1 não estiver confirmado como destino legado.

O PostgreSQL não aceita uma chave primária composta com colunas nullable. As colunas `season_num` e `episode_num` já fazem parte da PK atual e, portanto, estão efetivamente não nulas; isso deve ser verificado antes da troca da constraint.

### 4.3 Progresso não cabe em `watch_history` sem perda de identidade

`watch_history` possui `position_sec` e `duration_sec`, mas não possui:

```text
video_id
progress_key
content_type
```

O Kotlin exige `video_id` como identidade do vídeo reproduzível. Não é seguro derivá-lo de `media_id` sem confirmação.

**Correção recomendada:** tabela dedicada `ka_watch_progress`, com chave `(user_id, profile_index, progress_key)` e campos completos do contrato. Registros antigos sem `video_id` não devem ser convertidos silenciosamente.

### 4.4 Biblioteca não é equivalente a `user_favorites`

`LibrarySyncItem` exige campos que não existem em `user_favorites`, como:

```text
poster_shape
background
logo
description
release_info
imdb_rating
genres
addon_base_url
added_at
```

Não é seguro implementar `sync_pull_library` diretamente sobre favoritos. Deve existir armazenamento dedicado `ka_library_items` ou um contrato explícito de redução de campos, com adaptação Kotlin aprovada.

### 4.5 RPCs relacionadas também estão ausentes

A consulta ampla por `sync_%` encontrou apenas:

```text
sync_pull_profiles()
sync_push_profiles(...)
```

Logo, criar apenas:

```text
sync_get_library_delta_cursor
sync_pull_watched_items
sync_pull_watch_progress
```

não recuperaria os adapters. Também são necessários snapshot, delta, push e delete para cada domínio.

### 4.6 RLS e grants

RLS está habilitado nas tabelas legadas, mas as policies atuais de favoritos/histórico usam role `public` e validam apenas `auth.uid() = user_id`.

Isso protege a fronteira entre usuários, mas não separa perfis do mesmo usuário.

A migration final deve:

- usar `TO authenticated`;
- revogar acesso direto de `anon`;
- manter RLS habilitado;
- validar `auth.uid()` e ownership do `profile_index`;
- impedir acesso direto a todos os perfis se a API expuser as novas tabelas;
- conceder `EXECUTE` somente nas assinaturas necessárias;
- nunca usar `GRANT EXECUTE TO PUBLIC`.

### 4.7 SECURITY INVOKER versus SECURITY DEFINER

`SECURITY INVOKER` é preferível, mas não é suficiente se o acesso direto às tabelas for revogado e as RPCs precisarem operar como gateway controlado.

Uma futura `SECURITY DEFINER` só é justificável se:

- o corpo validar `auth.uid()`;
- validar o perfil em `ka_profiles`;
- usar `SET search_path` fixo;
- revogar execute público;
- conceder execute apenas a `authenticated`;
- tiver testes negativos de BOLA/IDOR.

A função de recompensa existente `ka_claim_ad_reward` não deve ser alterada.

## 5. Idempotência, cursor, exclusões e conflitos

O draft descreve esses requisitos, mas ainda não os implementa.

A migration concreta deverá ter:

- chave natural por usuário/perfil/conteúdo;
- eventos com `event_id` monotônico;
- tombstones para delete;
- `origin_client_id` para evitar eco;
- upsert idempotente;
- cursor por domínio, nunca cursor global misturado;
- `last_watched` como regra de frescor;
- rejeição de update antigo que tente substituir progresso mais novo;
- limite de página/lote;
- ordenação determinística.

## 6. Dados legados

### Procedimento obrigatório

1. Backup lógico verificável de favoritos e histórico.
2. Contagens por usuário.
3. Hash por registro ou export imutável.
4. Consulta de duplicidades antes da alteração de PK.
5. Confirmação explícita do perfil 1 como destino.
6. Migration transacional.
7. Comparação de contagens/hashes após execução.
8. Teste de restauração em ambiente isolado.

### Registros sem `video_id`

Devem permanecer no histórico legado até uma decisão de produto. Preencher `video_id = media_id` seria uma hipótese perigosa e pode quebrar retomada ou misturar vídeos.

## 7. Testes obrigatórios

Não foram executados contra banco isolado porque nenhum banco de desenvolvimento foi disponibilizado.

A suíte necessária deve criar pelo menos:

```text
user_a / profile_a1 / profile_a2
user_b / profile_b1 / profile_b2
```

### Casos críticos

- favoritos independentes entre A1/A2;
- histórico independente entre A1/A2;
- progresso independente entre A1/A2;
- isolamento completo entre A e B;
- tentativa de usar `p_profile_id` de outro usuário;
- offline → escrita local → reconexão;
- push repetido sem duplicação;
- delete propagado por tombstone;
- conflito antigo versus novo em `last_watched`;
- retomada exata após fechar/reabrir;
- preservação dos registros antigos;
- login/troca de perfil;
- recompensa funcionando sem modificar suas RPCs;
- reinstalação/reabertura conforme política de backup local.

## 8. Compatibilidade com APK 0.9.18

**Não aprovada ainda.**

O APK 0.9.18 espera todos os métodos dos adapters, mas o banco só possui RPCs de perfil e recompensa. Uma migration parcial faria o app continuar falhando em chamadas relacionadas.

A compatibilidade exige:

1. conjunto completo de RPCs por domínio;
2. JSON exatamente compatível com os DTOs Kotlin;
3. preservação da semântica de `progress_key` e `video_id`;
4. testes reais com o APK após aplicar em ambiente isolado.

## 9. SQL corrigido recomendado

O SQL corrigido não deve ser aplicado como um único `ALTER` sobre as tabelas legadas. A forma segura é separar:

```text
Migration A: escopo por perfil, após backup e confirmação do perfil 1
Migration B: tabelas dedicadas de biblioteca, histórico, eventos e progresso
Migration C: RPCs completas, policies/grants e cursores
Migration D: refresh do schema cache e validação PostgREST
```

A Migration A deve abortar antes de trocar as chaves se qualquer check de duplicidade, ownership ou perfil falhar.

A Migration B deve evitar backfill fictício de `video_id`.

A Migration C deve criar o conjunto completo de snapshot/delta/push/delete e aplicar idempotência/frescor.

A Migration D deve ocorrer somente após commit e smoke tests.

## 10. Recomendação

**Não implantar.**

A migration está segura como draft de planejamento porque não executa DDL, mas ainda não está pronta para aplicação. Os bloqueios são funcionais e de segurança, não apenas sintáticos:

1. falta isolamento por perfil nas tabelas legadas;
2. falta armazenamento compatível de progresso;
3. falta armazenamento compatível de biblioteca;
4. faltam todas as RPCs relacionadas, não apenas as três destacadas;
5. falta banco isolado para testes com quatro perfis e dois usuários;
6. falta confirmação do destino dos registros legados;
7. falta política para registros sem `video_id`.

## 11. Próximo passo seguro

Preparar as migrations concretas somente após aprovação destes pontos:

- perfil 1 como destino dos registros legados;
- criação de tabelas dedicadas de sync;
- política para histórico antigo sem `video_id`;
- estratégia de acesso RPC/RLS;
- disponibilidade de banco de desenvolvimento isolado.

Nenhuma alteração de produção, autenticação, recompensa ou interface visual foi realizada.
