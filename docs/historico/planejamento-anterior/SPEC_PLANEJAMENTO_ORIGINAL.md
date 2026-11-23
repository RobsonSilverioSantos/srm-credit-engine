# SPEC.md — SRM Credit Engine

**Versão:** 2.2 — 17/11/2026  
**Status:** contrato de implementação do MVP; premissas de negócio ainda não homologadas.  
**Referências:** [Matriz de ambiguidades](docs/negocio/MATRIZ_AMBIGUIDADES.md) · [Especificação técnica detalhada](docs/arquitetura/ESPECIFICACAO_DETALHADA.md) · `DECISIONS.md`.

## 1. Objetivo e limite

Simular a antecipação de recebíveis BRL (Duplicata Mercantil e Cheque Pré-datado), registrar **uma única liquidação/antecipação ao cedente** em BRL ou USD e consultar extratos paginados com filtros por período, cedente e moeda. Não envolve transferência bancária real, cobrança ao sacado, estorno ou liquidação parcial. API de integração `/api/v1/*` e BFF `/bff/v1/*` usam os mesmos casos de uso do monólito modular com arquitetura hexagonal.

## 2. Regras financeiras verificáveis

- Fórmula: `VP = VF / (1 + taxaBase + spread)^prazoMeses`; taxa base inicial versionada: **1,00% a.m.**; spreads: duplicata **1,50% a.m.**, cheque **2,50% a.m.**; prazo inteiro **positivo** (`prazoMeses` obrigatório).
- `BigDecimal`/`NUMERIC`, `HALF_EVEN`: arredondar **VP BRL a duas casas** antes de converter; deságio BRL = face BRL − VP BRL. Conversão USD = VP BRL / cotação BRL por 1 USD; arredondar USD a duas casas.
- A data de vencimento é **opcional e informativa no MVP**; não existe conversão automática de dias em meses. API/BFF aceitam `prazoMeses` como referência autorizada; a UI deve informar explicitamente o prazo e não calcular meses silenciosamente a partir da data. Convenção financeira permanece ambiguidade **P0**.
- C1: duplicata BRL 100.000,00, 3 meses → **VP 92.859,94**, deságio 7.140,06. C2: cheque BRL 25.000,00, 2 meses → **VP 23.337,77**. C3: C1 com USD a 5,4321 BRL/USD → **USD 17.094,67**.
- Montantes e taxas em JSON como **strings decimais**, com moeda explícita; cálculo somente no backend. Simulação não reserva cotação nem garante preço na liquidação.

## 3. Câmbio e corte temporal

Moedas em tabela (`moedas`), inicialmente BRL/USD; `cotacoes` preserva histórico com `vigente_em` e `criado_em`. Para o MVP, cotação intradiária **mockada**, com BRL como referência. Na liquidação, capturar **um único `instanteReferencia` em UTC** para avaliar elegibilidade e selecionar taxa base e cotação vigentes, guardando IDs, valores e instantes como snapshots imutáveis. Instantes são `timestamptz`; `dataVencimento` é `date` sem fuso. Exigir `vigente_em <= instanteReferencia`; cotação não pode ter registro posterior ao instante de seleção. A taxa ativa e sua versão também devem ser elegíveis nesse mesmo instante, sob leitura transacional coerente. Alterações posteriores não reprecificam liquidações.

A janela de **30 minutos** para idade máxima de cotação é somente **hipótese configurável** pendente de validação de negócio (P0), aplicável ao mock intradiário; sem cotação elegível, rejeitar liquidação USD, sem fallback. Atualização externa mockada tem timeout/retry; não realizar HTTP externo dentro da transação financeira.

## 4. Segurança, concorrência e idempotência

- Identidade obtida por Keycloak/OIDC; JWT validado e permissões mínimas. `tenantId` vem **exclusivamente do contexto autenticado confiável**, jamais do corpo como autorização. Cedentes, recebíveis e liquidações pertencem ao tenant; consultas/alterações sempre filtram e verificam propriedade.
- `Idempotency-Key` obrigatório para **escritas persistentes críticas**, na API e no BFF. Identidade **`(tenantId, tipoOperacao, idempotencyKey)`**, sem identificador de usuário. Comparar SHA-256 do comando canônico (incluindo parâmetros relevantes de rota e corpo). Mesmo escopo/chave/hash retorna status e corpo originais; mesmo escopo/chave com hash diferente → HTTP 409.
- `Recebivel` usa `@Version`/optimistic locking e transição `DISPONIVEL → LIQUIDADO`, sem `SELECT FOR UPDATE` explícito; requisições de liquidação simultâneas com chaves distintas devem produzir **apenas uma liquidação**, e a perdedora sofre conflito otimista, rollback e HTTP 409. `UNIQUE(recebivel_id)` é salvaguarda adicional.
- Mesma chave em andamento: timeout configurável de disputa inicialmente **2 s**, resposta HTTP 409 com `Retry-After`. Falha antes do commit → rollback; falha de resposta depois do commit → replay do resultado confirmado. Não fazer retry automático de conflito financeiro.

## 5. Persistência, auditoria e observabilidade

PostgreSQL + Flyway; transação ACID para atualização versionada do recebível, inserção de liquidação e conclusão da idempotência. Auditoria CUD por triggers nas tabelas operacionais, append-only e na mesma transação. Snapshot financeiro de `liquidacoes` é imutável; segredos, dados fiscais de cedentes, chaves/hash e corpos de idempotência **não** entram nos snapshots JSONB automáticos da auditoria. Registros rejeitados/rollback exigem logs externos para rastreio. Governança de retenção e acesso permanece **P1**.

Spring Actuator/Micrometer e logs JSON estruturados: liquidações **confirmadas após commit**, duração de precificação, conflitos otimistas e falhas cambiais; `correlationId` e contexto de tenant em logs, sem tokens nem labels de cardinalidade alta.

## 6. Interface do operador (requisito funcional)

**React/TypeScript** com apresentação, aplicação e adaptadores separados; integração exclusiva pelo BFF `/bff/v1/*`. Painel com valor de face, tipo de recebível, moeda de pagamento, `prazoMeses` positivo e vencimento informativo; fluxo **cadastro → simulação reativa pelo backend → confirmação idempotente**. No MVP, simulação e liquidação requerem recebível já cadastrado; não há prévia de recebível não cadastrado. A UI não calcula preço/câmbio/prazo; usa debounce e descarta respostas antigas. Histórico com **paginação server-side** e filtros por período, cedente e moeda; estados de carregamento, erro e vazio, e apresentação de dinheiro com strings decimais no transporte. B08 define o contrato do BFF antes de F01.

## 7. Critérios de aceite e limites

Testes JUnit 5 com PostgreSQL/Testcontainers para C1–C3, conflitos otimistas reais, idempotência, rollback, tenant cruzado e auditoria; timeout/retry mockado, ausência/expiração cambial, contratos REST/OpenAPI e linter/testes em GitHub Actions. C4 nível 1/2, ADRs, `REVIEW.md`, proposta EDA, desenho de 1 milhão de transações/minuto e post-mortem são **entregas documentais**, não funcionalidades distribuídas do MVP.

**Não homologado pelo negócio (P0):** modalidade/fonte cambial (PTAX × intradiária × negociada), fixação/validade da taxa, convenção de prazo, elegibilidade/cessão, taxa base/spread variável e regras de preço, segregação e autorização definitivas. **P0 indica esclarecimento prioritário, não obrigação de desenvolver todas as alternativas.** Consulte a matriz para riscos, perguntas e premissas. O produto não é considerado pronto para produção enquanto essas políticas não forem aprovadas.