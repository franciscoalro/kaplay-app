# Relatório de homologação Android — KA Play

Data: 2026-10-30  
Repositório: `franciscoalro/kaplay-app`  
Branch: `kaplay-rebrand`  
Commit de referência do APK: `91e166b4`  
Commit da proteção do workflow: `8e7b96b3`

## 1. Veredito

**PARCIAL — homologação Android real não aprovada.**

A compilação do APK Full Debug foi comprovada anteriormente, mas não existem evidências de login Android com Supabase Auth, instalação em emulador, alternância de quatro perfis ou sincronização end-to-end.

Nenhuma migration, autenticação ou recompensa de produção foi modificada.

## 2. Ambiente encontrado

### Backend local

```text
PostgreSQL 17.11: container ka-auth-pg
GoTrue v2.164.0: http://127.0.0.1:9999
PostgREST 12.2.3: http://127.0.0.1:3301
```

GoTrue foi inicializado com migrations próprias em schema `auth`, usando `DB_NAMESPACE=auth` e `DATABASE_URL` isolada. As migrations A/B/C foram aplicadas somente nesse banco.

Endpoints reais de Auth:

```text
/health: HTTP 200
/settings: HTTP 200
```

O primeiro boot exigiu criação do schema `auth` e uma correção isolada de compatibilidade da migration `factor_type`; nenhum arquivo de produção foi alterado.

### Android

```text
ADB: nenhum dispositivo/emulador conectado
APK instalado: não
```

O artifact foi baixado localmente e validado com `aapt2`:

```text
arquivo: build/ka-play-artifact/ka-play-full-debug.apk
package: com.kaplay.app.debug
versionCode: 191
versionName: 0.9.18
sha256: 9595226ac43b86a85abe1c7b15edaf3b1c74e253e667cbdf2ab860909dd8ef31
```

## 3. GitHub Actions

O BUILD ONLY foi aprovado no run `41083330476`, com APK Full Debug e artifact verificável. Esse resultado é de compilação, não de homologação funcional.

O workflow de integração atual:

```text
.github/workflows/ka-play-integration-android.yml
```

exige:

- URL HTTPS de desenvolvimento;
- host presente em allowlist explícita;
- chave anon de desenvolvimento;
- email e senha de usuário de teste;
- fontes de testes Android específicas de sync/Supabase;
- emulador Android.

A URL `10.0.2.2` não é aceita em runner GitHub-hosted.

## 4. Auditoria dos testes Android

Existem quatro arquivos em `androidApp/src/androidTest`:

```text
BackgroundDownloadTest.kt
LibraryTrackingUiTest.kt
MdbListLiveTest.kt
MdbListSettingsUiTest.kt
```

Eles não são testes end-to-end de autenticação e sincronização KA Play:

- `LibraryTrackingUiTest` usa controladores falsos em Compose;
- `MdbListSettingsUiTest` depende de `assumeTrue` e de autenticação MDBList;
- `MdbListLiveTest` não substitui o fluxo Supabase Auth/sync;
- não existe teste `Sync*Test.kt` ou `Supabase*Test.kt` no diretório Android instrumentado.

Para impedir aprovação falsa, o workflow foi endurecido para falhar quando não houver testes Android específicos de sync/Supabase.

## 5. Testes realmente executados nesta etapa

| Teste | Resultado |
|---|---|
| PostgreSQL 17 + GoTrue + PostgREST | aprovado no ambiente isolado |
| GoTrue `/health` e `/settings` | HTTP 200 |
| signup de dois usuários sintéticos | aprovado |
| login email/senha | aprovado, JWT real emitido |
| refresh token | HTTP 200 |
| RPC com JWT GoTrue/profile 1 | HTTP 204/200 |
| isolamento profile 2 | resposta vazia para dados de profile 1 |
| isolamento entre usuários | resposta vazia para dados do outro usuário |
| profile inexistente | HTTP 403 |
| APK em emulador | não executado: nenhum emulador |
| login dentro do Android | não executado |
| quatro perfis no APK | não executado |
| offline/reconexão no APK | não executado |
| crashes/ANRs em sessão real | não executado |

## 6. Falhas e bloqueios

1. O backend local completo existe, mas está acessível apenas na VPS/local, não no runner GitHub-hosted.
2. Não há URL HTTPS de desenvolvimento configurada no GitHub.
3. Não há secrets remotos de teste configurados.
4. Não há runner self-hosted para alcançar o backend local.
5. Não há emulador conectado localmente.
6. Não há testes instrumentados específicos de sincronização.

## 7. Correções aplicadas

- workflow de integração endurecido para exigir HTTPS e allowlist;
- produção explicitamente rejeitada;
- credenciais de teste obrigatórias;
- suíte vazia ou sem testes de sync não pode ser aprovada;
- nenhum código de produção, migration, recompensa ou autenticação foi alterado.

## 8. Próxima preparação necessária

1. Disponibilizar Supabase local completo com GoTrue, ou um projeto Supabase de desenvolvimento HTTPS.
2. Criar dois usuários sintéticos e quatro perfis.
3. Configurar os secrets de teste no repositório.
4. Implementar testes instrumentados reais para Auth e sync.
5. Executar o workflow de integração no GitHub Actions.
6. Instalar o APK no emulador do runner.
7. Capturar logs de login, troca de perfil, push/pull, offline e reconexão.

## 9. Conclusão

A autenticação real GoTrue e a aceitação de JWTs reais pelas RPCs foram comprovadas no ambiente isolado. Também foram comprovados login, refresh, dois usuários, quatro perfis e isolamento por usuário/perfil no HTTP. Contudo, a etapa solicitada exige que essas operações ocorram dentro do APK em Android Emulator, e esse runner/emulador/test harness ainda não está disponível.

**Resultado final: PARCIAL.**

Não é tecnicamente correto declarar homologação Android aprovada sem essas evidências.
