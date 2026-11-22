# ADR-025 — Proposta de política transacional para liquidação explícita em lote (B17)

**Estado:** PROPOSTA NÃO APROVADA — depende de escolha expressa do responsável técnico. **Não libera a B17.**
**Data de elaboração:** 22/11/2026.
**Escopo:** exclusivamente liquidação de recebíveis já cadastrados por B16; não altera a ADR-024, que rege o cadastro atômico.
**Referências:** [B17](../engenharia/B17_LIQUIDACAO_LOTE_CONCORRENCIA.md), [issue #65](https://github.com/RobsonSilverioSantos/srm-credit-engine/issues/65), [ADR-010](ADR-010-controle-otimista-sql-versionado.md), [ADR-024](ADR-024-cadastro-atomico-lotes.md), [B16 integrada PR #71](https://github.com/RobsonSilverioSantos/srm-credit-engine/pull/71).

## Problema e alternativas

A B16 importa e cadastra um lote de recebíveis, sem realizar liquidação. A B17 deve fornecer prévia de todas as simulações e confirmação financeira explícita, com concorrência e idempotência persistentes.

| Política | Comportamento na falha de um único título | Trade-off |
|---|---|---|
| **A — atômica por lote (recomendada para o MVP)** | Nenhum título do lote é liquidado; rollback integral | Garantia ACID de lote com decisão binária; aumenta a sensibilidade a conflitos e o tempo da transação |
| **B — parcial por recebível** | Títulos elegíveis podem ser liquidados; falhas são informadas individualmente | Maior flexibilidade operacional, porém exige checkpoint por item, replay de resultados mistos e conciliação |

A alternativa A é **recomendação técnica, não decisão vigente**. A adoção depende de manifestação expressa do responsável técnico e de PR documental posterior que registre essa aprovação, atualize o índice de decisões e libere literalmente a B17 em `origin/develop:TASKS.md`. O simples merge deste documento preparatório não autoriza implementação financeira.

## Política A proposta — comportamento obrigatório após eventual aprovação

1. **Fronteira de operações:** `Confirmar cadastro` da B16 nunca liquida. B17 oferece prévia financeira sem efeitos colaterais, apresentação de 100% dos itens e confirmação separada de `Liquidar lote`; o backend é a autoridade da validação.
2. **Escopo:** para o MVP, liquidar a totalidade dos títulos do lote selecionado, não um subconjunto silencioso. Limite inicial proposto de **200 recebíveis por confirmação**, configurável e aplicado no servidor. Não dividir a tentativa em múltiplos commits que retornem sucesso total.
3. **Prévia obrigatória:** obter estado/versão dos títulos, valores BRL e por moeda, valor presente, deságio, spread, taxa base, fonte e vigência cambial quando aplicável. Cada título deve ter simulação válida; a prévia não reserva câmbio nem garante preço futuro. A requisição de confirmação identifica a prévia/hash canônico e suas versões esperadas.
4. **Revalidação no servidor:** autenticação, organização, permissão, integridade do lote, número de títulos, titularidade, status `DISPONIVEL`, versões, vigência e taxas/cotações elegíveis. Mudança entre prévia e confirmação exige **rejeição integral e nova prévia/reconfirmação**, sem substituição silenciosa dos valores.
5. **Atomicidade:** após revalidação, uma **única transação PostgreSQL** contém atualizações otimistas dos títulos, registros de liquidação, snapshots, auditoria financeira e conclusão da chave idempotente. Qualquer falha, inclusive conflito no último título, provoca rollback integral de todos os efeitos financeiros da tentativa.
6. **Concorrência:** seguir ADR-010: `UPDATE` JDBC condicionado por organização, ID, versão e estado `DISPONIVEL`; exatamente uma linha atualizada por título, `UNIQUE` por recebível e isolamento por organização. Duas operações distintas sobre o mesmo recebível, com chaves idempotentes diferentes, devem produzir uma confirmação e um conflito, nunca liquidação duplicada. Sem `SELECT FOR UPDATE` explícito como mecanismo principal nem mutex apenas em memória.
7. **Idempotência:** escopo `(organizacaoId,tipoOperacao,chave)` e hash do comando; mesma chave e payload idêntico reproduzem **o resultado original**, mesmo após perda da resposta; mesma chave com conteúdo divergente gera `409`. Operação conflitante por versão/estado não deve ser repetida automaticamente com chave nova.
8. **Respostas:** sucesso integral somente se todas as liquidações foram confirmadas; conflitos de versão, prévia obsoleta e divergência de chave são rejeições da tentativa, sem sucesso parcial disfarçado. Contratos HTTP/API/BFF, corpos e código de cada rejeição exigem revisão antes da implementação. Relatório por item pode identificar causas de rejeição **sem afirmar liquidação parcial**.
9. **Valores e auditoria:** preservar `BigDecimal`, `HALF_EVEN`, casos de aferição C1–C3, snapshots imutáveis da operação confirmada e horário UTC. Não fazer HTTP externo dentro da transação; fonte cambial e precedência seguem política já vigente até eventual decisão separada B15.
10. **Segurança operacional:** proibir payloads financeiros completos, CNPJ, JWT ou segredos em logs; confirmar índices/constraints e limites/timeout no PostgreSQL real. Novas migrations Flyway somente em versões livres, coordenadas com B15, sem alterar migrations aplicadas.

## Cenários mínimos de aceite a exigir na implementação

- Arquivos A/B de 200 linhas e quatro referências coincidentes: após liquidação de A, quatro itens de B devem ser reconhecidos como `JA_LIQUIDADO`; nunca produzir segunda liquidação. Para cadastrar as 196 referências restantes, seguir o mecanismo de correção e nova confirmação da ADR-024.
- Prévia de 200/200 itens; item indisponível, cotação inválida, versão/estado alterado, prévia incompleta ou alteração de preço entre etapas bloqueiam liquidação financeira.
- Injeção de falha após o primeiro e após o penúltimo item: rollback de todos os títulos e snapshots, sem resíduos financeiros. Validar diretamente SQL/PostgreSQL.
- Corrida determinística com barreira/latch no PostgreSQL: **duas chaves diferentes, mesmo recebível**, exatamente uma confirmação e um conflito `409`; nenhuma segunda entrada no extrato.
- Mesmo comando e mesma chave: replay sem segunda escrita; mesma chave e payload diferente: `409`; perda da resposta depois do commit seguida de consulta/replay seguro.
- Isolamento multi-organização, revalidação de autorização, não duplicação por duplo clique, divergência de cotação/vigência e golden cases C1–C3.
- Suítes de integração PostgreSQL/Testcontainers e CI reais com resultados documentados. O roteiro k6 integrado no PR #70 testa **cadastro B16**, não substitui os testes concorrentes de liquidação B17.

## Condições para tornar vigente

- [ ] O responsável técnico registra **explicitamente a escolha A ou B**, podendo solicitar alterações à proposta.
- [ ] Revisor `architect` confere ACID, retries, limites, preview, snapshots e concorrência à luz da ADR-010, sem decidir a política financeira.
- [ ] A decisão aprovada é incorporada em `DECISIONS.md`, na especificação B17 e no `TASKS.md` em PR documental de liberação separado, integrado **humanamente** em `develop`.
- [ ] O executor `backend` só cria worktree/feature B17 quando `origin/develop:TASKS.md` apresentar o status literal **`Liberada para iniciar`** e dependências integradas.
- [ ] F08, B15 e F06 permanecem bloqueadas por seus próprios gates.

**Limite de evidência:** documento de desenho/preparação; não representa funcionalidade implementada, teste executado, implantação, autorização financeira nem homologação.
