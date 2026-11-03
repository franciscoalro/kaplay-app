# KA Play Login Redesign Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans (recommended) or superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implementar o novo visual premium da tela de login do KA Play preservando autenticação, modo anônimo, login por TV, sessão e dados locais.

**Architecture:** Evoluir a `AuthScreen` existente em vez de criar uma segunda autenticação. A rota continuará coletando `AuthRepository`, `DeviceLinkAuthRepository` e `ServerConnectionController`; o formulário receberá apenas tokens visuais, callbacks adicionais e estados locais de recuperação de senha. A única chamada nova de backend será `resetPasswordForEmail` através do `AuthRepository` existente.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, Material 3, Supabase Kotlin Auth, Robolectric Compose UI tests, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-11-03-ka-play-login-redesign-design.md`

## Global Constraints

- Pacote e sessão Supabase existentes permanecem inalterados.
- Não recriar ViewModel, AuthRepository paralelo, provedor de autenticação ou fluxo de sessão.
- Não limpar armazenamento local, favoritos, histórico, progresso ou sincronização durante implementação/testes.
- A logo original `AppBrandWordmark` deve ser reutilizada.
- O formulário deve continuar utilizável com teclado aberto, tela compacta, modo paisagem e controle/D-pad.
- O novo visual usa `#090610`, `#210B38`, `#A844F5`, `#C878FF`, `#171121`, `#493954`, `#F8F8FA` e `#101015` conforme o design aprovado.
- Não executar compilação Gradle local ou em VPS; toda compilação/teste Android será executada via GitHub Actions.

## Review Focus

- Login/cadastro durante carregamento: botão e campos devem impedir submissões duplicadas sem perder os valores digitados. (Task 3)
- Email inválido ou vazio no reset: nenhuma chamada Supabase deve ser iniciada e a mensagem deve ser localizada. (Task 1/3)
- Login por código de TV e modo anônimo: continuam visíveis somente quando suportados e não são removidos pelo redesign. (Task 2/3)
- Teclado/telas compactas: conteúdo deve rolar e o botão principal permanecer alcançável. (Task 2/3)
- Contraste/foco: campos, links, senha visível e botões devem ter foco visual e descrições acessíveis. (Task 3)

---

### Task 1: Contrato de validação e recuperação de senha

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/kaplay/app/features/auth/AuthValidation.kt`
- Modify: `composeApp/src/commonMain/kotlin/com/kaplay/app/core/auth/AuthRepository.kt`
- Modify: `composeApp/src/commonMain/composeResources/values/strings.xml`
- Modify: `composeApp/src/commonMain/composeResources/values-pt-rBR/strings.xml`
- Modify: `composeApp/src/commonMain/composeResources/values-pt/strings.xml`
- Modify: `.github/workflows/build.yml`
- Test: `composeApp/src/commonTest/kotlin/com/kaplay/app/features/auth/AuthValidationTest.kt`

**Interfaces:**
- Produces `internal fun isValidAuthEmail(value: String): Boolean`.
- Produces `internal fun canSubmitAuth(email: String, password: String, isLoading: Boolean): Boolean`.
- Produces `suspend fun AuthRepository.requestPasswordReset(email: String): Result<Unit>` usando exclusivamente `SupabaseProvider.client.auth.resetPasswordForEmail(email = email)`.
- Adds localized keys for forgot-password label, invalid-email message, reset success and reset failure; other locales use the existing base fallback until translated.

- [ ] **Step 1: Write the failing validation tests**
  - Add tests named `blankEmailIsInvalid`, `malformedEmailIsInvalid`, `trimmedEmailWithDomainIsValid`, `shortPasswordCannotSubmit`, `loadingStateCannotSubmit`, and `validCredentialsCanSubmit`.
  - Assert that validation trims only for the email check, requires a non-empty domain/user shape, requires password length 6 for submit, and always disables submit while loading.

- [ ] **Step 2: Run the focused tests through GitHub Actions and confirm RED**
  - Commit only the test/helper references needed for compilation if necessary; dispatch the sync/full host-test workflow on the feature branch.
  - Expected: the new test target fails because `AuthValidation.kt` and its functions do not exist; no local Gradle command is allowed.

- [ ] **Step 3: Implement the minimal validation and repository contract**
  - Implement the pure helpers without Android APIs so they remain common-testable.
  - Implement `requestPasswordReset` as a suspend `Result<Unit>` wrapper with the repository logger and no local-session mutation; preserve cancellation behavior.
  - Add resource strings in English, Brazilian Portuguese and Portuguese fallback without hardcoding user-facing copy in Kotlin.
  - Add the two new test selectors to `.github/workflows/build.yml` so GitHub host-test jobs execute both common validation and Android Compose UI tests.

- [ ] **Step 4: Run the focused tests and confirm GREEN**
  - Dispatch GitHub Actions with the focused/common tests and verify zero failures.

- [ ] **Step 5: Commit**
  - `git add .github/workflows/build.yml composeApp/src/commonMain/kotlin/com/kaplay/app/features/auth/AuthValidation.kt composeApp/src/commonMain/kotlin/com/kaplay/app/core/auth/AuthRepository.kt composeApp/src/commonMain/composeResources/values*/strings.xml composeApp/src/commonTest/kotlin/com/kaplay/app/features/auth/AuthValidationTest.kt`
  - `git commit -m "feat(auth): add password reset validation contract"`

### Task 2: Restyle existing AuthScreen without changing auth flows

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/kaplay/app/features/auth/AuthScreen.kt`
- Test: `composeApp/src/androidHostTest/kotlin/com/kaplay/app/features/auth/AuthScreenTest.kt`

