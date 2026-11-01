# KA Play Incremento 1 — integração vertical

## Entregue

- Contrato compartilhado `docs/contracts/ka-profile-sync-v1.md` com `user_id`, `profile_index`, RPCs, estados e erros.
- Backend isolado em branch `agentic/ka-incremento1-backend`: RLS/RPC assertions de ownership, cross-user, perfil, anônimo real e limites.
- Frontend isolado em branch `agentic/ka-incremento1-frontend`: ProfileSelectionScreen consome `ProfileSyncContractView` e estados reais por `userId`/`profileIndex`; ausência de perfil não exibe falso estado cloud-ready.

## Evidência

- Backend run `41321783803`: PostgreSQL isolado + sync host **success**; artifact `ka-etapa18-isolated-sql-41321783803`.
- Frontend run `41322738233`: compilação e host tests **success**; Pages protegido não é teste funcional.
- Revisões independentes:
  - Backend: PASS para isolamento SQL/RLS sintético; hosted RLS, concorrência e Android E2E permanecem não testados.
  - Frontend: PASS para integração de contrato/estado; UX completo, D-pad/TalkBack, Compose semantics, screenshots e retry clicável permanecem pendentes.

## Integração

Commits revisados foram cherry-picked para `agentic/ka-play-production-toolkit-20261101`, HEAD `53764b67`. `build-tomato-cmp` permanece em `e59fcc7f58678ac9dc1e483ab786a3d40f255349`.

Produção continua **NO-GO**. Nenhum acesso ao Supabase produtivo, APK, assinatura, publicação ou merge automático foi executado.
