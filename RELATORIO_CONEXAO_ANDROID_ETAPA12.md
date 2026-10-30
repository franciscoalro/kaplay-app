# Relatório de conexão Android — Etapa 12

Data: 2026-10-30  
Branch: `kaplay-rebrand`  
Gateway commit: `620ee979`

## 1. Serviços restaurados sem repetir migrations

Foram identificados volumes Docker PostgreSQL preservados. O volume com schema Auth foi reconhecido por conter 16 tabelas `auth`; ele foi reutilizado sem executar migrations novamente.

```text
PostgreSQL: PostgreSQL 17.11
Volume: 931bcf0f1ccf98e4d0e21bbd8f332dc4de787c2acfdaf9ce6ef3d088ab46a82c
GoTrue: supabase/gotrue:v2.164.0
PostgREST: postgrest/postgrest:v12.2.3
```

Portas observadas:

```text
127.0.0.1:3002  Nginx gateway
0.0.0.0:9999    GoTrue
0.0.0.0:3301    PostgREST
```

Nenhum SQL de produção foi executado.

## 2. Gateway

Arquivo:

```text
supabase/dev/nginx-ka-play.conf
```

Rotas verificadas:

```text
/auth/v1/health  -> HTTP 200
/auth/v1/settings -> HTTP 200
/rest/v1/ -> HTTP 404 esperado para raiz PostgREST
```

O gateway escuta apenas em `127.0.0.1:3002`:

```text
LISTEN 127.0.0.1:3002 nginx
```

Não houve exposição pública do gateway.

## 3. Login e RPC pelo gateway

Login real via GoTrue pelo gateway:

```text
POST /auth/v1/token?grant_type=password
resultado: JWT emitido
usuário: a1@example.local
```

RPC autenticada via caminho unificado:

```text
POST /rest/v1/rpc/sync_pull_watch_progress
resultado: HTTP 200
resultado: progresso do episódio show / s1e1 / v1 retornado
```

O conteúdo do JWT não foi registrado no relatório.

## 4. ADB e Termux

O ADB desta sessão não possui dispositivos:

```text
List of devices attached
```

O dispositivo `127.0.0.1:5555` está no ambiente Termux remoto do operador. Portanto, não foi possível confirmar daqui:

```text
adb -s 127.0.0.1:5555 get-state
adb -s 127.0.0.1:5555 reverse tcp:3002 tcp:3002
adb -s 127.0.0.1:5555 reverse --list
```

No Termux, o túnel SSH deve permanecer ativo:

```bash
ssh -NT -o ExitOnForwardFailure=yes -o ServerAliveInterval=20 \
  -L 127.0.0.1:3002:127.0.0.1:3002 root@167.233.60.72
```

E o reverse deve ser criado no segundo terminal:

```bash
adb -s 127.0.0.1:5555 reverse tcp:3002 tcp:3002
```

## 5. APK de integração

Foi iniciado build local com:

```text
KAPLAY_SUPABASE_URL=http://127.0.0.1:3002
KAPLAY_SUPABASE_ANON_KEY=local-anon
:androidApp:assembleFullDebug
```

O APK BUILD ONLY anterior não foi usado como evidência.

## 6. Resultado atual

| Verificação | Resultado |
|---|---|
| PostgreSQL preservado | aprovado |
| GoTrue restaurado | aprovado |
| PostgREST restaurado | aprovado |
| Nginx gateway | aprovado |
| Login pelo gateway | aprovado |
| RPC pelo gateway | aprovado |
| ADB local nesta sessão | bloqueado: sem dispositivo |
| SSH Termux → VPS | pendente no ambiente do operador |
| APK de integração | build em execução |
| Instalação Android | pendente |
| Login dentro do APK | pendente |
| Sincronização dentro do APK | pendente |

## 7. Veredito

**PARCIAL.**

A conectividade VPS e o gateway foram restaurados e validados por HTTP com autenticação real. A homologação Android ainda não pode ser aprovada sem o túnel Termux ativo, instalação do APK de integração e operação real dentro do aplicativo.
