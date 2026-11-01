# Homologação — Nuvio Mobile (KA Play) — `~/cloudstream_gate/nuvio-mobile`

**Projeto:** `nuvio-mobile` (symlink → `kaplay-app`) — Kotlin Multiplatform (Android + iOS), Compose Multiplatform
**Branch:** `kaplay-rebrand`
**Commit base:** `584f34d707f3b279173f88c78f01059cc310156e` — `test(network): qualify repository status helper`
**Data da homologação:** 26/10/2026
**Responsável:** agente DSH (ambiente Linux, JDK 17, Android SDK API 34/35/37)
**Pacote Android:** `br.com.kaplay.app` (full debug / full release)

---

## 0. Identidade e escopo

- **Alvo de homologação:** compilação Kotlin (`:androidApp:compileFullDebugKotlin`), testes de host (`:composeApp:testAndroidHostTest`) e smoke de assemble (`:androidApp:assembleFullDebug`).
- **Teste unitário de referência (CI):** `com.kaplay.app.features.tomato.TomatoProviderTest` (5 casos).
- **Workflow canônico:** `.github/workflows/build.yml` — job `compile-full-debug` (compile + unit test) e `assemble-full-release`.
- **Limitações do ambiente:** sem macOS → alvos iOS desabilitados; sem keystore de release → apenas debug/compile validáveis localmente.

---

## 1. Verificação automatizada (tentativa em 26/10/2026)

