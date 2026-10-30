# Relatório de integração real — KA Play

Data: 2026-10-30  
Commit de referência: `e3b30709`  
Ambiente: desenvolvimento isolado  
Produção Supabase `iibureqvpurmbgqupnvl`: **não modificada**

## 1. Veredito

**PARCIAL**

A integração PostgreSQL 17 + PostgREST + JWT sintético foi validada por HTTP. A integração Android ainda não pode ser aprovada porque o ambiente isolado não fornece Supabase Auth real nem endpoint `/auth/v1`, e o APK de teste ainda está em construção.

Não foram executadas migrations na produção, nem modificadas recompensas ou a Home.

## 2. Ambiente isolado

```text
PostgreSQL 17.11 (postgres:17-alpine)
PostgREST 12.2.3
PostgREST local: http://127.0.0.1:3300
Banco local: kaplay
```

As migrations A, B e C locais foram carregadas no PostgreSQL 17:

```text
supabase/proposed/20261030000001_ka_profile_scope_local.sql
supabase/proposed/20261030000002_ka_sync_storage_local.sql
supabase/proposed/20261030000003_ka_sync_rpcs_local.sql
```

Foram usados dois usuários sintéticos e quatro perfis. Nenhum dado pessoal foi copiado da produção.

## 3. PostgREST e schema cache

PostgREST iniciou com sucesso:

```text
Successfully connected to PostgreSQL 17.11
Schema cache loaded ... 19 Functions
```

A atualização do schema cache foi testada com:

```text
NOTIFY pgrst, 'reload schema'
```

Evidência nos logs:

```text
Received a schema cache reload message
Config reloaded
Schema cache queried
Schema cache loaded ... 19 Functions
```

## 4. Testes HTTP autenticados

Os JWTs foram sintéticos, assinados somente para o ambiente local, com role `authenticated`. Nenhum token de produção foi usado ou registrado neste relatório.

### Snapshot, push e pull

| Operação | HTTP | Resultado |
|---|---:|---|
| `sync_push_library_items` | 204 | aprovado |
| `sync_push_watched_items` | 204 | aprovado |
| `sync_push_watch_progress` | 204 | aprovado |
| `sync_pull_library` | 200 | JSON compatível |
| `sync_pull_watched_items` | 200 | JSON compatível |
| `sync_pull_watch_progress` | 200 | JSON compatível |

Respostas observadas:

```json
library: {"content_id":"a1","content_type":"series","name":"A1",...}
watched: {"content_id":"show","season":1,"episode":1,"watched_at":100}
progress: {"video_id":"video","position":12,"duration":100,"last_watched":200,"progress_key":"show_s1e1"}
```

### Delta e exclusão

| Operação | HTTP | Resultado |
|---|---:|---|
| `sync_get_library_delta_cursor` | 200 | cursor retornado |
| `sync_pull_library_delta` | 200 | `upsert` retornado |
| `sync_pull_watch_progress_delta` | 200 | `upsert` retornado |
| delete de biblioteca | 204 | aprovado |
| delete de histórico | 204 | aprovado |
| delete de progresso | 204 | aprovado |
| delta após delete | 200 | evento `delete` retornado |

### Recompensa

A função existente foi chamada sem alterar seu DDL:

```text
ka_claim_ad_reward: HTTP 200
```

A função não foi recriada nem modificada pelas migrations de sync.

## 5. Segurança HTTP

### Sem autenticação

```text
POST /rpc/sync_pull_library sem JWT: HTTP 401
```

### Perfil inexistente para o usuário

```text
user B -> profile_index 99: HTTP 403
message: profile access denied
```

### Isolamento entre usuários/perfis

```text
user A / profile 2: HTTP 200, conjunto independente
user B / profile 1: HTTP 200, conjunto independente
```

Nenhum usuário conseguiu ler o perfil inexistente de outro usuário. A função valida `auth.uid()` e a existência do perfil em `ka_profiles`.

### Permissões

- `anon`: sem `EXECUTE` nas RPCs de sync;
- `authenticated`: `EXECUTE` apenas nas RPCs explicitamente listadas;
- acesso direto às tabelas dedicadas: revogado;
- `search_path` das funções: `public, pg_temp` fixo;
- `service_role`: não incluído no APK nem no teste Android.

As funções `SECURITY DEFINER` continuam exigindo revisão final antes de produção. Elas foram usadas porque o acesso direto às tabelas foi revogado; o ownership é validado explicitamente em cada gateway.

