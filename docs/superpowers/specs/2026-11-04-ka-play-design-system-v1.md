# KA PLAY — DESIGN SYSTEM V1.0

**Plataforma:** Android / Kotlin / Compose Multiplatform
**Estilo:** Dark Streaming UI
**Referência:** Netflix — hierarquia, densidade e organização
**Identidade:** KA Play — preto, roxo e branco

---

## 1. Princípios do design

O KA Play deve apresentar uma interface profissional, moderna, compacta e funcional.

### Diretrizes obrigatórias

- Priorizar conteúdo em vez de elementos decorativos.
- Utilizar superfícies sólidas e cores chapadas.
- Manter a identidade roxa do aplicativo.
- Evitar transparências desnecessárias.
- Não utilizar glassmorphism, efeitos 3D ou neon exagerado.
- Evitar botões grandes e arredondamentos excessivos.
- Priorizar tipografia, alinhamento, espaçamento e hierarquia.
- Usar imagens reais do catálogo, sem recriá-las artificialmente.
- Garantir comportamento responsivo.
- Manter consistência entre todas as telas.

**Regra central:** a interface deve parecer um aplicativo de streaming real, não um conceito gráfico criado por IA.

---

## 2. Paleta de cores

### Cores principais

| Token | HEX | Aplicação |
|---|---|---|
| `background` | `#0B0B0F` | Fundo principal |
| `surface` | `#19191F` | Cards e painéis |
| `surfaceVariant` | `#23232B` | Campos e botões secundários |
| `surfaceElevated` | `#2C2C35` | Superfícies destacadas |
| `primary` | `#9729D7` | Ações principais |
| `primaryPressed` | `#7B20B4` | Pressionamento |
| `primaryLight` | `#C477E8` | Links e elementos selecionados |
| `onPrimary` | `#FFFFFF` | Texto sobre roxo |
| `onBackground` | `#F5F5F7` | Texto principal |
| `textSecondary` | `#A3A3AD` | Subtítulos |
| `textMuted` | `#777781` | Informações auxiliares |
| `outline` | `#393941` | Bordas discretas |
| `success` | `#36B57B` | Sincronização confirmada |
| `warning` | `#E7B34D` | Sincronização pendente |
| `error` | `#E46770` | Falhas |

### Regras de aplicação

- Preto e cinza dominam a interface.
- Roxo é reservado para ações principais, foco e seleção.
- Branco é utilizado para textos e ações neutras de destaque.
- Não aplicar contornos roxos em todos os componentes.
- Não usar gradientes roxos em cards comuns.

**Exceção:** a tela de login pode preservar o gradiente roxo-escuro discreto já aprovado.

---

## 3. Tipografia

**Fonte:** preferencialmente a fonte já utilizada pelo KA Play. Se não houver uma tipografia definida, utilizar Roboto ou uma fonte sans-serif compatível com Compose.

| Estilo | Tamanho | Peso | Line-height |
|---|---:|---|---:|
| Display | 30 sp | Bold | 36 sp |
| Título de página | 26 sp | Bold | 32 sp |
| Título de seção | 20 sp | SemiBold | 26 sp |
| Título de card | 14 sp | Medium | 19 sp |
| Corpo | 14 sp | Regular | 21 sp |
| Secundário | 12 sp | Regular | 17 sp |
| Legenda | 11 sp | Medium | 15 sp |
| Botão | 14 sp | SemiBold | 20 sp |
| Navegação inferior | 11 sp | Medium | 15 sp |

### Hierarquia

1. Título principal da tela.
2. Controles ou filtros.
3. Títulos das seções.
4. Conteúdo visual.
5. Informações secundárias.

Evitar títulos enormes que consumam espaço útil.

Aplicar `maxLines` e `TextOverflow.Ellipsis` em textos variáveis, preservando títulos completos nas páginas de detalhes.

---

## 4. Sistema de espaçamento

Utilizar escala baseada em 4 dp.

| Token | Valor |
|---|---:|
| `space1` | 4 dp |
| `space2` | 8 dp |
| `space3` | 12 dp |
| `space4` | 16 dp |
| `space5` | 20 dp |
| `space6` | 24 dp |
| `space8` | 32 dp |
| `space10` | 40 dp |

