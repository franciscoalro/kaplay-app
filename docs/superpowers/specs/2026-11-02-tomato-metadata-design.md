# Tomato metadata confiável

## Intenção

Impedir que séries Tomato sejam exibidas ou persistidas com identificadores como `Tomato #6945` ou `tomato:6945`. Nomes, temporadas e episódios devem vir dos campos reais da API Tomato.

## Objetivos

- Mapear nomes a partir de `anime_details.anime_name`.
- Mapear temporadas a partir de `anime_seasons` usando `season_id`, `season_number` e `season_name`.
- Mapear episódios a partir de `data` usando `ep_id`, `ep_number`, `ep_name` e `ep_thumbnail`.
- Evitar persistir fallback técnico como título de biblioteca/favorito.
- Cobrir o parser com testes usando o formato real observado na API.

## Não objetivos

- Alterar a API Tomato, banco Supabase ou dados já persistidos remotamente.
- Trocar o pacote ou a assinatura do APK.
- Fazer migração destrutiva de itens existentes.

## Abordagens consideradas

1. **Enriquecimento obrigatório por metadata (escolhida)** — resolver detalhes reais antes de criar/persistir a representação exibível; rejeitar ou adiar títulos sem nome válido. Corrige a origem e protege biblioteca, favoritos, histórico, temporadas e episódios.
2. **Somente ampliar o parser do feed** — menor alteração, mas não corrige itens antigos ou respostas que contenham apenas IDs.
3. **Corrigir apenas a UI** — mascara o problema e deixa dados inválidos armazenados.

## Decisão de implementação

Manter a identidade interna `tomato:<animeId>` para navegação e sincronização, mas separar essa identidade do título exibido. O parser aceitará os campos reais e aliases compatíveis. A camada de catálogo não usará `Tomato #<id>` como nome final quando uma consulta de detalhes puder fornecer o título. A camada de detalhes continuará usando os IDs estáveis para montar temporadas e episódios.

Itens Tomato já persistidos com título técnico serão tratados como inválidos para exibição e poderão ser enriquecidos sob demanda; não haverá exclusão automática.

## Verificação

- Testes unitários do parser para nome, temporadas e episódios com payload real.
- Teste de regressão garantindo que título técnico não seja aceito como nome exibível.
- Compilação/testes Android host e build Debug.
- Validação manual no mesmo pacote e assinatura existentes, sem limpar dados sem confirmação.
