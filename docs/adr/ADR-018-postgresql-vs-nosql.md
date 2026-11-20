# ADR-018 — PostgreSQL transacional versus NoSQL no registro financeiro

- **Status:** aprovado como decisão de arquitetura do MVP; sem inferir implementação
- **Decisões correlatas:** ADR-005, ADR-010, ADR-014

## Problema

Armazenar organizações, cedentes, recebíveis, cotações, liquidações e reservas de idempotência com integridade referencial, consultas filtradas por organização e recuperação auditável. O dado financeiro exige unicidade e transição consistente de estado; o extrato precisa de filtros combináveis por período, cedente e moeda.

## Alternativas

| Tecnologia | Benefício | Limite relevante |
|---|---|---|
| **PostgreSQL** | ACID, FKs, UNIQUE, versionamento otimista, SQL analítico e índices | Escala de escrita exige particionamento, tuning e eventualmente sharding; SQL não resolve sozinho picos globais |
| **NoSQL como sistema primário** | Distribuição horizontal de certos padrões de chave/acesso | Constraints relacionais e transações cruzadas dependem do mecanismo; maior esforço de verificação de invariantes |
| **Polyglot** | Cada armazenamento para sua necessidade específica | Operação de sincronização, dados duplicados e eventual consistência; não se justifica no MVP |

## Decisão

**PostgreSQL como fonte de verdade financeira**. Usar migrations Flyway, `numeric`/`BigDecimal`, FKs com `organizacao_id`, `UNIQUE` para liquidação e identidade idempotente, controle de versão do recebível, transação que inclui snapshot financeiro, idempotência e auditoria. Para extrato, SQL parametrizado em adaptador de leitura, paginação server-side e análise de plano quando houver dados representativos.

## Limites e mitigação

Não se presume throughput de 1 milhão de transações/minuto em uma instância. No cenário futuro, considerar *shards* relacionais, particionamento e read models projetados, mas manter um único dono transacional de cada recebível e chave idempotente. Dados derivados não podem substituir o ledger/fonte de verdade. NoSQL pode ser usado para telemetria ou read models **após** evidência de necessidade.

## Critérios de reavaliação

Carga medida, volume e seletividade de consultas, hot spots, requisitos de disponibilidade, custo de consistência, falhas cruzando partições e plano real de reprocessamento. Nenhuma mudança de motor só por preferência tecnológica.
