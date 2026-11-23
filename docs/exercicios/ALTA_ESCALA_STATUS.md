# Proposta independente — arquitetura para 1 milhão de transações/minuto

**Estado: HLD DOCUMENTADO — revisão de integração pendente.**

O documento [HLD_ALTA_ESCALA_1M_TPM.md](HLD_ALTA_ESCALA_1M_TPM.md) apresenta a arquitetura de referência para **1 milhão de liquidações unitárias concluídas por minuto**, com:

- microsserviços por fronteiras de domínio e arquitetura hexagonal;
- recebível como unidade ACID e lotes como orquestrações com resultado parcial;
- Kafka, Single Writer lógico por agregado, outbox/inbox, retry, DLQ e recuperação;
- sharding por recebível, células, fencing e migração segura entre proprietários;
- caching/CQRS, consistência eventual em leituras e status autoritativo;
- idempotência por item e por manifesto de lote, inclusive após reentrega e failover;
- alternativas ECS/Fargate e EKS Auto Mode, custos, riscos, SLOs e plano de benchmarks.

Os **dois diagramas** do HLD possuem versões SVG para leitura visual e fontes Mermaid editáveis em [diagramas/](diagramas/). Os documentos independentes [REVIEW.md](REVIEW.md) e [POST_MORTEM_ANEXO_B.md](POST_MORTEM_ANEXO_B.md) permanecem disponíveis, sem alterações funcionais.

**Limite da evidência:** a arquitetura está **documentada**, mas não implantada, testada sob carga ou homologada. Não afirmar que a meta de capacidade foi alcançada. O HLD é um exercício de desenho, não uma alteração das regras financeiras vigentes no sistema demonstrativo.

**Próxima ação:** revisão e integração humanas do PR documental para `develop`, seguidas do fluxo de promoção autorizado para `main`.
