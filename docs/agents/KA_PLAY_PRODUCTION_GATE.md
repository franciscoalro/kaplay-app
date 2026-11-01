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
| Backend/sync/RLS e compatibilidade 0.9.18→0.9.20 | ka-supabase-sync | Etapa 15 implementada/testada em isolamento, produção não migrada | Dois clientes, ownership, legado, 1201+ registros, rollback, backup |
| Android real, offline, perfis e recompensas | ka-qa-engineer | Pendente | Testes instrumentados, ADB isolado, logs/screenshot sanitizados |
| Playback e TLS em dispositivo | ka-android-player | Host tests CI aprovados; E2E pendente | MP4/HLS/DASH/legendas/redirect/HTTP compatível |
| Autenticação, privacidade, segredos | ka-appsec-reviewer | Parcial | Sem cross-user, sem bypass TLS, sem secret no APK |
| Identidade de assinatura | ka-release-engineer | **BLOQUEADO** | Fingerprint do keystore real do GitHub igual ao APK anterior |
| Secrets de release | ka-release-engineer | **BLOQUEADO** | `KAPLAY_LOCAL_PROPERTIES_BASE64` válido, sem expor senhas |
| iOS release | ka-release-engineer | Não testado | Runner `xcode-27`, assinatura e artefatos em fluxo autorizado |
| UX/TV e suporte | ka-ux-engineer | Parcial | Home, Search, Library, sync state, D-pad, telas/fluxos |
| Aprovação humana de rollout | Proprietário | Pendente | Plano explícito de deploy, smoke, monitoramento e rollback |

## Ação importante de assinatura
A fingerprint informada para APK Release anterior é `97807c6f56415754cffb6d2ffb6cdff642b30bffc3f892dd9bbf513f839e99a8`. O keystore local `kaplay-v2-release.jks` apresenta `71625f1fdccf6f556fd6fb0e608f505fb829c76edbfd2a03a640f324dd5a18d`; portanto NÃO usar automaticamente. É possível verificar **o certificado público** do secret `KAPLAY_RELEASE_KEYSTORE_B64` com um job manual read-only e temporário que decodifica no runner e usa `keytool` para comparar SHA-256, sem executar assinatura/assembly ou imprimir senhas. O job requer aprovação separada para alteração e execução.

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
