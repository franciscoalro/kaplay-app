# KA Play Etapa 18 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Validar SQL em ambiente isolado com evidência reproduzível e implementar melhorias UX visíveis em branch separada.

**Architecture:** Frente SQL endurece o runner Docker/PostgreSQL e assertions sem tocar produção. Frente UX cria componentes de estado e aplica mudanças nas telas existentes, preservando contratos e identidade visual.

**Tech Stack:** PostgreSQL/Supabase SQL, Docker via GitHub Actions, Kotlin Multiplatform, Compose Multiplatform, testes Kotlin/Compose host via GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-11-01-ka-etapa18-ux-sql-design.md`

## Global Constraints
- Não modificar `build-tomato-cmp` ou sua versão 0.9.20.
- Não aplicar SQL no Supabase de produção.
- Não instalar, assinar ou publicar APK.
- Não fazer merge automático.
- Não ocultar erros reais do backend por meio da interface.
- Builds/testes Android apenas por GitHub Actions.

## Review Focus
- SQL runner must preserve non-zero exit code and upload sanitized logs when an assertion fails — Task 1.
- Reward cross-profile and cross-user claims must fail under RLS/ownership checks — Task 1.
- Reward duplicate/concurrent claims must not mint extra balance — Task 1.
- UX empty/loading/error states must remain D-pad focusable and expose semantics — Task 2.
- Profile/sync status must show real states, not optimistic success — Task 2.

### Task 1: Reproducible isolated SQL validation

**Files:**
- Modify: `supabase/tests/run_etapa15_sync_v2_isolated.sh`
- Create/modify: isolated SQL assertions under `supabase/tests/`
- Modify: `.github/workflows/ka-play-sync-tests.yml` or add a dedicated workflow
- Create: CI report under `docs/agents/`

- [ ] Write failing SQL assertions for rewards ownership, same-event idempotency, cross-profile rejection, concurrent quota, sync keyset pagination, and RLS.
- [ ] Run the isolated Docker/Postgres job in GitHub Actions and capture exit code, sanitized log, and artifact paths.
- [ ] Fix runner capture/cleanup so assertion failures propagate non-zero and logs are always uploaded.
- [ ] Re-run until all assertions pass in isolated CI; do not contact production.
- [ ] Commit and request independent AppSec/Supabase review.

### Task 2: Visible KA Play UX improvements

**Files:**
- Modify existing Home/Search/Library/Profile/navigation Compose screens discovered by implementer.
- Create small shared state/focus components only where reused.
- Add common/host Compose tests for empty/loading/error/sync states.
- Add CI selectors for the new tests.

- [ ] Write failing tests for visible state models, focusability/semantics, and real sync failure/reconnect copy.
- [ ] Implement incremental Home spacing/overlap fixes and ad-free time indicator.
- [ ] Implement Search source-setup CTA, empty state, and loading feedback.
- [ ] Implement Library local-vs-cloud distinction and organized sections.
- [ ] Implement Profile sync status states and reconnect action.
- [ ] Improve D-pad/touch focus behavior without redesigning the app.
- [ ] Run Android/host tests through GitHub Actions; attach screenshots when the workflow produces them.
- [ ] Commit and request independent UX/QA review.

### Final integration

- [ ] Review both branch reports and CI URLs.
- [ ] Cherry-pick only reviewed commits into the toolkit branch; do not merge automatically.
- [ ] Update `KA_PLAY_PRODUCTION_GATE.md` with implemented, proven, and untested items.
- [ ] Verify candidate branch `build-tomato-cmp` remains at `e59fcc7f` and no production Supabase access occurred.
