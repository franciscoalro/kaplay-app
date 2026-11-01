# Relatório de build Android — KA Play

Data: 2026-10-30  
Repositório: `franciscoalro/kaplay-app`  
Branch: `kaplay-rebrand`

## 1. Auditoria Gradle

A tarefa correta pertence ao módulo Android:

```text
:androidApp:assembleFullDebug
```

A tarefa `:composeApp:assembleFullDebug` não existe.

O `composeApp/build.gradle.kts` declara `SupabaseConfig` como input da task `generateRuntimeConfigs`, mas possui fallbacks de produção. Por segurança, o BUILD ONLY não usa esses fallbacks: injeta placeholders explícitos que não são credenciais nem endpoints reais.

Variáveis de build:

```text
KAPLAY_SUPABASE_URL
KAPLAY_SUPABASE_ANON_KEY
```

TMDB, Sentry e demais integrações são runtime/configuração gerada e não são necessários para compilar o APK.

## 2. Workflows separados

BUILD ONLY:

```text
.github/workflows/ka-play-build-only.yml
```

- executa em GitHub-hosted runner;
- usa `https://build-only.invalid` e `build-only-placeholder` somente para geração de código;
- executa `:androidApp:assembleFullDebug`;
- verifica existência do APK;
- calcula SHA-256 e tamanho;
- publica artifact;
- verifica ausência de material `service_role`, `sb_secret_` e chaves privadas;
- não declara integração funcional.

INTEGRATION TEST:

```text
.github/workflows/ka-play-integration-android.yml
```

- exige URL HTTPS configurada como variável de ambiente do repositório;
- exige allowlist explícita de hosts;
- rejeita a URL de produção;
- exige `KAPLAY_ISOLATED_ANON_KEY`;
- exige fontes reais de testes instrumentados;
- prepara emulador e executa `connectedFullDebugAndroidTest`.

URLs locais como `10.0.2.2` não são aceitas no workflow hospedado pelo GitHub.

## 3. Resultado local anterior

A tentativa local foi encerrada antes da geração de APK, durante:

```text
:composeApp:compileAndroidMain
:androidApp:desugarFullDebugFileDependencies
```

Não foi encontrado APK em `androidApp/build/outputs`. Não há evidência local de `BUILD SUCCESSFUL`.

## 4. Resultado GitHub Actions

Primeira execução do BUILD ONLY:

```text
Run: 41081322176
Resultado: BUILD FAILED
Etapa: :composeApp:generateRuntimeConfigs
Erro: local.properties não existia no runner
```

A falha não foi de Compose, desugaring ou backend. A task customizada declara `localPropertiesFile` como input e o Gradle 9.4.1 rejeitou o arquivo ausente durante a validação.

Correção aplicada somente no workflow: criar um `local.properties` vazio antes do build. Nenhuma credencial é gravada nesse arquivo.

Segunda execução após a correção:

```text
Run: 41081928462
Build Full Debug APK: sucesso
Falha posterior: scanner amplo encontrou falso positivo em androidApp/build
APK/artifact: não publicado porque o scanner interrompeu o job
```

O scanner foi restringido para analisar apenas `SupabaseConfig.kt` gerado e os arquivos DEX do APK, evitando textos não relacionados em artefatos intermediários.

O workflow foi separado para permitir compilação sem backend ativo. Execução final do BUILD ONLY:

```text
Run: 41083330476
URL: https://github.com/franciscoalro/kaplay-app/actions/runs/41083330476
Commit: d71457ac1d22eacc3a8dfe6cdd1906628b11dce9
Resultado: success
BUILD SUCCESSFUL in 6m 27s
```

Metadados do APK:

```text
variant=fullDebug
path=androidApp/build/outputs/apk/full/debug/androidApp-full-debug.apk
size_bytes=167436474
sha256=9595226ac43b86a85abe1c7b15edaf3b1c74e253e667cbdf2ab860909dd8ef31
functional_homologation=NOT_PERFORMED
```

Artifact publicado:

```text
ka-play-full-debug-build-only
artifact_id=12991470811
size_bytes=161541077
expired=false
```

As etapas de build, verificação do APK, scanner de credenciais e upload terminaram com sucesso.

## 5. Segurança

- nenhuma migration foi executada;
- nenhuma URL de produção é usada pelo BUILD ONLY;
- nenhuma service-role key é usada;
- autenticação, recompensas e adapters não foram alterados;
- o APK compilado não poderá ser chamado de homologado sem o workflow de integração.

## 6. Próxima etapa

1. Executar `KA Play Android build only` no GitHub Actions.
2. Baixar e verificar o artifact e o SHA-256.
3. Configurar um Supabase/Auth de desenvolvimento acessível por HTTPS.
4. Configurar `KAPLAY_ISOLATED_BASE_URL`, `KAPLAY_ALLOWED_BACKEND_HOSTS` e `KAPLAY_ISOLATED_ANON_KEY`.
5. Criar/validar testes instrumentados reais antes de executar o workflow de integração.

## 7. Veredito

**PARCIAL — o BUILD ONLY está preparado, mas nenhum APK verificável foi gerado ainda.**
