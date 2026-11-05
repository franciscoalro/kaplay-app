# KA PLAY — SELETOR DE TEMPORADAS EM DROPDOWN (DESIGN SYSTEM V1.0)

**Plataforma:** Android / Kotlin / Compose Multiplatform
**Componente:** `KASeasonDropdown` em `DetailSeriesContent.kt`
**Estilo:** Dark Streaming UI (Netflix-inspired, KA Play Purple Identity)

---

## 1. Objetivo e Comportamento

Substituir a lista horizontal de chips de temporadas por um **Dropdown compacto** que exibe a temporada selecionada e se expande ao toque/clique, garantindo ergonomia em smartphones e suporte a D-pad no Android TV.

### Requisitos Funcionais
1. **Gatilho Compacto:** Botão compacto que exibe `Temporada X ▼` (ou `Especiais ▼` para temporada 0), ajustado ao conteúdo (sem ocupar toda a largura da tela).
2. **Menu Expansível (`DropdownMenu`):** Ao tocar ou pressionar Enter/D-pad Center, expande o menu com a lista completa de temporadas (`heightIn(max = 320.dp)` com rolagem vertical suave).
3. **Destaque da Temporada Ativa:** A temporada selecionada é renderizada com cor `KAColors.PrimaryLight` e ícone de seleção (`Icons.Default.Check`). As demais temporadas em branco.
4. **Atualização Imediata:** Ao clicar em uma temporada, o menu se fecha imediatamente e invoca `onSeasonSelected(season)`, atualizando os episódios exibidos.
5. **Preservação de Estado:** Preserva `selectedSeasonOverride`, seleção automática do episódio preferido e histórico de navegação.
6. **Suporte a Android TV:** Foco visível com borda branca de 2 dp de alto contraste e navegação fluida por controle remoto.
7. **Modo Pôsteres:** Mantém a alternância para o modo pôsteres (`SeasonPosterScrollRow`) quando existirem pôsteres de temporada configurados.

---

## 2. Tokens de Design

- **Fundo do Botão Gatilho:** `KAColors.SurfaceVariant` (`#23232B`)
- **Borda do Botão Gatilho:** 1 dp `KAColors.Outline` (`#393941`) (2 dp `Color.White` quando focado)
- **Fundo do Menu:** `KAColors.Surface` (`#19191F`)
- **Texto Normal:** `Color.White` (14 sp, Medium)
- **Texto Selecionado:** `KAColors.PrimaryLight` (`#C477E8`) (14 sp, SemiBold)
- **Raio dos Cantos:** 10 dp (`RoundedCornerShape(10.dp)`)
- **Altura Mínima:** 48 dp (`heightIn(min = 48.dp)`) para touch target acessível
