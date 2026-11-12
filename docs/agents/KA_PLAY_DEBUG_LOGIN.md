# KA Play — login em debug

## Causa observada

O APK debug testado tentou autenticar em `https://localhost/auth/v1/token`. No emulador, `localhost` é o próprio Android; portanto, a requisição falha se não houver um backend local bridged nessa porta.

A falha observada foi de configuração do endpoint, não de rejeição do email ou da senha. Nenhuma credencial é registrada neste documento.

## Tipos de artefato

### Build-only

O workflow `.github/workflows/ka-play-build-only.yml` usa:

- `KAPLAY_BUILD_ONLY=true`
- `KAPLAY_SUPABASE_URL=https://build-only.invalid`
- `KAPLAY_SUPABASE_ANON_KEY=build-only-placeholder`

Esse APK serve para validar compilação, assinatura do artefato, manifesto e ausência de credenciais privilegiadas. **Não serve para login.**

### Login funcional isolado

Para testar login, usar somente `.github/workflows/ka-play-integration-android.yml` com:

- URL HTTPS de backend de desenvolvimento isolado;
- host incluído na allowlist do workflow;
- chave publishable/anon do ambiente isolado;
- usuário sintético de teste;
- testes Android instrumentados de Auth/sync.

Não usar o projeto Supabase de produção como substituto de backend de teste.

## Regras de build

Um build debug exige `KAPLAY_SUPABASE_URL` e `KAPLAY_SUPABASE_ANON_KEY`. Endpoints `localhost`, loopback ou redes privadas só são aceitos com `KAPLAY_ALLOW_LOOPBACK_BACKEND=true` e uma ponte explicitamente testada. Um endpoint fora da rede privada precisa usar HTTPS.

O validador local é:

```text
scripts/validate_debug_backend_config.sh
```

Ele não imprime chaves e rejeita configurações ausentes, loopback não autorizado e combinações incorretas de build-only.

## Critério de verificação

Login só pode ser marcado como `TESTADO` quando um APK gerado pelo workflow de integração for instalado em emulador, o Auth emitir sessão para o usuário sintético e a tela do app sair do formulário de login sem erro de conexão. O APK debug testado anteriormente permanece `NÃO TESTADO` para login porque apontava para `https://localhost`.
