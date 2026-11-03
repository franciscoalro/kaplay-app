# Atualização Direta pelo Aplicativo — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Publicar uma release Android compatível com o updater do KA Play, mantendo pacote, assinatura e dados para atualização direta sem desinstalação.

**Architecture:** O aplicativo de produção continua usando `com.kaplay.app`, consulta a release estável da API do GitHub, seleciona o APK correspondente ao ABI, baixa para o cache protegido por `FileProvider` e abre o instalador Android. O CI usa a keystore persistente de release para que o APK publicado tenha a mesma assinatura de todas as releases futuras. A branch padrão será a única fonte de publicação.

**Tech Stack:** Kotlin Multiplatform, Android Package Installer, GitHub Releases API, GitHub Actions, Gradle Kotlin DSL, `gh` CLI.

**Spec:** `docs/superpowers/specs/2026-11-03-in-app-update-spec.md`

## Global Constraints

- Pacote de produção: `com.kaplay.app`.
- A assinatura deve ser a keystore persistente configurada nos secrets `KAPLAY_RELEASE_*`.
- Não desinstalar, limpar dados ou trocar o pacote instalado.
- Não publicar uma release a partir de branch de trabalho; o workflow exige a branch padrão `build-tomato-cmp`.
- Não registrar ou expor senhas, tokens, keystore ou conteúdo de secrets.
- A atualização deve preservar dados locais e usar `adb install -r` somente quando a assinatura for comprovadamente compatível.

## Review Focus

- Release com versão igual ou inferior à instalada deve ser ignorada pelo updater.
- Release sem APK ou sem APK compatível com o ABI deve produzir “sem atualização”, não erro de instalação.
- APK de release deve ter package `com.kaplay.app` e o certificado persistente esperado.
- Download interrompido ou arquivo ausente deve falhar sem abrir um instalador inválido.
- Instalação deve usar `FileProvider`/URI concedida e solicitar permissão de fontes desconhecidas quando necessário.

---

### Task 1: Validar e preservar a assinatura de release

**Files:**
- Inspect: `androidApp/build.gradle.kts`
- Inspect: `.github/workflows/android-release.yml`
- Inspect: `.github/workflows/build.yml`
- Test: GitHub Actions release/preflight jobs

**Interfaces:**
- Consumes: secrets `KAPLAY_RELEASE_KEYSTORE_B64`, `KAPLAY_RELEASE_STORE_PASSWORD`, `KAPLAY_RELEASE_KEY_ALIAS`, `KAPLAY_RELEASE_KEY_PASSWORD`.
- Produces: APK release assinado com o certificado persistente, sem modificar applicationId.

- [ ] **Step 1: Verify current release metadata and certificate source**
  - Run `scripts/release-metadata.sh HEAD` locally only for inspection.
  - Confirm the workflow resolves the keystore from secrets and writes only temporary `local.properties`.

- [ ] **Step 2: Add/retain a CI assertion for release identity**
  - In the release build job, inspect the generated APK with `aapt`/`apksigner`.
  - Assert application id `com.kaplay.app` and non-empty certificate digest.
  - Do not print passwords or keystore contents.

- [ ] **Step 3: Run the release preflight**
  - Run the read-only preflight workflow.
  - Expected: persistent keystore found and release build configuration accepted.

- [ ] **Step 4: Commit any CI-only assertion change**
  - Use a focused commit; do not alter application behavior in this task.

### Task 2: Verify updater contract against a real GitHub release

**Files:**
- Inspect: `composeApp/src/commonMain/kotlin/com/kaplay/app/features/updater/AppUpdaterRepository.kt`
- Inspect: `composeApp/src/androidFull/kotlin/com/kaplay/app/features/updater/AndroidAppUpdaterPlatform.kt`
- Inspect: `androidApp/src/main/AndroidManifest.xml`
- Test: `composeApp/src/commonTest/kotlin/com/kaplay/app/features/updater/AppUpdaterRepositoryTest.kt`
- Test: `composeApp/src/androidFullHostTest/kotlin/com/kaplay/app/features/updater/AppUpdaterControllerTest.kt`

