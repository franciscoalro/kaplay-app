---
name: ka-ux-dpad-accessibility
description: "Aprimorar experiência Android TV e mobile de Home, Search, Library, Profiles e estado de sync."
---

# ka-ux-dpad-accessibility

## Acionamento
Aprimorar experiência Android TV e mobile de Home, Search, Library, Profiles e estado de sync.

Leia primeiro `AGENTS.md` e `docs/agents/KA_PLAY_PRODUCTION_GATE.md`. Os procedimentos abaixo não autorizam alterar produção.

## Workflow de especialidade
1. Ler RELATORIO_ADB_PRODUCAO_KA_PLAY.md: badge 1h grátis e bottom nav sobrepõem conteúdo, Search sem CTA.
2. Corrigir empty state de Search para adicionar fonte, Biblioteca local/nuvem e Profile muito denso.
3. Exibir SYNCING/SYNCED/PENDING/OFFLINE/ERROR de verdade sem mentir sobre RPCs falhadas.
4. Verificar focus D-pad, contraste, navegação Back, hit targets e leitor de tela em resoluções variadas.
5. Manter correções UX separadas de migrations SQL e release, registrar screenshots/diff e testes.

## Critérios de aceite
- [ ] Nenhum componente sobreposto e fluxo D-pad utilizável.
- [ ] Erros de sync e estados vazios acionáveis sem mensagem falsa.

## Evidência obrigatória
Commit, branch, link GitHub Actions run/job, testes realmente executados, scripts/reprodução, riscos residuais, rollback, status IMPLEMENTADO/TESTADO/NÃO TESTADO/BLOQUEADO/REQUER AUTORIZAÇÃO. Não publicar release nem mexer em Supabase de produção sem autorização distinta.