### 1.1 Comandos executados

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export ANDROID_HOME=/usr/lib/android-sdk
export ANDROID_SDK_ROOT=/usr/lib/android-sdk
./gradlew :androidApp:compileFullDebugKotlin --no-daemon --no-parallel --max-workers=1 --stacktrace
./gradlew :composeApp:testAndroidHostTest --tests 'com.kaplay.app.features.tomato.TomatoProviderTest' --no-daemon --no-parallel --max-workers=1 --stacktrace
```

### 1.2 Resultado observado

- **Compile Kotlin:** NÃO concluído neste ambiente. O build foi interrompido repetidamente por outros agentes da sessão que derrubaram o daemon do Gradle (`DaemonDisappearedException: Gradle build daemon disappeared unexpectedly`). O código não apresentou erros de compilação até o ponto de interrupção (última tarefa concluída: `:composeApp:compileAndroidMain` em andamento).
- **Unit tests:** NÃO executados até o fim pelo mesmo motivo (daemon derrubado durante `:composeApp:compileAndroidMain`).
- **Causa raiz do bloqueio:** concorrência de sessões DSH no mesmo host, não defeito do projeto. O `build.yml` (GitHub Actions, runner `ubuntu-latest`, JDK 17 Temurin) é a fonte canônica e não sofre essa interferência.

### 1.3 Ação recomendada

Disparar o workflow `kaplay-build` via `workflow_dispatch` no repositório (ou push na branch `kaplay-rebrand`) para obter evidência limpa de `compileFullDebugKotlin` + `TomatoProviderTest`. Até lá, a homologação local fica marcada como **NÃO EXECUTADA** (não aprovada nem reprovada).

---

## 2. Verificação estática (realizada)

- [x] `local.properties` aponta para `/usr/lib/android-sdk` (API 34/35/37 presentes).
- [x] JDK 17 disponível (`/usr/lib/jvm/java-17-openjdk-amd64`).
- [x] Gradle 9.4.1 wrapper funcional.
- [x] Estrutura de fontes: `composeApp/src/commonMain`, `androidMain`, `commonTest` (TomatoProviderTest).
- [x] Teste de referência presente: `composeApp/src/commonTest/kotlin/com/kaplay/app/features/tomato/TomatoProviderTest.kt` (5 casos: feed cards, detail envelope, stream header `x-app=1.4.3`, home categories, parse de ids).
- [x] `settings.gradle.kts` inclui `:composeApp` e `:androidApp`; MPVKit (submódulo) não é necessário para compilar (referenciado apenas em `LicensesAttributionsPage.kt`).
- [x] Sem diretório de testes Android instrumentados (`androidTest`) — apenas testes de host KMP.

---

## 3. Checklist de homologação manual (espelho de HOMOLOGACAO-REDMI, adaptado)

### 3.1 Build & empacotamento (BUILD-01)

- [ ] `./gradlew :androidApp:compileFullDebugKotlin` → BUILD SUCCESSFUL, 0 erros.
- [ ] `./gradlew :composeApp:testAndroidHostTest --tests 'com.kaplay.app.features.tomato.TomatoProviderTest'` → 5/5 testes aprovados.
- [ ] `./gradlew :androidApp:assembleFullDebug` → APK gerado em `androidApp/build/outputs/apk/full/debug/`.
- [ ] `./gradlew :androidApp:assembleFullRelease` (com keystore) → APK assinado.
- [ ] Lint `fullDebug` sem erros bloqueantes.
- [ ] ABI da biblioteca nativa aprovada (MPV).

### 3.2 Instalação e inicialização (BASE-01)

- [ ] APK instala sem erro em device/emulador (Android 13/14/15).
- [ ] App abre na Home sem crash.
- [ ] Troca de todas as abas (Início, Busca, Biblioteca, Downloads, Configurações).
- [ ] Rotação retrato ↔ paisagem preserva posição.
- [ ] Voltar retorna à tela anterior.

### 3.3 Provedor Tomato (TOMATO-01)

- [ ] Home exibe categorias ("Com dublagem", "Romance" e outras).
- [ ] Detalhes de anime carrega nome e temporadas.
- [ ] Stream inicia com header `x-app: 1.4.3`.
- [ ] Parse de ids `tomato:<id>` e `tomato:<id>:<ep>` funcional.

### 3.4 Reprodução (PLAY-01)

- [ ] Assistir → pausar → avançar → sair → reabrir.
- [ ] Progresso salvo e retomado em ≤ 5s.
- [ ] Troca de episódio (série) reproduz o correto.
- [ ] PiP entra/sai sem crash.
- [ ] Bloquear/desbloquear tela durante reprodução.

### 3.5 Biblioteca e perfis (LIB-01, PROFILE-01)

- [ ] Favoritar/remover favorito; consistência Home ↔ Biblioteca.
- [ ] Favoritos persistem após reinício.
- [ ] Criar perfis A/B com dados isolados.
- [ ] PIN correto/incorreto.

### 3.6 Downloads (DL-01)

- [ ] Enfileirar 2 itens; pausar, retomar, cancelar, excluir.
- [ ] Reproduzir arquivo concluído em modo avião.
- [ ] Sem entradas fantasma após cancelamento.

### 3.7 Atualização (UPDATE-01)

- [ ] Busca manual de atualização funciona.
- [ ] Busca automática respeita configuração do usuário.

### 3.8 Rede e resiliência (NET-01)

- [ ] App degrada graciosamente offline (sem crash, CTAs de login).
- [ ] Timeout/retry em chamadas de backend não configurado (`skip optional backend calls when unconfigured`).

---

## 4. Registro de evidência (modelo)

```
ID do teste: __________
Responsável: __________
Versão/commit/pacote: 584f34d / kaplay-rebrand / __________
Aparelho/Android/orientação: __________
Passos executados: __________
Resultado esperado: __________
Resultado observado: __________
Status: aprovado | reprovado | não executado
Evidência: captura/vídeo/relatório link
Defeito/próximo passo: __________
```

---

## 5. Resumo de prioridade

- **P0 (bloqueia uso):** Seção 3.1 (build) + 3.2 (inicialização) + 3.4 (reprodução).
- **P1:** Seções 3.3 (Tomato), 3.5 (biblioteca/perfis), 3.6 (downloads).
- **P2:** Seções 3.7 (atualização), 3.8 (rede).

**Critério de promoção:** `compileFullDebugKotlin` + `TomatoProviderTest` (5/5) verdes no CI + zero crashes em smoke manual (Seção 3.2) + PR da branch `kaplay-rebrand` mergeado.

---

*Gerado em 26/10/2026. Atualizar este arquivo ao encerrar cada rodada de homologação.*
