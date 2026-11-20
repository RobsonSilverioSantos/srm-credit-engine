# TASKS.md — Backlog de implementação do novo repositório

**Baseline inicial:** 20/11/2026. **Atualização factual:** 20/11/2026 — G00 operacional, B02 e F00 integradas em `develop` após PRs e revisão humana. Testes citados nas descrições dos PRs são evidências declaradas pelos respectivos agentes; não equivalem a reexecução independente. O histórico do repositório anterior não é evidência de implementação atual. **Este backlog contém exclusivamente trabalho do MVP, CI e documentos da arquitetura do MVP.**

`ID` e `Trabalho` determinam a branch `feature/<id-normalizado>-<slug-da-coluna-Trabalho>`. A coluna Trabalho deve ser usada como fonte do resumo; não criar branch arbitrária. O primeiro commit de baseline em `main`/`develop` será realizado pelo responsável técnico, não por agente.

| ID | Prioridade | Responsável | Trabalho | Dependências integradas em develop | Critério objetivo de aceite | Situação |
|---|---|---|---|---|---|---|
| G00 | P0 | Responsável técnico | Inicializar repositorio GitHub main develop e regras de PR | Nenhuma | Baseline publicada; branches existentes; Cursor/Copilot visíveis no Git; acesso do gh conferido | **Operacional** — baseline + governança PR #1 e release PR #2 integrados; guard PR #3 e hook local validados; **`gh auth status` ainda sem evidência direta** |
| B02 | P0 | backend/devops | Preparar backend Kotlin Spring Boot Gradle e Docker Compose | G00 | Java 21 e wrapper reproduzíveis; módulo hexagonal sem Spring/JPA no domínio; banco local sobe; healthcheck | **Integrada em `develop` — PR #5** (commit de merge `1a3ba3b`; build, smoke JUnit e Compose relatados no PR; revisão/merge humanos) |
| B01 | P0 | backend | Criar migrations PostgreSQL de organizacoes usuarios cedentes recebiveis cambio liquidacoes e idempotencia | B02 | Flyway em banco limpo; FKs por organizacao_id; versão otimista, índices e constraints verificados | A fazer |
| B03 | P0 | backend/qa | Implementar Strategy de precificacao e golden cases financeiros C1 C2 C3 | B02 | BigDecimal; HALF_EVEN; três valores exatos em centavos; prazos inválidos rejeitados | A fazer |
| B04 | P0 | backend | Implementar cadastro de cedentes e recebiveis com isolamento por organizacao | B01,B02 | Organização bootstrap para demonstração; organização do principal, FKs/consultas com escopo; sem autorização por body | A fazer |
| B05 | P0 | backend | Implementar cotacoes BRL USD e taxa base versionadas | B01,B02 | Vigência UTC, cotação elegível e snapshot; validade configurável, erro sem cotação válida | A fazer |
| S01 | P0 | backend/qa | Implementar autenticacao local Spring Security e autorizacao por organizacao | B02,B04 | BCrypt, token assinado com expiração, identidade confiável, testes de acesso cruzado; sem Keycloak | A fazer |
| B06 | P0 | backend | Implementar liquidacao ACID idempotente com optimistic locking e auditoria | B03,B04,B05,S01 | UNIQUE, hash canônico e replay, conflito 409, rollback, snapshot e auditoria atômica | A fazer |
| B07 | P0 | qa | Comprovar concorrencia rollback idempotencia e isolamento em PostgreSQL real | B06 | Duas requisições concorrentes; só uma liquidação; replay idêntico; 0 efeitos parciais | A fazer |
| B08 | P0 | backend | Expor contratos REST BFF e OpenAPI dos casos de uso | B06 | `/api/v1` e `/bff/v1`; mesmo use case sem HTTP interno; erros semânticos; decimal string | A fazer |
| B08-S01 | P0 | backend | Implementar simulacao precadastro nao persistente na API e no BFF | B03,B05,B08 | Sem recebivelId, escrita ou chave de idempotência; preço indicativo com taxa/câmbio elegíveis | A fazer |
| B08-E01 | P0 | backend | Implementar extrato analitico filtrado e paginado no servidor | B04,B06,B08 | Período/cedente/moeda; SQL parametrizado, isolamento, ordenação estável e testes PostgreSQL | A fazer |
| F00 | P0 | frontend | Definir tema Material UI e componentes visuais reutilizaveis | G00 | React/TS/Vite, MUI Core gratuito, tema, layout, acessibilidade e mocks explícitos | **Integrada em `develop` — PR #4** (commit de merge `c081fd5`; lint, 4 testes e build relatados no PR; revisão/merge humanos) |
| F01 | P0 | frontend | Implementar painel de simulacao cadastro e liquidacao | F00,B08-S01,S01 | Debounce, prévia não persistente, confirmação idempotente, estados de erro e loading, BFF | A fazer |
| F03 | P0 | frontend | Implementar extrato com filtros e paginacao server side | F00,B08-E01 | Filtros/paginação no BFF, estados vazio/erro/loading e testes de componente | A fazer |
| B09 | P1 | backend/qa | Implementar timeout e retry seguro na consulta de cambio mockado | B05 | Sem HTTP externo na transação financeira; falhas tratadas; testes de indisponibilidade | A fazer |
| B10 | P1 | backend/devops | Implementar logs estruturados metricas de negocio e trilha operacional | B06 | Métricas Micrometer sem labels de alta cardinalidade; logs correlacionados sem segredos; confirmações só pós-commit | A fazer |
| B12 | P0 | devops/qa | Configurar GitHub Actions de build testes e lint backend frontend | B02,F00 | Checks em PR; falhas visíveis; comandos descritos em README; sem green inventado | A fazer |
| D01 | P1 | architect | Revisar C4 niveis um e dois ADRs e proposta EDA do MVP | B02 | Contexto/conteineres do MVP, ADRs SQL/NoSQL, monólito/serviços, sync/eventos, EDA; **sem alta escala** | A fazer (rascunhos disponíveis) |
| D03 | P0 | analyst/reviewer | Consolidar README AI_USAGE e roteiro de defesa com evidencias reais | B07,B08,B08-S01,B08-E01,F01,F03,B12 | README com execução comprovada, links PR, limitações, custos e intervenções humanas | A fazer |
| A01 | P0 | todos | Registrar evidencias reais de IA e PRs didaticos | Contínua | ID, agente, prompt/resumo, revisão, comandos, testes efetivos, commit e PR | A fazer |

