# KA Play Incremento 2 — integração

## Implementado

- Frontend: estados de sincronização corrigidos (`Verificando sincronização…` em `LOADING`) e ação funcional `Tentar novamente` ligada ao `SyncManager.pullAllForProfile`; Biblioteca existente auditada para loading/empty/offline/error.
- Persistência P0: filas pendentes de Biblioteca, progresso e assistidos agora são reexecutadas após login, abertura, troca de perfil, reconexão/pull e falha transitória, com backoff limitado e confirmação somente após retorno bem-sucedido da RPC. Exclusões de progresso e itens assistidos também persistem como entradas pendentes.
- Backend proposto: tabelas e RPCs profile-scoped para favoritos/histórico, RLS, paginação por cursor, replay idempotente e monotonicidade de histórico. A alteração permanece em `supabase/proposed/`; não foi aplicada em produção.
- QA: teste SQL determinístico de reabertura de sessão, logout/login, isolamento entre contas/perfis e replay offline/reconexão.
- AppSec: revisão independente e redaction dos refresh tokens no backup de homologação atual.

## Evidências

- Isolado local: `bash supabase/tests/run_etapa15_sync_v2_isolated.sh` → `isolated_sql_status=PASS`; evidência `INC2_AFTER_REPLAY` confirmou `library_items=1`, `watch_progress=1`, `watched_items=1`, e `ETAPA20_REOPEN_IDENTITY_QUEUE_OK`.
- CI integrado SQL/sync: run [41388880617](https://github.com/franciscoalro/kaplay-app/actions/runs/41388880617) → credential scan, PostgreSQL isolado e sync host **success**; inclui backoff da fila P0, filas de exclusão e o teste de persistência/reabertura dos três domínios.
- CI integrado Kotlin/host: run [41338931981](https://github.com/franciscoalro/kaplay-app/actions/runs/41338931981) → compile Full/Play Store, TLS checks e host tests **success**; somente Pages foi bloqueado por proteção de ambiente.
- AppSec report: `docs/agents/KA_PLAY_INCREMENTO2_APPSEC_REPORT.md`.
- QA report: `docs/agents/KA_PLAY_INCREMENTO2_QA_REPORT.md`.

## Pendências críticas

- Histórico Git ainda contém o backup original com tokens; a cópia atual está redigida, mas rotação/remoção histórica requer operação de segurança separada.
- RPCs v2 ainda estão em `supabase/proposed/`, portanto deployabilidade hospedada não foi aprovada.
- Android instrumentado exige secrets de backend isolado ausentes.
- Conflitos de escrita de perfis e filtro local de progresso permanecem fora deste slice.

## APK Debug

Run [41384340077](https://github.com/franciscoalro/kaplay-app/actions/runs/41384340077) gerou o Full Debug com endpoint Supabase autorizado explicitamente no workflow. Artefato `ka-play-full-debug-build-only`; SHA-256 `463fb92680b6d0cb71ba34070bfd01c729c776a3921830b31e68807fef9f66e8`; `com.kaplay.app.debug`, versionName `0.9.20`, versionCode `193`. O scan de credenciais privilegiadas passou.

As capturas do emulador foram produzidas nos runs [41341327563](https://github.com/franciscoalro/kaplay-app/actions/runs/41341327563) e [41344922133](https://github.com/franciscoalro/kaplay-app/actions/runs/41344922133), mas mostram diálogos do sistema (`System UI/Settings isn't responding`); não são screenshots válidas da Biblioteca/perfil. O teste Compose instrumentado foi tentado nos runs [41350402365](https://github.com/franciscoalro/kaplay-app/actions/runs/41350402365) e [41352092751](https://github.com/franciscoalro/kaplay-app/actions/runs/41352092751), mas a instalação falhou com `Device ... API level=1`. O workflow inspeciona `uiautomator` e valida assinatura PNG antes de aceitar qualquer artefato.

Produção continua NO-GO para migrations/publicação. Nenhuma migration foi executada no Supabase de produção, nenhum APK Release foi assinado e não houve publicação automática.
