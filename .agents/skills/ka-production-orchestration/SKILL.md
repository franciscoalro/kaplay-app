---
name: ka-production-orchestration
description: "Planejar e coordenar as dependências reais para concluir KA Play e tomar decisão go/no-go."
---

# ka-production-orchestration

## Acionamento
Planejar e coordenar as dependências reais para concluir KA Play e tomar decisão go/no-go.

Leia primeiro `AGENTS.md` e `docs/agents/KA_PLAY_PRODUCTION_GATE.md`. Os procedimentos abaixo não autorizam alterar produção.

## Workflow de especialidade
1. Ler AGENTS.md, RELATORIO_ADB_PRODUCAO_KA_PLAY.md, RELATORIO_IMPLEMENTACAO_SYNC_KA_PLAY_ETAPA15.md e matriz de gates.
2. Manter backlog P0/P1 por owner, risco, dependência, teste, SHA e rollback; não repetir relatórios em vez de implementar.
3. Delegar workstreams sync, playback, QA, security, release e UX; designar revisor distinto do autor.
4. Não marcar backend ou Android como produção apenas com CI Debug e dry-run; exigir evidência de dispositivo e API.
5. Consolidar decisão NO-GO quando segredo de release, fingerprint, migração ou testes de usuário estiverem pendentes.

## Critérios de aceite
- [ ] Matriz de gates com status/provas e não só recomendações.
- [ ] Relatório de rollout/rollback e autorizações explícitas para etapas perigosas.

## Evidência obrigatória
Commit, branch, link GitHub Actions run/job, testes realmente executados, scripts/reprodução, riscos residuais, rollback, status IMPLEMENTADO/TESTADO/NÃO TESTADO/BLOQUEADO/REQUER AUTORIZAÇÃO. Não publicar release nem mexer em Supabase de produção sem autorização distinta.
