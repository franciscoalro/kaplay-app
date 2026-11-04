# KA Play Login Redesign — Design

## Objetivo

Atualizar apenas a apresentação da `AuthScreen` para o visual premium do KA Play, preservando o fluxo atual de login, cadastro, modo anônimo, login por código de TV, estados de erro/carregamento e persistência local/Supabase.

## Decisão de arquitetura

A implementação será uma evolução cirúrgica de `composeApp/src/commonMain/kotlin/com/kaplay/app/features/auth/AuthScreen.kt`; não haverá uma segunda tela de autenticação nem um novo ViewModel. `AuthRepository`, `DeviceLinkAuthRepository`, `ServerConnectionController` e `AppGate` continuam sendo as fontes dos estados e ações.

A tela continuará usando os dois layouts existentes (`AuthMobileLayout` e `AuthLargeLayout`) para preservar responsividade e navegação por controle. O formulário continuará com componentes customizados compatíveis com o comportamento atual de foco/toque, recebendo apenas novos tokens de cor, borda, raio, espaçamento, tipografia e fundo em Canvas/Brush.

## Comportamento preservado

- Login Supabase e cadastro continuam chamando `AuthRepository`.
- Erros e carregamento continuam provenientes dos estados existentes.
- Modo anônimo e login por código/TV permanecem disponíveis quando suportados.
- Nenhum armazenamento local, sessão, favorito, histórico ou sincronização será limpo.
- A logo original (`AppBrandWordmark`) será reutilizada.

## Funcionalidade adicional delimitada

O link “Esqueceu a senha?” será exibido somente no modo de login. Ele usará uma nova função pequena de `AuthRepository` baseada em `supabase.auth.resetPasswordForEmail(email)`, com email validado localmente, estado de sucesso/erro local e strings traduzíveis. Não será criado um sistema paralelo de recuperação nem alterado o roteamento de sessão.

## Visual

- Fundo `#090610` com gradientes nativos e glows discretos.
- Roxos `#210B38`, `#A844F5` e `#C878FF` para fundo, foco e links.
- Campos `#171121`, borda `#493954`, raio de 20 dp e altura próxima de 60 dp.
- Botão principal claro `#F8F8FA` com texto `#101015`.
- Formulário limitado a 440–480 dp em telas grandes e rolável com teclado aberto.
- Contraste, foco D-pad e acessibilidade mantidos; sem animações pesadas.

## Limites

Não serão adicionados novos provedores, mudanças de banco/RLS, migrações Supabase, alterações de pacote ou mudanças na sincronização. Strings novas serão adicionadas ao catálogo base e aos recursos localizados seguindo o padrão existente.

## Critérios de aceitação

1. A composição visual segue a paleta e proporções aprovadas em mobile e telas grandes.
2. Login, cadastro, modo anônimo, alternância de modo, mostrar/ocultar senha e login por TV continuam funcionais.
3. Recuperação envia solicitação para o Supabase somente com email válido e mostra resultado sem limpar os campos.
4. Campos vazios, email inválido, senha curta, erro remoto e carregamento têm estados claros.
5. Testes de lógica/UI e GitHub Actions passam; nenhuma compilação local/VPS será usada.
