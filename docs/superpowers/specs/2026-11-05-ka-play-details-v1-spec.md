# KA PLAY — DETALHES DE CONTEÚDO (DESIGN SYSTEM V1.0)

**Plataforma:** Android / Kotlin / Compose Multiplatform
**Tela:** `MetaDetailsScreen` (Página de Detalhes de Filmes e Séries)
**Estilo:** Dark Streaming UI (Netflix-inspired, KA Play Purple Identity)
**Referência:** `docs/superpowers/specs/2026-11-04-ka-play-design-system-v1.md`

---

## 1. Princípios e Hierarquia da Tela

A página de detalhes deve apresentar visual imersivo, limpo e direto ao ponto, com foco no conteúdo audiovisual e navegação rápida por temporadas e episódios.

### Hierarquia Obrigatória
1. **Floating Header Superior:**
   - Botão de voltar acessível (touch target >= 48 dp).
   - Título e ação rápida de salvar/favoritar que surgem suavemente à medida que a página é rolada.
   - Fundo translúcido que atinge `KAColors.Background` (`#0B0B0F`) na rolagem completa.

2. **Hero Backdrop:**
   - Imagem de fundo em alta definição (backdrop/banner real do catálogo).
   - Gradiente inferior vertical suave terminando em `KAColors.Background` (`#0B0B0F`), integrando a arte com o fundo sólido da tela sem cortes secos.
   - Logo oficial do título ou tipografia Display (30 sp, Bold).

3. **Metadados Principais (Inline):**
   - Ano de lançamento, classificação indicativa (badge compacto de 8 dp em `KAColors.Outline` e texto `KAColors.TextSecondary`), duração e notas (IMDb / TMDb).
   - Gêneros e sinopse com expansão controlada (`TextOverflow.Ellipsis`).

4. **Botões de Ação Principais:**
   - **Botão "Assistir":** Fundo `KAColors.Primary` (`#9729D7`), texto branco, cantos de 10 dp (`KAShapes.Button`), altura 48 dp (área de toque >= 48 dp).
   - **Botões Secundários ("Minha Lista / Salvar", "Trailer", "Aleatório"):** Fundo `KAColors.SurfaceVariant` (`#23232B`), texto branco, cantos de 10 dp, sem sombras ou bordas neon.

5. **Conteúdo de Séries (Seletor de Temporadas e Lista de Episódios):**
   - Seletor de temporadas em chips compactos (8 dp, `KAShapes.Chip`), ativo em `KAColors.Primary` e inativo em `KAColors.SurfaceVariant`.
   - Cards de episódio em proporção horizontal 16:9, cantos de 10 dp (`KAShapes.Card`).
   - Thumbnail real do episódio com fallback para o backdrop da série.
   - Barra de progresso roxa nativa (`KAColors.Primary`, `#9729D7`) com trilha discreta para episódios em andamento.
   - Título do episódio limitado a 2 linhas (`TextOverflow.Ellipsis`), tempo de duração e data.

6. **Elenco e Conteúdos Relacionados:**
   - Avatares de elenco circulares com nome do ator e personagem.
   - Trilho de recomendações/títulos similares com posters 2:3 (cantos de 8 dp, `KAShapes.Poster`).

---

## 2. Tokens Aplicados

- `background`: `#0B0B0F` (`KAColors.Background`)
- `surface`: `#19191F` (`KAColors.Surface`)
- `surfaceVariant`: `#23232B` (`KAColors.SurfaceVariant`)
- `primary`: `#9729D7` (`KAColors.Primary`)
- `primaryLight`: `#C477E8` (`KAColors.PrimaryLight`)
- `textPrimary`: `#F5F5F7` (`KAColors.TextPrimary`)
- `textSecondary`: `#A3A3AD` (`KAColors.TextSecondary`)
- `textMuted`: `#777781` (`KAColors.TextMuted`)
- `outline`: `#393941` (`KAColors.Outline`)
- `error`: `#E46770` (`KAColors.Error`)

- Raio de botões: 10 dp (`KAShapes.Button`)
- Raio de cards de episódio: 10 dp (`KAShapes.Card`)
- Raio de posters relacionados: 8 dp (`KAShapes.Poster`)
- Raio de chips de temporada: 8 dp (`KAShapes.Chip`)
