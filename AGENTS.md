# KA Play — instruções persistentes dos agentes

Estas instruções orientam agentes Codex e demais assistentes que trabalham no repositório `franciscoalro/kaplay-app`. Especializações: `.agents/skills/*/SKILL.md`; perfis personalizados: `.codex/config.toml` e `.github/agents/`; status: `docs/agents/KA_PLAY_PRODUCTION_GATE.md`.

## Missão
Concluir KA Play com backend compatível, integridade de perfis, reprodução, recompensas, segurança, UX, testes Android e assinatura verificada. **CI verde e dry-run não são homologação de produção.** Distinguir `IMPLEMENTADO`, `TESTADO`, `NÃO TESTADO`, `BLOQUEADO`, `REQUER AUTORIZAÇÃO`.

## Regras de segurança e produção
- Supabase de produção: `iibureqvpurmbgqupnvl`. Nunca aplicar migrations, DDL, limpeza, mudança de Auth/RLS, crédito publicitário ou escritas a dados reais sem aprovação expressa por operação. Antes: simulação isolada, inventário, backup restaurável, rollback, compatibilidade do APK antigo.
- Build/testes Android/iOS **exclusivamente no GitHub Actions**. Não executar `gradlew`, `java`, `kotlinc`, assembly ou instalação na VPS. Uma execução não efetuada é `NÃO TESTADO`.
- Nunca executar release `draft`/`publish`, `publish_release=true`, assinatura, upload de APK/IPA ou instalação automática sem aprovação distinta.
- Segredos: nunca revelar keystore, senha, JWT, tokens, `local.properties`, `service_role` ou `TRAKT_CLIENT_SECRET`; segredo Trakt nunca dentro do APK. Não inventar secrets.
- TLS: nunca reintroduzir trust-all, hostname verifier permissivo, fallback HTTPS→HTTP inseguro ou credenciais em HTTP, redirects e playlists HLS/DASH.
- Android 10 instalado usa `com.kaplay.app` e ADB conhecido `127.0.0.1:5555`. Não reinstalar, limpar dados, reiniciar ADB ou alterar sessão/recompensas do usuário sem aprovação explícita; testes destrutivos em ambiente isolado.
- Branch `build-tomato-cmp` contém versão candidata `0.9.20`/`versionCode 193`, bump `e59fcc7f58678ac9dc1e483ab786a3d40f255349`. Alteração após o bump invalida a política estrita. Este toolkit está em **branch separada**, não fazer merge na candidata antes de planejar nova versão.
- Em revisão, quem escreveu o patch não aprova a própria correção; diferenciar risco P0 de UX P1.

## Procedimento
1. Ler evidências existentes (`RELATORIO_ADB_PRODUCAO_KA_PLAY.md`, `RELATORIO_IMPLEMENTACAO_SYNC_KA_PLAY_ETAPA15.md`, `PLANO_CORRECAO_PRODUCAO_KA_PLAY.md`) e verificar estado real do HEAD.
2. Selecionar skill correspondente e delegar implementação + revisão de segurança/QA independentes.
3. Trabalhar somente em branch isolada, planejar teste e rollback; respeitar escopo.
4. Executar CI existente; registrar SHA, workflow, run/job, variante, artefato, resultado real e falhas separadas (Pages ≠ Android).
5. Entregar matriz de gate e solicitar autorização humana por etapa. Em bloqueio P0, **NO-GO**.

## Bloqueios prioritários conhecidos (2026-11-01)
- `KAPLAY_LOCAL_PROPERTIES_BASE64` ausente no GitHub; secrets individuais de assinatura presentes, mas workflow de release ainda não está plenamente provisionado.
- Fingerprint SHA-256 da assinatura do APK Release anterior `97807c6f56415754cffb6d2ffb6cdff642b30bffc3f892dd9bbf513f839e99a8`; `kaplay-v2-release.jks` local `71625f1fdccf6f556fd6fb0e608f505fb829c76edbfd2a03a640f324dd5a18d`. **Não coincidem.** Secret do GitHub não inspecionado. Não assinar com chave substituta.
- Runner `xcode-27` declarado, mas nunca exercitado em release.
- Sync Kotlin v2/SQL candidatos Etapa 15 têm testes isolados; migrations **não aplicadas** em produção. APK antigo exibiu `PGRST202`, homologação de compatibilidade pendente.
- Reprodução real, fluxos de perfis, favoritos, recompensas e UX em Android ainda precisam de evidência funcional.

## Coordenação
Orquestrador `ka-delivery-lead`, especialistas `ka-supabase-sync`, `ka-android-player`, `ka-appsec-reviewer`, `ka-qa-engineer`, `ka-release-engineer`, `ka-ux-engineer`. Subagentes NÃO têm permissão automática para executar ações de produção.
