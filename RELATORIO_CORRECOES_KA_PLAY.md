# RELATÓRIO DE CORREÇÕES — KA Play

Data da auditoria: 2026-10-30
APK auditado: `0.9.18` (`versionCode 191`)
Projeto Supabase confirmado: `https://iibureqvpurmbgqupnvl.supabase.co`

## Escopo e restrições

- Auditoria realizada antes de novas alterações funcionais.
- Nenhuma tabela, função, RLS ou permissão do Supabase foi alterada.
- Nenhum token, senha ou JWT foi incluído neste relatório.
- Logs foram coletados com os valores sensíveis já mascarados pelo cliente.

## 1. RPCs Supabase

### Resumo

| RPC | Chamada Kotlin | Parâmetros observados | Resultado remoto | Classificação |
|---|---|---|---|---|
| `get_avatar_catalog` | `AvatarRepository.fetchStandardCatalog()` | nenhum | `PGRST202`, não encontrado no schema cache | Bloqueio confirmado |
| `get_my_member_access` | `MemberAccessRemoteDataSource.getMemberAccess()` | nenhum | `PGRST202`, não encontrado no schema cache | Bloqueio confirmado |
| `sync_get_library_delta_cursor` | `SupabaseLibrarySyncAdapter.getDeltaCursor(profileId)` | `p_profile_id: integer` | `PGRST202`, não encontrado no schema cache | Bloqueio confirmado |
| `sync_pull_watched_items` | `SupabaseWatchedSyncAdapter.pull(profileId, pageSize)` | `p_profile_id`, `p_page`, `p_page_size` | `PGRST202`, não encontrado no schema cache | Bloqueio confirmado |
| `sync_pull_watch_progress` | `SupabaseProgressSyncAdapter.pull(profileId, sinceLastWatched, limit)` | `p_profile_id`, `p_since_last_watched?`, `p_limit?` | `PGRST202`, não encontrado no schema cache | Bloqueio confirmado |

### Evidência remota

As cinco chamadas foram executadas contra o projeto configurado pelo APK usando somente a chave pública e parâmetros compatíveis com o código. Todas retornaram HTTP 404 / código `PGRST202`, com mensagens informando que a função não foi encontrada no schema cache `public`.

Não foi utilizado acesso privilegiado para contornar o erro. A evidência não distingue, sozinha, entre função ausente no banco e função criada mas não exposta/atualizada no cache PostgREST.

### Evidência local

As migrations locais encontradas são:

- `supabase/migrations/20261028000000_ka_ad_rewards.sql`
- `supabase/migrations/20261029000000_ka_profiles.sql`

Nenhuma delas declara as cinco RPCs auditadas. A migration de perfis declara apenas `sync_pull_profiles()` e `sync_push_profiles(...)`.

### Correção recomendada

1. Confirmar no Supabase Dashboard/CLI, com acesso administrativo autorizado, se as funções existem em `pg_proc` e em qual schema.
2. Se existirem, atualizar/recarregar o schema cache e confirmar `GRANT EXECUTE` para `authenticated`.
3. Se não existirem, criar migrations específicas somente após revisão de contrato, retorno, RLS e testes.
4. Não substituir RPCs nem desabilitar RLS automaticamente.

**Resultado:** reprovado para sincronização remota; causa estrutural confirmada, causa exata (ausência versus cache) ainda pendente de acesso administrativo.

## 2. Sincronização

### Histórico de episódios assistidos

- Código usa `SupabaseWatchedSyncAdapter` com `profileId`, paginação e operações delta.
- O pull remoto falha em `sync_pull_watched_items`.
- O `SyncManager` isola a falha por etapa e continua outras etapas.

**Resultado:** parcialmente funcional; cache/local pode continuar, sincronização remota não validada.

### Progresso e retomada

- Código usa `SupabaseProgressSyncAdapter`, com posição, duração, episódio, `lastWatched` e `progressKey`.
- A sessão auditada registrou `progress=false` e `watched=false` durante o refresh de atividade.
- A reprodução local não foi interrompida no recorte coletado, mas a retomada entre sessões não pode ser aprovada.

**Resultado:** reprovado para sincronização remota; retomada local não verificada ponta a ponta.

### Favoritos / Minha lista

- `SupabaseLibrarySyncAdapter` usa `p_profile_id`, paginação, cursor delta e operações push/delete.
- O cursor `sync_get_library_delta_cursor` está indisponível remotamente.
- A sincronização da biblioteca não pode ser considerada confiável entre sessões.

**Resultado:** bloqueado remotamente; isolamento lógico por `profileId` está presente no cliente, mas não foi validado no banco.

### Isolamento entre perfis

- Adaptadores recebem `profileId` explicitamente.
- Não foi possível validar as políticas e funções remotas porque as RPCs não estão disponíveis no schema cache.