### Padrões

- Margem horizontal das páginas: 16 dp.
- Distância entre título e conteúdo: 12–16 dp.
- Espaçamento entre seções: 24–28 dp.
- Distância entre cards: 8–12 dp.
- Espaçamento interno de cards: 12–16 dp.
- Evitar blocos vazios excessivos.

---

## 5. Bordas e arredondamentos

| Componente | Raio |
|---|---:|
| Campos de texto | 12 dp |
| Botões principais | 10 dp |
| Botões secundários | 10 dp |
| Cards comuns | 10 dp |
| Poster de filme | 8 dp |
| Banner de destaque | 12 dp |
| Chips de categoria | 8 dp |
| Avatar | Circular |
| Modal | 16 dp |

**Evitar arredondamento de 24–30 dp em todos os componentes.**

---

## 6. Botões

### Botão principal

Uso: login, salvar, criar perfil, reproduzir quando houver destaque.

- Altura visual: 44–48 dp.
- Fundo: `primary`.
- Texto: branco.
- Cantos: 10 dp.
- Sem brilho e sem sombra exagerada.
- Feedback visual ao pressionar.
- Área de toque acessível, preferencialmente de pelo menos 48 dp.

### Botão secundário

- Fundo: `surfaceVariant`.
- Texto branco.
- Sem borda, salvo necessidade funcional.
- Altura: 44–48 dp.

### Botão de texto

Uso: “Cadastrar”, “Esqueceu a senha?”, “Ver tudo”.

- Sem fundo.
- Sem contorno.
- Texto roxo ou branco.
- Área de toque acessível.

### Botões do banner

- Compactos.
- “Assistir” com maior destaque.
- “Minha lista” como ação secundária.
- Altura aproximada: 36–40 dp, com área de toque acessível.
- Evitar que os botões cubram a arte ou o título.

---

## 7. Campos de formulário

### Padrão

- Altura: 52 dp.
- Fundo: `surface`.
- Bordas: 1 dp, somente quando necessárias.
- Raio: 12 dp.
- Texto: 15 sp.
- Ícones: 20 dp.
- Placeholder com contraste adequado.

### Estados

**Normal:** borda cinza discreta.

**Focado:** borda roxa.

**Erro:** borda vermelha e mensagem objetiva.

**Desabilitado:** aparência atenuada, mantendo legibilidade.

### Tela de login

Manter:

- Logo centralizada.
- Slogan.
- “Bem-vindo de volta”.
- E-mail.
- Senha.
- Recuperação de senha.
- Entrar.
- Cadastrar.
- Continuar sem conta.

**Não incluir “Entrar por link”.**

“Esqueceu a senha?” e “Cadastrar” devem ser links simples, sem retângulos ou bordas ao redor.

O aviso de armazenamento local deve ficar diretamente abaixo de “Continuar sem conta”.

---

## 8. Navegação inferior

### Estrutura

Quatro destinos:

1. Início
2. Busca
3. Biblioteca
4. Perfil

### Estilo

- Fundo preto ou `surface`.
- Ícones de 22–24 dp.
- Rótulos de 10–11 sp.
- Item selecionado em roxo.
- Itens inativos em cinza.
- Sem grandes cápsulas ou efeitos de vidro.
- Separador superior discreto, se necessário.

### Comportamento

- Barra fixa no rodapé.
- Respeitar as áreas seguras do Android.
- Não cobrir o conteúdo.
- A lista precisa ter padding inferior adequado.
- Ocultar a navegação quando o player estiver em tela cheia.

---

## 9. Home — Tela inicial

### Hierarquia obrigatória

**1. Header**

- Logo KA Play à esquerda.
- Busca e perfil à direita.
- Recompensa de anúncios em formato compacto, sem competir com o conteúdo.

**2. Categorias**

- Início
- Filmes
- Séries
- Animes
- TV

Usar chips compactos com seleção em roxo.

**3. Banner rotativo**

