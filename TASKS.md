# TASKS.md — Backlog de implementação do novo repositório

**Baseline inicial:** 20/11/2026. **Atualização factual:** 20/11/2026 — G00 operacional; B01, B02, B03, B04, B05, B09, B12, S01, D01 e F00 integradas em `develop` via PRs e revisão humana. **Progresso por contagem de itens:** 11/21 (52,4%), incluindo G00 operacional; prioridades P0: 9/18 (50,0%). `gh auth status` da G00 permanece sem evidência local direta. Execuções declaradas por agentes, testes locais confirmados pelo responsável e CI são diferenciados nas evidências. O histórico do repositório anterior não é evidência de implementação atual. **Este backlog contém exclusivamente trabalho do MVP, CI e documentos da arquitetura do MVP.**

`ID` e `Trabalho` determinam a branch `feature/<id-normalizado>-<slug-da-coluna-Trabalho>`. A coluna Trabalho deve ser usada como fonte do resumo; não criar branch arbitrária. O primeiro commit de baseline em `main`/`develop` será realizado pelo responsável técnico, não por agente.

| ID | Prioridade | Responsável | Trabalho | Dependências integradas em develop | Critério objetivo de aceite | Situação |
|---|---|---|---|---|---|---|
| G00 | P0 | Responsável técnico | Inicializar repositorio GitHub main develop e regras de PR | Nenhuma | Baseline publicada; branches existentes; Cursor/Copilot visíveis no Git; acesso do gh conferido | **Operacional** — baseline + governança PR #1 e release PR #2 integrados; guard PR #3 e hook local validados; **`gh auth status` ainda sem evidência direta** |
| B02 | P0 | backend/devops | Preparar backend Kotlin Spring Boot Gradle e Docker Compose | G00 | Java 21 e wrapper reproduzíveis; módulo hexagonal sem Spring/JPA no domínio; banco local sobe; healthcheck | **Integrada em `develop` — PR #5** (commit de merge `1a3ba3b`; build, smoke JUnit e Compose relatados no PR; revisão/merge humanos) |
| B01 | P0 | backend | Criar migrations PostgreSQL de organizacoes usuarios cedentes recebiveis cambio liquidacoes e idempotencia | B02 | Flyway em banco limpo; FKs por organizacao_id; versão otimista, índices e constraints verificados | **Integrada em `develop` — PR #9** (merge `af9c703`; migrations V1–V3, correção de modalidades, testes PostgreSQL relatados no PR e CI aprovada) |
| B03 | P0 | backend/qa | Implementar Strategy de precificacao e golden cases financeiros C1 C2 C3 | B02 | BigDecimal; HALF_EVEN; três valores exatos em centavos; prazos inválidos rejeitados | **Integrada em `develop` — PR #8** (merge `a75e62a`; Strategy, golden cases C1–C3 e CI aprovada) |
| B04 | P0 | backend | Implementar cadastro de cedentes e recebiveis com isolamento por organizacao | B01,B02 | Organização bootstrap para demonstração; organização do principal, FKs/consultas com escopo; sem autorização por body | **Integrada em `develop` — PR #10** (merge `3cd2bde`; cadastro multi-organização, testes PostgreSQL e CI aprovados) |
| B05 | P0 | backend | Implementar cotacoes BRL USD e taxa base versionadas | B01,B02 | Vigência UTC, cotação elegível e snapshot; validade configurável, erro sem cotação válida | **Integrada em `develop` — PR #11** (merge `21c649b`; histórico de taxas/câmbio, teste PostgreSQL relatado no PR e CI aprovada) |
| S01 | P0 | backend/qa | Implementar autenticacao local Spring Security e autorizacao por organizacao | B02,B04 | BCrypt, token assinado com expiração, identidade confiável, testes de acesso cruzado; sem Keycloak | **Integrada em `develop` — PR #15** (merge `bd37ba9`; Spring Security/BCrypt/JWT, contexto confiável por organização e CI backend/frontend aprovada) |
| B06 | P0 | backend | Implementar liquidacao ACID idempotente com optimistic locking e auditoria | B03,B04,B05,S01 | UNIQUE, hash canônico e replay, conflito 409, rollback, snapshot e auditoria atômica | **Liberada para iniciar** — B03, B04, B05 e S01 integradas; sem PR de implementação |
| B07 | P0 | qa | Comprovar concorrencia rollback idempotencia e isolamento em PostgreSQL real | B06 | Duas requisições concorrentes; só uma liquidação; replay idêntico; 0 efeitos parciais | A fazer |
| B08 | P0 | backend | Expor contratos REST BFF e OpenAPI dos casos de uso | B06 | `/api/v1` e `/bff/v1`; mesmo use case sem HTTP interno; erros semânticos; decimal string | A fazer |
| B08-S01 | P0 | backend | Implementar simulacao precadastro nao persistente na API e no BFF | B03,B05,B08 | Sem recebivelId, escrita ou chave de idempotência; preço indicativo com taxa/câmbio elegíveis | A fazer |
| B08-E01 | P0 | backend | Implementar extrato analitico filtrado e paginado no servidor | B04,B06,B08 | Período/cedente/moeda; SQL parametrizado, isolamento, ordenação estável e testes PostgreSQL | A fazer |
| F00 | P0 | frontend | Definir tema Material UI e componentes visuais reutilizaveis | G00 | React/TS/Vite, MUI Core gratuito, tema, layout, acessibilidade e mocks explícitos | **Integrada em `develop` — PR #4** (commit de merge `c081fd5`; lint, 4 testes e build relatados no PR; revisão/merge humanos) |
| F01 | P0 | frontend | Implementar painel de simulacao cadastro e liquidacao | F00,B08-S01,S01 | Debounce, prévia não persistente, confirmação idempotente, estados de erro e loading, BFF | A fazer |
| F03 | P0 | frontend | Implementar extrato com filtros e paginacao server side | F00,B08-E01 | Filtros/paginação no BFF, estados vazio/erro/loading e testes de componente | A fazer |
| B09 | P1 | backend/qa | Implementar timeout e retry seguro na consulta de cambio mockado | B05 | Sem HTTP externo na transação financeira; falhas tratadas; testes de indisponibilidade | **Integrada em `develop` — PR #14** (merge `8c1c975`; timeout/retry limitado para consulta JDBC de câmbio, testes locais confirmados e CI aprovada; não há provedor externo mockado) |
| B10 | P1 | backend/devops | Implementar logs estruturados metricas de negocio e trilha operacional | B06 | Métricas Micrometer sem labels de alta cardinalidade; logs correlacionados sem segredos; confirmações só pós-commit | A fazer |
| B12 | P0 | devops/qa | Configurar GitHub Actions de build testes e lint backend frontend | B02,F00 | Checks em PR; falhas visíveis; comandos descritos em README; sem green inventado | **Integrada em `develop` — PR #7** (merge `6c3bcd5`; GitHub Actions com jobs backend/frontend e execuções aprovadas) |
| D01 | P1 | architect | Revisar C4 niveis um e dois ADRs e proposta EDA do MVP | B02 | Contexto/conteineres do MVP, ADRs SQL/NoSQL, monólito/serviços, sync/eventos, EDA; **sem alta escala** | **Integrada em `develop` — PR #13** (merge `5a426d7`; C4/ADRs/EDA revisados; diagramas conferidos pelo responsável e CI aprovada; alta escala fora do escopo) |
| D03 | P0 | analyst/reviewer | Consolidar README AI_USAGE e roteiro de defesa com evidencias reais | B07,B08,B08-S01,B08-E01,F01,F03,B12 | README com execução comprovada, links PR, limitações, custos e intervenções humanas | A fazer |
| A01 | P0 | todos | Registrar evidencias reais de IA e PRs didaticos | Contínua | ID, agente, prompt/resumo, revisão, comandos, testes efetivos, commit e PR | A fazer |

