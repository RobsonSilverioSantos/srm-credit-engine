# ADR-019 — Fluxo síncrono transacional versus arquitetura orientada a eventos

- **Status:** síncrono para confirmação lógica no MVP; EDA como evolução documentada, não implementada
- **Artefato de desenho:** `docs/arquitetura/EDA_LIQUIDACAO.md`

## Contexto

A confirmação de uma antecipação financeira exige que um recebível seja liquidado uma única vez, com valor, taxa e cotação reproduzíveis. Processamento externo de pagamento pode ser lento, ficar indisponível ou retornar um estado desconhecido.

## Alternativas

1. **Fluxo síncrono com uma transação PostgreSQL:** simplifica a demonstração do domínio; confirma a liquidação lógica local com ACID. Não garante uma transferência bancária externa.
2. **Publicar diretamente no broker durante a transação:** parece simples, mas abre janela entre commit do banco e publicação ou vice-versa; **rejeitada**.
3. **Transação local + transactional outbox + consumidores idempotentes:** recomendado para integração futura de desembolso. Suporta retentativas, mas exige controle de duplicatas e reconciliação.
4. **Transação distribuída/XA entre banco e provedor de pagamento:** elevada complexidade/aderência limitada a provedores externos; **não selecionada**.

## Decisão

**MVP:** endpoint síncrono usa transação PostgreSQL, atualização otimista, idempotência e auditoria; a liquidação representa a confirmação lógica da antecipação ao cedente, **não prova efetivação bancária**.

**Evolução:** registrar eventos na *outbox* na mesma transação da mudança de estado; publicar depois do commit; consumidores com *inbox* e deduplicação; estado de pagamento separado (pendente, enviado, confirmado, rejeitado ou desconhecido), conciliação com processador externo. Publicação e entrega de eventos são **ao menos uma vez**, não uma promessa de execução exatamente uma vez.

## Trade-offs

A resposta síncrona é previsível para o usuário, mas não bloqueia em transferência externa. A EDA absorve picos e desacopla sistemas, porém introduz consistência eventual entre liquidação lógica e comprovante bancário. Eventos são contratos versionados, sem dados fiscais ou segredos.

## Condições para adoção

Operação com desembolso real, requisitos de indisponibilidade/reprocessamento externo, observabilidade e conciliação definidos, testes de duplicidade, reordenação, poison messages e recuperação de outbox.
