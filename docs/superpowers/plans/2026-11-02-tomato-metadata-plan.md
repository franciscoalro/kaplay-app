# Tomato Metadata Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Garantir que séries Tomato usem nomes, temporadas e episódios reais da API, sem exibir ou persistir identificadores técnicos como título.

**Architecture:** Manter `tomato:<animeId>` como identidade estável. Centralizar parsing e validação no `TomatoProvider`; enriquecer cards de catálogo com detalhes reais; montar detalhes de série a partir de `anime_details`, `anime_seasons` e páginas de episódios. Títulos técnicos não serão considerados nomes válidos.

**Tech Stack:** Kotlin Multiplatform, kotlinx.serialization JSON, Compose Multiplatform, Kotlin coroutines, testes `commonTest`.

**Spec:** `docs/superpowers/specs/2026-11-02-tomato-metadata-design.md`

## Global Constraints

- Não alterar API Tomato, Supabase, schema ou dados remotos.
- Não trocar applicationId nem assinatura APK.
- Não excluir automaticamente itens Tomato já persistidos.
- Não executar rede ou parsing pesado na thread principal.
- Não persistir `Tomato #<id>`, `tomato:<id>` ou título vazio como nome exibível.

## Review Focus

- Payload real com `anime_details.anime_name` deve produzir o nome da série — teste `detailName_readsAnimeDetailsName`.
- Payload com temporadas nomeadas e numeradas deve preservar `season_id` e número — teste `animeDetails_mapsSeasonMetadata`.
- Payload de episódios em `data[]` deve produzir `ep_id`, `ep_number` e `ep_name` — teste `episodeItems_mapsRealEpisodeFields`.
- Campo técnico ou nome ausente não pode virar título de catálogo — teste `titleForDisplay_rejectsTechnicalFallback`.
- Paginação não deve duplicar episódios nem perder o total declarado — teste `seasonEpisodes_deduplicatesAndStopsAtDeclaredTotal`.

---

### Task 1: Fixar o contrato de metadata Tomato com testes

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/kaplay/app/features/tomato/TomatoProvider.kt`
- Test: `composeApp/src/commonTest/kotlin/com/kaplay/app/features/tomato/TomatoProviderTest.kt`

**Interfaces:**
- Consumes: JSON real com `anime_details`, `anime_seasons`, `data`, `episodes`.
- Produces: helpers de parsing que retornam nome válido, `TomatoSeason` e `TomatoEpisode` com IDs/números preservados.

- [ ] **Step 1: Write the failing tests**
  - Adicionar payloads mínimos com `anime_details.anime_name = "Even the Student Council Has Its Holes!"`, temporada `season_id=2256`, `season_number=1`, e episódio `ep_id=37521`, `ep_number=1`, `ep_name="Episódio 1"`.
  - Verificar o nome, o número/ID da temporada e o ID/número/nome do episódio.
  - Adicionar caso de nome vazio/técnico e afirmar que não é um título válido.

- [ ] **Step 2: Run tests to verify failure**
  - Run: `./gradlew :composeApp:allTests --tests '*TomatoProviderTest*'`
  - Expected: FAIL no teste do contrato inválido ou no comportamento ainda não definido.

- [ ] **Step 3: Implement minimal metadata validation**
  - Criar helper interno `isTechnicalTitle(value: String): Boolean` e `displayTitleOrNull(value: String): String?` em `TomatoProvider`.
  - Reconhecer como inválidos strings vazias, `tomato:<id>` e `Tomato #<id>`.
  - Ampliar aliases somente onde o contrato real justificar; preservar os campos atuais.

- [ ] **Step 4: Run tests to verify pass**
  - Run: `./gradlew :composeApp:allTests --tests '*TomatoProviderTest*'`
  - Expected: PASS.

- [ ] **Step 5: Commit**
  - `git add composeApp/src/commonMain/kotlin/com/kaplay/app/features/tomato/TomatoProvider.kt composeApp/src/commonTest/kotlin/com/kaplay/app/features/tomato/TomatoProviderTest.kt`
  - `git commit -m "test: define Tomato metadata contract"`

### Task 2: Impedir fallback técnico no catálogo

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/kaplay/app/features/tomato/TomatoProvider.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/kaplay/app/features/tomato/TomatoCatalog.kt`
- Test: `composeApp/src/commonTest/kotlin/com/kaplay/app/features/tomato/TomatoProviderTest.kt`

**Interfaces:**
- Consumes: `TomatoProvider.displayTitleOrNull` e `animeDetails(animeId)`.
- Produces: `TomatoTitle` com nome real; item sem metadata válida é omitido do catálogo em vez de ser exibido como ID técnico.

- [ ] **Step 1: Add failing regression test**
  - Cobrir item de feed que contém somente `anime_id` e item com `anime_name` real.
  - Afirmar que o item sem nome não gera `Tomato #<id>`.

