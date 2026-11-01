---
name: ka-android-player-security
description: "Reforçar playback Media3/OkHttp de KA Play sem regressão TLS, HLS/DASH e sem vazamento de credenciais."
---

# ka-android-player-security

## Acionamento
Reforçar playback Media3/OkHttp de KA Play sem regressão TLS, HLS/DASH e sem vazamento de credenciais.

Leia primeiro `AGENTS.md` e `docs/agents/KA_PLAY_PRODUCTION_GATE.md`. Os procedimentos abaixo não autorizam alterar produção.

## Workflow de especialidade
1. Ler PlaybackRequestSecurityPolicy, PlayerPlaybackNetworking, factories Full/Play Store e YoutubeChunkedDataSourceFactory.
2. Testar MockWebServer HTTPS válido/inválido, hostname errado, redirects cross-origin e same-origin entre recursos, HTTPS→HTTP.
3. Testar Authorization, Cookies, Referer com tokens e URL HTTP user-info/query; never trust-all ou fallback inseguro.
4. Cobrir playlist, segmentos, chaves, áudio alternativo, legendas, MP4, HTTP legado e 65s timeout quando aplicável.
5. Compilar e testar apenas GitHub Actions; verificar reprodução E2E no Android isolado antes de chamar pronto.

## Critérios de aceite
- [ ] TLS inválido falha e HTTPS válido funciona; nenhuma credencial vazada.
- [ ] Relatar risco residual de HTTP assinado e evidência HLS/DASH por dispositivo.

## Evidência obrigatória
Commit, branch, link GitHub Actions run/job, testes realmente executados, scripts/reprodução, riscos residuais, rollback, status IMPLEMENTADO/TESTADO/NÃO TESTADO/BLOQUEADO/REQUER AUTORIZAÇÃO. Não publicar release nem mexer em Supabase de produção sem autorização distinta.