## Indicadores de progresso — 20/11/2026

| Indicador | Quantidade | Percentual |
|---|---:|---:|
| Itens integrados ou operacionais | 11 de 21 | **52,4%** |
| Itens ainda não concluídos | 10 de 21 | 47,6% |
| Prioridade P0 integrada ou operacional | 9 de 18 | **50,0%** |
| Prioridade P0 ainda pendente | 9 de 18 | 50,0% |

**Método:** contagem simples das 21 tarefas, não ponderada por esforço ou dificuldade. G00 conta como operacional, embora `gh auth status` permaneça sem evidência local; A01 é contínua e ainda não está concluída. B06 está liberada, **não implementada**. Este percentual não é percentual de funcionalidades operacionais nem de horas executadas.

**Caminho crítico atual:** `B06` (liquidação ACID/idempotência/concorrência), liberada por `B03+B04+B05+S01` integradas. Em seguida, `B07` e `B08` poderão iniciar em paralelo; `B08-S01` e `B08-E01` aguardam B08, enquanto `F01` e `F03` dependem dos respectivos contratos BFF. `B10` depende de B06; `D03` fecha as evidências após as entregas do MVP.

## Evidências verificáveis desta atualização

- **G00:** baseline `05b9528`, [PR #1](https://github.com/RobsonSilverioSantos/srm-credit-engine/pull/1) integrado em `develop` e [PR #2](https://github.com/RobsonSilverioSantos/srm-credit-engine/pull/2) integrado em `main` como sincronização inicial. [PR de teste #3](https://github.com/RobsonSilverioSantos/srm-credit-engine/pull/3) de feature para `main` fechado automaticamente pelo GitHub Actions; [execução correspondente](https://github.com/RobsonSilverioSantos/srm-credit-engine/actions/runs/44789563476) terminou com falha **esperada** para o caminho proibido. O responsável técnico demonstrou a recusa local do hook para `main` e `develop`. Risco residual sem Ruleset: ADR-022. **Verificação do GitHub CLI local permanece pendente**.
- **B02:** [PR #5](https://github.com/RobsonSilverioSantos/srm-credit-engine/pull/5), branch exclusiva, commit da feature `053c215`, merge `1a3ba3b`. Descrição do PR registra Gradle build/check, 1 teste JUnit e verificações Docker/Actuator em PostgreSQL local; **resultados declarados pelo agente, sem CI B12 ainda**.
- **F00:** [PR #4](https://github.com/RobsonSilverioSantos/srm-credit-engine/pull/4), branch exclusiva, commit da feature `80cc60f`, merge `c081fd5`. Descrição do PR registra `npm ci`, lint, 4/4 testes, build e verificações de interface; **resultados declarados pelo agente, sem CI B12 ainda**.
- **B12:** [PR #7](https://github.com/RobsonSilverioSantos/srm-credit-engine/pull/7), merge `6c3bcd5`. [Execução CI](https://github.com/RobsonSilverioSantos/srm-credit-engine/actions/runs/44806750599) com jobs backend e frontend aprovados; pipeline integrado antes dos PRs posteriores.
- **B03:** [PR #8](https://github.com/RobsonSilverioSantos/srm-credit-engine/pull/8), merge `a75e62a`. [CI](https://github.com/RobsonSilverioSantos/srm-credit-engine/actions/runs/44808357093) aprovada; o PR registra testes de Strategy, precisão HALF_EVEN e golden cases C1–C3.
- **B01:** [PR #9](https://github.com/RobsonSilverioSantos/srm-credit-engine/pull/9), merge `af9c703`. A convenção `DUPLICATA_MERCANTIL` foi alinhada à B03 antes da integração. [CI final](https://github.com/RobsonSilverioSantos/srm-credit-engine/actions/runs/44811816556) aprovada. O teste completo `./scripts/verify-persistence.sh` em PostgreSQL real foi relatado pelo agente no PR; não é o mesmo comando da CI padrão.
- **B04:** [PR #10](https://github.com/RobsonSilverioSantos/srm-credit-engine/pull/10), merge `3cd2bde`. O responsável técnico confirmou execução local bem-sucedida; o agente registrou 14 testes, incluindo 3 de PostgreSQL real. [CI](https://github.com/RobsonSilverioSantos/srm-credit-engine/actions/runs/44814020563) aprovou backend (executando `scripts/test-cadastro-postgres.sh`) e frontend. A autorização real depende da S01.
- **B05:** [PR #11](https://github.com/RobsonSilverioSantos/srm-credit-engine/pull/11), merge `21c649b`. [CI](https://github.com/RobsonSilverioSantos/srm-credit-engine/actions/runs/44814145711) aprovada; o agente registra execução local do script `./scripts/verify-taxas-postgres.sh`. Atenção na futura B06: estratégias B03 ainda têm spreads fixos, portanto a integração deve utilizar a versão elegível obtida da B05.
- **D01:** [PR #13](https://github.com/RobsonSilverioSantos/srm-credit-engine/pull/13), merge `5a426d7`; revisão dos diagramas C4, ADR-005/018/019 e EDA, [CI aprovada](https://github.com/RobsonSilverioSantos/srm-credit-engine/actions/runs/44819607540); diagramas revisados pelo responsável. A arquitetura de 1 milhão/min permanece exercício separado, não concluído.
- **B09:** [PR #14](https://github.com/RobsonSilverioSantos/srm-credit-engine/pull/14), merge `8c1c975`; [CI aprovada](https://github.com/RobsonSilverioSantos/srm-credit-engine/actions/runs/44819849931). Testes locais confirmados pelo responsável. O adaptador de resiliência atua sobre leitura JDBC de câmbio (não provedor HTTP mockado), escolha registrada no PR; não há retry em escrita nem HTTP em transação.
- **S01:** [PR #15](https://github.com/RobsonSilverioSantos/srm-credit-engine/pull/15), merge `bd37ba9`; [CI backend/frontend aprovada](https://github.com/RobsonSilverioSantos/srm-credit-engine/actions/runs/44820996219). Autenticação com BCrypt/JWT HS256, validação de usuário/organização ativos e `ContextoOrganizacao` derivado do principal autenticado. Testes PostgreSQL reportados pelo agente no PR.
- **Próxima tarefa desbloqueada:** **B06** (P0), com todas as dependências integradas. Exige transação PostgreSQL única, idempotência e replay, locking otimista, snapshots de taxas/spread/câmbio versionados, rollback e auditoria. Branch nova exclusiva a partir de `origin/develop`. Após B06 integrada: B07 e B08.

## Sequenciamento para evitar trabalho acumulado

1. **G00 manual**: criar repositório privado vazio, publicar baseline em `main`/`develop` e revisar permissões.
2. **Paralelo real a partir de G00:** `B02` (backend) e `F00` (frontend) em worktrees separados; `D01` pode aguardar. Não misturar backend e frontend no mesmo PR.
3. **Já integradas:** B01, B02, B03, B04, B05, B09, B12, S01, D01 e F00 (G00 operacional). **B06 está liberada**, mas só estará concluída após implementação, testes e integração; atenção à aplicação do spread histórico da B05 no cálculo B03 e à consulta cambial B09 fora da transação.
4. `B07` e `B08` podem ser paralelas após `B06`; `B08-S01` e `B08-E01` seguem seus contratos integrados. `F01` e `F03` somente após as respectivas APIs/BFF.
5. `B12` e `D01` já integradas; `D03` fecha a entrega com fatos verificáveis, sem transformar o modelo fictício de PR em evidência.
6. Um único responsável atualiza `TASKS.md`, `SPEC.md`, `DECISIONS.md` e `AI_USAGE.md` após integração. Agentes registram evidências no corpo do PR.

## Regras dos agentes

- Autorizados: criar branch/worktree, implementar, testar, commitar, push **somente de `feature/*`**, abrir PR com base **`develop`**.
- Não autorizados: merge, auto-merge, push direto em `main`/`develop`, force push, reset destrutivo, excluir worktrees sem autorização, assumir dependência não integrada.
- Teste falho, `gh` ausente, branch existente ou conflito de dependências: relatar; não anunciar PR pronto.
- Os três exercícios de `docs/exercicios/` (review TypeScript, post-mortem e futura arquitetura de alta escala) **não estão neste TASKS.md e não ocupam agentes**.
