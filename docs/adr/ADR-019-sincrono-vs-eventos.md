# ADR-019 — Confirmação síncrona versus eventos

**Status:** decisão aprovada para o MVP; implementação de liquidação pendente. EDA é evolução futura, não infraestrutura existente. Referência normativa: [DECISIONS](../../DECISIONS.md), ADR-019.

## Contexto

Confirmar uma antecipação exige um resultado local único e snapshot reproduzível. O MVP não faz desembolso real; um provedor futuro poderá falhar ou retornar resultado incerto, sem participar da transação local.

## Alternativas

| Alternativa | Benefício | Custo ou motivo de rejeição |
|---|---|---|
| Confirmação síncrona em transação local | Resultado imediato após commit; invariantes locais mais simples | Disponibilidade/latência ligadas ao banco; não comprova pagamento externo |
| Publicar diretamente no broker e gravar no banco | Integração aparentemente simples | Rejeitada: falha entre escritas cria evento sem estado ou estado sem evento |
| Transação local, outbox e consumidores idempotentes | Comunicação futura desacoplada e recuperável | Consistência eventual, duplicatas, operação e reconciliação |
| Transação distribuída/XA com provedor | Tentativa de coordenação atômica entre participantes | Acoplamento operacional e suporte externo incerto; não selecionada |

## Decisão

No MVP, a futura operação síncrona confirma **liquidação lógica** no PostgreSQL, com versão, idempotência, snapshot e auditoria atômicos. Não se bloqueia em pagamento externo nem se afirma pagamento bancário. A implementação atual dispõe de esquema preparatório, não do caso de uso, endpoint ou garantias fim a fim.

Se integração assíncrona for aprovada, gravar evento na outbox na mesma transação local e publicar após commit; consumidores devem deduplicar efeitos locais e reconciliar efeitos externos. Entrega é **ao menos uma vez**, não execução exatamente uma vez. Detalhes, falhas e validação proposta estão em [EDA](../arquitetura/EDA_LIQUIDACAO.md).

## Consequências e gatilhos de revisão

O síncrono reduz componentes e consistência distribuída, mas compartilha a disponibilidade do banco. A evolução aumenta desacoplamento às custas de atraso, reprocessamento e estados incertos; broker, relay, inbox e provedor não estão implementados. Reavaliar quando houver necessidade aprovada de integração externa, exigências de disponibilidade e volume medidos, contrato e operação de reconciliação, além de testes de falhas e duplicidade. Nenhuma decisão altera a semântica financeira aprovada sem revisão humana.
