# ADR-010 — Controle otimista versionado por SQL condicional na liquidação

**Estado:** decisão vigente, alinhamento documental à implementação B06/B07 integrada.  
**Data de reconciliação:** 22/11/2026.  
**Referências:** [índice normativo](../../DECISIONS.md), [B06 PR #17](https://github.com/RobsonSilverioSantos/srm-credit-engine/pull/17), [B07 PR #19](https://github.com/RobsonSilverioSantos/srm-credit-engine/pull/19), [B13 PR #48](https://github.com/RobsonSilverioSantos/srm-credit-engine/pull/48) e [histórico anterior](../historico/planejamento-anterior/README.md).

## Contexto

O enunciado exige **optimistic locking** e teste demonstrando conflito entre duas liquidações concorrentes do mesmo recebível. A proposta inicial registrada no documento de 17/11 adotava `@Version` de JPA e rejeitava `SELECT FOR UPDATE` como mecanismo principal. A implementação atual **não utiliza `@Version`**: utiliza JDBC e atualização condicionada à versão e ao estado no PostgreSQL, com verificação de linhas alteradas.

A diferença entre a anotação escolhida na fase documental e o mecanismo efetivo foi identificada na B13. A semântica de concorrência é o requisito relevante; a anotação JPA não é mandatória para alcançá-la. Esta ADR registra a divergência sem reescrever documentos de origem.

## Decisão vigente

- A operação altera o recebível com SQL condicional, filtrando obrigatoriamente organização, ID, versão esperada e estado `DISPONIVEL`:
  
  ```sql
  UPDATE recebiveis
     SET status = 'LIQUIDADO',
         versao = versao + 1,
         atualizado_em = CURRENT_TIMESTAMP
   WHERE organizacao_id = ?
     AND id = ?
     AND versao = ?
     AND status = 'DISPONIVEL';
  ```
- **Exatamente uma linha atualizada** autoriza prosseguir com a liquidação. Se nenhuma linha for atualizada, a aplicação produz `ConflitoLiquidacao` e a borda HTTP traduz para `409`, sem confirmar uma segunda liquidação.
- Atualização versionada, inserção da liquidação, snapshot e conclusão da idempotência pertencem à **mesma transação ACID** no banco; rollback não pode deixar efeitos financeiros parciais.
- Uma constraint `UNIQUE` por recebível na liquidação oferece defesa em profundidade independente do controle de versão.
- O identificador da operação idempotente é escopado por organização/tipo de operação/chave; **a mesma chave com payload equivalente deve reproduzir a resposta confirmada**, enquanto chaves diferentes sobre o mesmo recebível representam uma disputa legítima e devem resultar em um sucesso e um conflito.
- Não utilizar `SELECT FOR UPDATE` explícito no recebível como estratégia de concorrência nem retry automático de transação financeira após conflito de estado/versão.

## Alternativas e consequências

| Alternativa | Avaliação |
|---|---|
| Anotação JPA `@Version` | Válida quando o agregado é persistido pelo ORM; **não se aplica literalmente ao adaptador JDBC integrado** |
| `UPDATE ... WHERE versao = ? AND status = 'DISPONIVEL'` | **Escolhida/implementada**; versionamento explícito, atomicidade por instrução e verificação do número de linhas; exige disciplina no SQL e testes com PostgreSQL real |
| `SELECT FOR UPDATE` explícito | Não escolhido como controle principal para demonstrar optimistic locking; não confundir com locks internos normais do PostgreSQL |

**Evidência e limite:** PRs B06/B07 registram cenários PostgreSQL reais de idempotência, rollback, concorrência e isolamento. Os resultados de CI são verificáveis nos PRs. Esta ADR não é nova execução de testes e não muda o comportamento do software; não afirma uso de Hibernate/JPA no caminho financeiro implementado.

## Gatilhos de revisão

Revisar se houver migração consciente para JPA, persistência distribuída, liquidação de lote com política transacional nova (B17), alteração de isolamento ou novas exigências de concorrência. O requisito de uma única liquidação por recebível permanece invariante.
