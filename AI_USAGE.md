# AI_USAGE.md — SRM Credit Engine (novo repositório)

> **Aviso de rastreabilidade — 20/11/2026:** o conteúdo abaixo inclui o **histórico factual do repositório anterior**, marcado como tal. Nenhum commit, teste, artefato compilado ou serviço do repositório anterior equivale a implementação no novo repositório. O novo repositório começa sem código e terá evidências próprias. A escolha de novo Git/repositório e de escopo é decisão do candidato.

## RESET-01 — Reinício planejado após incidente Git e revisão de custo/prazo (20/11/2026)

- **Relato humano:** após operações Git/merge inadequadas, o candidato relata perda do estado útil do código e de configuração dos agentes; decidiu **não priorizar recuperação** e reiniciar em repositório novo. Não houve investigação forense ou confirmação independente de perda irreversível.
- **Problema de processo identificado:** B01–B06 foram trabalhadas inicialmente numa sessão/branch acumuladora, com histórico de commits e PRs insuficientemente segregados. O uso de uma sessão não cria branches automaticamente; a ausência do gate de branch por tarefa foi parte da causa de rastreabilidade deficiente.
- **Mudança autorizada:** agentes do Cursor e do Copilot permanecem; cada tarefa usa Git worktree/branch própria. **Nova autorização:** agentes podem gerar nomes de feature a partir de ID + Trabalho, criar worktrees, executar testes, criar commit com mensagem descritiva, dar push **somente da feature** e abrir PR **somente para develop**. O candidato controla revisão e merge.
- **Corte de escopo por prazo e capacidade de revisão:** remover Keycloak/federação e adotar autenticação local demonstrativa Spring Security + BCrypt + token assinado. Não enfraquecer autorização por organização. DECISIONS.md ADR-007.
- **Mudanças de domínio/frontend:** organização é o escopo lógico de acesso e cedente permanece entidade de negócio vinculada à organização; não incluir entidade adicional para a parte compradora neste MVP. Material UI Core definido como design system, com tarefa F00 antes do frontend.
- **Evidência externa e custo:** preservar OPS-01 (esgotamento de cota do Cursor) e os registros reais de mudança de ferramenta. Não afirmar preço de assinatura ou gastos sem comprovante.
- **Escopo independente:** REVIEW.md e post-mortem continuam exercícios separados e **não** entram no TASKS.md deste repositório.
- **Estado desta entrada:** planejamento/documentação; nenhum repositório remoto criado por esta entrada, nenhum comando Git, teste, build ou nova implementação realizada.

### TERMINOLOGIA-01 — Organização e cedente (20/11/2026)

- **Decisão humana:** manter `Organizacao` como unidade de isolamento e `Cedente` como entidade do domínio que cede recebíveis. Retirar do modelo planejado a referência a uma entidade específica para a parte compradora, evitando introduzir escopo desnecessário.
- **Arquivos documentais ajustados:** `SPEC.md`, `DECISIONS.md` (ADR-006), `README.md` e `.github/copilot-instructions.md`. A especificação original do desafio foi preservada sem alterações.
- **Natureza da mudança:** exclusivamente documental no novo bootstrap; nenhum código, migration, teste, commit ou PR foi produzido por esta adequação.

## BOOTSTRAP-02 — Pacote documental de reinício preparado (20/11/2026)

- **Ferramenta:** ChatGPT, com inspeção dos arquivos normativos e do enunciado original disponível; sem executar código do novo repositório.
- **Orientação humana:** criar novo repositório GitHub, preservar organização e cedente, Git Flow simplificado e worktrees por tarefa, agentes autorizados a commit/push/PR para `develop`, merge exclusivamente humano, retirar Keycloak do MVP e adotar Material UI.
- **Separação de escopo reforçada:** Review TypeScript, post-mortem de war room e proposta de alta escala são três exercícios independentes em `docs/exercicios/`, fora de `TASKS.md`.
- **Correção documental:** a proposta anterior de arquitetura de 1 milhão/min foi considerada incorreta e **não será usada**. O documento de status é apenas placeholder; projeto arquitetural independente será elaborado em discussão posterior entre responsável técnico e ChatGPT, sem ocupar agentes.
- **Artefatos:** SPEC, DECISIONS, TASKS, README, guia Git, prompts, agentes Cursor/Copilot, template de PR, ADRs/EDA do MVP, matriz de avaliação e exercícios independentes já redigidos.
- **Estado:** arquivos documentais preparados para extração; **nenhum repositório remoto, commit, push, PR, workflow CI, build, teste ou benchmark novo confirmado**.

## Evidências do novo repositório — atualização 20/11/2026