- Imagem real do catálogo.
- Proporção horizontal.
- Gradiente preto discreto somente para legibilidade.
- Título com máximo de 2 linhas.
- Metadados resumidos.
- Botões compactos: Assistir e Minha lista.
- Indicador pequeno da posição do carrossel.

Não permitir títulos cortados de forma excessiva.

**4. Continuar assistindo**

- Cards horizontais.
- Imagem do conteúdo.
- Título.
- Temporada/episódio quando disponível.
- Barra de progresso roxa.
- Tempo restante quando confiável.

Não mostrar identificadores técnicos como `tomato:6906` ao usuário.

**5. Trilhos de conteúdo**

Exemplos:

- Em alta
- Lançamentos
- Filmes populares
- Séries populares
- Animes em alta
- Minha lista

Somente mostrar seções que possuam dados ou um estado vazio útil.

**6. Navegação inferior**

Fixa e sem sobreposição.

---

## 10. Banner rotativo

### Proporções

- Largura: disponível na tela, com margens coerentes.
- Altura no smartphone: aproximadamente 200–260 dp, ajustável pela largura.
- Raio: 12 dp.
- Imagem com `ContentScale.Crop`.

### Conteúdo

- Categoria ou tipo.
- Título.
- Sinopse curta, se houver espaço.
- Ações.
- Indicadores de carrossel.

### Comportamento

- Rotação automática opcional.
- Intervalo sugerido: 6–8 segundos.
- Pausar quando o usuário estiver interagindo.
- Não reiniciar o carrossel a cada recomposição.
- Suportar gestos horizontais.
- Manter posição durante mudanças de estado.

Evitar animações de zoom excessivas.

---

## 11. Cards de conteúdo

### Poster vertical

Proporção: 2:3.

Componentes:

- Imagem.
- Título abaixo.
- Informações secundárias opcionais.

### Card horizontal

Proporção aproximada: 16:9.

Uso: Continuar assistindo e destaques compactos.

Componentes:

- Thumbnail.
- Progresso.
- Título.
- Informações de episódio.

### Estados

- Carregando: skeleton discreto.
- Sem imagem: placeholder neutro.
- Erro: fallback.
- Selecionado: indicador roxo discreto.

Não utilizar imagens decorativas inventadas para representar o catálogo real.

---

## 12. Biblioteca

### Hierarquia

1. Título Biblioteca.
2. Alternância Salvo / Nuvem.
3. Ordenação.
4. Filtros, quando necessários.
5. Conteúdo.
6. Navegação inferior.

### Layout

- Grade de 3 posters em celulares com largura suficiente.
- Ajustar dinamicamente para telas menores.
- Espaçamento entre posters: 8–12 dp.
- Títulos limitados a 2 linhas.
- Evitar grandes áreas vazias sem explicação.

### Sincronização

Diferenciar:

- Salvo neste dispositivo.
- Pendente de sincronização.
- Sincronizando.
- Salvo na nuvem.
- Erro de sincronização.

Nunca marcar como sincronizado antes da confirmação real do Supabase.

---

## 13. Busca

### Hierarquia

1. Título Busca.
2. Campo de pesquisa.
3. Filtros de tipo, catálogo e gênero.
4. Resultados ou estado vazio.
5. Barra inferior.

### Estado vazio

Utilizar:

- Ícone simples.
- Título objetivo.
- Descrição curta.
- Ação funcional, quando aplicável.

Evitar grandes ilustrações 3D ou cards decorativos que ocupem a tela.

Exemplo:

**Nenhuma fonte conectada**

Adicione um addon ou conecte um servidor para encontrar conteúdos.

Ações:

- Adicionar fonte.
- Configurar servidor.

---

## 14. Perfis

### Seleção

- Logo KA Play.
- Título “Quem está assistindo?”.
- Avatares organizados em grade.
- Nome abaixo.
- Edição acessível separadamente.
- Adicionar perfil.
- Ação de concluir edição, somente quando o modo de gerenciamento exigir.

### Adicionar perfil

- Título.
- Prévia do avatar.
- Nome.
- Usar addons primários.
- Avatar personalizado opcional.
- Catálogo de avatares.
- Criar perfil.

Manter o formulário compacto, sem cards excessivamente grandes.

