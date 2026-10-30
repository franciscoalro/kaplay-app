# Relatório do gateway e Android — KA Play

Data: 2026-10-30  
Branch: `kaplay-rebrand`  
APK de referência: Full Debug `0.9.18`, package `com.kaplay.app.debug`

## 1. Gateway

Arquivo criado:

```text
supabase/dev/nginx-ka-play.conf
```

Arquitetura:

```text
127.0.0.1:3002/auth/v1/* -> 127.0.0.1:9999/*
127.0.0.1:3002/rest/v1/* -> 127.0.0.1:3301/*
127.0.0.1:3002/rpc/* -> 127.0.0.1:3301/rpc/*
```

A configuração foi validada com Nginx 1.27 Alpine:

```text
syntax is ok
configuration file ... test is successful
```

O Nginx escuta somente `127.0.0.1:3002` e não expõe a porta externamente.

## 2. Estado dos serviços

No momento da validação nesta máquina:

```text
porta 3002: livre
GoTrue 9999: não ativo
PostgREST 3301: não ativo
```

Os containers usados anteriormente foram encerrados após a validação Auth/PostgREST. Portanto, não foi declarado gateway funcional sem seus upstreams ativos.

## 3. ADB

Consulta local:

```text
adb devices
List of devices attached
```

Não há dispositivo conectado ao ADB desta sessão. O dispositivo informado pelo operador (`127.0.0.1:5555`) pertence ao ambiente Termux remoto e não está acessível pelo executor desta sessão.

Consequentemente, não foi possível executar localmente:

```text
adb -s 127.0.0.1:5555 get-state
adb -s 127.0.0.1:5555 reverse tcp:3002 tcp:3002
adb install ...
```

## 4. APK

O APK BUILD ONLY anterior não deve ser usado para homologação, pois contém `https://build-only.invalid`.

APK de integração ainda não foi gerado com `http://127.0.0.1:3002`.

## 5. Resultado

| Verificação | Resultado |
|---|---|
| Configuração Nginx | aprovada sintaticamente |
| Gateway escutando | pendente de upstreams |
| GoTrue via gateway | não executado nesta sessão |
| PostgREST via gateway | não executado nesta sessão |
| ADB device | não disponível localmente |
| `adb reverse` | não executado localmente |
| APK de integração | não gerado |
| Android E2E | não executado |

## 6. Veredito

**PARCIAL.**

O gateway foi criado e validado sintaticamente, sem exposição externa. A homologação Android não pode ser aprovada porque os serviços Auth/PostgREST não estão ativos nesta sessão e o dispositivo ADB está em outro ambiente.

## 7. Comandos no host que executa os serviços

Com GoTrue e PostgREST ativos:

```bash
docker run --rm --network host \
  -v "$PWD/supabase/dev/nginx-ka-play.conf:/etc/nginx/nginx.conf:ro" \
  nginx:1.27-alpine
```

Depois, no ambiente que possui o dispositivo:

```bash
adb -s 127.0.0.1:5555 get-state
adb -s 127.0.0.1:5555 reverse tcp:3002 tcp:3002
adb -s 127.0.0.1:5555 reverse --list
```

O APK precisa ser recompilado com:

```text
KAPLAY_SUPABASE_URL=http://127.0.0.1:3002
```

Nenhuma migration, recompensa ou produção foi alterada.
