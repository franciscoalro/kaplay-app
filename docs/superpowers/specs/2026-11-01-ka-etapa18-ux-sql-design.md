# KA Play Etapa 18 — UX visível + validação SQL isolada

## Intenção
Avançar desenvolvimento sem tocar a candidata `build-tomato-cmp`/0.9.20 nem Supabase de produção: tornar a validação SQL reproduzível em CI e melhorar estados visíveis de Home, Busca, Biblioteca, Perfil e navegação.

## Objetivos
- SQL: stack PostgreSQL/Supabase isolada, migrations candidatas aplicadas apenas no job, assertions que falham o job, logs sanitizados e artefatos verificáveis.
- UX: mudanças incrementais, identidade KA Play preservada, estados vazios/carregamento/sync reais, foco D-pad e acessibilidade.

## Não objetivos
- Merge automático, release, assinatura, APK, instalação, publicação ou redesign completo.
- Esconder erros de backend ou usar dados falsos para mascarar falhas.

## Abordagens consideradas
1. Uma frente única: rejeitada por conflito de arquivos e por misturar risco de banco com UI.
2. Duas worktrees independentes, SQL via GitHub Actions e UX com testes Compose/host: escolhida por isolamento, revisão independente e evidência separada.
3. Ajustes somente cosméticos: rejeitada porque não comprova estados reais de erro/reconexão e sincronização.

## Decisões
- SQL usa o script isolado existente como base, corrigindo captura de logs e adicionando assertions para rewards/ownership/idempotência/concurrency.
- UX começa por componentes de estado/reutilização e telas com maior impacto, sem alterar contratos do backend.
- Builds/testes Android continuam exclusivamente no GitHub Actions; nenhuma execução local de Gradle/Java.
