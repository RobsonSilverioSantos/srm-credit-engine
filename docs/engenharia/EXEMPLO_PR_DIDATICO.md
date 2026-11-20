# Exemplo de PR didático — referência de escrita para agentes

> **Exemplo hipotético de qualidade editorial. Não representa PR, commit ou testes realmente executados.** O agente deve substituir estes dados por fatos verificáveis da própria tarefa.

**Título de exemplo:** `feat(precificacao): implementar cálculo decimal e golden cases C1–C3`

## Qual problema resolvemos e por quê?

O domínio calcula valor presente de dois tipos de recebível. A regra deve reproduzir os golden cases do contrato e preservar centavos em operações financeiras. Separar Strategy e cálculo permite adicionar modalidades sem duplicar regras de arredondamento.

## Como a solução foi organizada?

- Estratégias selecionam o spread de cada modalidade; um componente puro realiza o cálculo com `BigDecimal`.
- Contratos de API transportam decimal como string, não float.
- `HALF_EVEN` só é aplicado nos pontos definidos no `SPEC.md`.

## Alternativas e decisões

- **Rejeitado:** `Double` por risco de divergência em centavos.
- **Rejeitado:** repetir fórmula em cada Strategy por duplicação de manutenção.
- **Escolhido:** função de cálculo compartilhada e estratégias pequenas.

## Como revisar e validar? *(substituir por execução real)*

- Comando a executar: `./gradlew test` no diretório do backend.
- Verificações esperadas: C1 BRL 92.859,94, C2 BRL 23.337,77, C3 USD 17.094,67; prazo não positivo rejeitado; política HALF_EVEN aplicada.
- **Resultado real:** `PENDENTE — preencher somente após executar`; **CI:** `PENDENTE`.

## Riscos / pontos para o revisor

Não ampliar política de prazo ou câmbio sem decisão; qualquer arredondamento intermediário extra pode modificar centavos. Conferir se a estratégia é independente de JPA e HTTP.

## O que um desenvolvedor júnior pode aprender com o PR?

A Strategy seleciona **uma política de negócio**, não necessariamente a fórmula inteira. A função financeira é pura para permitir testar os resultados sem banco ou servidor. A precisão monetária é uma regra explícita de contrato, não um detalhe de formatação visual.

## Checklist de apresentação

- [ ] Branch e destino conferidos (`feature/*` → `develop`)
- [ ] Motivação, alternativas e consequências demonstradas
- [ ] Testes executados descritos **com resultado real** e limites declarados
- [ ] Mudanças pequenas o bastante para revisão
- [ ] Nenhuma decisão financeira alterada silenciosamente
- [ ] PR aguardando revisão humana; agente não faz merge
