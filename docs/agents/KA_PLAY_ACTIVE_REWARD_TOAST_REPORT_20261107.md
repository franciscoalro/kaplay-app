# Relatório — aviso de recompensa ativa no toque

Data da rodada: 2026-11-07
Repositório: `/root/cloudstream_gate/kaplay-app`
Branch: `build-tomato-cmp`
Base antes da alteração: `e8eec45f (HEAD -> build-tomato-cmp) chore: bump app version to 0.9.30`

## Escopo implementado

Quando a recompensa já está ativa:

- a faixa/botão da Home continua clicável, exceto durante sincronização;
- o clique lê `AdRewardRepository.currentState()` no instante do toque;
- mostra `KAPlayToastController` com `Você já está sem anúncios. Restam ... de benefício.`;
- trata durações em horas/minutos, minutos e `menos de 1 minuto`;
- retorna antes de login, gate de claim e `RewardedAdsPlatform.show`, portanto não abre anúncio nem concede/estende/reinicia benefício;
- o fluxo inativo continua chamando `showRewarded()` normalmente;
- em estado ativo com erro visual, o caminho ativo tem precedência sobre retry.

Nenhuma credencial, migration, backend, versão ou configuração de release foi alterada.

## TDD — RED pré-implementação

Teste/contrato adicionado antes da implementação:

- `composeApp/src/commonTest/kotlin/com/kaplay/app/features/rewards/AdRewardActiveMessageTest.kt`
- caso adicional em `composeApp/src/commonTest/kotlin/com/kaplay/app/features/home/HomeRewardLabelTest.kt`

Comando executado antes da implementação:

```sh
python3 -c 'from pathlib import Path
controller=Path("composeApp/src/commonMain/kotlin/com/kaplay/app/features/rewards/AdRewardController.kt").read_text()
home=Path("composeApp/src/commonMain/kotlin/com/kaplay/app/features/home/HomeScreen.kt").read_text()
failures=[]
if "fun activeAdRewardMessage" not in controller: failures.append("activeAdRewardMessage ainda não existe")
if "remainingMs <= 0L || rewardState.error != null" in home: failures.append("faixa ativa ainda está desabilitada")
if not failures: raise SystemExit("RED esperado não ocorreu")
print("RED (pré-implementação) — contrato falhou como esperado:")
for failure in failures: print(f"- {failure}")
raise SystemExit(1)'
```

Resultado exato: exit `1`.

```text
RED (pré-implementação) — contrato falhou como esperado:
- activeAdRewardMessage ainda não existe
- faixa ativa ainda está desabilitada
```

## GREEN pós-implementação

Contrato automatizado executado depois da implementação:

```text
GREEN — verificações de contrato passaram:
- mensagem pt-BR
- menos de minuto
- leitura real no clique
- toast no estado ativo
- retorno sem anúncio
- botão ativo clicável
- teste de menos de minuto
```

`git diff --check`: exit `0` (`diff_check_exit=0`).

A suíte Kotlin/Gradle e build Android/iOS não foram executados localmente porque `AGENTS.md:10` determina explicitamente: “Build/testes Android/iOS exclusivamente no GitHub Actions. Não executar gradlew, java, kotlinc...”. Assim, a suíte pertinente e o build permanecem **NÃO TESTADOS** nesta rodada; o GREEN acima é uma verificação de contrato de fonte, não substitui a execução Kotlin no CI.

## Arquivos alterados

- `composeApp/src/commonMain/kotlin/com/kaplay/app/features/home/HomeScreen.kt`
  - habilita toque durante benefício ativo;
  - prioriza o aviso ativo antes do retry.
- `composeApp/src/commonMain/kotlin/com/kaplay/app/features/rewards/AdRewardController.kt`
  - lê saldo atual no clique;
  - exibe toast e retorna sem fluxo de anúncio/claim.
- `composeApp/src/commonMain/kotlin/com/kaplay/app/features/rewards/AdRewardMessage.kt`
  - formatação pt-BR da duração restante.
- `composeApp/src/commonTest/kotlin/com/kaplay/app/features/home/HomeRewardLabelTest.kt`
  - regressão: benefício ativo continua clicável.
- `composeApp/src/commonTest/kotlin/com/kaplay/app/features/rewards/AdRewardActiveMessageTest.kt`
  - horas/minutos, minuto singular e menos de um minuto.
- este relatório.

## Checklist manual para CI/dispositivo

1. Entrar com conta não anônima e obter benefício ativo.
2. Tocar a faixa da Home e a linha equivalente em Configurações.
3. Confirmar toast com o prefixo/sufixo exatos e duração correspondente ao momento do toque.
4. Repetir com menos de um minuto restante e confirmar `Restam menos de 1 minuto de benefício.`.
5. Confirmar que nenhum anúncio abre, nenhum novo claim é criado e a expiração não é estendida/reiniciada.
6. Com benefício expirado/inativo, tocar e confirmar que o fluxo atual de anúncio recompensado permanece.
7. Com usuário anônimo/deslogado, confirmar que o fluxo atual de autenticação permanece.
8. Validar no GitHub Actions a suíte host pertinente e build autorizado; anexar SHA do commit, workflow, run/job e artefato.

## Limitações/status

- Não há dispositivo ADB conectado nesta sessão (`adb devices -l` retornou somente `List of devices attached`).
- Não houve validação visual, instalação de APK, login, anúncio real ou teste de expiração em dispositivo.
- O texto exibido pela faixa pode continuar sendo um snapshot arredondado; o toast do clique usa a expiração persistida recalculada no instante do toque.
- Não houve push, publicação, assinatura, alteração de dados reais ou dispatch de workflow.
