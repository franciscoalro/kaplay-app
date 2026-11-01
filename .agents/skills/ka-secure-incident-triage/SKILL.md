---
name: ka-secure-incident-triage
description: "Investigar PGRST202, crashes, regressões TLS e falhas CI com diagnóstico sanitizado e rollback."
---

# ka-secure-incident-triage

## Acionamento
Investigar PGRST202, crashes, regressões TLS e falhas CI com diagnóstico sanitizado e rollback.

Leia primeiro `AGENTS.md` e `docs/agents/KA_PLAY_PRODUCTION_GATE.md`. Os procedimentos abaixo não autorizam alterar produção.

## Workflow de especialidade
1. Registrar versão, SHA, timestamp, erro, ambiente e impacto sem JWT, senha, token ou dados do usuário.
2. Separar infraestrutura CI/Pages, erro de API, erro TLS e execução não testada.
3. Usar consultas read-only de funções/grants e logs sanitizados antes de propor qualquer SQL.
4. Reproduzir em ambiente isolado, corrigir causa mínima, acrescentar teste de regressão e definir rollback.
5. Não esconder erro criando RPC fake nem liberar recompensa artificial para testes em produção.

## Critérios de aceite
- [ ] Relatório de causa raiz e reprodução com evidência.
- [ ] Patches reversíveis, sem mudanças de produção não aprovadas.

## Evidência obrigatória
Commit, branch, link GitHub Actions run/job, testes realmente executados, scripts/reprodução, riscos residuais, rollback, status IMPLEMENTADO/TESTADO/NÃO TESTADO/BLOQUEADO/REQUER AUTORIZAÇÃO. Não publicar release nem mexer em Supabase de produção sem autorização distinta.
