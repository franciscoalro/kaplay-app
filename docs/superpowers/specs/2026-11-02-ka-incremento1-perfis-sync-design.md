# KA Play — Incremento 1: perfis + sincronização

## Intenção
Entregar um slice vertical verificável em que a seleção de perfil e os estados de sincronização do Android consumam contratos reais de RPC/RLS Supabase, enquanto o backend é validado somente em PostgreSQL isolado via GitHub Actions.

## Objetivos
- Perfil: carregar, criar/editar e selecionar dados pertencentes ao usuário autenticado.
- Sync: representar `SYNCING`, `SYNCED`, `PENDING`, `OFFLINE` e `ERROR` por perfil sem sucesso otimista.
- Contrato compartilhado: nomes de RPC, campos obrigatórios, ownership, erros e estados documentados.
- Evidência: testes Kotlin, SQL isolado, integração CI e revisão QA/AppSec.

## Não objetivos
- Acesso ou migration no projeto Supabase de produção.
- APK, assinatura, publicação, instalação ou alteração de `build-tomato-cmp`/0.9.20.
- Redesign amplo ou suporte a funcionalidades sem RPC/contrato correspondente.

## Abordagens consideradas
1. Frontend primeiro contra mocks: rejeitada porque mascara ausência de RPC e viola o contrato de dados reais.
2. Backend completo antes da UI: rejeitada porque retoma o fluxo sequencial que esta organização pretende eliminar.
3. **Slice vertical contrato-first em branches paralelas (escolhida):** backend define/valida RPC, ownership e erros em isolamento; frontend implementa estados derivados do mesmo contrato; integração ocorre por cherry-pick controlado e CI.

## Contrato inicial
- RPCs: `sync_pull_profiles`, `sync_push_profiles` e estados publicados por `SyncManager.profileSyncStates`.
- Perfil identifica-se por `user_id` + `profile_index`; cliente nunca escolhe ownership de outro usuário.
- Estados: loading/syncing, synced, pending, offline, error/reconnect.
- Falhas de contrato, autorização e rede permanecem observáveis para a UI e para os artefatos CI.

## Entregáveis e verificação
- Branch backend isolada: migrations/assertions/runner SQL, sem produção.
- Branch frontend isolada: Compose, navegação/estado e testes host.
- QA/AppSec: revisão independente de ownership, RLS, exposição de RPCs e comportamento offline.
- Toolkit recebe somente commits revisados; matriz de prontidão registra evidência e limites.
