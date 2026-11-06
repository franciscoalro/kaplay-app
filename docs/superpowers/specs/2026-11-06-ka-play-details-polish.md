# KA Play — Polimento Final da Página de Detalhes

Refinar a página de detalhes existente sem reconstruir componentes. Preservar episódios verticais, dropdown, reprodução, histórico, progresso, assistido, favoritos, sincronização, perfis e seleção de streams.

Requisitos principais:
- Cabeçalho compacto com contagem real da temporada selecionada e dropdown existente.
- Cards verticais com superfície escura, borda discreta, thumbnail 16:9, título de até duas linhas, menu de 48dp, progresso real e indicador assistido.
- Remover identificação de temporada/episódio duplicada no metadata do card.
- Hero preserva altura atual, trailer, modos de fundo, insets e imagem; reduzir zoom fixo e manter gradiente inferior legível.
- Usar KAColors/KAShapes existentes; sem glassmorphism, glow, 3D ou sombras pesadas.
- Não modificar repositórios de autenticação, perfil, progresso, assistido, Supabase, RPCs ou resolução de streams.
- Validar em CI; não executar publicação Release.
