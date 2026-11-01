# Goal: Integrar os 13 Provedores CloudStream no KA Play

## 1. Escopo de Provedores
1. Tomato (Ja integrado)
2. AnimeFire (Animes - API Angular)
3. CineGato (Filmes/Series - REST API)
4. Pobreflix (Filmes/Series - Web embed)
5. NetCine (Filmes/Series - Scraper)
6. RedeCanais / RedeCanaisAF (Filmes/Series/Animes - Scraper)
7. CineVision (Filmes/Series - API/HLS)
8. SuperCine (Filmes/Series - Scraper)
9. TopAnimes (Animes - Scraper)
10. VeloPlayTV (Canais/Streams - M3U8)
11. AnimesOrion (Animes - Scraper)
12. MegaFilmesHD / EmbedPlay (Filmes/Series - Embed)
13. PPCine (Filmes/Series - Token/API)

## 2. Etapas de Execucao
- [ ] Fase 1: Auditar cada provedor Kotlin no kythourcl (endpoints, seletores, autenticacao e extratores de video).
- [ ] Fase 2: Implementar os scrapers QuickJS correspondentes em plugins/<provedor>/<provedor>.js no kaplay-app.
- [ ] Fase 3: Atualizar plugins/manifest.json para registrar todos os 13 scrapers com tipos de midia suportados e configuracoes.
- [ ] Fase 4: Atualizar EmbeddedPlugins.kt e PluginRepository.kt no kaplay-app para embutir todos os 13 scrapers nativamente no APK.
- [ ] Fase 5: Atualizar e testar a publicacao do GitHub Pages para servir o repositorio completo de plugins em https://franciscoalro.github.io/kaplay-app/manifest.json.
- [ ] Fase 6: Executar compilacao e testes no GitHub Actions (build.yml) gerando os APKs release.
- [ ] Fase 7: Baixar, inspecionar e validar os APKs release com aapt e apksigner.
- [ ] Fase 8: Publicar a nova release oficial com todos os provedores embutidos.
