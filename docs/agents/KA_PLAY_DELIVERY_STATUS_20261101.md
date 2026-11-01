# KA Play — Delivery Status 2026-11-01

## Escopo e segurança

- Branch de auditoria: `agentic/ka-play-production-toolkit-20261101`
- HEAD auditado: `ee66ff1995c0e15fd5f357a36515c7a0ca748bf4`
- Candidata preservada, sem merge: `build-tomato-cmp@e59fcc7f58678ac9dc1e483ab786a3d40f255349` (`0.9.20`, versionCode `193`)
- Supabase de produção `iibureqvpurmbgqupnvl`: não acessado, não alterado, sem migration/DDL/write.
- APK instalado e sessão do emulador/dispositivo: não tocados.
- Assinatura, draft, publish e instalação: não executados.

## Evidência executada nesta rodada

| Workstream | Run | Resultado |
|---|---|---|
| Sync host tests | [41302445739](https://github.com/franciscoalro/kaplay-app/actions/runs/41302445739) / job `124836033691` | **TESTADO** — sucesso |
| Android integration isolated | [41302447332](https://github.com/franciscoalro/kaplay-app/actions/runs/41302447332) / job `124836038439` | **BLOQUEADO** — validação do backend isolado falhou antes do build/emulador |

O workflow de integração exigiu URL HTTPS isolada, allowlist de host, anon key e credenciais de teste. Não iniciou Android SDK, APK ou emulator.

## Matriz de gates

| Gate | Status | Evidência/bloqueio |
|---|---|---|
| Identidade de assinatura | **BLOQUEADO P0** | APK anterior: SHA-256 `97807c6f...e99a8`; keystore local `kaplay-v2-release.jks`: `71625f1f...a18d`; mismatch. Secret GitHub não pode ser lido. |
| Secrets de release | **BLOQUEADO P0** | `KAPLAY_LOCAL_PROPERTIES_BASE64` ausente; secrets individuais de assinatura existem. |
| Backend/RPC sync | **BLOQUEADO P0** | Produção teve `PGRST202`; migrations não aplicadas. C3 v2 isolado existe, mas o cliente ainda tem ramo progress chamando `sync_pull_watch_progress` v1 enquanto C3 define `sync_pull_watch_progress_v2`. |
| SQL isolado | **TESTADO (histórico); NÃO TESTADO nesta rodada** | `ETAPA15_SYNC_V2_ISOLATED_OK`: 1201 registros, 15 RPCs, keyset/ownership/anon/batch. O workflow CI sync executa host Kotlin, não Docker/SQL. |
| Android funcional | **BLOQUEADO P0** | Sem backend HTTPS isolado/secrets e sem testes instrumentados `Sync*`/`Supabase*`; integração parou no preflight. |
| Playback/TLS | **PARCIAL** | Host MockWebServer/TLS/header/redirect tests aprovados; MP4/HLS/DASH/subtitle/Media3 e TLS em dispositivo não testados. |
| Rewards/perfis | **BLOQUEADO P0** | Estado local de recompensa é user-scoped, não profile-scoped; RPC `ka_claim_ad_reward` não comprova ownership do perfil; sem testes de replay/cancelamento/offline/expiry. |
| AppSec | **NO-GO** | Médio: callback Trakt aceita `state` ausente; tokens Trakt em armazenamento não criptografado; fallback Supabase permite configuração HTTP e retry com headers. |
| UX/D-pad | **PARCIAL/NÃO TESTADO** | Search sem CTA em estados sem fonte, sync status sem consumidor global, overlaps/empty states e navegação D-pad sem E2E atual. |
| Aprovação de rollout | **REQUER AUTORIZAÇÃO** | Nenhum plano de rollout/rollback foi autorizado para execução. |

## Achados independentes principais

### P0 — sincronização e compatibilidade

- O backend de produção ainda não tem as RPCs observadas pelo APK legado (`PGRST202`).
- O contrato v1/v2 de progress não é compatível no ramo com parâmetros `sinceLastWatched`/`limit`.
- Não aplicar migrations em produção sem inventário read-only, backup restaurável, revisão RLS/ownership, teste de compatibilidade 0.9.18→0.9.20 e rollback aprovado.

### P0 — recompensas e isolamento de perfil

- `AdRewardRepository` persiste expiração por `userId`, mas refresh agrega o maior saldo entre perfis.
- `ka_claim_ad_reward` precisa validar ownership do perfil antes de conceder.
- `AdRewardController` não tem guarda atômica/in-flight; chamadas rápidas podem criar múltiplos eventos.
- Limpeza de logout não inclui o armazenamento de recompensas.

### P0 — homologação Android

Ainda não há evidência de dispositivo/emulador para login, dois usuários, quatro perfis, biblioteca/favoritos/histórico, progress 1201+, offline/reconexão, rewards, playback Media3 ou logs/screenshot sanitizados.

### P1/Médio — segurança e UX

- OAuth Trakt aceita callback sem `state`; exigir estado pendente e comparação constante antes da troca.
- Tokens Trakt estão em SharedPreferences/NSUserDefaults não criptografados; migrar para Keystore/Keychain.
- Fallback Supabase deve rejeitar HTTP e qualquer downgrade HTTPS→HTTP antes de transportar headers.
- Search, Library, Home e Profile precisam de testes de CTA, overlay, foco, Back, contraste e semântica TalkBack.

## Próxima sequência segura

1. Corrigir/validar o contrato v1/v2 de progress em branch de trabalho, com teste de regressão e revisão independente.
2. Corrigir isolamento de rewards/perfis e ownership no backend isolado; executar testes idempotência/cancelamento/expiração antes de qualquer proposta de migration.
3. Criar workflow/manual preflight read-only de certificado do secret GitHub; decodificar somente em `RUNNER_TEMP`, imprimir apenas alias/fingerprint e falhar em mismatch. Não assinar.
4. Provisionar backend HTTPS isolado e credenciais de teste; adicionar testes instrumentados reais antes de executar integração Android.
5. Corrigir os achados AppSec e adicionar testes de regressão.
6. Reexecutar QA Android/playback em ambiente isolado e somente então reavaliar a matriz.

## Decisão atual

**NO-GO para produção.** A candidata `0.9.20` permanece intacta. Nenhum bloqueio pode ser considerado resolvido por CI Debug, dry-run ou testes host isolados isoladamente.
