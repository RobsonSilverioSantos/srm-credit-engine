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

## Integração contínua

O workflow [`CI`](.github/workflows/ci.yml) roda em PRs para `develop` na
abertura, reabertura, atualização de commits e edição (inclusive mudança de
base). Os jobs são independentes para identificar se a falha está no backend
ou no frontend. Para reproduzir os checks:

```sh
cd backend
./gradlew clean build
```

Use JDK 21. O build inclui testes e verificações hexagonais; o smoke test atual
não usa PostgreSQL e não substitui testes de integração com banco.

```sh
cd frontend
npm ci
npm run lint
npm test
npm run build
```

Use Node.js 24 (ou outra versão compatível com `frontend/package.json`). No
GitHub, abra o PR, escolha **Checks** e o job com falha; expanda o primeiro
passo vermelho para identificar o comando e a mensagem. Corrija a causa,
execute os comandos locais e envie outro commit para a feature: o PR dispara
uma nova execução. Um passo ignorado após falha não significa que passou;
execução pendente ou ausente também não é CI verde.

O workflow usa `pull_request`, não `pull_request_target`, token somente de
leitura e nenhuma credencial de produção. As actions são fixadas por commit;
o Gradle valida os wrappers e usa cache, assim como o npm. Caches de PR ficam
limitados pelo escopo de referências do GitHub e não devem conter segredos;
código do PR pode ser executado no runner e acessa caches disponíveis à sua
base. Esses checks não são proteção obrigatória de merge: veja ADR-022 abaixo.

## Evidências de execução, custos e limites

Em 20/11/2026, na branch documental D03, a regressão integrada do backend foi
executada com `cd backend && bash scripts/test-cadastro-postgres.sh`:
`BUILD SUCCESSFUL`, 70 testes, 0 falhas, 0 erros e 0 ignorados, usando PostgreSQL
efêmero removido pelo script. No frontend, `cd frontend && npm ci` instalou as
dependências do lockfile e `npm run lint`, `npm test` e `npm run build`
passaram; Vitest reportou 4 arquivos e 21 testes aprovados. Esses são resultados
locais desta sessão, não testes manuais de ponta a ponta.

As implementações que compõem a baseline foram integradas por PRs revisados e
integrados pelo responsável técnico: [B07 #19](https://github.com/RobsonSilverioSantos/srm-credit-engine/pull/19),
[B08 #20](https://github.com/RobsonSilverioSantos/srm-credit-engine/pull/20),
[B08-S01 #23](https://github.com/RobsonSilverioSantos/srm-credit-engine/pull/23),
[B08-E01 #22](https://github.com/RobsonSilverioSantos/srm-credit-engine/pull/22),
[F03 #25](https://github.com/RobsonSilverioSantos/srm-credit-engine/pull/25) e
[F01 #26](https://github.com/RobsonSilverioSantos/srm-credit-engine/pull/26).
Os checks remotos de backend e frontend passaram em [B12 #7](https://github.com/RobsonSilverioSantos/srm-credit-engine/pull/7),
[F03 #25](https://github.com/RobsonSilverioSantos/srm-credit-engine/pull/25)
e [F01 #26](https://github.com/RobsonSilverioSantos/srm-credit-engine/pull/26).
O CI do PR D03 [#29](https://github.com/RobsonSilverioSantos/srm-credit-engine/pull/29)
passou em ambos os jobs no commit inicial `f71eb43` ([execução #44848917116](https://github.com/RobsonSilverioSantos/srm-credit-engine/actions/runs/44848917116)).
No commit documental `6a61fda`, o backend passou; o frontend falhou uma vez em
um teste de diálogo e passou após uma única repetição ([execução #44849306596](https://github.com/RobsonSilverioSantos/srm-credit-engine/actions/runs/44849306596)).
No head `50d950d`, o backend passou e o mesmo teste frontend voltou a falhar
([execução #44849740354](https://github.com/RobsonSilverioSantos/srm-credit-engine/actions/runs/44849740354));
essa execução ficou vermelha. O teste local do frontend passou e nenhum código
foi alterado em resposta; a causa da falha remota não foi estabelecida. Confira
o estado do PR antes de integração. No commit documental `cbdbd20`, os dois jobs
passaram ([execução #44850079836](https://github.com/RobsonSilverioSantos/srm-credit-engine/actions/runs/44850079836));
as falhas anteriores permanecem registradas como histórico.
Os relatos de teste contidos nos PRs são identificados como relatos; não são
apresentados como execução local do D03.

O teste local utilizou apenas infraestrutura efêmera; não houve deploy,
pagamento real ou uso de provedor cloud nesta validação. Não há comprovante
para atribuir valor monetário a assinaturas ou uso de IA; consulte
[`AI_USAGE.md`](AI_USAGE.md), que registra o limite de cota do Cursor sem
inferir cobrança. O benchmark de desempenho da SPEC não foi executado.
Também não se deve apresentar F01 e F03 como fluxo integrado de ponta a ponta:
as telas ainda têm entradas e autenticação independentes, conforme
[`frontend/README.md`](frontend/README.md).

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

**Bootstrap:** este repositório já foi criado e versionado. Para reproduzir a
configuração inicial em outro repositório, consulte [`INICIO_GIT.md`](INICIO_GIT.md);
para executar este checkout, use os guias do backend e do frontend acima.

