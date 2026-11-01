# KA Play — APK Debug conectado a Supabase de desenvolvimento

## Sintoma e causa confirmada
O workflow `.github/workflows/ka-play-build-only.yml` usava `https://build-only.invalid` e `build-only-placeholder` para compilar uma **prévia visual offline**. Esse APK não autentica, não sincroniza e não é apropriado para testes funcionais conectados.

Esta correção disponibiliza um modo manual **connected** que falha antes do build se não houver um Supabase de testes real, além de manter **offline-preview** apenas para preview explícito (e para eventos push legados).

## Pré-requisito: projeto de desenvolvimento isolado
A conexão Supabase consultada em 2026-11-01 tinha somente o projeto de **produção** `iibureqvpurmbgqupnvl`. Não usar sua URL/chave no modo Debug conectado. Criar ou conectar um projeto Supabase de desenvolvimento isolado, sem copiar contas ou dados pessoais reais. Definir permissões/RLS e identidade de perfis de acordo com os contratos do ambiente de teste, com migrations candidatas validadas somente nesse projeto.

## Configuração do GitHub
No repositório `franciscoalro/kaplay-app`, acessar **Settings → Secrets and variables → Actions → Repository secrets** e configurar:

| Secret | Valor |
| --- | --- |
| `KAPLAY_DEBUG_SUPABASE_URL` | `https://<ref-de-desenvolvimento>.supabase.co` (sem barra final) |
| `KAPLAY_DEBUG_SUPABASE_ANON_KEY` | Chave **publishable** `sb_publishable_...` do mesmo projeto, ou chave legada anon com claim `role=anon` |

A chave publishable é projetada para aplicativos cliente e ficará incorporada ao APK; a proteção dos dados depende de RLS/Auth corretos. **Nunca** usar `service_role`, `sb_secret_`, chave privada nem segredos Trakt.

## Gerar APK Debug funcional
1. Revisar e integrar a correção do workflow na branch apropriada, sem tocar `build-tomato-cmp` nem fazer merge automático.
2. Em **Actions → KA Play Android Debug preview (connected or offline) → Run workflow**, selecionar a branch com a correção e **backend_mode = connected**.
3. O job confirma secret URL/chave, rejeita produção e domínio inválido, verifica **HTTP 200** em `/auth/v1/health`, valida `SupabaseConfig.URL`/`ANON_KEY` embutidos e confirma que `FALLBACK_URL` é vazio.
4. Se passou, baixar o artifact **`ka-play-full-debug-connected`**; verificar SHA-256 e o metadado `backend_mode=connected`.
5. O pacote Debug é `com.kaplay.app.debug`, separado da versão instalada `com.kaplay.app`. Instalar manualmente só após autorização e preferencialmente em dispositivo/emulador de teste.
6. Validar login real com **conta de desenvolvimento**, seleção de perfis, RPCs e sincronização; health HTTP 200 não garante que tudo isso funciona.

## Modo offline
O modo **offline-preview** gera `ka-play-full-debug-offline-preview`. Não usar esse artefato para testar login, autenticação ou sincronização: sua URL continua propositalmente `build-only.invalid`, com rótulo explícito em log e metadata. Nenhum pipeline deve apresentar esse APK como funcional.

## Se persistir erro de conexão
- Conferir metadata `backend_mode=connected` e SHA-256 do APK realmente instalado.
- Se já havia um servidor customizado salvo na instalação Debug, usar a opção de **servidor oficial** do app para voltar à URL embutida, sem limpar dados; `ServerConfigurationRepository` pode preferir configuração customizada persistida.
- Verificar que o projeto Supabase de desenvolvimento está ativo, possui Auth configurado, conta de teste e RPCs necessárias; o teste do endpoint `/auth/v1/health` não cria conta nem valida credenciais do usuário.
- Verificar logs sanitizados de DNS/TLS/HTTP sem publicar JWT, credenciais, refresh tokens ou endereços privados.

## Segurança da entrega
Mudança apenas no workflow/docs, sem migrations, keystore, build Release, publish, instalação ou acesso ao Supabase de produção. O commit de workflow por si só **não cria um backend de desenvolvimento**, não preenche secrets e não produz APK conectado.
