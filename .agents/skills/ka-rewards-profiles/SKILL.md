---
name: ka-rewards-profiles
description: "Testar bônus 1h sem anúncios, idempotência rewarded, perfis, saldo temporal e isolamento."
---

# ka-rewards-profiles

## Acionamento
Testar bônus 1h sem anúncios, idempotência rewarded, perfis, saldo temporal e isolamento.

Leia primeiro `AGENTS.md` e `docs/agents/KA_PLAY_PRODUCTION_GATE.md`. Os procedimentos abaixo não autorizam alterar produção.

## Workflow de especialidade
1. Extrair comportamento atual do código e contratos; não presumir que regras antigas estão implementadas.
2. Em ambiente isolado testar grant uma vez por evento, duplicatas/replay, cancelamento, limite e reset diário.
3. Trocar perfil, reiniciar app, logout/login e usar múltiplos usuários; validar que um perfil nunca herda bônus de outro.
4. Testar offline, expiração, mudança de relógio/fuso e privacidade; não conceder tempo real em produção.
5. Produzir matriz de teste e correções com relógio controlado, sem tornar monetização insegura.

## Critérios de aceite
- [ ] Sem créditos indevidos ou duplicações/reexposição entre perfis.
- [ ] Teste de anúncio concluído vs. cancelado, execução comprovada.

## Evidência obrigatória
Commit, branch, link GitHub Actions run/job, testes realmente executados, scripts/reprodução, riscos residuais, rollback, status IMPLEMENTADO/TESTADO/NÃO TESTADO/BLOQUEADO/REQUER AUTORIZAÇÃO. Não publicar release nem mexer em Supabase de produção sem autorização distinta.
