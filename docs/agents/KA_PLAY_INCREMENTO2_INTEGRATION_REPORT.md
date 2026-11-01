# KA Play Incremento 2 — integração

## Implementado

- Frontend: estados de sincronização corrigidos (`Verificando sincronização…` em `LOADING`) e ação funcional `Tentar novamente` ligada ao `SyncManager.pullAllForProfile`; Biblioteca existente auditada para loading/empty/offline/error.
- Backend proposto: tabelas e RPCs profile-scoped para favoritos/histórico, RLS, paginação por cursor, replay idempotente e monotonicidade de histórico. A alteração permanece em `supabase/proposed/`; não foi aplicada em produção.
- QA: teste SQL determinístico de reabertura de sessão, logout/login, isolamento entre contas/perfis e replay offline/reconexão.
- AppSec: revisão independente e redaction dos refresh tokens no backup de homologação atual.

## Evidências

- Isolado local: `bash supabase/tests/run_etapa15_sync_v2_isolated.sh` → `isolated_sql_status=PASS`.
- CI integrado SQL/sync: run [41346203822](https://github.com/franciscoalro/kaplay-app/actions/runs/41346203822) → credential scan, PostgreSQL isolado e sync host **success**.
- CI integrado Kotlin/host: run [41338931981](https://github.com/franciscoalro/kaplay-app/actions/runs/41338931981) → compile Full/Play Store, TLS checks e host tests **success**; somente Pages foi bloqueado por proteção de ambiente.
- AppSec report: `docs/agents/KA_PLAY_INCREMENTO2_APPSEC_REPORT.md`.
- QA report: `docs/agents/KA_PLAY_INCREMENTO2_QA_REPORT.md`.

## Pendências críticas

- Histórico Git ainda contém o backup original com tokens; a cópia atual está redigida, mas rotação/remoção histórica requer operação de segurança separada.
- RPCs v2 ainda estão em `supabase/proposed/`, portanto deployabilidade hospedada não foi aprovada.
- Android instrumentado exige secrets de backend isolado ausentes.
- Conflitos de escrita de perfis e filtro local de progresso permanecem fora deste slice.

## APK Debug

Run [41341327563](https://github.com/franciscoalro/kaplay-app/actions/runs/41341327563) gerou o Full Debug com endpoint Supabase autorizado explicitamente no workflow. Artefato `ka-play-full-debug-build-only`; SHA-256 `a1bb48b0ed674eb9ff903ce34d806c5b55c3b6bc020e645dc3a0802725f0ffdb`; `com.kaplay.app.debug`, versionName `0.9.20`, versionCode `193`. O scan de credenciais privilegiadas passou.

As capturas do emulador foram produzidas nos runs [41341327563](https://github.com/franciscoalro/kaplay-app/actions/runs/41341327563) e [41344922133](https://github.com/franciscoalro/kaplay-app/actions/runs/41344922133), mas mostram diálogos do sistema (`System UI/Settings isn't responding`); não são screenshots válidas da Biblioteca/perfil. O workflow agora inspeciona `uiautomator` e falha explicitamente quando detecta esse tipo de captura.

Produção continua NO-GO para migrations/publicação. Nenhuma migration foi executada no Supabase de produção, nenhum APK Release foi assinado e não houve publicação automática.