---

## 15. Configurações

### Organização

Grupos:

- Conta
- Geral
- Sobre
- Avançado

### Linha de configuração

- Ícone à esquerda.
- Título: 14–15 sp.
- Descrição: 11–12 sp.
- Seta ou controle à direita.
- Padding vertical aproximado: 10–12 dp.
- Altura adaptativa ao conteúdo.

Evitar títulos enormes e cards excessivamente altos.

Rodapé:

- Logo KA Play.
- Créditos.
- Versão.

---

## 16. Animações

### Permitidas

- Transição de tela: 150–250 ms.
- Feedback de toque.
- Fade discreto.
- Indicador de carregamento.
- Transição simples entre imagens do banner.

### Evitar

- Glow.
- Glassmorphism.
- Sombras coloridas.
- Escala 3D.
- Parallax exagerado.
- Transparência excessiva.
- Animações permanentes sem finalidade.

Respeitar configurações de acessibilidade e redução de movimento.

---

## 17. Tokens Kotlin Compose

Exemplo inicial:

```kotlin
object KAColors {
    val Background = Color(0xFF0B0B0F)
    val Surface = Color(0xFF19191F)
    val SurfaceVariant = Color(0xFF23232B)
    val SurfaceElevated = Color(0xFF2C2C35)

    val Primary = Color(0xFF9729D7)
    val PrimaryPressed = Color(0xFF7B20B4)
    val PrimaryLight = Color(0xFFC477E8)

    val TextPrimary = Color(0xFFF5F5F7)
    val TextSecondary = Color(0xFFA3A3AD)
    val TextMuted = Color(0xFF777781)

    val Outline = Color(0xFF393941)
    val Success = Color(0xFF36B57B)
    val Warning = Color(0xFFE7B34D)
    val Error = Color(0xFFE46770)
}
```

### Espaçamento

```kotlin
object KASpacing {
    val XS = 4.dp
    val SM = 8.dp
    val MD = 12.dp
    val LG = 16.dp
    val XL = 20.dp
    val XXL = 24.dp
    val Section = 28.dp
}
```

### Arredondamentos

```kotlin
object KAShapes {
    val Button = RoundedCornerShape(10.dp)
    val Field = RoundedCornerShape(12.dp)
    val Card = RoundedCornerShape(10.dp)
    val Poster = RoundedCornerShape(8.dp)
    val Banner = RoundedCornerShape(12.dp)
}
```

### Botão principal

```kotlin
@Composable
fun KAPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .heightIn(min = 48.dp),
        shape = KAShapes.Button,
        colors = ButtonDefaults.buttonColors(
            containerColor = KAColors.Primary,
            contentColor = Color.White
        )
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
```

Os componentes existentes devem ser reutilizados sempre que possível. Não criar um segundo tema global sem necessidade.

---

## 18. Requisitos de implementação

**Obrigatório:**

1. Identificar os componentes atuais do KA Play.
2. Centralizar tokens de design.
3. Remover estilos inconsistentes.
4. Aplicar o sistema gradualmente.
5. Preservar a lógica de autenticação.
6. Preservar a sincronização e os dados locais.
7. Garantir acessibilidade e responsividade.
8. Validar no dispositivo real.
9. Registrar alterações em commits separados por tela.
10. Gerar APK Debug para homologação.

**Não executar migrations, não apagar dados, não assinar Release e não publicar automaticamente.**

---

## 19. Critérios de aprovação

O Design System estará aplicado corretamente quando:

- Todas as telas utilizarem a mesma paleta.
- A tipografia seguir uma hierarquia consistente.
- Os botões apresentarem dimensões compactas.
- As bordas e os espaçamentos forem uniformes.
- A navegação não cobrir conteúdos.
- O banner tiver altura proporcional.
- A Biblioteca usar grades adaptativas.
- O app não parecer uma interface artificial com efeitos exagerados.
- O desempenho continuar adequado.
- Nenhuma funcionalidade existente for perdida.

**Resultado esperado:** KA Play com visual de streaming profissional, escuro, clean, compacto e funcional, com roxo utilizado como identidade e destaque — não como decoração excessiva.
