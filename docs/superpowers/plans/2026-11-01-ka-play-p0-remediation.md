# KA Play P0 Remediation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Corrigir os bloqueios P0 verificáveis do KA Play em branch isolada e produzir evidência CI/isolada sem tocar produção, assinar ou publicar.

**Architecture:** Cada frente será implementada em worktree próprio e integrada somente em `agentic/ka-play-production-toolkit-20261101`. Contratos de cliente e backend isolado serão tratados sem aplicar migrations no projeto real. Testes de regressão precedem código; cada frente recebe revisão independente.

**Tech Stack:** Kotlin Multiplatform, Compose, Supabase/PostgREST SQL isolado, Android host/instrumented tests, GitHub Actions, keytool read-only.

**Spec:** `docs/agents/KA_PLAY_PRODUCTION_GATE.md` and user instruction “KA PLAY — ETAPA 17: CORRIGIR BLOQUEIOS P0 PARA PRODUÇÃO”.

## Global Constraints

- Não alterar `build-tomato-cmp` nem o bump `0.9.20`.
- Não acessar, migrar, escrever ou alterar o Supabase de produção `iibureqvpurmbgqupnvl`.
- Compilação/testes Android e iOS somente via GitHub Actions; nunca Gradle/Java local.
- Não executar `draft`, `publish`, assinatura, upload de APK ou instalação.
- Não expor secrets, senhas, JWT, keystore, `local.properties` ou `TRAKT_CLIENT_SECRET`.
- Não inventar credenciais; ambiente isolado deve falhar fechado se não estiver provisionado.
- Preservar TLS estrito, autenticação, sincronização e recompensas sem falsos sucessos.

## Workstreams

### Task 1 — Progress v1/v2 contract

- [ ] Add focused failing host tests for filtered/limited progress pull and keyset cursor semantics.
- [ ] Implement the smallest client-side contract correction without silently calling an unverified production RPC.
- [ ] Validate 1201+ rows, limits, cursor preservation and partial-failure behavior in isolated SQL/host tests.
- [ ] Run relevant GitHub Actions workflow and obtain independent review.

### Task 2 — Rewards/profile isolation

- [ ] Add failing tests for profile-scoped storage/refresh, ownership, duplicate event, cancellation, logout and expiry.
- [ ] Correct local storage scope and in-flight/idempotency behavior.
- [ ] Correct isolated SQL ownership/idempotency/quota behavior without applying production migration.
- [ ] Run isolated SQL and CI tests; independent security/QA review.

### Task 3 — AppSec remediation

- [ ] Add failing callback-state tests and enforce nonblank matching state.
- [ ] Add platform-safe token persistence approach without exposing secrets.
- [ ] Reject HTTP Supabase fallback and HTTPS→HTTP credential downgrade; add regression tests.
- [ ] Run focused CI tests and independent AppSec review.

### Task 4 — Isolated QA infrastructure

- [ ] Add instrumented sync test coverage required by the integration workflow, without fake production data.
- [ ] Keep backend URL/host allowlist and secrets mandatory; never default to production.
- [ ] Run `ka-play-sync-tests.yml` and, only when isolated variables/tests exist, `ka-play-integration-android.yml`.
- [ ] Record skipped/blocked jobs distinctly from successful tests.

### Task 5 — Signing read-only preflight

- [ ] Add a manual read-only workflow that decodes the GitHub keystore only in `RUNNER_TEMP`.
- [ ] Print only alias/fingerprint and compare with prior APK SHA-256.
- [ ] Do not run Gradle, assemble, signing, artifacts, draft or publish.
- [ ] Present workflow for authorization before dispatch; report missing properties/fingerprint mismatch.

### Task 6 — Consolidation

- [ ] Re-run independent review across integrated toolkit branch.
- [ ] Update `KA_PLAY_PRODUCTION_GATE.md` and delivery status with exact commits/runs and remaining P0/P1 blockers.
- [ ] Confirm candidate SHA and production project are untouched.
