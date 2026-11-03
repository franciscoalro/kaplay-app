# Atualização direta pelo aplicativo

## Objetivo

Usuários da versão de produção devem conseguir verificar, baixar e instalar uma nova release pelo próprio aplicativo, sem desinstalar, perder dados locais ou trocar o pacote/assinatura.

## Contrato

- Pacote de produção: `com.kaplay.app`.
- Origem: GitHub Releases estável do repositório `franciscoalro/kaplay-app`.
- Assets: APKs por ABI ou universal; o dispositivo deve receber o ABI compatível.
- Assinatura: keystore persistente `KAPLAY_RELEASE_*` usada em todas as releases.
- Instalação: download em cache, `FileProvider`, permissão de instalação desconhecida quando exigida pelo Android.
- Segurança: não expor secrets e não instalar APK de pacote/assinatura incompatíveis.

## Fora do escopo

- Atualização automática de builds Debug `com.kaplay.app.debug` usando releases de produção.
- Desinstalação, limpeza de dados ou migração destrutiva.
- Publicação a partir de branch diferente da branch padrão.

## Critério de aceite

Uma release publicada na branch padrão é encontrada pelo updater, o asset correspondente é selecionado, o instalador Android é aberto e a atualização mantém `com.kaplay.app`, a assinatura persistente e os dados locais.