**Interfaces:**
- Keeps `@Composable fun AuthScreen(modifier: Modifier = Modifier)` as the only route entry point.
- Extends the existing private `AuthForm` callback set with `onForgotPassword: () -> Unit`, `resetMessage: String?`, and `isResetLoading: Boolean`.
- Keeps `AuthMobileLayout` and `AuthLargeLayout` and all existing device-link/anonymous callbacks.

- [ ] **Step 1: Add the UI regression test skeleton**
  - Add/extend `composeApp/src/androidHostTest/kotlin/com/kaplay/app/features/auth/AuthScreenTest.kt` with a deterministic stateless form host using `DeviceLinkAuthState.Idle` and injected callbacks.
  - Cover that the rendered form exposes `E-mail`, `Senha`, `Entrar`, `Esqueceu a senha?`, `Cadastrar`/mode toggle and `Continuar sem conta` labels.
  - Assert the password visibility action toggles its content description and the forgot-password callback is not available in sign-up mode.

- [ ] **Step 2: Run the host test through GitHub Actions and confirm RED**
  - Dispatch the Android host test workflow for the feature branch.
  - Expected: the new test fails because the forgot-password semantics/callback and approved visual state do not exist yet.

- [ ] **Step 3: Apply the approved visual system surgically**
  - Replace existing auth color constants with the approved palette while retaining existing focus and pointer behavior.
  - Restyle `authGradientBackground`, brand lockup, headings, fields, primary/secondary buttons and separators with native Compose `Brush`, `Canvas`, borders and rounded shapes.
  - Keep the current mobile scroll/IME padding and large-screen form width cap; tune compact spacing so the primary button remains reachable.
  - Add the forgot-password text action only for sign-in. It calls the local handler, disables while loading/resetting, preserves email/password, and displays localized success/error state.
  - Keep login/cadastro callbacks exactly mapped to `AuthRepository`, keep anonymous sign-in and TV device-link sections, and add content descriptions/test tags needed by accessibility tests.

- [ ] **Step 4: Run the host test and confirm GREEN**
  - Run the Android host test target via GitHub Actions; verify labels, callback behavior, password visibility and sign-up omission pass.

- [ ] **Step 5: Commit**
  - `git add composeApp/src/commonMain/kotlin/com/kaplay/app/features/auth/AuthScreen.kt composeApp/src/androidHostTest/kotlin/com/kaplay/app/features/auth/AuthScreenTest.kt`
  - `git commit -m "feat(auth): apply KA Play login redesign"`

### Task 3: Connect recovery state and regression coverage

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/kaplay/app/features/auth/AuthScreen.kt`
- Test: `composeApp/src/androidHostTest/kotlin/com/kaplay/app/features/auth/AuthScreenTest.kt`
- Test: `composeApp/src/commonTest/kotlin/com/kaplay/app/features/auth/AuthValidationTest.kt`

**Interfaces:**
- `AuthScreen` owns only transient `isResetLoading` and `resetMessage` state.
- The forgot handler trims the email, checks `isValidAuthEmail`, then launches `AuthRepository.requestPasswordReset`; successful result uses the success string and failure uses the failure string without touching auth session state.

- [ ] **Step 1: Write the failing recovery interaction assertions**
  - Assert invalid email does not invoke the injected reset callback and exposes the invalid-email message.
  - Assert valid email invokes reset once, disables the action during the request, and exposes success/failure result text.
  - Assert switching to sign-up hides the recovery action and clears transient reset feedback.

- [ ] **Step 2: Run the focused host/common tests and confirm RED**
  - Dispatch GitHub Actions; expected failure is missing recovery state behavior.

- [ ] **Step 3: Implement the route wiring**
  - In `AuthScreen`, add the reset coroutine and state transitions while preserving existing `isLoading`, `AuthRepository.error`, device-link cancellation and focus clearing.
  - Never clear `email`, `password`, anonymous ID, Supabase session or local account data as part of reset.

- [ ] **Step 4: Run all auth tests and confirm GREEN**
  - Dispatch the full host-test selection containing `AuthValidationTest` and `AuthScreenTest`; inspect reports for zero failures.

- [ ] **Step 5: Commit**
  - `git add composeApp/src/commonMain/kotlin/com/kaplay/app/features/auth/AuthScreen.kt composeApp/src/androidHostTest/kotlin/com/kaplay/app/features/auth/AuthScreenTest.kt composeApp/src/commonTest/kotlin/com/kaplay/app/features/auth/AuthValidationTest.kt`
  - `git commit -m "test(auth): cover login redesign interactions"`

### Task 4: Full GitHub verification and review

**Files:**
- Modify: none beyond the committed implementation and design/plan docs.
- Verify: GitHub Actions workflows and final worktree state.

**Interfaces:**
- The release branch remains untouched; this feature branch is validated independently.

- [ ] **Step 1: Run the complete GitHub validation matrix**
  - Push the feature branch to `buildci`.
  - Run `kaplay-build`, `KA Play sync unit tests`, and `KA Play Android build only` from GitHub Actions.
  - Expected: compile, host tests, secret scan and APK checks pass; no local/VPS Gradle invocation.

- [ ] **Step 2: Review UI behavior evidence**
  - Inspect Compose host-test reports and any available screenshot/preview artifacts for mobile, compact screen and large-screen layout.
  - Confirm no destructive emulator operation is used; only non-destructive UI tests are allowed.

- [ ] **Step 3: Request one fresh whole-branch code review**
  - Review the final diff for auth-flow preservation, Supabase call boundaries, accessibility, localized strings, and no persistence regressions.

- [ ] **Step 4: Run final checks**
  - `git diff --check`
  - `git status --short`
  - Verify all relevant GitHub run IDs are `completed/success`.

- [ ] **Step 5: Commit documentation if needed**
  - The design and plan documents must remain in the feature branch for review; do not merge or publish this redesign without explicit approval.
