# Relatório de testes de sincronização isolada — KA Play

Data: 2026-10-30  
Commit de referência: `3291395f`  
Implementação testada: drafts locais adicionados após a validação  
APK de referência: `0.9.18` / `versionCode 191`

## 1. Ambiente

Foi usado PostgreSQL local isolado, sem Supabase hospedado:

```text
Imagem: postgres:16-alpine
Container: ka-sync-pg
Porta local: 55432
Databases: kaplay, kaplay_a
```

O Docker estava disponível com aproximadamente 4 GB de memória e o banco foi executado em container separado. Nenhuma conexão de produção foi usada para aplicar SQL.

O schema base foi reproduzido com:

```text
supabase/migrations/20261028000000_ka_ad_rewards.sql
supabase/migrations/20261029000000_ka_profiles.sql
```

As tabelas legadas `user_favorites` e `watch_history` foram criadas com as colunas confirmadas na introspecção. Foram usados apenas UUIDs sintéticos.

Para simular autenticação local, o stub de teste `auth.uid()` lê `request.jwt.claim.sub` em uma sessão PostgreSQL. Isso não substitui um teste com JWT real do Supabase.

## 2. Arquivos testados

Migration A local:

```text
supabase/proposed/20261030000001_ka_profile_scope_local.sql
```

Migration B local:

```text
supabase/proposed/20261030000002_ka_sync_storage_local.sql
```

Migration C local:

```text
supabase/proposed/20261030000003_ka_sync_rpcs_local.sql
```

Os drafts bloqueados originais permanecem sem execução automática:

```text
supabase/proposed/20261030000000_ka_sync_rpc_recovery.draft.sql
supabase/proposed/20261030000001_ka_sync_profile_storage.draft.sql
```

## 3. Resultado de compilação SQL

### Migration A

Executada com sucesso no banco `kaplay_a`:

```text
BEGIN
ALTER TABLE x4
DO
ALTER TABLE x4
CREATE INDEX x2
COMMIT
```

Validou:

- adição de `profile_index`;
- checks de faixa `1..6`;
- verificação de duplicidade;
- verificação de perfil proprietário;
- substituição das chaves primárias;
- índices por usuário/perfil/data;
- preservação do registro legado no perfil 1.

### Migration B

Executada com sucesso:

```text
CREATE TABLE x6
CREATE INDEX x3
CREATE FUNCTION ka_assert_profile_access
ALTER TABLE ... ENABLE ROW LEVEL SECURITY x6
REVOKE ... FROM anon, authenticated
```

Foram criadas estruturas dedicadas para biblioteca, histórico, eventos e progresso.

### Migration C

Executada com sucesso após duas correções de sintaxe encontradas durante a validação:

1. `position` foi identificado como palavra reservada em uma declaração `RETURNS TABLE`; foi corrigido para o campo JSON SQL quoted `"position"`.
2. O grant amplo `GRANT EXECUTE ON ALL FUNCTIONS` foi removido e substituído por grants explícitos apenas nas RPCs de sync.

Resultado final:

```text
CREATE FUNCTION x15
REVOKE x15
GRANT x15
```

## 4. Testes executados

### 4.1 Dois usuários e quatro perfis

Dados sintéticos:

```text
user_a: profile 1 (A1), profile 2 (A2)
user_b: profile 1 (B1), profile 2 (B2)
```

Cada perfil recebeu item próprio de biblioteca, histórico e progresso.

Resultados observados:

```text
A1 library: 1
A2 library: 1
A1 history: 1
A2 history: 1
A1 progress: 1
A2 progress: 1
B1 library: 1
```

Nenhum dado de A apareceu na consulta de B.

**Resultado: aprovado no banco isolado.**

### 4.2 Preservação de legado

Antes das novas estruturas, foram inseridos registros legados sem `profile_index`. A Migration A atribuiu esses registros ao perfil 1 somente no banco sintético de teste.

Resultado:

```text
legacy_favorites_profile1: 1
legacy_history_profile1: 1
```

Em produção, essa atribuição continua condicionada à confirmação do proprietário do produto.

**Resultado: aprovado tecnicamente; aplicação em produção pendente de autorização semântica.**

### 4.3 Idempotência

O mesmo item de biblioteca foi enviado duas vezes.

```text
library_events_after_repeat: 1
```

O mesmo progresso antigo foi enviado depois de um progresso mais recente.

Estado final:

```text
video_id: v-a1
position: 20
last_watched: 200
progress_events_after_old: 1
```

O valor antigo não substituiu o progresso mais novo e não gerou evento adicional.

**Resultado: aprovado no banco isolado.**

### 4.4 Autorização de perfil

A sessão de `user_a` tentou acessar `profile_index=99`, inexistente para o usuário.

Resultado:

