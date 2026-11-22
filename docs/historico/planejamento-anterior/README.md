# Arquivo histórico — planejamento anterior à baseline Git vigente

**Natureza:** documentos originais fornecidos pelo responsável técnico em 22/11/2026, contendo planejamento e registros iniciados em 17/11/2026. O conteúdo foi arquivado **sem substituir ou editar retroativamente as decisões feitas à época**. Estas cópias são para rastreabilidade, **não são fontes normativas ou evidência de implementação do código atual**.

## Documentos originais preservados

- [AI_USAGE_PLANEJAMENTO_ORIGINAL.md](AI_USAGE_PLANEJAMENTO_ORIGINAL.md) — planejamento com agentes Cursor, revisões humanas e evidências daquele ciclo, incluindo limitações e erros de IA.
- [DECISIONS_PLANEJAMENTO_ORIGINAL.md](DECISIONS_PLANEJAMENTO_ORIGINAL.md) — registro detalhado das decisões e propostas iniciais, inclusive alternativas posteriormente substituídas.
- [SPEC_PLANEJAMENTO_ORIGINAL.md](SPEC_PLANEJAMENTO_ORIGINAL.md) — premissas, regras e fluxos previstos antes do ciclo Git vigente.

## Autoridade documental

1. **Requisitos externos:** [enunciado original](../../requisitos/ENUNCIADO_ORIGINAL_SRM_CREDIT_ENGINE_V2.md); não editar ou reinterpretar retroativamente suas exigências.
2. **Especificação vigente:** [SPEC.md](../../../SPEC.md), com regras demonstrativas e ambiguidades não homologadas explicitadas.
3. **Decisões vigentes:** [DECISIONS.md](../../../DECISIONS.md) e ADRs aplicáveis.
4. **Código, testes e PRs integrados:** mostram o mecanismo efetivamente executado. Divergência entre norma e código requer registro e revisão; não é solucionada alterando o histórico.
5. **Uso de IA e evidências:** [AI_USAGE.md](../../../AI_USAGE.md) reúne a cronologia das duas etapas, identificando o que é relato humano, execução confirmada, CI ou proposta.

## Evolução rastreável — decisões alteradas

| Tema | Planejamento anterior | Decisão / implementação vigente | Evidência e consequência |
|---|---|---|---|
| Identidade/autenticação | Keycloak com federação Google/Microsoft planejada (DECISIONS §3 e §22; SPEC §4) | Autenticação local Spring Security/BCrypt/JWT, isolada por organização; Keycloak fora do MVP | ADR-007/017; [PR #15](https://github.com/RobsonSilverioSantos/srm-credit-engine/pull/15) S01; não alegar SSO/MFA |
| Simulação | Exigia cadastro antes de qualquer simulação (DECISIONS §13/30; SPEC §6) | Pré-simulação **não persistente** admitida antes do cadastro; recebível ainda precisa existir antes de liquidar | ADR-009, [PR #23](https://github.com/RobsonSilverioSantos/srm-credit-engine/pull/23) B08-S01 |
| Controle otimista | Plano com anotação literal JPA `@Version` (DECISIONS §19; SPEC §4) | Controle otimista **por SQL condicional** no adaptador JDBC: `UPDATE ... WHERE organizacao_id = ? AND id = ? AND versao = ? AND status = 'DISPONIVEL'`; sem JPA `@Version` | [PR #17](https://github.com/RobsonSilverioSantos/srm-credit-engine/pull/17) B06 e [PR #19](https://github.com/RobsonSilverioSantos/srm-credit-engine/pull/19) B07; [detalhamento ADR-010](../../adr/ADR-010-controle-otimista-sql-versionado.md) |
| Isolamento e terminologia | `tenantId`, `tenants` e distinções em elaboração (DECISIONS §25) | `Organizacao`/`organizacaoId` como identidade confiável de isolamento; `Cedente` é entidade de negócio separada | ADR-006; schemas atuais e casos de uso |
| Linguagem e camada de infraestrutura | Kotlin/Spring/Gradle planejados com avaliação de ORM | Kotlin/Spring Boot com JDBC nos adaptadores, regras desacopladas em portas hexagonais | ADR-004/005; [PR #48](https://github.com/RobsonSilverioSantos/srm-credit-engine/pull/48) B13 |

**Limites:** a migração de abordagem não implica transferência de commits, testes ou banco de dados do trabalho anterior. A autoria e a responsabilidade técnica são tratadas por papéis e evidências: agentes produziram entregas identificadas nos PRs, e o responsável técnico definiu diretrizes, examinou decisões/entregas, realizou integrações autorizadas e confirmou resultados de uso; esses registros não significam que cada linha foi digitada manualmente nem que todos os fluxos foram homologados em produção.

## Processo de mudança

Em revisões futuras, preservar estas três cópias originais. Atualizar somente os documentos vigentes, relacionando o motivo da revisão, o código/PR que comprova a escolha, a responsabilidade humana e os riscos remanescentes. Uma escolha pretérita pode ter sido adequada àquele plano e ainda assim não ser a solução implementada.
