---
name: ka-supabase-sync-integrity
description: "Validar contratos Kotlin↔RPC, migrations A1/B2/C3 v2, perfil UUID e RLS sem alterar banco de produção."
---

# ka-supabase-sync-integrity

## Acionamento
Validar contratos Kotlin↔RPC, migrations A1/B2/C3 v2, perfil UUID e RLS sem alterar banco de produção.

Leia primeiro `AGENTS.md` e `docs/agents/KA_PLAY_PRODUCTION_GATE.md`. Os procedimentos abaixo não autorizam alterar produção.

## Workflow de especialidade
1. Comparar cliente v2 com funções e assinaturas reais em ambiente isolado; PGRST202 do APK legado é risco de compatibilidade.
2. Testar identity UUID estável, reexposição após delete/recriação, cross-user e authenticated anônimo, grants e SECURITY INVOKER.
3. Exercitar keyset com 1201+ registros, batching ≤500, delete watched com content_type, tombstones, concorrência e stale progress.
4. Verificar nenhum sucesso falso do SyncManager, cursores preservados na falha, retries limitados e persistência local.
5. Produzir diff, backup restaurável, compatibilidade v1/v2 e rollback; nenhuma migration em iibureqvpurmbgqupnvl sem aprovação específica.

## Critérios de aceite
- [ ] Testes SQL isolados reproduzíveis, ownership comprovado.
- [ ] Tabela de RPC compatível por versão Android, no-go se faltarem RPCs em produção.

## Evidência obrigatória
Commit, branch, link GitHub Actions run/job, testes realmente executados, scripts/reprodução, riscos residuais, rollback, status IMPLEMENTADO/TESTADO/NÃO TESTADO/BLOQUEADO/REQUER AUTORIZAÇÃO. Não publicar release nem mexer em Supabase de produção sem autorização distinta.