```text
NOTICE: unauthorized profile blocked
```

As funções rejeitaram o acesso com `42501`.

**Resultado: aprovado para ownership de perfil no gateway RPC.**

### 4.5 Exclusões e deltas

Foram executados deletes de biblioteca, histórico e progresso.

Resultado:

```text
a1_library_after_delete: 0
a1_history_after_delete: 0
a1_progress_after_delete: 0
library_cursor_ok: true
watched_cursor_ok: true
progress_cursor_ok: true
```

Os deltas preservaram os eventos de `upsert` e `delete`:

```text
library: upsert + delete
awatched: upsert + delete
progress: upsert + delete
```

**Resultado: aprovado no banco isolado.**

### 4.6 Privilégios

Resultado de `has_*_privilege`:

```text
authenticated execute sync_pull_watch_progress: true
anon execute sync_pull_watch_progress: false
authenticated direct table SELECT: false
```

As tabelas dedicadas não são acessíveis diretamente por `authenticated`; as RPCs são o gateway controlado.

**Resultado: aprovado no banco isolado.**

### 4.7 Backup e restauração

Foi criado backup PostgreSQL custom e restaurado em outro database do mesmo container.

```text
backup_size: 69762 bytes
restore_exit: 0
```

**Resultado: backup/restauração técnica aprovada no ambiente isolado.**

## 5. Compatibilidade com APK 0.9.18

### Compatível

- assinaturas e parâmetros dos adapters Kotlin;
- campos de retorno dos DTOs de biblioteca, histórico e progresso;
- `progress_key` e `video_id` separados;
- paginação limitada;
- cursores `bigint`;
- deltas com `operation`, `event_id` e payload;
- `origin_client_id` aceito nas operações de escrita.

### Ainda não validado

- PostgREST real e refresh de schema cache;
- JWT real emitido pelo Supabase Auth;
- Ktor/Supabase Kotlin contra esse banco;
- APK 0.9.18 instalado apontando para um backend isolado;
- comportamento de sync após reinstalação do APK;
- compatibilidade de APK antigo que ainda use o schema legado;
- adaptação de dados históricos antigos sem `video_id`.

## 6. Recompensas

A migration local preservou as migrations e a função:

```text
ka_claim_ad_reward(integer, uuid)
```

Nenhuma tabela ou função de recompensa foi alterada pela Migration B/C.

O fluxo de recompensa não foi executado com um JWT real; a não regressão estrutural foi verificada por introspecção e preservação do DDL.

**Resultado: parcialmente aprovado; smoke test real ainda pendente.**

## 7. Problemas restantes

1. O código testado é uma implementação local de validação, não uma migration de produção final.
2. Os adapters chamam o conjunto completo de RPCs; todas foram criadas apenas no banco isolado.
3. A Migration A ainda depende de confirmação explícita de que o legado pertence ao perfil 1.
4. Dados legados sem `video_id` não receberam backfill fictício.
5. Não foi validado PostgREST real, JWT real ou schema cache real.
6. Não há fonte oficial de avatares ou membership; essas funções não foram criadas.
7. O script de RLS usa `SECURITY DEFINER` com ownership explícito porque o acesso direto às tabelas foi revogado. Isso exige revisão de segurança antes de produção.

## 8. Estratégia de implantação futura

1. Criar projeto/branch de desenvolvimento isolado, sem dados pessoais.
2. Aplicar Migration A somente após backup e confirmação do perfil 1.
3. Aplicar Migration B e C em transação.
4. Atualizar/validar schema cache PostgREST no ambiente isolado.
5. Rodar chamadas REST autenticadas com dois usuários e quatro perfis.
6. Instalar APK 0.9.18 apontando para o ambiente isolado.
7. Rodar os testes manuais de login, troca de perfil, favorito, histórico, retomada, offline e recompensa.
8. Comparar contagens/hashes do legado.
9. Fazer implantação controlada somente após aprovação.

## 9. Recuperação em caso de falha

- interromper antes de qualquer produção;
- restaurar o backup lógico em ambiente de recuperação;
- reverter o APK para a versão anterior se o contrato falhar;
- remover somente objetos novos e vazios, sem `DROP ... CASCADE`;
- não apagar tabelas legadas;
- não alterar ou reverter a RPC de recompensa;
- manter os logs redigidos e registrar o ponto exato da falha.

## 10. Veredito

**Ambiente isolado: aprovado para os testes SQL executados.**

**Produção: não aprovado para implantação.**

A implementação demonstra isolamento, idempotência, frescor de progresso, exclusões, cursores e backup em PostgreSQL local. Ainda são necessários testes com PostgREST/JWT reais e APK 0.9.18 antes de qualquer autorização de produção.

Nenhuma migration foi executada no Supabase de produção. O redesign visual permanece bloqueado nesta etapa.
