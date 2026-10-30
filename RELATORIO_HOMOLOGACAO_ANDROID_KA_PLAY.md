# Relatório de homologação Android — KA Play

Data: 2026-10-30  
Repositório: `franciscoalro/kaplay-app`  
Branch: `kaplay-rebrand`  
Commit de referência do APK: `91e166b4`  
Commit da proteção do workflow: pendente nesta entrega

## 1. Veredito

**PARCIAL — homologação Android real não aprovada.**

A compilação do APK Full Debug foi comprovada anteriormente, mas não existem evidências de login Android com Supabase Auth, instalação em emulador, alternância de quatro perfis ou sincronização end-to-end.

Nenhuma migration, autenticação ou recompensa de produção foi modificada.

## 2. Ambiente encontrado

### Backend local

```text
PostgreSQL 17: container anterior usado para validação
PostgREST: http://127.0.0.1:3300
PostgREST raiz: HTTP 404 (serviço ativo sem rota raiz)
```

Os endpoints de Auth não existem nesse gateway local:

```text
/auth/v1/settings: HTTP 404
/auth/v1/health: HTTP 404
```

O ambiente atual contém PostgreSQL/PostgREST, mas não contém GoTrue/Supabase Auth. Portanto, não há emissão de JWT real pelo fluxo de login do Supabase.

### Android

```text
ADB: nenhum dispositivo/emulador conectado
APK instalado: não
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
| PostgreSQL 17/PostgREST local ativo | aprovado anteriormente |
| RPCs HTTP com JWT sintético | aprovado anteriormente |
| endpoint Auth local | bloqueado: HTTP 404 |
| APK em emulador | não executado: nenhum emulador |
| login Android com GoTrue | não executado: Auth ausente |
| quatro perfis no APK | não executado |
| favoritos/histórico/progresso no APK | não executado |
| offline/reconexão no APK | não executado |
| crashes/ANRs em sessão real | não executado |

## 6. Falhas e bloqueios

1. O backend local não possui GoTrue/Supabase Auth.
2. Não há URL HTTPS de desenvolvimento configurada no GitHub.
3. Não há secrets de teste `KAPLAY_ISOLATED_ANON_KEY`, `KAPLAY_ISOLATED_TEST_EMAIL` ou `KAPLAY_ISOLATED_TEST_PASSWORD`.
4. Não há runner self-hosted para alcançar o PostgREST local.
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

A compilação está comprovada. A API PostgreSQL/PostgREST com JWT sintético também foi validada anteriormente. Contudo, a etapa solicitada exige autenticação Supabase real e Android Emulator, e esses recursos não estão disponíveis no ambiente atual.

**Resultado final: PARCIAL.**

Não é tecnicamente correto declarar homologação Android aprovada sem essas evidências.