## 6. Idempotência e regressão de progresso

Foi repetido o mesmo push de biblioteca e enviado progresso antigo após o progresso novo.

Resultados HTTP/DB:

```text
library duplicate event: não criado
progress antigo: ignorado
last_watched final: 200
video_id final: video
```

## 7. Android

Foi inicialmente tentada a tarefa incorreta `:composeApp:assembleFullDebug`, que não existe neste projeto. A tarefa correta foi identificada como:

```text
:androidApp:assembleFullDebug
```

O build de teste foi iniciado com configuração exclusivamente de processo:

```text
KAPLAY_SUPABASE_URL=http://10.0.2.2:3300
KAPLAY_SUPABASE_ANON_KEY=local-anon
```

Isso não altera `local.properties`, o flavor de produção ou o Supabase de produção.

Estado no fechamento deste relatório:

```text
APK Android: build em andamento
APK instalado no emulador: não
Login real: não validado
Offline/reconexão no APK: não validado
```

O PostgREST local não implementa Supabase Auth. Portanto, não é possível declarar login de sessão Android como aprovado sem adicionar um Auth isolado ou um backend de teste equivalente.

## 8. Compatibilidade com 0.9.18

Validado no nível de contrato HTTP:

- nomes das RPCs;
- parâmetros JSON;
- campos de biblioteca;
- campos de histórico;
- `video_id` e `progress_key` do progresso;
- cursores e eventos de delta;
- delete por chave.

Ainda pendente:

- teste do APK 0.9.18 instalado contra o endpoint local;
- sessão JWT obtida pelo fluxo normal de Auth;
- comportamento do APK antigo que grava no formato legado;
- atualização preservando cache local;
- confirmação de que todos os dados legados pertencem ao perfil 1.

## 9. Problemas encontrados e correções locais

1. O primeiro comando Gradle usou `:composeApp:assembleFullDebug`; a tarefa correta pertence a `:androidApp`.
2. O stub local de `auth.uid()` inicialmente lia somente `request.jwt.claim.sub`; PostgREST 12 forneceu os claims em `request.jwt.claims`. O stub local foi ajustado para aceitar ambos. Nenhum código de produção foi alterado.
3. `position` precisou ser quoted em uma declaração SQL `RETURNS TABLE` por conflito com palavra reservada.

## 10. Riscos restantes

- PostgREST/JWT local não equivale integralmente ao Supabase Auth hospedado.
- Não houve teste Android end-to-end com login real.
- Não houve teste de falha de conexão dentro do APK.
- A estratégia de migração de dados antigos para perfil 1 ainda depende de confirmação do produto.
- `SECURITY DEFINER` precisa de revisão independente antes de produção.
- Avatares e membership permanecem fora do escopo e sem RPCs fictícias.

## 11. Estratégia de implantação futura

1. Disponibilizar ambiente Supabase de desenvolvimento com Auth real, sem dados pessoais.
2. Aplicar A/B/C somente nesse ambiente.
3. Testar PostgREST com JWT emitido pelo Auth.
4. Apontar um APK de teste para o ambiente isolado.
5. Executar os cenários de dois usuários/quatro perfis, offline, reconexão, retomada e recompensas.
6. Revalidar backup/restauração.
7. Fazer revisão independente das funções `SECURITY DEFINER`.
8. Somente então solicitar autorização para implantação controlada.

## 12. Conclusão

A API isolada está funcional e respondeu corretamente às operações críticas por HTTP em PostgreSQL 17/PostgREST 12.2.3. O Android ainda não tem evidência end-to-end suficiente para aprovação, principalmente por ausência de Auth isolado e build APK ainda não concluído.

**Veredito final: PARCIAL.**

## 13. Continuidade via GitHub Actions

Foi criado o workflow manual:

```text
.github/workflows/ka-play-isolated-android.yml
```

Ele:

- exige uma URL de backend isolado como input;
- rejeita explicitamente as URLs de produção;
- exige somente uma chave anon/publishable de teste;
- constrói `:androidApp:assembleFullDebug` ou `:androidApp:assemblePlaystoreDebug`;
- publica o APK como artifact por sete dias;
- não usa service-role key;
- não altera a configuração de produção.

O workflow foi criado localmente, mas ainda não foi disparado no GitHub porque isso requer publicação do commit no repositório remoto e um backend isolado acessível pelo runner.