- [ ] **Step 2: Run focused test and verify failure**
  - Run: `./gradlew :composeApp:allTests --tests '*TomatoProviderTest*'`
  - Expected: FAIL enquanto o fallback `Tomato #$id` existir.

- [ ] **Step 3: Implement catalog behavior**
  - Substituir o fallback técnico em `feedSections` por nome validado.
  - Usar o nome resolvido por `animeDetails` quando o item de feed não trouxer título.
  - Não alterar `id = "tomato:$id"`.

- [ ] **Step 4: Run focused tests**
  - Run: `./gradlew :composeApp:allTests --tests '*TomatoProviderTest*'`
  - Expected: PASS.

- [ ] **Step 5: Commit**
  - `git add composeApp/src/commonMain/kotlin/com/kaplay/app/features/tomato/TomatoProvider.kt composeApp/src/commonMain/kotlin/com/kaplay/app/features/tomato/TomatoCatalog.kt composeApp/src/commonTest/kotlin/com/kaplay/app/features/tomato/TomatoProviderTest.kt`
  - `git commit -m "fix: prevent technical Tomato titles"`

### Task 3: Garantir detalhes, temporadas e episódios reais

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/kaplay/app/features/tomato/TomatoProvider.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/kaplay/app/features/details/MetaDetailsRepository.kt`
- Test: `composeApp/src/commonTest/kotlin/com/kaplay/app/features/tomato/TomatoProviderTest.kt`

**Interfaces:**
- Consumes: `TomatoDetails`, `TomatoSeason`, `TomatoEpisode`.
- Produces: `MetaDetails.name` real e `MetaVideo` com `tomato:<animeId>:<episodeId>`, temporada, episódio e título real.

- [ ] **Step 1: Add failing mapping tests**
  - Testar `animeDetails` com o payload observado e conferir nome, poster/banner, tags e temporada.
  - Testar episódios com `data[]`, deduplicação por `ep_id`, ordenação de páginas e parada por `episodes`.
  - Testar que episódio sem `ep_name` recebe somente o rótulo numérico `E01`, nunca o ID Tomato.

- [ ] **Step 2: Run tests and verify failure**
  - Run: `./gradlew :composeApp:allTests --tests '*TomatoProviderTest*'`
  - Expected: FAIL em qualquer caso de contrato ainda não coberto.

- [ ] **Step 3: Implement the minimal mapping correction**
  - Ajustar apenas parsing de nome, temporadas e episódios conforme o payload real.
  - Validar `season_id` antes de solicitar episódios.
  - Manter chamadas em coroutine fora da UI e preservar IDs estáveis.
  - Em `MetaDetailsRepository`, usar nome validado e não construir metadata exibível com nome técnico.

- [ ] **Step 4: Run focused tests**
  - Run: `./gradlew :composeApp:allTests --tests '*TomatoProviderTest*'`
  - Expected: PASS.

- [ ] **Step 5: Commit**
  - `git add composeApp/src/commonMain/kotlin/com/kaplay/app/features/tomato/TomatoProvider.kt composeApp/src/commonMain/kotlin/com/kaplay/app/features/details/MetaDetailsRepository.kt composeApp/src/commonTest/kotlin/com/kaplay/app/features/tomato/TomatoProviderTest.kt`
  - `git commit -m "fix: load Tomato series seasons and episodes"`

### Task 4: Verificação integrada e evidência

**Files:**
- Modify: `docs/agents/KA_PLAY_RUNTIME_WRITE_FAILURE_20261102.md` only if new runtime evidence is collected.

**Interfaces:**
- Consumes: build e testes das Tasks 1–3.
- Produces: build Debug verificável e evidência de metadata sem alteração de pacote/assinatura.

- [ ] **Step 1: Run full relevant test suite**
  - Run: `./gradlew :composeApp:allTests`
  - Expected: PASS.

- [ ] **Step 2: Run static/build verification**
  - Run: `./gradlew :composeApp:compileFullDebugKotlinAndroid`
  - Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Inspect diff and metadata contract**
  - Run: `git diff --check` and inspect changed files.
  - Expected: no whitespace errors, no package/applicationId/signature changes, no technical title fallback.

- [ ] **Step 4: Commit verification evidence**
  - Update only the runtime evidence document if manual validation was actually performed.
  - `git status --short` must show only intended changes before final report.
