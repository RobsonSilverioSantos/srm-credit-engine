# ADR-018 — PostgreSQL versus NoSQL para dados financeiros

**Status:** aprovado para o MVP; referência normativa: [DECISIONS](../../DECISIONS.md), ADR-018. Revisão factual: `develop` `3cd2bde`.

## Contexto

Recebíveis e liquidações exigem precisão decimal, integridade por organização, unicidade e recuperação auditável. O extrato precisa de filtros e paginação consistentes. A escolha de banco deve apoiar essas invariantes sem presumir que o banco implemente sozinho autorização ou todo o processo.

## Alternativas

| Alternativa | Benefícios | Custos e riscos |
|---|---|---|
| PostgreSQL como autoridade | Transações ACID locais, `NUMERIC`, FKs, constraints, SQL e índices | Capacidade e disponibilidade dependem de operação, contenção, dimensionamento e recuperação |
| NoSQL como autoridade | Flexibilidade e distribuição conforme o produto e padrão de acesso | Garantias transacionais e relacionais variam por tecnologia; invariantes e consultas exigem validação específica |
| Armazenamento poliglota | Projeções ajustadas a leituras específicas | Duplicação, sincronização, consistência eventual e operação adicional |

## Decisão

PostgreSQL é a **fonte de verdade financeira** do MVP. Flyway versiona o esquema; valores usam `NUMERIC` e o domínio `BigDecimal`. A transação futura deve incluir mudança versionada do recebível, liquidação/snapshot, idempotência e auditoria. O extrato previsto usará SQL parametrizado e paginação no servidor. A autenticação estabelece a organização; FKs compostas e consultas escopadas reforçam, mas não substituem, autorização.

## Consequências e evidências

As [migrations V1–V3](../../backend/app/src/main/resources/db/migration) definem FKs, unicidade por recebível e chave idempotente, coluna de versão e proteção contra alteração de registros financeiros. O histórico de taxas tem consulta e gravação internas implementadas. Isso não comprova `@Version`, fluxo de liquidação atômico, replay ou extrato: são trabalho futuro ([C4](../arquitetura/C4_MVP.md)). Auditoria imutável não é backup; produção exigiria recuperação, controle de acesso e verificação operacional próprios.

NoSQL ou outra projeção pode servir leituras futuras se justificada, com atraso e reconstrução controlados; não substitui a autoridade financeira por conveniência. Unicidade, atomicidade e isolamento de transações devem ser comprovados no desenho escolhido, não inferidos da marca do banco.

## Gatilhos de revisão

Planos de consulta e volumes representativos, contenção, disponibilidade, objetivos de recuperação, crescimento e custo operacional medidos podem exigir reavaliação. Particionamento, réplicas ou distribuição exigiriam definir propriedade dos dados, unicidade/idempotência e efeitos entre fronteiras. Não se afirma capacidade para 1 milhão/min nem se aprova uma topologia de alta escala.
