# SRM Credit Engine

Este repositório é independente do anterior, que permanece como histórico em
[`AI_USAGE.md`](AI_USAGE.md). O bootstrap do backend não implementa os fluxos de negócio do MVP.

## Visão e stack

Motor demonstrativo de antecipação de recebíveis com **Kotlin / Spring Boot / Gradle / Java 21**, **PostgreSQL / Flyway**, **React / TypeScript / Vite / Material UI Core**. Arquitetura hexagonal e monólito modular. Regras: precificação por Strategy, golden cases C1–C3, câmbio mockado BRL/USD com vigência, liquidação ACID e idempotente, controle otimista, auditoria e extrato paginado/filtrado.

- **Organização:** limite lógico de identidade, autorização e isolamento (`Organizacao`, `organizacaoId`, `organizacao_id`).
- **Cedente:** parte que cede o recebível, vinculada à organização; não substitui a organização na autorização.
- **Autenticação de demonstração:** Spring Security, BCrypt, token assinado e verificado; sem Keycloak/OIDC/SSO no MVP. A organização não é escolhida livremente no body.
- **Frontend:** tema e componentes MUI gratuitos; pré-simulação sem persistência, depois cadastro e confirmação da liquidação; nenhuma matemática financeira executada no browser.
- **Não há desembolso bancário real.** O sistema registra a liquidação lógica e seu snapshot.

## Como rodar o backend

Veja o [guia do backend](backend/README.md) para preparar o `.env` local,
subir o PostgreSQL, executar o Spring Boot e verificar o build e o healthcheck.

## Documentos: fonte de verdade e finalidade

1. `docs/requisitos/ENUNCIADO_ORIGINAL_SRM_CREDIT_ENGINE_V2.md` — enunciado original, preservado sem alteração.
2. [`SPEC.md`](SPEC.md) — premissas de negócio, regras verificáveis e aceites do MVP.
3. [`DECISIONS.md`](DECISIONS.md) — decisões e cortes de escopo para implementação **e** defesa.
4. [`TASKS.md`](TASKS.md) — **somente** backlog da implementação do MVP, CI e documentação vinculada.
5. [`AI_USAGE.md`](AI_USAGE.md) — histórico honesto da colaboração com IA; não confundir evidência histórica do repositório anterior com resultados novos.
6. [`INICIO_GIT.md`](INICIO_GIT.md) — passos para criar `main`, `develop`, worktrees e PRs.
7. [`PROMPTS_INICIAIS.md`](PROMPTS_INICIAIS.md) — instruções de abertura de sessões dos agentes.

## Exercícios independentes — fora do backlog dos agentes

Os três exercícios são **entregáveis anexados ao repositório**, não funcionalidades deste backend nem tarefas do `TASKS.md`:

- [`docs/exercicios/REVIEW.md`](docs/exercicios/REVIEW.md) — code review de um PR TypeScript hipotético, no papel de Tech Lead.
- [`docs/exercicios/POST_MORTEM_ANEXO_B.md`](docs/exercicios/POST_MORTEM_ANEXO_B.md) — post-mortem hipotético de war room de sustentação; não descreve incidente real no novo MVP.
- [`docs/exercicios/ALTA_ESCALA_STATUS.md`](docs/exercicios/ALTA_ESCALA_STATUS.md) — **somente marcador de pendência** da proposta arquitetural de 1 milhão de transações por minuto. O desenho anterior foi rejeitado e **não está neste pacote**. O responsável técnico e ChatGPT vão desenvolver a proposta separadamente, sem ocupar agentes.

ADRs sobre escolhas **do MVP** e proposta de EDA evolutiva ficam em `docs/adr/` e `docs/arquitetura/EDA_LIQUIDACAO.md`. O C4 do MVP está em `docs/arquitetura/C4_MVP.md` como **rascunho a validar**, não como arquitetura de alta escala.

## Por que Git Flow simplificado nesta entrega

**Estratégia:** `main` representa versões deliberadamente liberadas; `develop` integra features revisadas; cada ID do `TASKS.md` produz `feature/<id>-<slug-do-trabalho>` em **worktree independente**. Os agentes são autorizados a fazer commit, push e abrir PR **sempre para `develop`**; **apenas o responsável técnico revisa e decide merge**. O trabalho de um agente não aparece automaticamente na branch de outro: dependências precisam estar integradas antes de iniciar uma tarefa.

