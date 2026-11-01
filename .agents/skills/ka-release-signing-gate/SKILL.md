---
name: ka-release-signing-gate
description: "Garantir chave de assinatura correta, secrets, bump, dry-run e publicação controlada 0.9.20."
---

# ka-release-signing-gate

## Acionamento
Garantir chave de assinatura correta, secrets, bump, dry-run e publicação controlada 0.9.20.

Leia primeiro `AGENTS.md` e `docs/agents/KA_PLAY_PRODUCTION_GATE.md`. Os procedimentos abaixo não autorizam alterar produção.

## Workflow de especialidade
1. Confirmar HEAD/versão e ausência de arquivos funcionais depois do bump; 0.9.20/193 em build-tomato-cmp.
2. Verificar somente existência de KAPLAY_LOCAL_PROPERTIES_BASE64, KAPLAY_RELEASE_KEYSTORE_B64 e propriedades de assinatura, jamais valores.
3. Não usar kaplay-v2-release.jks local porque fingerprint não coincide com APK Release anterior.
4. Propor preflight manual de certificado do keystore GitHub com keytool no runner, sem assinar ou imprimir segredo; bloquear mismatch.
5. Usar android-release.yml apenas dry-run sem autorização de draft/publish; observar runner xcode-27 ainda não testado.

## Critérios de aceite
- [ ] Fingerprint idêntico à assinatura anterior ou BLOQUEADO.
- [ ] Prova de versionCode/tag/CI e autorização específica antes de release.

## Evidência obrigatória
Commit, branch, link GitHub Actions run/job, testes realmente executados, scripts/reprodução, riscos residuais, rollback, status IMPLEMENTADO/TESTADO/NÃO TESTADO/BLOQUEADO/REQUER AUTORIZAÇÃO. Não publicar release nem mexer em Supabase de produção sem autorização distinta.