## Evidências verificáveis desta atualização

- **G00:** baseline `05b9528`, [PR #1](https://github.com/RobsonSilverioSantos/srm-credit-engine/pull/1) integrado em `develop` e [PR #2](https://github.com/RobsonSilverioSantos/srm-credit-engine/pull/2) integrado em `main` como sincronização inicial. [PR de teste #3](https://github.com/RobsonSilverioSantos/srm-credit-engine/pull/3) de feature para `main` fechado automaticamente pelo GitHub Actions; [execução correspondente](https://github.com/RobsonSilverioSantos/srm-credit-engine/actions/runs/44789563476) terminou com falha **esperada** para o caminho proibido. O responsável técnico demonstrou a recusa local do hook para `main` e `develop`. Risco residual sem Ruleset: ADR-022. **Verificação do GitHub CLI local permanece pendente**.
- **B02:** [PR #5](https://github.com/RobsonSilverioSantos/srm-credit-engine/pull/5), branch exclusiva, commit da feature `053c215`, merge `1a3ba3b`. Descrição do PR registra Gradle build/check, 1 teste JUnit e verificações Docker/Actuator em PostgreSQL local; **resultados declarados pelo agente, sem CI B12 ainda**.
- **F00:** [PR #4](https://github.com/RobsonSilverioSantos/srm-credit-engine/pull/4), branch exclusiva, commit da feature `80cc60f`, merge `c081fd5`. Descrição do PR registra `npm ci`, lint, 4/4 testes, build e verificações de interface; **resultados declarados pelo agente, sem CI B12 ainda**.
- **Próximas tarefas desbloqueadas:** B01 e B03 dependem de B02; B12 depende de B02 e F00. Criar as três features exclusivamente da ponta atual de `origin/develop`; nenhuma tarefa inicia a partir da feature de outra.

## Sequenciamento para evitar trabalho acumulado

1. **G00 manual**: criar repositório privado vazio, publicar baseline em `main`/`develop` e revisar permissões.
2. **Paralelo real a partir de G00:** `B02` (backend) e `F00` (frontend) em worktrees separados; `D01` pode aguardar. Não misturar backend e frontend no mesmo PR.
3. `B03` pode iniciar após `B02` integrada; `B01` também. `B04` e `B05` exigem `B01` integrada. `S01` só conclui após `B04`; `B06` aguarda `B03/B04/B05/S01` integradas.
4. `B07` e `B08` podem ser paralelas após `B06`; `B08-S01` e `B08-E01` seguem seus contratos integrados. `F01` e `F03` somente após as respectivas APIs/BFF.
5. `B12` após a existência de backend e frontend; `D03` fecha a entrega com fatos, sem transformar o modelo fictício de PR em evidência.
6. Um único responsável atualiza `TASKS.md`, `SPEC.md`, `DECISIONS.md` e `AI_USAGE.md` após integração. Agentes registram evidências no corpo do PR.

## Regras dos agentes

- Autorizados: criar branch/worktree, implementar, testar, commitar, push **somente de `feature/*`**, abrir PR com base **`develop`**.
- Não autorizados: merge, auto-merge, push direto em `main`/`develop`, force push, reset destrutivo, excluir worktrees sem autorização, assumir dependência não integrada.
- Teste falho, `gh` ausente, branch existente ou conflito de dependências: relatar; não anunciar PR pronto.
- Os três exercícios de `docs/exercicios/` (review TypeScript, post-mortem e futura arquitetura de alta escala) **não estão neste TASKS.md e não ocupam agentes**.
