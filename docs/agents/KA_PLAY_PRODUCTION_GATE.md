# KA Play — Gate de produção e agentes especializados

**Última referência:** 2026-11-01. **Decisão:** NO-GO para publicação até concluir todos os P0.

## Perfis de operação
- Codex: raiz `AGENTS.md`, skills `.agents/skills/*/SKILL.md`, perfis em `.codex/config.toml` e `.codex/agents/` (dependendo do suporte do cliente Codex).
- GitHub Copilot, onde disponível: `.github/agents/*.agent.md`.
- `ka-delivery-lead` atribui tarefas; especialistas implementam; `ka-appsec-reviewer` e `ka-qa-engineer` fazem revisões independentes. Caso a ferramenta não execute subagentes, executar os papéis sequencialmente e informar essa limitação.
- Os arquivos definem instruções reutilizáveis; **não criam processos autônomos permanentes**.

## Matriz de gates
| Gate | Responsável | Situação conhecida | Evidência para GO |
|---|---|---|---|
| Backend/sync/RLS e compatibilidade 0.9.18→0.9.20 | ka-supabase-sync | **Incremento 1 isolado CI verde; anonymous/RLS sintético coberto; hosted RLS/concurrency ainda limitados** | Dois clientes, ownership, legado, 1201+ registros, rollback, backup |
| Android real, offline, perfis e recompensas | ka-qa-engineer | **BLOQUEADO por secrets/backend isolado** | Testes instrumentados, ADB isolado, logs/screenshot sanitizados |
| Playback e TLS em dispositivo | ka-android-player | Host tests CI aprovados; E2E pendente | MP4/HLS/DASH/legendas/redirect/HTTP compatível |
| Autenticação, privacidade, segredos | ka-appsec-reviewer | **Trakt state corrigido; secure storage/TLS fallback ainda P0/P1 aberto** | Sem cross-user, sem bypass TLS, sem secret no APK |
| Identidade de assinatura | ka-release-engineer | **STATIC PASS; execução bloqueada/não testada** | Fingerprint do keystore real do GitHub igual ao APK anterior |
| Secrets de release | ka-release-engineer | **BLOQUEADO** | `KAPLAY_LOCAL_PROPERTIES_BASE64` válido, sem expor senhas |
| iOS release | ka-release-engineer | Não testado | Runner `xcode-27`, assinatura e artefatos em fluxo autorizado |
| UX/TV e suporte | ka-ux-engineer | Parcial | Home, Search, Library, sync state, D-pad, telas/fluxos |
| Aprovação humana de rollout | Proprietário | Pendente | Plano explícito de deploy, smoke, monitoramento e rollback |

## Ação importante de assinatura
A fingerprint informada para APK Release anterior é `97807c6f56415754cffb6d2ffb6cdff642b30bffc3f892dd9bbf513f839e99a8`. O keystore local `kaplay-v2-release.jks` apresenta `71625f1fdccf6f556fd6fb0e608f505fb829c76edbfd2a03a640f324dd5a18d`; portanto NÃO usar automaticamente. É possível verificar **o certificado público** do secret `KAPLAY_RELEASE_KEYSTORE_B64` com um job manual read-only e temporário que decodifica no runner e usa `keytool` para comparar SHA-256, sem executar assinatura/assembly ou imprimir senhas. O job requer aprovação separada para alteração e execução.