| Tarefa | Branch / origem | Commit / merge observado | PR e evidência | Testes registrados | Revisão / merge humano |
|---|---|---|---|---|---|
| G00 — baseline e governança | `main`, `develop`; governança em `feature/g00-...` | Baseline `05b9528`; governança integrada `7b89828`; sincronização inicial da `main` `5e7a87b` | [PR #1](https://github.com/RobsonSilverioSantos/srm-credit-engine/pull/1) → `develop`; [PR #2](https://github.com/RobsonSilverioSantos/srm-credit-engine/pull/2) `develop` → `main`; [PR #3](https://github.com/RobsonSilverioSantos/srm-credit-engine/pull/3) — teste proibido, não integrado | [Action do caminho permitido](https://github.com/RobsonSilverioSantos/srm-credit-engine/actions/runs/44787664195): sucesso; [Action do caminho proibido](https://github.com/RobsonSilverioSantos/srm-credit-engine/actions/runs/44789563476): falha intencional, PR fechado pelo bot; recusa local de push nas duas branches demonstrada pelo responsável | #1 e #2 integrados mediante decisão humana; #3 fechado pelo bot, sem merge. A verificação de `gh auth status` no Linux ainda não consta como evidência direta |
| B02 — bootstrap backend | `feature/b02-preparar-backend-kotlin-spring-boot-gradle-e-docker-compose` | Feature `053c215`; merge `1a3ba3b` | [PR #5](https://github.com/RobsonSilverioSantos/srm-credit-engine/pull/5) para `develop` | **Declarado no PR pelo agente:** `./gradlew --no-daemon clean build --warning-mode all`, `./gradlew --no-daemon check`, 1 teste JUnit e smoke Compose/PostgreSQL/Actuator; não existe validação de CI B12 desse PR | Responsável técnico informou aprovação e GitHub confirma merge em `develop` |
| F00 — design system frontend | `feature/f00-definir-tema-material-ui-e-componentes-visuais-reutilizaveis` | Feature `80cc60f`; merge `c081fd5` | [PR #4](https://github.com/RobsonSilverioSantos/srm-credit-engine/pull/4) para `develop` | **Declarado no PR pelo agente:** `npm ci`, `npm run lint`, `npm test` (4/4) e `npm run build`, com verificações visuais; CI B12 ainda pendente | Responsável técnico informou aprovação e GitHub confirma merge em `develop` |

### GOV-01 — Controle compensatório de PRs sem GitHub Pro

- **Condição externa confirmada:** repositório privado sem Rulesets disponíveis na configuração atual; tentativa de consulta da API retornou HTTP 403 com exigência de GitHub Pro ou repositório público.
- **Decisão humana:** preservar repositório privado e evitar custo adicional; ADR-022 registra a combinação de GitHub Actions, hook local e instruções dos agentes, aceitando risco residual por ausência de bloqueio server-side.
- **Validação automatizada positiva:** PR #2 com origem `develop` para `main` — workflow `Guardar branch main` completou com sucesso.
- **Validação negativa real:** PR #3 com origem `feature/g00-validar-guard-main` para `main` — execução [44789563476](https://github.com/RobsonSilverioSantos/srm-credit-engine/actions/runs/44789563476) terminou em falha intencional; `github-actions[bot]` comentou e fechou o PR automaticamente. Não houve merge.
- **Validação local do hook:** responsável executou entradas de teste para `refs/heads/main` e `refs/heads/develop` em `.githooks/pre-push`, obtendo a mensagem de bloqueio; teste da `develop` retornou status 1. Esses comandos validam a lógica local, **não** um bloqueio imposto pelo servidor.
- **Limitação:** há janela até a Action reagir; a UI/API do GitHub e clones sem hook não são abrangidos. Não declarar proteção absoluta de `main`.

### DEV-01 — Execução de agentes em worktrees isolados

- Backend B02: sessão de agente inicialmente arquivada antes de executar; tentativa de restauração repetiu a interrupção. Uma **nova sessão** no mesmo worktree concluiu a tarefa e publicou PR #5. A causa exata do arquivamento não foi diagnosticada.
- Frontend F00: sessão distinta concluiu tema e componentes, publicou PR #4. Ambas as tarefas foram integradas por revisão e merge humanos.
- **Rastreabilidade e limites:** descrições dos PRs documentam comandos e resultados relatados por cada agente. GitHub confirma as branches, commits, PRs e merges, mas não substitui reexecução independente dos testes. B12 (CI automatizada em PRs) permanece pendente; B01/B03/B12 estão desbloqueadas pelas dependências integradas.
- **Roteiro de trabalho:** não utilizar a mesma sessão nem o mesmo worktree para B01, B03 ou B12; cada tarefa deverá ter PR didático para `develop`. Os exercícios independentes de review, post-mortem e alta escala permanecem fora deste backlog.

---

## D03 — Consolidação de evidências e roteiro de defesa (20/11/2026)

- **Agente/ferramenta:** analyst/reviewer no GitHub Copilot Agent para VS Code.
- **Solicitação resumida:** consolidar README, AI_USAGE e roteiro de defesa com execução verificável, links de PR, limites, custos e intervenções humanas; manter decisões financeiras e contratos existentes.
- **Escopo realizado:** alteração documental em `README.md`, `AI_USAGE.md` e `docs/defesa/RASTREABILIDADE_STAFF_TECH_LEAD.md`. Nenhum código, tarefa global, regra financeira ou arquitetura foi alterado.
- **Execução local verificada neste worktree:**
  - Ambiente: Linux; OpenJDK `24.0.1`; Node.js `v24.13.1`; npm `11.8.0`; Docker Engine `29.10.0`.
  - `cd backend && bash scripts/test-cadastro-postgres.sh` → `BUILD SUCCESSFUL`; 70 testes, 0 falhas, 0 erros e 0 ignorados. O script iniciou um PostgreSQL descartável e removeu seu contêiner, rede e volume ao terminar.
  - `cd frontend && npm ci` → 313 pacotes adicionados; auditoria informou 0 vulnerabilidades.
  - `cd frontend && npm run lint && npm test && npm run build` → lint e build concluídos; Vitest: 4 arquivos e 21 testes aprovados.
- **CI remota consultada:** os jobs de backend e frontend passaram em [B12 #7](https://github.com/RobsonSilverioSantos/srm-credit-engine/pull/7), [F03 #25](https://github.com/RobsonSilverioSantos/srm-credit-engine/pull/25), [F01 #26](https://github.com/RobsonSilverioSantos/srm-credit-engine/pull/26) e [G00 #28](https://github.com/RobsonSilverioSantos/srm-credit-engine/pull/28). No [PR D03 #29](https://github.com/RobsonSilverioSantos/srm-credit-engine/pull/29), os dois jobs passaram para `f71eb43` ([execução #44848917116](https://github.com/RobsonSilverioSantos/srm-credit-engine/actions/runs/44848917116)). No commit documental `6a61fda`, o backend passou; o primeiro job frontend falhou no teste `frontend/test/operacoes.test.tsx` por não encontrar o botão “Entrar”, e passou após uma repetição ([execução #44849306596](https://github.com/RobsonSilverioSantos/srm-credit-engine/actions/runs/44849306596)). No head `50d950d`, o backend passou, mas o mesmo job frontend voltou a falhar ([execução #44849740354](https://github.com/RobsonSilverioSantos/srm-credit-engine/actions/runs/44849740354)); essa execução ficou vermelha. No commit documental `cbdbd20`, os dois jobs passaram ([execução #44850079836](https://github.com/RobsonSilverioSantos/srm-credit-engine/actions/runs/44850079836)). A suíte local passou; nenhum código foi alterado. A causa das falhas pontuais não foi estabelecida; conferir o CI do head atual antes da integração.
- **PRs e intervenção humana:** [B07 #19](https://github.com/RobsonSilverioSantos/srm-credit-engine/pull/19), [B08 #20](https://github.com/RobsonSilverioSantos/srm-credit-engine/pull/20), [B08-E01 #22](https://github.com/RobsonSilverioSantos/srm-credit-engine/pull/22), [B08-S01 #23](https://github.com/RobsonSilverioSantos/srm-credit-engine/pull/23), [F03 #25](https://github.com/RobsonSilverioSantos/srm-credit-engine/pull/25) e [F01 #26](https://github.com/RobsonSilverioSantos/srm-credit-engine/pull/26) foram integrados em `develop` pelo responsável identificado no GitHub. Revisão e integração permanecem decisões humanas; integração de PR não foi interpretada como teste manual ponta a ponta.
- **Erro operacional do agente detectado:** as primeiras chamadas de build/lint foram feitas no diretório raiz, onde não existem `./gradlew` nem `package.json`; a chamada de lint também revelou dependências frontend ausentes. As mensagens `not found`, `ENOENT` e `eslint: not found` identificaram o problema. Os comandos foram corrigidos para os diretórios indicados nos READMEs (`backend/` e `frontend/`), e as dependências foram instaladas com `npm ci`; as execuções acima são os resultados corrigidos. Nenhum desses erros ocorreu em código de produto.
- **Custos:** não houve deploy, serviço cloud ou pagamento real durante esta validação. O teste PostgreSQL foi efêmero. Não foi verificado valor de assinatura ou custo monetário do uso de IA; o registro OPS-01 descreve uma mensagem de limite do Cursor, não uma cobrança.
- **Limitações:** não foi executado benchmark; não foi feita demonstração manual do fluxo completo F01/F03; as telas ainda têm entradas/autenticação independentes. Relatos nos PRs e checks remotos permanecem separados dos testes locais acima. Decisões e premissas do MVP continuam sujeitas à validação humana descrita em `SPEC.md` e `DECISIONS.md`.

## A01 — Registro inicial de evidências de IA e PRs didáticos (20/11/2026)

Esta coleta inicial documenta B10 e S02, integradas depois da consolidação D03. Os resumos abaixo são paráfrases das descrições dos PRs e do backlog, não transcrições dos prompts originais. Os resultados de testes locais são relatos dos agentes, **não reexecutados nesta coleta**; os checks remotos são apresentados separadamente. **O registro posterior de F04 e o encerramento documental da A01 constam no adendo abaixo**, realizado após os merges dos PRs #37 e #36.

### A01 — Esta coleta documental

- **Agente / ID:** Copilot SDK no VS Code; A01.
- **Prompt/resumo:** pedido para confirmar branch, base e dependências; registrar evidências reais de IA e PRs didáticos com ID, agente, resumo do prompt, revisão, comandos, testes, commit e PR; manter o escopo documental e adiar a conclusão até F04.
- **Revisão no momento da abertura do PR:** o agente conferiu o diff e o escopo. Posteriormente, o PR #36 foi integrado em `develop` pelo responsável técnico; o GitHub confirma merge, não necessariamente uma aprovação formal de review.
- **Comandos/testes desta coleta:** `git branch --show-current`, `git status --short` e `git worktree list` confirmaram o worktree/branch previstos; `git diff --check` passou. Nenhum teste de aplicação foi executado porque a mudança é somente documental.
- **Commit / PR:** commit de conteúdo `3d3df56`; [PR #36](https://github.com/RobsonSilverioSantos/srm-credit-engine/pull/36), base `develop`, merge `d7177f0`; [CI backend/frontend aprovada](https://github.com/RobsonSilverioSantos/srm-credit-engine/actions/runs/44856268536).
- **Estado:** registro inicial da A01 integrado. O adendo F04 nesta mesma seção completa sua rastreabilidade, sem alegar reexecução dos testes da F04 pela A01.

### B10 — Logs estruturados, métricas e trilha operacional

- **Agente / ID:** agente Copilot no papel backend/devops (B10, conforme `TASKS.md`); o commit da feature registra Robson Silvério Santos e Copilot como coautores.
- **Prompt/resumo:** implementar logs JSON correlacionados sem segredos, métricas Micrometer de confirmações/replays/duração sem tags de alta cardinalidade e expor métricas pelo Actuator autenticado, sem alterar contratos financeiros.
- **Revisão:** PR [#33](https://github.com/RobsonSilverioSantos/srm-credit-engine/pull/33) integrado em `develop` por RobsonSilverioSantos. A consulta ao GitHub não retornou aprovação/review formal (`reviews` vazio e `reviewDecision` vazio); portanto, o merge não é apresentado como aprovação formal. A descrição do PR registra pontos para revisão humana e correções feitas durante a iteração.
- **Comandos/testes locais relatados no PR:** `cd backend && bash scripts/test-cadastro-postgres.sh` — `BUILD SUCCESSFUL`, 74 testes, 0 falhas, 0 erros e 0 ignorados, com PostgreSQL temporário; `git diff --check` — passou. Incluídos testes para JSON/MDC, correlação, métricas sem tags, replay, rollback e confirmação pós-commit. Uma chamada inicial do wrapper na raiz e uma configuração YAML incorreta falharam durante a iteração; foram corrigidas antes da execução final reportada.
- **CI remota:** execução [44852388657](https://github.com/RobsonSilverioSantos/srm-credit-engine/actions/runs/44852388657): jobs backend e frontend aprovados. Isso não é uma reexecução local feita por A01.
- **Commit / PR:** feature `49f4392`; merge `f82f224`; [PR #33](https://github.com/RobsonSilverioSantos/srm-credit-engine/pull/33), base `develop`.
- **Limite didático:** métricas não têm exporter Prometheus, dashboard ou alerta; não houve benchmark nem teste de carga.

### S02 — Gestão segura de usuários organizacionais

- **Agente / ID:** agente Copilot no papel backend (S02, conforme `TASKS.md`); o commit da feature registra Robson Silvério Santos e Copilot como coautores.
- **Prompt/resumo:** implementar perfis MASTER/ADMIN/OPERADOR e bootstrap local controlado do primeiro MASTER; derivar organização da identidade JWT validada, consultar autorização atual no PostgreSQL e compartilhar os casos de uso entre API e BFF, sem cadastro público de MASTER nem reescrita das migrations V1–V3.
- **Revisão:** PR [#34](https://github.com/RobsonSilverioSantos/srm-credit-engine/pull/34) integrado em `develop` por RobsonSilverioSantos. A consulta ao GitHub não retornou aprovação/review formal (`reviews` vazio e `reviewDecision` vazio); portanto, o merge não é apresentado como aprovação formal. A descrição do PR registra as limitações do bootstrap e que o fluxo interativo completo do terminal não foi validado manualmente.
- **Comandos/testes locais relatados no PR:** `cd backend && bash scripts/test-cadastro-postgres.sh` — `BUILD SUCCESSFUL`, 73 testes, 0 falhas, 0 erros e 0 ignorados, com PostgreSQL efêmero; `bash -n backend/scripts/bootstrap-master.sh` — passou; `git diff --cached --check` — passou. O PR relata testes de bootstrap BCrypt/reexecução, permissões, escopo, API/BFF, segredos e revogação de JWT. Uma asserção de teste e um problema de nulabilidade foram corrigidos antes da execução final reportada.
- **CI remota:** execução [44852904037](https://github.com/RobsonSilverioSantos/srm-credit-engine/actions/runs/44852904037): jobs backend e frontend aprovados. Isso não é uma reexecução local feita por A01.
- **Commit / PR:** feature `736a5e8`; merge `8dc1251`; [PR #34](https://github.com/RobsonSilverioSantos/srm-credit-engine/pull/34), base `develop`.
- **Limite didático:** o bootstrap interativo e os testes mobile de F04 continuam pendentes; não presumir um fluxo administrativo frontend integrado.

### F04 — Administração de usuários (adendo após os merges)

- **Agente / ID:** GitHub Copilot no papel frontend, F04; evidência originada no PR #37 e na CI. O adendo documental registra fatos verificados no GitHub e **não** representa nova execução do agente F04.
- **Solicitação resumida:** construir a interface responsiva MUI para MASTER criar ADMIN/OPERADOR e ADMIN criar OPERADOR, consultar capacidades em `/bff/v1/usuarios/me`, consumir API/BFF da S02 por portas/adaptadores hexagonais, listar e ativar/inativar usuários da própria organização com feedback e confirmação, sem cadastro de MASTER no navegador.
- **Resultado registrado no PR:** tela administrativa reutiliza a sessão F01 em memória; criação, listagem e alteração de status com escopo e autorização revalidados pelo backend S02. F03 mantém entrada independente, sem prova de navegação/sessão unificadas.
- **Comandos e testes locais relatados pelo agente F04:** `cd frontend && npm ci` (313 pacotes, 0 vulnerabilidades reportadas); `npm run lint` passou; `npm test` passou com 8 arquivos e 51 testes; `npm run build` passou. Verificação visual com **BFF simulado** e viewport de `390×844` confirmou formulário em coluna única e rolagem horizontal da tabela. **Não foi teste ponta a ponta com backend real, nem no celular físico**, e não foi reexecutado na coleta A01.
- **CI remota comprovada:** [execução #44856619021](https://github.com/RobsonSilverioSantos/srm-credit-engine/actions/runs/44856619021), jobs backend e frontend concluídos com sucesso no head `13102a4`.
- **Commit / PR / merge:** feature `13102a4`; [PR #37](https://github.com/RobsonSilverioSantos/srm-credit-engine/pull/37), base `develop`, merge `8493928`; integração confirmada no GitHub. Revisão técnica e merge são responsabilidades humanas; a API informa o estado do merge, não a execução de testes manuais.
- **Documentação A01:** [PR #36](https://github.com/RobsonSilverioSantos/srm-credit-engine/pull/36) integrou a coleta inicial de B10/S02 (merge `d7177f0`), com CI verde. A complementação factual F04 foi incorporada posteriormente neste adendo para encerrar a rastreabilidade do backlog.
- **Limitações persistentes:** bootstrap interativo do primeiro MASTER ainda não foi comprovado manualmente; testar login MASTER → ADMIN → OPERADOR, permissões 401/403/409, simulação, cadastro, liquidação e extrato com banco e BFF reais, inclusive no celular. Não há benchmark ou homologação de negócio pela conclusão dos PRs.

### A01 — Fechamento do registro documental

- **Base verificada:** PRs #33 (B10), #34 (S02), #36 (A01) e #37 (F04), com merges reais e CI backend/frontend aprovadas em seus respectivos heads.
- **Escopo deste complemento:** consolidar o registro F04 e o estado de conclusão no `AI_USAGE.md` e no `TASKS.md`. Nenhum teste local da aplicação foi realizado neste complemento; os resultados do agente permanecem claramente atribuídos ao PR de origem.
- **Situação:** evidências do backlog MVP registradas para os 24 itens integrados/operacionais; testes de aceitação humana/end-to-end ainda necessários e independentes da contagem de tarefas.

## AFERICAO-MANUAL-01 — Golden cases C1, C2 e C3 pela interface (21/11/2026)

- **Ator e origem da evidência:** responsável técnico do projeto, executando a aplicação local e comparando os resultados com a seção de aferição obrigatória do enunciado. **C1 e C2** foram declarados corretos até os centavos pelo responsável; não foi fornecida captura individual desses casos. **C3** foi declarado correto e apresentado em captura visual da pré-simulação React.
- **Resultados exigidos e conferidos:**

  | Caso | Modalidade / dados | Resultado esperado | Evidência manual |
  |---|---|---|---|
  | C1 | Duplicata mercantil; R$ 100.000,00; 3 meses; BRL | **R$ 92.859,94** (deságio R$ 7.140,06) | **Confirmado pelo responsável** como coincidente nos centavos; sem captura versionada |
  | C2 | Cheque pré-datado; R$ 25.000,00; 2 meses; BRL | **R$ 23.337,77** (deságio R$ 1.662,23) | **Confirmado pelo responsável** como coincidente nos centavos; sem captura versionada |
  | C3 | Duplicata mercantil; R$ 100.000,00; 3 meses; USD; 5,4321 BRL/USD | **US$ 17.094,67** (VP BRL R$ 92.859,94; deságio R$ 7.140,06) | **Captura da interface apresentada** com os três valores correspondentes |

- **Evidência visual de C3:** tela `Simulação pré-cadastro` apresenta pagamento `US$ 17.094,67`, valor presente `R$ 92.859,94`, deságio `R$ 7.140,06`, taxa base mensal `1,00000000%`, spread mensal `1,50000000%` e cotação `5.4321000000`. Referência da simulação: `2026-11-21T22:26:22.110780Z`. **Imagem não incorporada ao repositório neste registro**.
- **Teste automatizado separado:** `backend/app/src/test/kotlin/br/com/srm/creditengine/precificacao/PrecificacaoTest.kt` já possui assertions Kotlin `BigDecimal` para C1/C2/C3, mais cenários de `HALF_EVEN`. A regressão local PostgreSQL em `D03` é evidência histórica própria; **não foi reexecutada** nesta coleta documental.
- **Intervenção humana:** aferição na interface e relato dos resultados são ações do responsável, não execução por agente de IA. ChatGPT revisou a captura C3, conferiu valores e referências de código/repositório e estruturou o registro documental. **B14 já integrada** no PR [#56](https://github.com/RobsonSilverioSantos/srm-credit-engine/pull/56); essa atualização não implementa ou reinicia B14.
- **Limites:** evidência **3/3 dos resultados de pré-simulação conferidos em centavos**, não homologação financeira produtiva, liquidação ponta a ponta, transação bancária, PTAX oficial, disponibilidade contínua ou teste de todas as combinações. A cotação de C3 é mock fixo 5,4321; o problema visual dos zeros de escala está em [PR #59](https://github.com/RobsonSilverioSantos/srm-credit-engine/pull/59), sem impacto no valor calculado. Não foram usados tokens JWT nem informações de empresas reais no registro documental.
- **Documento de apoio:** [VALIDACAO_GOLDEN_CASES_C1_C2_C3_2026-11-21.md](docs/validacao/VALIDACAO_GOLDEN_CASES_C1_C2_C3_2026-11-21.md). Os valores e a origem de cada evidência ficam reunidos nesse relatório.

## Histórico do repositório anterior (preservado, não vigente para o novo código)


**Exceção editorial:** este documento registra explicitamente o uso de IA, inclusive prompts, conversas, verificações, limitações e correções, quando houver evidências. A restrição de linguagem metadiscursiva aplicada aos documentos técnicos não se aplica a este registro.

## 1. Objetivo e transparência

Este documento registra a estratégia de colaboração com IA no desafio técnico **SRM Credit Engine — Edição AI-native (v2)**. O uso de IA é uma ferramenta de engenharia, não uma delegação de responsabilidade técnica. As decisões finais, a validação dos resultados e a defesa do código cabem ao candidato.

**Estado deste registro:** B01 revalidada (24 PASS). B02 reexecutada. B03 publicada em `fa6616f`. B04, B05 e a separação de tipos públicos entram no commit autorizado pelo candidato em 18/11/2026. Branch: `feature/b01-migrations`. Sem push nesta etapa.

## 2. Contexto e decisões de colaboração

A partir de conversas com ChatGPT, foram explorados: (a) Strategy e persistência/configuração de regras financeiras, incluindo possibilidade de novas fórmulas; (b) cálculo de valor presente em Java e necessidade de precisão aos centavos; (c) agentes especializados no Cursor, monorepo, opções de hospedagem, custos e autenticação com Keycloak, Google e Microsoft.

A opção por **monorepo** busca facilitar rastreabilidade, integração e revisão. A proposta inicial de AWS Amplify/ECS/Fargate/Terraform foi reavaliada em função do caráter temporário do desafio, do custo e do esforço-alvo. A publicação em nuvem não será tratada como pré-requisito sem exigência explícita do enunciado. **Keycloak é um diferencial escolhido pelo candidato, não um requisito do enunciado**, e deve ser limitado para não prejudicar o core.

## 3. Estratégia de agentes de IA (Cursor e preparação para GitHub Copilot)

| Agente | Responsabilidade | Saída esperada | Limite |
|---|---|---|---|
| analyst | Ambiguidades, perguntas ao negócio, critérios de aceite | proposta de `SPEC.md` | não decide premissas sozinho |
| architect | Domínio, Strategy, ACID, idempotência, ADRs e C4 | desenho, contratos e ADRs | não impõe arquitetura excessiva |
| backend | API, precificação, câmbio, liquidação e auditoria | implementação e testes | não define política financeira unilateralmente |
| frontend | Simulação e grid com filtros/paginação | interface e testes | não replica cálculo financeiro |
| devops | Compose, CI, deploy opcional e custos | configuração reproduzível | sem deploy ou gastos sem aprovação |
| reviewer | Revisão independente e Anexo A | `REVIEW.md` e achados | não altera código durante review |
| qa | Golden cases, concorrência, integração e regressão | testes e evidências | não altera expectativas para fazer testes passarem |

O agente principal distribui tarefas; o candidato aprova premissas, contratos e decisões de risco. O revisor e o QA devem confrontar o código com a especificação, não apenas confirmar que compila.

## 4. Specs/prompts estratégicos

### P1 — Análise antes de implementação

> Analise o enunciado SRM Credit Engine v2. Liste ambiguidades sobre unidade do prazo, taxa base, vigência cambial, arredondamento, liquidação e idempotência. Proponha alternativas, impactos e perguntas ao negócio. Produza um rascunho de SPEC.md com decisões marcadas como pendentes de aprovação. Não escreva código.

**Critério de validação:** cada premissa deve ser explícita, justificável e compatível com os golden cases de aferição, que possuem regras próprias.

### P2 — Motor financeiro e golden cases

> Implemente Strategy para Duplicata Mercantil e Cheque Pré-datado, separando regras de precificação de persistência e HTTP. Use BigDecimal e arredondamento HALF_EVEN somente no momento especificado. Crie testes C1, C2 e C3 do enunciado sem alterar os valores esperados. Mostre cálculos intermediários e explique escala, unidade das taxas e conversão cambial.

**Critério de validação:** C1 = BRL 92.859,94; C2 = BRL 23.337,77; C3 = USD 17.094,67, conforme a tabela fornecida; conferir também deságios e coerência dos dados de entrada. Se a fórmula e a tabela divergirem, registrar e investigar antes de ajustar implementação ou expectativas.

### P3 — Liquidação robusta

> Proponha e implemente liquidação atômica, idempotente e auditável. Inclua testes para retry da mesma chave, requisições concorrentes sobre o mesmo recebível, rollback e imutabilidade do registro. Identifique restrições de banco necessárias e efeitos de falhas do serviço de câmbio.

**Critério de validação:** evidências de testes de integração e concorrência; ausência de liquidação duplicada; nenhuma escrita parcial.

### P4 — Revisão reversa

> Faça code review do Anexo A do enunciado. Ordene problemas por severidade e, para cada achado, apresente impacto em produção, evidência no trecho e correção proposta. Não modifique o trecho analisado. Gere proposta para REVIEW.md.

**Critério de validação:** revisão humana da priorização e dos impactos; foco em SQL injection, precisão, transação, idempotência, concorrência, tratamento de falhas e semântica financeira.

### P5 — Arquitetura e defesa

> Proponha ADRs enxutos, C4 níveis 1 e 2, evolução orientada a eventos e desenho conceitual para 1 milhão de transações/minuto. Distingua claramente implementação atual de evolução hipotética. Prepare perguntas para uma alteração ao vivo de Strategy, moeda ou arredondamento.

**Critério de validação:** trade-offs claros, sem alegar que a escala projetada foi implementada ou medida.

## 5. Verificação independente

- Testes unitários e de integração executados por ferramentas, não apenas descritos pela IA.
- Golden cases preservados como oráculo externo de aferição; resultados divergentes são bloqueadores.
- Revisão humana de transações, isolamento, índices/constraints, locking e trilha de auditoria.
- PRs pequenos com motivação, evidências e alternativas rejeitadas.
- Registro dos comandos executados, seus resultados e links para commits/PRs.

## 6. Caso concreto de recomendação incorreta da IA — detectado na revisão do enunciado

**Estado da evidência:** erro real de orientação arquitetural durante o planejamento, identificado e corrigido antes da implementação. Não se trata de defeito executado, bug em produção ou teste automatizado já realizado. A evidência de código/commit e de teste deverá ser anexada após implementação.

- **Contexto e pedido:** ao definir concorrência, idempotência e limites transacionais da liquidação, a IA propôs bloqueio pessimista do recebível, além de constraint de unicidade e reserva de chave idempotente. O candidato buscava uma solução segura para o desafio e sua defesa.
- **Recomendação incorreta:** usar bloqueio pessimista (`SELECT FOR UPDATE`) para serializar liquidações simultâneas do mesmo recebível. A solução poderia evitar duplicidade, mas não demonstrava o mecanismo exigido pelo enunciado.
- **Detecção humana:** o candidato releu a seção “Sênior — operação” do enunciado e confrontou a proposta com o requisito textual: “optimistic locking na liquidação, com um teste que demonstra o conflito sendo tratado (duas liquidações simultâneas do mesmo recebível)”. Questionou explicitamente se o bloqueio pessimista prejudicaria a avaliação.
- **Correção aprovada (17/11/2026):** optimistic locking com versão no `Recebivel` (`@Version` no adaptador JPA), atualização versionada dentro da transação, rollback do perdedor e HTTP 409; manter índice `UNIQUE` em liquidação e idempotência por `(tenant, tipo_operacao, Idempotency-Key)` com hash canônico do payload.
- **Validação planejada:** teste de integração com PostgreSQL/Testcontainers e duas transações sincronizadas lendo a mesma versão, comprovando conflito otimista, exatamente uma liquidação e ausência de estado parcial. O teste ainda não foi executado neste registro.
- **Mudança no processo:** confrontar recomendações de agentes com requisitos explícitos e critérios de avaliação antes de aprová-las; incluir o requisito de optimistic locking no contrato do agente backend e na revisão QA.

**Evidências a anexar no repositório:** referência ao requisito no enunciado; decisão em `DECISIONS.md` §19; teste de integração e sua execução; commit/PR de implementação. O histórico da conversa é evidência da detecção humana, mas não substitui testes.

## 7. O que não foi delegado à IA

- Escolha final das premissas financeiras e sua justificativa perante o negócio.
- Aceitação de divergências dos golden cases e definição da política de arredondamento.
- Aprovação de mudanças em semântica de liquidação, idempotência e auditoria.
- Aceitação final de riscos de segurança, segredos, permissões e gastos de nuvem.
- Decisão sobre escopo e cortes documentados em `DECISIONS.md`.
- Defesa ao vivo e explicação/autoria do código entregue.

A IA pode sugerir opções e implementar alternativas; o candidato precisa compreender, testar e aprovar.

## 8. Registro de evidências antes da entrega

| Item | Evidência a acrescentar | Estado |
|---|---|---|
| SPEC.md revisado | commit/PR | Pendente |
| Golden cases | B03: `backend/gradlew -p backend test --no-daemon`, 7 testes da suíte de golden cases no registro B03; commit `fa6616f` | Evidência registrada em B03; revisão humana pendente |
| Idempotência/concorrência | testes e resultado | Pendente |
| REVIEW.md | commit e revisão humana | Pendente |
| ADRs/DECISIONS.md | commits e decisões | Pendente |
| Erro real de IA | recomendação pessimista identificada na revisão do enunciado; acrescentar diff/teste após implementação | Evidência de planejamento registrada; teste pendente |
| CI | URL de execução | Pendente |
| Regressão de recuperação de artefatos | seção REC-01; ZIPs da baseline v2.2 e agente frontend existente | Evidência documental registrada; testes não executados |
| Lacuna de rastreabilidade frontend/extrato | ESCOPO-01; requisitos do enunciado e atualização de TASKS.md | Identificada pelo candidato; RF01 aprovada em 18/11/2026 e registrada em RF01-APROVACAO; sem implementação/testes de código |
| Limite de uso e custo da ferramenta | OPS-01; captura da mensagem de quota do Cursor | Bloqueio real; migração posteriormente efetivada com B01–B05 no Copilot, conforme registros abaixo; valor da assinatura não verificado neste documento |

## 9. Registro verificável das interações dos agentes

Este documento registra **interações relevantes**, não transcrições integrais. Cada entrada deve ser acrescentada ao final da tarefa, com revisão do candidato. Os agentes não devem inventar ou reconstruir prompts exatos que não estejam disponíveis; nesse caso, identificar o registro como **resumo**.

### GATE-0 — revisão arquitetural antes do B01 (17/11/2026)

- **Agente:** `architect` no Cursor. **Origem:** parecer apresentado pelo candidato; a transcrição literal do prompt submetido ao Cursor não foi incorporada a este registro.
- **Objetivo resumido:** validar conformidade de enunciado, SPEC/DECISIONS, modelo físico e migrations antes da implementação B01; identificar conflitos de tenant, idempotência, locking e temporalidade.
- **Saída real informada:** parecer “aprovado com ressalvas”, com hierarquia de artefatos, verificação estática de FKs/constraints e quatro aprovações requeridas (prevalência, catálogo `tipoOperacao`, seleção temporal de taxas/cotações e FK de cotação/moeda).
- **Revisão humana registrada:** aprovada prevalência enunciado > SPEC > DECISIONS > modelo físico/migrations > referências; aprovado catálogo `CADASTRAR_CEDENTE`, `CADASTRAR_RECEBIVEL`, `REGISTRAR_COTACAO`, `SUBSTITUIR_TAXA_BASE`, `LIQUIDAR_RECEBIVEL`; hash SHA-256 de comando normalizado; instante UTC único; FK composta para cotação/moeda com constraint de referência; índice de auditoria por tenant na migration V2; separação de administração global e tenant. Política de 30 min segue premissa P0.
- **Arquivos de código alterados pelo agente:** nenhum, conforme seu parecer. **Migrations executadas:** não. **Testes executados:** não. **Commit/PR:** não informado.
- **Próxima evidência obrigatória:** B01 deve informar arquivos/diffs, comandos reais, saída das migrations em PostgreSQL de teste e testes das constraints antes de qualquer declaração de aprovação técnica.


### REC-01 — Regressão na recuperação de artefatos e auditoria do agente frontend (17/11/2026)

- **Natureza:** erro real de recuperação de contexto/artefatos e interpretação de configuração pela IA durante a fase documental; **não é defeito de código executado, incidente de produção nem falha demonstrada por teste**.
- **Contexto:** o planejamento havia alcançado o Gate 0, com parecer do agente `architect`, documentação do frontend React/TypeScript e arquivos ZIP preparados para continuar a B01. Nenhum repositório Git ou código de aplicação havia sido criado.
- **Erro verificável:** ao recuperar a última solicitação após interrupção, a IA apresentou como referência arquivos soltos `SPEC.md` v2.1 e `DECISIONS.md` sem a decisão 30, concluindo incorretamente que o pacote de planejamento estava incompleto quanto ao frontend. Essas cópias eram anteriores aos artefatos `SRM_ATUALIZACAO_PRE_B01.zip` e `SRM_PLANEJAMENTO_B01_COMPLETO.zip`.
- **Detecção humana:** o candidato identificou a ausência das decisões de frontend no material recuperado e informou que a revisão do agente de arquitetura e os ZIPs completos já haviam sido produzidos. Mais tarde esclareceu que os dois ZIPs eram os artefatos mais recentes e pediu um reinício limpo, sem herança de sessões antigas.
- **Investigação e evidência:** inspeção direta dos ZIPs existentes mostrou `SPEC.md` v2.2, `DECISIONS.md` §30, `TASKS.md` B08/F01, relatório do Gate 0 e `.cursor/agents/frontend.md`. Portanto, o agente frontend **já existia**; a deficiência real era o detalhamento insuficiente de seu contrato, agravado pela recuperação de versões antigas. Os arquivos soltos não tinham prioridade sobre o ZIP consolidado.
- **Decisão humana e correção:** manter `SRM_PLANEJAMENTO_B01_COMPLETO.zip` como **baseline documental de origem** e `SRM_ATUALIZACAO_PRE_B01.zip` como conferência; elaborar um novo pacote limpo de início de repositório preservando documentos e histórico de decisões, sem incluir sessões do Cursor, arquivos de build, commits inexistentes ou contexto de execução anterior. Fortalecer o agente frontend e fornecer `docs/arquitetura/FRONTEND.md`; usar B08 como dependência para F01. Não apagar registros autênticos de uso da IA.
- **Verificação desta correção:** listagem e comparação do conteúdo dos dois ZIPs, presença das decisões de frontend e checagem de integridade do novo pacote. **Não houve** execução de backend, frontend, migrations, testes, CI ou comandos Git na recuperação.
- **Prevenção:** (a) inventariar e comparar artefatos por nome/versão/data e conteúdo antes de declarar perda; (b) trabalhar a partir de uma baseline consolidada e imutável, com hash SHA-256; (c) verificar `SPEC.md` §6, `DECISIONS.md` §30, `.cursor/agents/frontend.md` e B08/F01 na abertura; (d) não interpretar ausência em arquivos avulsos como ausência no pacote; (e) documentar o resultado e separar decisões aprovadas de implementação testada.
- **Evidências de código/testes para anexar depois:** commits e execução efetiva de B01, B08 e F01. Não existentes no momento deste registro.

### OPS-01 — Cota gratuita esgotada antes da B01 e migração de ferramenta planejada (17/11/2026)

- **Natureza:** limitação real de acesso/cota da plataforma de agentes; **não é bug de código, falha de testes, indisponibilidade do GitHub nem comprovação de gasto financeiro efetivo**.
- **Contexto:** após concluir planejamento, revisão do Gate 0, recuperação da baseline documental e configuração do Git Flow, o candidato preparou no Cursor o prompt de execução da B01. A interface exibiu a mensagem *"You've hit your usage limit"* e ofereceu upgrade para Premium. A tarefa não chegou a executar; nenhuma migration foi aplicada ou teste rodado em decorrência desse prompt.
- **Evidência:** captura de tela apresentada pelo candidato em 17/11/2026 com o aviso de limite do Cursor. A imagem demonstra o bloqueio por cota, mas **não** demonstra cobrança, valor de assinatura contratado ou consumo exato de créditos.
- **Impacto:** impediu o início da primeira tarefa planejada, inviabilizou a execução noturna no plano gratuito e tornou necessário revisar o custo de operação das ferramentas de IA sob o prazo curto do desafio.
- **Decisão humana:** não contratar uma assinatura Premium imediatamente apenas para contornar o bloqueio; preparar migração das instruções dos agentes para GitHub Copilot no VS Code, preservando `SPEC.md`, `DECISIONS.md`, `TASKS.md`, limites dos módulos, Git Flow e histórico autêntico de IA. Suspender a execução dos agentes até que o candidato possa acompanhar e revisar os resultados.
- **Correção/prevenção:** separar arquitetura e regras permanentes do formato específico de cada IDE; conservar instruções versionáveis `.github/agents/*.agent.md` e `.github/copilot-instructions.md`; controlar consumo/cotas e validar previamente o acesso ao modelo/ferramentas, pois **Copilot Free também possui limites e pode não permitir execução intensiva de agentes**. Estabelecer checkpoints e aprovação humana antes de qualquer operação financeira/código crítico.
- **Estado da evidência:** adaptação documental preparada; execução da B01 no Copilot **não realizada**; testes, commits de implementação, PRs, builds e custos efetivamente incorridos **não verificados**. Adicionar posteriormente o plano efetivamente usado, os comandos executados e as evidências de revisão.

### B01 — Baseline V1–V3 alinhada, aplicada e constraints validadas (18/11/2026)

- **Agente / ferramenta:** `architect` (análise) e execução delegada no GitHub Copilot / VS Code, sob autorização humana.
- **Prompt relevante:** resumo — (1) atuar como architect, analisar exclusivamente a B01, comparar DECISIONS/SPEC/modelos com migrations V1–V3 e produzir plano de correção sem modificar nada; (2) autorização humana subsequente: "Pode executar a opção A, validar a escala do campo valor_pago e manter para os demais campos numeric alinhar o check manter a validade de 30 min da cotação".
- **Objetivo e limites:** aplicar a Opção A (alinhar baseline nunca aplicada); sem commit/push; sem alteração de premissas P0; validade de 30 min mantida como premissa P0.
- **Resultado produzido:**
  - `db/migration/V1__criar_estrutura_financeira.sql`: `UNIQUE (id, codigo_moeda)` em `cotacoes`; FK composta `liquidacoes (cotacao_id, codigo_moeda_pagamento) → cotacoes (id, codigo_moeda)` substituindo a FK simples; CHECKs de `taxa_base_aplicada` e `spread_aplicado` alinhados (`>= 0 AND < 1`).
  - `db/migration/V2__auditoria_e_integridade.sql`: índice `registros_auditoria_tenant_idx (tenant_id, ocorrido_em DESC)`; bloco duplicado de `COMMENT ON` removido (conjunto único mencionando redação de campos sensíveis).
  - `docs/arquitetura/MODELO_DOMINIO.md` v0.4: `Liquidacao` com `configuracaoTaxaBaseId` e `instanteReferencia`; `Cotacao` harmonizada (`codigoMoeda`, `valorEmReais`); Mermaid corrigido (id de auditoria `long`; FK composta cotação–moeda documentada).
  - `docs/arquitetura/MODELO_FISICO_POSTGRESQL.md` v0.4: FK composta e índice registrados na baseline; seção de decisões atualizada.
  - `db/validacao/validacao_b01_constraints.sql`: script re-executável com 21 verificações PASS/FAIL.
- **Revisão humana:** aprovada a Opção A (baseline nunca aplicada, conforme comentário da V1 e inventário de bootstrap); **validada a escala `numeric(20,6)` de `liquidacoes.valor_pago` (mantida)**; aprovado o alinhamento dos CHECKs de taxa no snapshot; mantida a validade de 30 min da cotação como premissa P0.
- **Execuções verificadas:** PostgreSQL 15.20 descartável (`docker run postgres:15`, porta 55432) + `docker run flyway/flyway:10 ... migrate` → "Successfully applied 3 migrations ... now at version v3"; `psql < db/validacao/validacao_b01_constraints.sql` em banco recriado do zero → **21 PASS, 0 FAIL** (histórico Flyway, FK composta cotação–moeda, isolamento por tenant, UNIQUE de liquidação, idempotência e hash, imutabilidade, redação/operador/rollback de auditoria, uso do índice por tenant no plano, taxa ativa única, CHECK de taxa, checklist de catálogo). Container removido após a validação.
- **Erro de IA detectado e corrigido durante a tarefa:** a primeira versão da verificação V08 do script capturava apenas a primeira linha do `EXPLAIN (FORMAT TEXT)` via `EXECUTE ... INTO`, gerando FAIL falso; detecção pela leitura do plano truncado ("Limit") e correção para `EXPLAIN (FORMAT JSON)`, com reexecução integral em banco limpo.
- **Evidência:** saídas de Flyway e do script registradas na sessão; artefato re-executável `db/validacao/validacao_b01_constraints.sql`. Commit/PR: **nenhum** (sem autorização de commit nesta etapa).
- **Pendências e riscos:** commit em branch `feature/b01-migrations` autorizado pelo candidato, com mensagem definida por ele (não executado por este agente); limites de taxa continuam técnicos provisórios; B02 iniciada em seguida.

### B01-ESCALA — validação da escala única `numeric(20,6)` (18/11/2026)

- **Agente / ferramenta:** revisão do agente principal (architect/backend) após edição humana direta em `db/migration/V1__criar_estrutura_financeira.sql`.
- **Prompt relevante:** citação — "Alterei manualmente os campos de valores para numeric(20,6) os campos valor_face_brl, valor_presente_brl, desagio_brl, valor_pago nas tabelas recebiveis e liquidacoes, autorizo commit porém eu decido a mensagem de commit, limites de taxa pode manter e iniciar o B02, valide a decisão no AI_USAGE.md".
- **Decisão humana:** persistir os quatro montantes em `numeric(20,6)`. Limites `taxa_mensal`/`taxa_base_aplicada`/`spread_aplicado` (`>= 0 AND < 1`) permanecem. Validade de 30 minutos da cotação não foi alterada.
- **Validação técnica:** a decisão é compatível com `SPEC.md` §2 e `DECISIONS.md` §17. `numeric(20,6)` é capacidade de armazenamento alinhada ao teto `moedas.casas_decimais BETWEEN 0 AND 6`; não autoriza calcular ou expor BRL/USD com seis casas. O domínio deve continuar arredondando com `HALF_EVEN` a 2 casas no VP BRL e no valor pago de BRL/USD antes de gravar. A igualdade `valor_face_brl = valor_presente_brl + desagio_brl` segue exata porque as três colunas têm a mesma escala. A baseline segue editável: não houve aplicação em banco compartilhado; o PostgreSQL descartável da B01 foi removido.
- **Arquivos alinhados à decisão humana:** comentários da V1, `docs/arquitetura/MODELO_FISICO_POSTGRESQL.md` e verificação V12 em `db/validacao/validacao_b01_constraints.sql`. A V1 não foi revertida.
- **Execuções verificadas nesta entrada:** conferência estática do DDL alterado pelo candidato. A reaplicação da escala foi comprovada depois, no teste `persisteMontantesEmNumeric20Escala6` da B02.
- **Commit:** autorizado, não executado aqui, porque a mensagem fica a cargo do candidato.

### B02 — bootstrap do monólito hexagonal (18/11/2026)

- **Agente / ferramenta:** backend/devops no GitHub Copilot / VS Code, após autorização para iniciar a B02.
- **Prompt relevante:** o mesmo pedido citado em B01-ESCALA, na parte "iniciar o B02".
- **Resultado produzido:** projeto Gradle Kotlin DSL em `backend/` (Spring Boot 4.1.2, Kotlin 2.3.21, Java toolchain 21, Flyway 12 via BOM), pacotes `dominio`, `aplicacao` e `adaptadores` sem fórmula financeira, `docker-compose.yml` com PostgreSQL 15 e a aplicação. Flyway lê `db/migration` por `filesystem`, não copia regra de negócio para adaptador.
- **Versões validadas no build real:** Spring Boot `4.1.2`, Kotlin `2.3.21`, Gradle Wrapper `9.1.0`, PostgreSQL 15.20. O Initializr público falhou ao resolver o BOM (`4.1.2.RELEASE`); as coordenadas sem sufixo `.RELEASE` foram confirmadas no Maven Central e no build.
- **Execuções verificadas:**
  - `JAVA_HOME` Temurin 24 apenas para iniciar o Wrapper; compilação/teste pelo toolchain 21 provisionado pelo plugin Foojay. Primeira execução falhou por ausência de JDK 21 local (só havia JRE 21 e não foi possível instalar pacote com sudo). Correção: plugin `org.gradle.toolchains.foojay-resolver-convention` 1.0.0.
  - `backend/gradlew -p backend test --no-daemon`: primeira vez 1 falha por contagem incorreta no teste (esperava 4 colunas e o esquema tem 5: face do recebível mais quatro montantes da liquidação). Erro do teste, não do esquema. Corrigido para 5 e reexecutado: `BUILD SUCCESSFUL`.
  - `backend/gradlew -p backend bootJar --no-daemon`: `BUILD SUCCESSFUL`; artefato `backend/build/libs/motor-credito.jar`.
  - `docker compose -p srm-b02 up -d --build`: imagem construída com Temurin 21; log `Successfully applied 3 migrations to schema "public", now at version v3`; `GET http://localhost:8080/actuator/health` retornou `{"status":"UP"}`. Em seguida `docker compose -p srm-b02 down -v`; nenhum container `srm-b02` permaneceu.
- **Limites mantidos:** CHECKs de taxa `< 1` e validade de 30 minutos da cotação não foram alterados. Nenhuma regra de precificação foi colocada em adaptador.
- **Commit/PR:** nenhum. A autorização de commit existe, mas a mensagem será definida pelo candidato.
- **Pendências:** ktlint/Detekt/CI permanecem na B12; Keycloak não entra neste bootstrap; revisão humana do diff antes do merge. O commit `8e6a8a9` foi criado depois desta entrada, com mensagem definida pelo candidato, e publicado em `origin/feature/b01-migrations`. Nenhum PR foi aberto.

### B01-REVALIDACAO — conferência pós-commit (18/11/2026)

- **Agente / ferramenta:** agente principal no GitHub Copilot / VS Code.
- **Prompt relevante:** citação — "pode atualizar e verificar se a B01 foi executada?"
- **Objetivo:** não aceitar só o registro anterior; reaplicar a baseline commitada e o script de constraints, e corrigir TASKS/AI_USAGE que ainda diziam "sem commit".
- **Execuções verificadas:**
  - `docker run postgres:15` (PostgreSQL 15.20), rede e container descartáveis `srm-b01-net` / `srm-b01-pg`.
  - `docker run flyway/flyway:10` (Flyway OSS 10.22.0) com `db/migration` montado: `Successfully applied 3 migrations to schema "public", now at version v3`.
  - `psql -v ON_ERROR_STOP=1 -f db/validacao/validacao_b01_constraints.sql`: saída com 24 linhas PASS (V01–V12) e nenhuma FAIL; código de saída 0. V12 confirmou os cinco montantes em `numeric(20,6)`.
  - Ambiente removido com `docker rm -f srm-b01-pg` e `docker network rm srm-b01-net`.
- **Leitura da evidência anterior:** a entrada B01 citava 21 PASS. Essa contagem é anterior à verificação V12, acrescentada depois da escala humana. A reexecução do script commitado é a evidência atual: 24 PASS, 0 FAIL.
- **B02 nesta conferência:** não reexecutada. A evidência dela continua sendo a entrada B02 (Gradle e Compose), já incluída em `8e6a8a9`.
- **Commit desta atualização documental:** ainda não criado; a mensagem fica a cargo do candidato.

### B02-REVALIDACAO — conferência antes da B03 (18/11/2026)

- **Agente / ferramenta:** agente principal no GitHub Copilot / VS Code.
- **Prompt relevante:** citação — "verifique se A b02 foi concluída e passe para a B03 caso a tarefa esteja concluida".
- **Critério conferido:** build inicia, Flyway em banco limpo e domínio/aplicação sem framework. O Compose e o health UP não foram repetidos nesta conferência; continuam registrados na entrada B02, já commitada em `8e6a8a9`.
- **Execução verificada:** `backend/gradlew -p backend test --no-daemon --tests br.com.srm.motorcredito.arquitetura.FronteiraHexagonalTest --tests br.com.srm.motorcredito.infraestrutura.MigracaoFlywayIntegracaoTest` → `BUILD SUCCESSFUL` em 17s. A suíte posterior, já com a B03, repetiu esses testes: 2 + 2, 0 falhas.
- **Conclusão:** B02 está concluída para o critério de aceite. Não há PR.

### B03 — golden cases e Strategy (18/11/2026)

- **Agente / ferramenta:** agente principal no GitHub Copilot / VS Code, após a revalidação da B02.
- **Prompt relevante:** o mesmo citado em B02-REVALIDACAO.
- **Resultado produzido:** motor de domínio em `backend/src/main/kotlin/br/com/srm/motorcredito/dominio/precificacao/`. `EstrategiaDuplicataMercantil` (spread `0.015`) e `EstrategiaChequePreDatado` (spread `0.025`) selecionam a política; a fórmula compartilhada fica em `CalculadoraValorPresente`. Intermediários usam `MathContext.DECIMAL128`. `HALF_EVEN` em 2 casas só no VP BRL e, depois disso, no valor USD. Deságio = face − VP arredondado. `prazoMeses <= 0` é rejeitado. Sem Spring, JPA ou HTTP nesse pacote.
- **Conferência independente da fórmula, antes do código:** Python `decimal` com 50 dígitos reproduziu C1 `92859.94`, C2 `23337.77` e C3 `17094.67`.
- **Execução verificada:** `backend/gradlew -p backend test --no-daemon` → `BUILD SUCCESSFUL` em 26s. Relatórios JUnit: `GoldenCasesPrecificacaoTest` 7 testes, `FronteiraHexagonalTest` 2, `MigracaoFlywayIntegracaoTest` 2; 0 falhas e 0 erros.
- **Limites:** não persiste taxa, não simula a partir de recebível cadastrado e não liquida. Isso permanece em B04–B06. A taxa base dos golden cases entra no pedido (`0.01`), não é lida do banco.
- **Commit/PR:** nenhum neste momento. O commit posterior está em B03-PUBLICACAO.

### B03-PUBLICACAO — commit e push (18/11/2026)

- **Prompt relevante:** citação — "ok pode subir a B03 para o git e executar a B04 e B05".
- **Resultado:** commit `fa6616f` com a mensagem fornecida pelo candidato, `:construction:  inicio da criação do motor de precificação`, publicado em `origin/feature/b01-migrations`. Sem PR.
- **Limite:** a mensagem foi decidida pelo candidato; o agente não a reescreveu.

### B04 e B05 — cadastro por tenant, cotação e taxa base (18/11/2026)

- **Agente / ferramenta:** agente principal no GitHub Copilot / VS Code.
- **Prompt relevante:** citação — "ok pode subir a B03 para o git e executar a B04 e B05".
- **Decisão humana anterior reaplicada:** validade inicial da cotação permanece `PT30M`; limites de taxa não foram alterados; escala monetária `numeric(20,6)` já estava na baseline.
- **Resultado produzido:** cadastro de cedente e recebível usa `tenantId` somente do `ContextoAutenticado`. O contexto atual é `ContextoAutenticadoTransiente` até a B11; não há Keycloak nesta etapa. `prazoMeses` é persistido como informado e `dataVencimento` permanece informativo. A seleção de cotação exige vigência e registro não posteriores ao instante e idade da vigência menor ou igual à validade. A substituição da taxa desativa a ativa, faz flush e insere a versão seguinte. O provedor mockado recusa chamada com transação Spring ativa. Não há cliente HTTP, timeout nem retry; isso permanece na B09.
- **Erro real de compilação:** `@JvmField` nas entidades foi rejeitado porque o plugin JPA torna as propriedades abertas. A correção foi remover o campo JVM e inserir com `versao` nula no recebível, para o Hibernate tratar a inclusão como nova. Um teste unitário também tentou instanciar a interface `ContextoAutenticado`; foi corrigido para uma implementação anônima.
- **Arquivos:** domínio `cadastro` e `cambio`; portas e casos de uso; adaptadores de persistência, contexto, configuração e provedor mockado; `application.yml`; testes de cadastro, elegibilidade e fronteira.
- **Execução verificada:** `backend/gradlew -p backend test --no-daemon` → `BUILD SUCCESSFUL` em 33s. Relatórios JUnit: 19 testes, 0 falhas e 0 erros, incluindo os 7 golden cases. PostgreSQL de teste veio do Testcontainers `postgres:15-alpine`.
- **Commit/PR:** o candidato autorizou o commit em 18/11/2026 com a mensagem exata `:construction:  Criar cadastro de cedentes/recebíveis por tenant; input prazo/vencimento e autenticação confiável e Criar o provedor de cotações mockado`. Sem push e sem PR nesta etapa.
- **Pendências:** revisão humana, push, B06 (liquidação, conflito otimista e idempotência), B09 (HTTP) e B11 (OIDC).
- **Reorganização posterior, a pedido do candidato:** os três arquivos de persistência foram separados em `persistencia/adapters`, `persistencia/entity` e `persistencia/repository`, um tipo por arquivo. Pacotes em inglês por refletirem JPA/Spring Data. `backend/gradlew -p backend test --no-daemon` → `BUILD SUCCESSFUL` em 31s. Incluída no mesmo commit.
- **Reorganização de tipos públicos (18/11/2026):** um tipo público principal por arquivo em casos de uso, portas, estratégias, comandos e resultados. Pacotes e assinaturas preservados. `Pagamento` permaneceu privado em `MotorPrecificacao`. `backend/gradlew -p backend test --no-daemon` → `BUILD SUCCESSFUL` em 29s, 19 testes, 0 falhas. Incluída no mesmo commit.

### ESCOPO-01 — Lacuna de rastreabilidade identificada na revisão de frontend/extrato (18/11/2026)

- **Natureza:** omissão/incompletude de planejamento na decomposição de tarefas sugerida com apoio de IA; não é defeito de código executado nem incidente de produção.
- **Detecção humana:** o candidato confrontou o `TASKS.md` com trechos do enunciado sobre painel do operador, simulação em tempo real, grid de transações com paginação server-side/filtros dinâmicos e extrato analítico filtrado por período, cedente e moeda. Apontou que não identificava tarefas suficientemente explícitas e que a lacuna poderia demandar mudanças no backend.
- **Evidência inicial:** o `TASKS.md` recebido continha uma B08 genérica, com “extrato paginado”, e uma F01 única, combinando formulário, simulação reativa e grid, sem critérios completos para os filtros de extrato, a query analítica e o grid. O requisito estava parcialmente representado, mas sem rastreabilidade e aceite independentes.
- **Divergência de negócio identificada:** a premissa documentada de simular apenas recebíveis cadastrados não se encaixa automaticamente na interação descrita como simulação durante o preenchimento do painel. **Nenhuma alteração de regra foi aprovada nesta etapa.**
- **Decisão de planejamento:** criar RF01 (avaliação/decisão humana sobre simulação pré-cadastro), B08-S01 (contrato de simulação no backend), B08-E01 (extrato analítico no backend) e F03 (grid filtrável e paginado no frontend); restringir B08 aos adaptadores/contratos comuns e F01 ao painel com simulação. Preservar B01–B05 e B05-R01 sem reabertura.
- **Trade-off:** manter a premissa existente até deliberação evita introduzir comportamento financeiro arbitrário; a separação em tarefas aumenta a rastreabilidade e deixa explícitas dependências e critérios de aceite para as duas camadas. Query builder/SQL nativo otimizado permanece diferencial avaliado, não obrigação indiscriminada de usar SQL nativo.
- **Artefatos documentais:** `TASKS.md` revisado e presente registro em `AI_USAGE.md`. `SPEC.md`, `DECISIONS.md`, código, migrations e testes **não foram modificados** por esta adequação.
- **Execuções e evidências:** comparação textual do enunciado e do `TASKS.md` fornecido; **nenhum agente executado, compilação, teste, commit, push ou PR realizado nesta atualização documental**. A revisão final e o versionamento desses arquivos ficam sob controle do candidato.
- **Prevenção:** adotar matriz requisito → tarefa backend/frontend → critério de aceite → teste; revisar dependências entre interface e contratos do backend antes de avançar a B08/F01; não assumir que menção genérica no backlog comprova cumprimento do enunciado.

### Modelo de entrada para as próximas tarefas (copiar e preencher apenas após executar)

```markdown
### [ID/TAREFA] — [título] ([data])
- Agente / ferramenta: ...
- Prompt relevante: [citação fiel se preservada; senão "resumo", não entre aspas]
- Objetivo e instruções/limites fornecidos: ...
- Resultado produzido: ...
- Revisão humana: o que foi aprovado, rejeitado ou corrigido e por quê
- Arquivos alterados / diff: ...
- Execuções verificadas: comando; ambiente; resultado (passou/falhou/não executado)
- Evidência: commit/PR, log ou arquivo de teste, se existente
- Pendências e riscos: ...
```

**Política de atualização:** não gravar tokens, chaves idempotentes reais, dados de clientes ou segredos. Agentes em paralelo fornecem resumos ao orquestrador; apenas um responsável edita `AI_USAGE.md` por vez, para evitar conflitos. O candidato valida cada registro.

## 10. Escopo e priorização

O enunciado fixa prazo de até cinco dias e esforço-alvo de 8–16 horas, privilegiando julgamento sobre volume. Ordem de prioridade: SPEC → golden cases/core → ACID/idempotência/auditoria → REVIEW → testes/CI → ADRs/DECISIONS e defesa → extras como Keycloak e hospedagem. Registrar em `DECISIONS.md` qualquer redução de escopo deliberada.


### RF01-APROVACAO — Simulação pré-cadastro sem persistência (18/11/2026)

- **Origem:** revisão humana do escopo e decisão expressa do candidato: **"Pode aprovar a simulação"**, após a apresentação da proposta de cálculo antes do cadastro.
- **Problema identificado:** a decisão anterior exigia `Recebivel` cadastrado para simular, embora o enunciado previsse painel com simulação do líquido em tempo real durante o preenchimento. O planejamento ESCOPO-01 havia sinalizado a divergência; a presente aprovação fecha RF01, sem modificar retroativamente o relato de detecção.
- **Decisão humana:** habilitar prévia sem persistência antes do cadastro, mantendo o cadastro obrigatório para efetivar liquidação. A simulação usa o motor financeiro do backend, não grava `recebiveis`, `liquidacoes` nem `requisicoes_idempotentes`, não reserva taxas/cotação e não exige `Idempotency-Key` por não ser escrita persistente crítica. O frontend consome o BFF, aplica debounce e evita exibir respostas obsoletas. A prévia é estimativa não vinculante, sujeita a revalidação no momento de liquidar.
- **Arquivos documentais atualizados:** `SPEC.md` v2.3, `DECISIONS.md` v1.0 (§31) e `TASKS.md` (RF01, B08-S01 e F01), além deste `AI_USAGE.md`.
- **Evidências desta etapa:** aprovação explícita e comparação documental. **Nenhum agente de implementação, teste, build, commit, push ou PR foi executado como parte desta decisão**. Comandos e resultados de B01–B05 permanecem os previamente relatados, sem nova validação.
- **Verificações futuras:** testes de simulação pré-cadastro sem inserções no banco, golden cases C1–C3, moeda/taxa elegíveis, contrato API/BFF, validação e fluxo da interface. A banca deverá distinguir prévia indicativa de liquidação efetivada.
- **Governança Git:** seleção de arquivos para commit, autoria da mensagem, commits e push permanecem sob controle do candidato.


### STAFF-DOC-01 — Rastreabilidade documental Staff/Tech Lead (20/11/2026)

> **Atualização posterior (BOOTSTRAP-02):** houve um rascunho de escala nesta etapa histórica, **mas o responsável técnico o rejeitou** por não constituir projeto arquitetural adequado. Esse rascunho foi excluído do pacote inicial; o exercício permanece pendente e independente. Os fatos abaixo registram o trabalho anterior, não validade atual do documento.

- **Natureza:** preparação assistida por IA de documentação para os critérios Staff/Tech Lead; não é implementação da arquitetura distribuída, medição de throughput nem evidência de commits no novo repositório.
- **Pedido do responsável:** garantir ADRs comparando SQL/NoSQL, monólito/serviços e síncrono/eventos; desenho de 1 milhão de transações/minuto com caching, sharding, consistência eventual e idempotência; EDA para liquidação; post-mortem independente; justificativa de Git Flow no README; PRs com explicação didática.
- **Saída documental preparada:** detalhamento de ADR-005/018/019, DECISIONS atualizado, proposta EDA, desenho de escala, seção Git no README, template e exemplo hipotético de PR e matriz de rastreabilidade. Os documentos independentes REVIEW e POST_MORTEM foram mantidos sem alterar seu conteúdo.
- **Verificação nesta preparação:** consistência de caminhos/nomes e preservação dos arquivos originais no pacote gerado; **não houve** acesso ao repositório remoto, criação de PR, execução de build, carga ou testes.
- **Revisão humana pendente:** avaliar mérito de alternativas/restrições, aceitar os documentos como baseline e anexar links de PR/CI **apenas após execução real**.
