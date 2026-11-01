---
name: ka-android-qa-gates
description: "Organizar testes de integração, CI Android, emulator, falhas de sincronização e evidências de release."
---

# ka-android-qa-gates

## Acionamento
Organizar testes de integração, CI Android, emulator, falhas de sincronização e evidências de release.

Leia primeiro `AGENTS.md` e `docs/agents/KA_PLAY_PRODUCTION_GATE.md`. Os procedimentos abaixo não autorizam alterar produção.

## Workflow de especialidade
1. Usar build.yml, ka-play-sync-tests.yml, ka-play-integration-android.yml (último apenas backend isolado allowlisted).
2. Executar Full Debug/Play Store Debug e host tests CI; skipped não é sucesso e Pages é separado de Android.
3. Testar login, 2 usuários e perfis, biblioteca, favoritos, progress 1201+, rede offline, reconexão e retomada.
4. Testar reprodução MP4/HLS/DASH, subtitles e chamadas abortadas; documentar APK/OS/sha e passos reproduzíveis.
5. Não reinstalar com.kaplay.app de produção nem limpar sessão atual; logs/screenshot sem dados sensíveis.

## Critérios de aceite
- [ ] Evidência objetiva de cada fluxo e ambiente; não confundir compilação com homologação.
- [ ] Relatório de falhas P0 com reprodução e teste de regressão.

## Evidência obrigatória
Commit, branch, link GitHub Actions run/job, testes realmente executados, scripts/reprodução, riscos residuais, rollback, status IMPLEMENTADO/TESTADO/NÃO TESTADO/BLOQUEADO/REQUER AUTORIZAÇÃO. Não publicar release nem mexer em Supabase de produção sem autorização distinta.