## Evidências desta rodada (sem acesso a produção)
- Progress v2: commits `ea4ae7d5`–`655e82ff`; workflow `ka-play-sync-tests.yml`, run [41307336701](https://github.com/franciscoalro/kaplay-app/actions/runs/41307336701), job `testAndroidHostTest (sync)` **success**. O adapter não chama mais `sync_pull_watch_progress`; `sinceLastWatched` permanece filtro `>` local, sem inversão do cursor DESC; limites inválidos são rejeitados.
- Trakt callback state: commits `c755eba3`–`d6e2616f`; run [41306618106](https://github.com/franciscoalro/kaplay-app/actions/runs/41306618106), job `compileFullDebugKotlin` **success**. O workflow geral também registra `publish-plugin-pages` protegido/falhou; isso não invalida o job de compilação/testes.
- Android isolado: commit `44483ce3`; run [41307060306](https://github.com/franciscoalro/kaplay-app/actions/runs/41307060306) parou antes do build porque `KAPLAY_ISOLATED_BASE_URL`, allowlist e credenciais estavam vazios. Nenhum backend de produção foi usado.
- Keystore: commits `80ac9295`/`5f2242b1`; workflow read-only com `keytool`, checkout apenas para self-check, sem Gradle/assembly/assinatura/upload/publicação. Execução e fingerprint do secret GitHub continuam **não testados**; não fazer dispatch sem autorização.
- SQL isolado Etapa 18: commits `e410e9d2`–`f96d72f2` e relatório `b96a6f39`; run [41311582090](https://github.com/franciscoalro/kaplay-app/actions/runs/41311582090) com jobs PostgreSQL isolado e sync host **success**. Artifact `ka-etapa18-isolated-sql-41311582090`; runner propaga falhas, redige senha sintética e executa rewards como `authenticated`. Follow-up de quota em `304c3aef`/`a851d11b`, run [41312864564](https://github.com/franciscoalro/kaplay-app/actions/runs/41312864564) **success**, artifact `ka-etapa18-isolated-sql-41312864564`. Ainda não é validação de produção: corrida concorrente e matriz RLS hospedada seguem pendentes.
- Incremento 1 contrato perfil-sync: commits `439fbbd7`/`bf9903e0`/`6cb00312`; contract `docs/contracts/ka-profile-sync-v1.md` includes `user_id` + `profile_index`, host test `ProfileSyncContractTest`, CI run [41315815417](https://github.com/franciscoalro/kaplay-app/actions/runs/41315815417) has compile/host tests **success**; Pages remains protected. Production remains untouched.
- Incremento 2 Biblioteca/sync: commits integrados `f8eadf0c`–`d528f241` e relatório `docs/agents/KA_PLAY_INCREMENTO2_INTEGRATION_REPORT.md`; SQL local e run [41346203822](https://github.com/franciscoalro/kaplay-app/actions/runs/41346203822) **success**; Kotlin/host run [41338931981](https://github.com/franciscoalro/kaplay-app/actions/runs/41338931981) compilação/testes **success** e Pages protegido. RPCs v2 continuam em `supabase/proposed/`, Android instrumentado aguarda secrets isolados, e revisão AppSec mantém NO-GO até resolver histórico de tokens/concorrência.
- Incremento 1 vertical perfis/sync: backend commits `05abb697`–`cd9d49a5`, report `a95a8959`, final isolated CI [41321783803](https://github.com/franciscoalro/kaplay-app/actions/runs/41321783803) **success** with artifact `ka-etapa18-isolated-sql-41321783803`; explicit `is_anonymous` RLS/RPC protection and realistic anonymous fixture verified. Frontend commits `a47f71d8`–`7a3f46a0`, report `2cb9c57a`, CI [41322738233](https://github.com/franciscoalro/kaplay-app/actions/runs/41322738233) compile/host **success**; Pages protected. Both branches consumed `ka-profile-sync-v1`; no production access.
- UX Etapa 18: Home em `cc730312`–`ab286dae`, relatório `2cf44e6a`/`0ab44fd5`; run [41311512200](https://github.com/franciscoalro/kaplay-app/actions/runs/41311512200), compilação/testes host **success**, Pages protegido. Profile sync em `9e0f14d6`/`3ed07950`, relatório `e5b11847`; run [41313486083](https://github.com/franciscoalro/kaplay-app/actions/runs/41313486083), compilação/testes host **success**, Pages protegido. Entregues estados visíveis Home e Profile/Cloud; Search, Library ampla, D-pad/device screenshots e TalkBack seguem não testados.
- Rewards: commits `2f55b426`/`facf11d3`/`fe7be19b`/`580f46a5` integrados; isolamento local, refresh por `profile_index`, ownership/idempotência SQL e gate in-flight. No run [41307619233](https://github.com/franciscoalro/kaplay-app/actions/runs/41307619233), `compileFullDebugKotlin` **success** e testes host passaram; `publish-plugin-pages` foi bloqueado por proteção de ambiente. Permanecem quota concorrente, perfil trocado durante anúncio, nonce/ad proof e refresh de estado ao trocar perfil.

**Decisão permanece NO-GO:** não fazer merge automático, migration em `iibureqvpurmbgqupnvl`, build/assinatura/publicação/instalação de APK ou alteração de `build-tomato-cmp`.

## Ordem de trabalho
1. P0: backend RPC v2 e contrato do cliente, testes isolados e compatibilidade.
2. P0: homologação Android/TV, recompensas e regressões sem produção real.
3. P0: chave de assinatura e secrets, CI de releases e rollback.
4. P1: UX vazios, D-pad, acessibilidade e erros visíveis.
5. Geração de release **somente após autorização**; versão `0.9.20` não admite alterações posteriores ao bump sem replanejamento.

## Handoff obrigatório de qualquer agente
- Base SHA e head SHA, arquivos, scope, testes executados vs. não executados.
- Links CI workflow/run/job, ambiente, screenshots/logs sem segredos.
- P0/P1, risco residual, rollback, bloqueios e ações que exigem aprovação.
- Sem GO quando algum P0 estiver bloqueado, não testado ou reprovado.