**Interfaces:**
- Consumes: GitHub `releases/latest` JSON and ABI list.
- Produces: `AppUpdate` with a valid APK URL, cache path, FileProvider URI, and installer intent.

- [ ] **Step 1: Add a regression test for release asset selection**
  - Assert stable selection prefers the device ABI APK and rejects non-APK assets.
  - Assert equal/older versions are not offered.

- [ ] **Step 2: Run updater tests on GitHub Actions**
  - Run the existing sync/host test workflow.
  - Expected: updater repository/controller tests pass.

- [ ] **Step 3: Validate the live release response without secrets**
  - Fetch `https://api.github.com/repos/franciscoalro/kaplay-app/releases/latest`.
  - Confirm at least one APK exists and that its package is `com.kaplay.app`.

- [ ] **Step 4: Validate installer resources**
  - Confirm `REQUEST_INSTALL_PACKAGES`, FileProvider authority `${applicationId}.fileprovider`, and `cache/updates` path are present in the merged production manifest.

### Task 3: Integrate the release-ready branch

**Files:**
- GitHub branch `agentic/ka-play-production-toolkit-20261101`
- GitHub default branch `build-tomato-cmp`
- Optional PR metadata only; no unrelated source changes.

**Interfaces:**
- Consumes: commits from the release-ready branch and passing CI.
- Produces: merged default-branch commit accepted by `android-release.yml`.

- [ ] **Step 1: Verify branch CI before integration**
  - Confirm metadata tests and stable Debug build jobs are successful.
  - Confirm worktree is clean and intended commits are pushed.

- [ ] **Step 2: Open a pull request to `build-tomato-cmp`**
  - Include the user-visible result and signing/update verification evidence.
  - Do not enable auto-merge.

- [ ] **Step 3: Wait for required checks and human review**
  - Expected: PR is mergeable; no direct force-push or bypass of branch protection.

- [ ] **Step 4: Merge only after explicit approval**
  - Preserve the default branch history and release metadata requirements.

### Task 4: Build and publish the production release

**Files:**
- Workflow: `.github/workflows/android-release.yml`
- Release assets: generated by GitHub Actions only

**Interfaces:**
- Consumes: default branch, persistent release secrets, release metadata.
- Produces: published GitHub release with signed ABI APKs discoverable by the updater.

- [ ] **Step 1: Run release workflow in `dry-run` mode**
  - Dispatch from `build-tomato-cmp`.
  - Expected: version/tag validation and release notes pass.

- [ ] **Step 2: Run release workflow in `publish` mode after human approval**
  - Expected: Android release APKs are built and attached to the GitHub release.
  - Do not publish from the worktree branch.

- [ ] **Step 3: Inspect published assets**
  - Assert package `com.kaplay.app`, version newer than the installed version, and certificate digest equals the persistent release certificate.

### Task 5: Prove the end-to-end update path

**Files:**
- Evidence: `docs/agents/KA_PLAY_RUNTIME_UPDATE_EVIDENCE_20261103.md`

**Interfaces:**
- Consumes: published release and a production APK at an older version.
- Produces: non-destructive evidence that the app offers, downloads and launches the update without uninstalling.

- [ ] **Step 1: Install an older production APK only if it is already signed with the persistent release key**
  - Compare package and certificate before installation.
  - Never uninstall or clear data.

- [ ] **Step 2: Trigger “verificar atualização” inside the production app**
  - Record the release tag, selected ABI asset, and update dialog.

- [ ] **Step 3: Download and launch the installer**
  - Confirm downloaded file exists under cache `updates/` and the installer opens through the FileProvider URI.

- [ ] **Step 4: Complete the Android update and verify data preservation**
  - Confirm package remains `com.kaplay.app`, signature remains unchanged, and local library/profile data remains present.

- [ ] **Step 5: Write sanitized evidence and run final checks**
  - Record only package, version, certificate digest, release URL, APK digest, and exit codes.
  - Run `git diff --check`, relevant GitHub CI checks, and `git status --short`.
