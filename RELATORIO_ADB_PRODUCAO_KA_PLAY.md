# Relatório ADB e inspeção controlada em produção — KA Play

Data: 2026-10-30
Repositório: `franciscoalro/kaplay-app`
Branch: `kaplay-rebrand`
Projeto Supabase: `iibureqvpurmbgqupnvl`

## 1. Escopo e segurança

Foi realizada somente inspeção visual/runtime de leitura no APK de produção já instalado. Não foram executados:

- migrations A/B/C;
- DDL, alterações de RLS ou AUTH;
- limpeza de dados, reinstalação ou desinstalação;
- logout, revogação de permissões ou reset do aplicativo;
- operações de escrita em favoritos, histórico, progresso ou recompensas.

JWTs, chaves, senhas e headers de autorização não foram armazenados no relatório.

## 2. ADB e APK

ADB remoto acessado pelo servidor ADB encaminhado do Termux:

```text
127.0.0.1:5555         device
product: sailfish
model: Pixel
transport_id: 2
emulator-5554          device
product: sailfish
model: Pixel
transport_id: 1
```

Serial usado na inspeção: `127.0.0.1:5555`.

```text
Android: 10
package: com.kaplay.app
userId: 10127
versionCode: 191
versionName: 0.9.18
minSdk: 24
targetSdk: 36
```

Activity de produção observada:

```text
com.kaplay.app/.launcher.AppIconDefault
```

## 3. Capturas

Capturas reais, sem dados pessoais visíveis, salvas em:

```text
evidence/ka-play-prod-20261030/home.png
evidence/ka-play-prod-20261030/search.png
evidence/ka-play-prod-20261030/library.png
evidence/ka-play-prod-20261030/profile.png
```

Log sanitizado:

```text
evidence/ka-play-prod-20261030/logcat-sanitized.txt
```

## 4. Telas e fluxos observados

### Home — PARCIAL

Observado:

- hero de conteúdo em destaque;
- botão `Ver Detalhes`;
- carrosséis `Animes em alta`, `Novos episódios` e categorias;
- navegação inferior `Início`, `Busca`, `Biblioteca`, `Perfil`;
- badge `1h grátis` sobre a área superior.

Pontos de melhoria:

- badge superior sobrepõe conteúdo;
- barra inferior sobrepõe o início de seções em telas longas;
- títulos dos cards são truncados agressivamente;
- falta estado visual claro de sincronização;
- revisar foco D-pad e contraste do item selecionado.

### Busca — PARCIAL

A tela exibiu:

```text
Buscar filmes, séries...
Nenhuma fonte de conteúdo
Adicione um addon ou conecte um servidor de mídia para buscar.
```

O estado vazio é compreensível, porém não apresenta CTA direto para `Adicionar fonte` ou `Configurar servidor`, criando um beco sem saída.

### Biblioteca — PARCIAL

A tela exibiu:

```text
Salvo
Nuvem
Sua biblioteca está vazia
Títulos salvos aparecerão aqui após você tocar em Salvar em uma tela de detalhes.
```

Pontos de melhoria:

- explicar melhor diferença entre `Salvo` e `Nuvem`;
- rotular semanticamente os ícones de grade/download;
- indicar dependência de autenticação/sincronização quando aplicável;
- oferecer CTA para descoberta de conteúdo.

### Perfil/Configurações — PARCIAL

Itens observados:

```text
Alternar Perfil
Conta e status de sincronização
Sessão em grupo
Rastreamento
Layout
Conteúdo & Descoberta
Reprodução
```

Pontos de melhoria:

- tela muito vertical e densa;
- descrições longas para navegação D-pad;
- estado de conta/sync não é persistente na navegação;
- falhas do SyncManager não aparecem como erro acionável na tela.

## 5. Runtime e RPCs em produção

O APK iniciou sincronização para `profile=1`. Os logs sanitizados registraram repetidamente `PGRST202` para RPCs ausentes:

```text
public.sync_get_watch_progress_delta_cursor(p_profile_id)
public.sync_pull_watch_progress(p_profile_id)
public.sync_pull_watched_items(p_page, p_page_size, p_profile_id)
public.sync_get_library_delta_cursor(p_profile_id)
```

Também houve ausência de:

```text
public.get_avatar_catalog()
```

Sequência observada:

```text
Activity sync started profile=1 reason=foreground
Source refresh incomplete ... progress=false watched=false
Activity sync step failed profile=1 step=ActiveWatchSource
Activity sync incomplete ... failedSteps=[ActiveWatchSource]
```

Apesar disso, posteriormente apareceu uma mensagem de conclusão de atividade. Isso pode mascarar falha para o usuário e deve ser corrigido: `completed` não deve ser emitido quando existem etapas falhas.

## 6. Matriz de funcionalidades

| Funcionalidade | Estado | Evidência |
|---|---|---|
| APK abre | APROVADO | Activity em foreground |
| Home carrega | APROVADO | captura `home.png` |
| Navegação inferior | APROVADO | Home/Busca/Biblioteca/Perfil acessíveis |
| Busca sem fonte | PARCIAL | estado vazio mostrado, sem CTA de configuração |
| Biblioteca vazia | PARCIAL | estado vazio mostrado |
| Perfil/configurações | PARCIAL | tela acessível |
| Login existente | NÃO TESTADO nesta inspeção | sem logout/relogin desnecessário |
| Alternância de perfis | NÃO TESTADO | não alterar sessão atual |
| Favoritos | NÃO TESTADO | nenhuma escrita em produção |
| Histórico | NÃO TESTADO | nenhuma escrita em produção |
| Progresso | REPROVADO | RPC `sync_pull_watch_progress` ausente |
| Sincronização delta | REPROVADO | RPCs de cursor ausentes |
| Itens assistidos | REPROVADO | `sync_pull_watched_items` ausente |
| Recompensa 1h | NÃO TESTADO | nenhum consumo/evento artificial |
| Offline/reconexão | NÃO TESTADO | risco de alterar estado atual |

## 7. Riscos classificados

### Crítico

- APK de produção aponta para backend de produção enquanto as RPCs esperadas pelo cliente não estão disponíveis.
- Sincronização de progresso, biblioteca e histórico não pode ser aprovada.

### Alto

- `SyncManager` registra falha, mas também registra conclusão da atividade.
- Falha de RPC não possui indicação visual clara para o usuário.
- Não há distinção evidente entre estado local, nuvem, pendente e erro.

### Médio

- Busca sem CTA de recuperação.
- Sobreposição do badge `1h grátis` e barra inferior.
- Truncamento de títulos e descrições longas para D-pad.
- Ícones sem rótulos acessíveis suficientes.

## 8. Alterações realizadas

Nenhuma alteração no APK, banco, Supabase Auth, RLS, recompensas ou produção.

O único artefato adicional gerado foi evidência local sanitizada em `evidence/ka-play-prod-20261030/`.

## 9. Próximos passos recomendados

1. Não aplicar migrations diretamente em produção.
2. Alinhar formalmente o conjunto de RPCs do APK com o schema candidato isolado.
3. Corrigir o estado de sucesso do `SyncManager` antes de qualquer homologação.
4. Gerar APK de integração separado com gateway local.
5. Repetir testes de login, perfis, progresso e reconexão somente no ambiente isolado.
6. Tratar Busca, Biblioteca e estados de erro em commit separado de UI.

## 10. Veredito

**PARCIAL.**

A inspeção visual e runtime do APK de produção foi executada com ADB ativo e evidências reais. A sincronização permanece **REPROVADA para homologação**, pois as RPCs necessárias retornam `PGRST202`/não existem no backend acessado. Nenhuma alteração de produção foi realizada.