- **Trunk-based:** excelente em equipes maduras com testes e feature flags, porém sessões paralelas de agentes sem gates humanos trariam maior risco de misturar alterações e dificultariam evidenciar cada decisão durante o exercício.
- **GitHub Flow:** válido e mais leve, mas não separa `main` de uma área explícita de integração; aqui `develop` permite validar backend, frontend e migrações antes da liberação final.
- **Custo da escolha:** mais branches e PRs. Evitar long-lived features e reuniões desnecessárias; não usar `git flow feature finish` (que faz merge local) nem obrigar `release/*` e `hotfix/*` neste prazo.
- **PR didático:** cada PR deve explicar motivação, responsabilidades, alternativas, testes realizados, riscos e um ponto de aprendizado para um júnior. Template em `.github/PULL_REQUEST_TEMPLATE.md`; exemplo **fictício** em `docs/engenharia/EXEMPLO_PR_DIDATICO.md`.
- **Review do próprio autor:** em um repositório individual onde a mesma identidade abre o PR, o GitHub não permite aprovação formal do próprio PR. Fazer a revisão humana do diff e executar o merge manualmente; **não ativar requisito de aprovação por terceiro que não existe**. Verificar disponibilidade das proteções conforme o plano GitHub e configuração do repositório.


### Proteção compensatória de branches — restrição de plano (ADR-022)

O repositório é privado e a conta atualmente não dispõe de Rulesets para esta configuração: uma consulta de 20/11/2026 à API retornou HTTP 403 com a orientação de usar GitHub Pro ou tornar o repositório público. **Não tornaremos o repositório público nem adicionaremos despesa exclusivamente para este exercício.**

- `.github/workflows/guard-main.yml`: ao detectar PR para `main`, permite somente origem `develop` do mesmo repositório; fecha outras origens automaticamente, quando GitHub Actions estiver executando e autorizado.
- `.githooks/pre-push`: impede pushes locais diretamente para `main` e `develop`, desde que o clone tenha executado `git config core.hooksPath .githooks`. Hooks não se ativam automaticamente com clone/pull.
- Instruções dos agentes: apenas push em `feature/*`, PR com base `develop`, jamais merge/automerge. O responsável técnico revisa e integra manualmente.
- **Limitação aceita:** sem Ruleset/branch protection no servidor, esses controles não impedem todos os pushes/merges por CLI, interface ou API. O workflow pode executar depois da tentativa de merge, e hooks podem ser contornados. Não descrever como bloqueio absoluto.
- **Evidências pendentes:** merge do PR de governança, autorização de execução do GitHub Actions e teste real de PR indevido fechado; ativação do hook local e teste de recusa antes de prosseguir. Ver `docs/adr/ADR-022-controles-github-free.md` e matriz `docs/defesa/RASTREABILIDADE_STAFF_TECH_LEAD.md`.

## Agentes e governança

- Cursor: `.cursor/agents/` e `.cursor/rules/srm-standards.mdc`.
- Copilot: `.github/agents/` e `.github/copilot-instructions.md`.
- Mantêm-se os sete papéis: analyst, architect, backend, frontend, devops, qa, reviewer. Eles trabalham **somente no escopo de tarefas autorizado**; revisão de PR/merge e decisões financeiras são humanas.
- Não modificar simultaneamente `SPEC.md`, `DECISIONS.md`, `TASKS.md` e `AI_USAGE.md` em vários worktrees; centralizar mudanças documentais normativas após PR.

## Defesa Staff / Tech Lead

ADRs: `docs/adr/ADR-005-*.md`, `ADR-018-*.md`, `ADR-019-*.md`; EDA: `docs/arquitetura/EDA_LIQUIDACAO.md`; matriz: `docs/defesa/RASTREABILIDADE_STAFF_TECH_LEAD.md`. O design para 1 milhão/min permanece **pendente e independente**. Nenhum documento atesta benchmark executado. Keycloak e deploy em nuvem são cortes conscientes por prazo, justificados em `DECISIONS.md`.

**Primeira ação:** crie um repositório privado **vazio** no GitHub, extraia este pacote e siga [`INICIO_GIT.md`](INICIO_GIT.md). Não apague o repositório antigo.