**Resultado:** não verificado com segurança.

### Falhas de internet

- O código possui tratamento por etapa e estados offline/cache.
- Não foi executado um ciclo completo: offline → mutação local → reconexão → sincronização remota.

**Resultado:** parcial / não verificado ponta a ponta.

## 3. Tela inicial

### Catálogos

No runtime, a Home exibiu seções distintas. Após a correção do parser `ep_anime_id`, “Novos episódios” mostrou itens próprios, incluindo:

- `Link Click`
- `koori no jouheki`
- `The Iceblade...`

“Animes em alta” e “Recém adicionados” também carregaram cards. A correção correspondente está no commit `a2628b4e`, com teste em `TomatoProviderTest.kt`.

**Resultado:** aprovado para diferenciação dos catálogos no APK auditado.

### Navegação inferior e sobreposição

A Home usa `kaplaySafeBottomPadding()` e `LocalKAPlayBottomNavigationOverlayPadding`. Foi aplicado o ajuste de reserva de `72.dp` para `120.dp` no commit `bb0dc513`.

O screenshot posterior ainda mostrou o título “Novos episódios” parcialmente sob a barra flutuante em determinada posição de scroll. Portanto, o aumento isolado do padding não resolve completamente a sobreposição.

**Resultado:** reprovado visualmente; requer investigação independente antes de novo redesign.

### Barra de status / hero

- O hero ocupa a região superior e respeita a área de status no screenshot auditado.
- O botão de recompensa permanece visível no canto superior direito.

**Resultado:** aprovado visualmente no recorte auditado, sem validação em todos os tamanhos.

### Imagens e cache

- Imagens carregaram no runtime com posters visíveis em múltiplas seções.
- Não foi possível medir cache hit/miss ou testar falha de CDN de forma controlada.

**Resultado:** funcionamento básico aprovado; desempenho/cache não verificado quantitativamente.

### Recompensa

O botão `1h grátis` permaneceu visível durante o runtime da Home. A entrega de recompensa e remoção de anúncios não foram validadas neste ciclo.

**Resultado:** presença aprovada; fluxo de anúncio/recompensa não verificado.

### Navegação e Voltar

A atividade `com.kaplay.app/.launcher.AppIconDefault` permaneceu em foreground durante a coleta. Não foi concluído um roteiro completo de abrir detalhes → voltar → rolar → trocar aba.

**Resultado:** não verificado ponta a ponta.

## 4. Runtime, crashes e memória

- APK instalado: `0.9.18`, `versionCode 191`.
- `dumpsys meminfo` anterior registrou aproximadamente 339 MB PSS total e heap alocado de aproximadamente 40 MB.
- Não foi encontrado `FATAL EXCEPTION` ou ANR no recorte usado para esta auditoria.
- Foram encontrados erros de RPC e avisos de serviços de rede do emulador.
- A hierarquia UIAutomator expôs apenas o `ComposeView`, sem semântica interna suficiente para automação externa.
- Frida estava escutando em `27042`, mas a enumeração de processos terminou com `TransportError: connection reset by peer`; hooks não foram considerados aprovados.

## 5. Build e artefatos

GitHub Actions:

- Workflow de catálogo `40994600767`: `compileFullDebugKotlin` e `assembleFullRelease` concluídos com sucesso.
- Workflow de espaçamento `41061665088`: `compileFullDebugKotlin`, `assembleFullRelease` e `publish-plugin-pages` concluídos com sucesso.

A compilação passou, mas isso não foi usado como prova de correção funcional; as conclusões acima foram baseadas nos probes RPC, logs e screenshots reais.

## 6. Bloqueios críticos

1. Cinco RPCs exigidas pelo cliente não estão disponíveis no schema cache remoto.
2. A sincronização de histórico, progresso e biblioteca não pode ser aprovada entre sessões.
3. A sobreposição da navegação inferior permanece reproduzível visualmente.
4. Isolamento de dados entre perfis ainda não foi validado no banco.

## 7. Próximas etapas recomendadas

1. Obter confirmação administrativa do estado das cinco RPCs no projeto Supabase, sem alterar nada.
2. Produzir uma matriz de contratos: assinatura, retorno, `GRANT EXECUTE`, `security invoker/definer`, RLS e testes.
3. Só depois decidir entre migration ausente ou refresh de schema cache.
4. Corrigir a navegação inferior em uma alteração isolada, com screenshot antes/depois.
5. Repetir teste de dois perfis para histórico, progresso e biblioteca.
6. Repetir o fluxo de recompensa e reprodução com logs redigidos.

## Conclusão

**Status geral: REPROVADO para sincronização remota; PARCIAL para a Home; APK compilável e executável.**

Não iniciar redesign da Home nem criar/substituir RPCs enquanto os bloqueios de backend e a sobreposição da navegação não estiverem isolados e aprovados.
