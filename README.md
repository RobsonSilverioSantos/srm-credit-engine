# SRM Credit Engine

Implementação demonstrativa do SRM Credit Engine, com backend, frontend e persistência PostgreSQL. A documentação de regras, arquitetura, execução e testes está organizada neste repositório.

## Visão e stack

Motor demonstrativo de antecipação de recebíveis com **Kotlin / Spring Boot / Gradle / Java 21**, **PostgreSQL / Flyway**, **React / TypeScript / Vite / Material UI Core**. Arquitetura hexagonal e monólito modular. Regras: precificação por Strategy, golden cases C1–C3, câmbio mockado BRL/USD com vigência, liquidação ACID e idempotente, controle otimista, auditoria e extrato paginado/filtrado.

- **Organização:** limite lógico de identidade, autorização e isolamento (`Organizacao`, `organizacaoId`, `organizacao_id`).
- **Cedente:** parte que cede o recebível, vinculada à organização; não substitui a organização na autorização.
- **Autenticação de demonstração:** Spring Security, BCrypt, token assinado e verificado; sem Keycloak/OIDC/SSO no MVP. A organização não é escolhida livremente no body.
- **Frontend:** tema e componentes MUI gratuitos; pré-simulação sem persistência, depois cadastro e confirmação da liquidação; nenhuma matemática financeira executada no browser.
- **Não há desembolso bancário real.** O sistema registra a liquidação lógica e seu snapshot.

## Iniciar o sistema completo (Windows, macOS e Linux)

**Pré-requisito único para executar a aplicação:** Docker Desktop com Linux
containers e Docker Compose v2 (Windows/macOS), ou Docker Engine + Compose v2
(Linux). No Windows, habilite o backend WSL2 do Docker Desktop. É necessário
que o Docker esteja iniciado. Não é preciso instalar Java, Gradle, Node ou
PostgreSQL no computador: as imagens cuidam do build e da execução.

Esta configuração é para **demonstração local**, não para produção: o PostgreSQL
persiste em volume Docker; o backend é servido por Java 21 em contêiner e o
frontend React compilado é servido por Nginx, que encaminha `/api` e `/bff`
ao backend. O Compose usa `backend/compose.yaml` mais o overlay
`backend/compose.full.yaml`. Não executar o backend `bootRun` ou Vite ao mesmo
tempo nas mesmas portas.

### macOS e Linux (Terminal)

Na raiz do repositório:

```sh
sh scripts/start.sh
```

### Windows (PowerShell)

No PowerShell, na raiz do repositório, com Docker Desktop iniciado:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\start.ps1
```

Em PowerShell 7, também é possível executar `.\scripts\start.ps1` se a
política de execução permitir. O `-ExecutionPolicy Bypass` do exemplo acima
se aplica somente ao processo iniciado; não altera a política global.

Na primeira execução, os scripts criam `backend/.env` (ignorado pelo Git)
com senha aleatória do PostgreSQL e segredo JWT aleatório. Se o arquivo já
existir, as credenciais do banco são preservadas, e o segredo JWT só é
adicionado quando estiver ausente. **Não recrie o `.env` nem remova volumes
Docker se já houver dados**: a senha do PostgreSQL é gravada no primeiro
uso do volume e não muda ao alterar o arquivo. Para corrigir senha divergente,
consulte a seção de configuração do [backend](backend/README.md).
Não comite senhas, JWTs ou `backend/.env`.

O primeiro start baixa as imagens necessárias, compila o backend e o frontend
e aguarda os três serviços ficarem saudáveis. Acesse:

| Serviço | Endereço padrão |
|---|---|
| Frontend: operações e administração | http://localhost:5173/ |
| Saúde do backend | http://localhost:8080/actuator/health |
| OpenAPI/Swagger | http://localhost:8080/swagger-ui/index.html |
| PostgreSQL (uso local) | localhost:5432 |

A interface e o extrato são acessados pelo Nginx na porta 5173. O frontend
não deve conectar diretamente ao `localhost:8080` do navegador de outro
computador: as chamadas relativas `/api` e `/bff` passam pelo Nginx e chegam
ao contêiner `app`. O acesso ao backend e ao PostgreSQL permanece restrito à
interface loopback do computador hospedeiro.

### Primeiro usuário MASTER

No primeiro start, a organização de **demonstração** é criada de forma
explícita pelo perfil `demo` (ID
`00000000-0000-4000-8000-000000000001`). **Nenhum usuário é criado
automaticamente.** Para criar o primeiro MASTER, com os três serviços ativos,
use um terminal interativo:

- macOS/Linux: `sh scripts/bootstrap-master.sh`
- Windows PowerShell: `.\scripts\bootstrap-master.ps1` (ou
  `powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\bootstrap-master.ps1`).

O comando executa o **JAR dentro de um contêiner com TTY**, não
`gradlew bootRun`, e solicita UUID de organização, e-mail, senha
(12 a 72 bytes UTF-8) e confirmação, sem exibir a senha.
Se o UUID, o e-mail, o tamanho da senha ou a confirmação estiverem inválidos,
o terminal informa a restrição e permite corrigir o campo sem reiniciar
o provisionamento. A senha não é registrada nos logs nem em argumentos.

Use o UUID demonstrativo acima e aguarde a mensagem `MASTER provisionado.`
antes de tentar entrar. Só é permitido um MASTER por organização; se ele
já existir, não tente reprovisioná-lo nem apagar os dados.
Após atualizar o código do provisionamento, reconstrua os contêineres
com o comando normal de inicialização antes de executá-lo.
Depois, entre pela interface principal para criar ADMIN e OPERADOR,
conforme a hierarquia S02/F04.

### Taxas financeiras de demonstração

O ambiente completo iniciado pelos scripts ativa, **somente no perfil `demo`**,
a carga opt-in `SRM_DEMO_BOOTSTRAP_TAXAS=true`. No primeiro start com banco
sem taxas elegíveis, a aplicação grava os valores de referência do
[enunciado original](docs/requisitos/ENUNCIADO_ORIGINAL_SRM_CREDIT_ENGINE_V2.md):

| Histórico financeiro | Valor inicial |
|---|---|
| Taxa base mensal | **1,00% a.m.** (`0.01`) |
| Spread de duplicata mercantil | **1,50% a.m.** (`0.015`) |
| Spread de cheque pré-datado | **2,50% a.m.** (`0.025`) |
| Cotação ilustrativa BRL por USD, golden case C3 | **5,4321** |

A taxa base e os spreads ficam vigentes até que novas versões sejam
registradas. **A cotação C3 é fictícia**, não representa mercado real e expira
em **30 minutos** por padrão (`srm.cambio.validade`). Se não houver cotação
ainda válida, uma nova versão fictícia será criada no próximo início do
backend em modo demo; a execução em andamento não renova cotações
automaticamente. Simulações em BRL continuam funcionando após a expiração da
cotação. Para testar USD após o vencimento, reinicie os serviços da aplicação
com `sh scripts/start.sh restart` (`.\scripts\start.ps1 restart` no Windows),
ou registre uma nova cotação conforme o
[guia backend](backend/README.md). Não use esses valores em produção.

O bootstrap **não sobrescreve** taxas ou spreads vigentes já cadastrados,
não edita históricos nem apaga liquidações. A carga é idempotente: iniciar a
aplicação novamente não duplica valores elegíveis. A execução somente do
backend, sem o overlay `compose.full.yaml`, continua sem carga automática.


### Operação e diagnóstico

| Ação | macOS/Linux | Windows PowerShell |
|---|---|---|
| Iniciar/reconstruir | `sh scripts/start.sh` | `.\scripts\start.ps1` |
| Reiniciar backend e frontend (sem rebuild) | `sh scripts/start.sh restart` | `.\scripts\start.ps1 restart` |
| Ver status | `sh scripts/start.sh status` | `.\scripts\start.ps1 status` |
| Ver logs | `sh scripts/start.sh logs` | `.\scripts\start.ps1 logs` |
| Parar, preservando dados | `sh scripts/start.sh stop` | `.\scripts\start.ps1 stop` |

**`restart`** reinicia somente os contêineres `app` e `frontend`: mantém o
PostgreSQL e seus volumes intactos e **não recompila imagens nem aplica mudanças
na configuração dos contêineres**. Para carregar novo código, alterar Compose
ou atualizar variáveis de ambiente, execute **`start`**, que reconstrói e
reconcilia a stack via `docker compose up -d --build --wait`. Em ambos os casos,
aguarde a aplicação ficar saudável antes de fazer novas operações.

As portas opcionais `SRM_FRONTEND_PORT` (5173), `SRM_APP_PORT` (8080) e
`SRM_DB_PORT` (5432) podem ser definidas em `backend/.env`, caso estejam
ocupadas. Após alterá-las, execute **`start`** para recriar os contêineres com
as novas configurações. Se o banco usar volume existente, mantenha seu usuário
e senha originais.

Para demonstrar **no celular da mesma rede**, configure
`SRM_FRONTEND_BIND=0.0.0.0` no `backend/.env` e reinicie pelo script.
Acesse `http://IP_DO_COMPUTADOR:5173/` (ou a porta configurada), permitindo a
porta no firewall local se necessário. Restrinja-se a Wi-Fi privado e
dados fictícios: este modo usa **HTTP sem TLS**, não deve ficar exposto à
internet. A sessão do extrato ainda é independente da sessão do painel.

Na falha de inicialização, examine os logs e o status com os comandos acima.
Confira primeiro Docker ativo, portas disponíveis e credenciais do banco.
O workflow de CI inclui um smoke test Docker Compose para validar os três
contêineres, healthchecks e o proxy HTTP. Esse smoke test **não substitui**
provisionamento manual, integração financeira ponta a ponta ou testes em
celular real.

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

Use JDK 21. O build local verifica código e fronteiras hexagonais. Para incluir os testes de integração PostgreSQL, execute `bash scripts/test-cadastro-postgres.sh` na pasta `backend`. A CI utiliza esse script e também possui um job de smoke test dos três contêineres Docker Compose; nenhum desses testes substitui a validação manual de ponta a ponta.

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

**Aferição manual adicional em 21/11/2026:** o responsável técnico confirmou que
**C1 (R$ 92.859,94), C2 (R$ 23.337,77) e C3 (US$ 17.094,67)**
coincidiram com o enunciado até os centavos na **pré-simulação pela interface**.
C1 e C2 são **relatos de conferência humana** sem captura individual versionada;
C3 foi conferido também em **captura visual compartilhada**, com valor presente
BRL R$ 92.859,94, deságio R$ 7.140,06 e câmbio de demonstração 5,4321 BRL/USD.
Ver [registro de validação dos golden cases](docs/validacao/VALIDACAO_GOLDEN_CASES_C1_C2_C3_2026-11-21.md)
e [AI_USAGE.md](AI_USAGE.md#AFERICAO-MANUAL-01--golden-cases-c1-c2-e-c3-pela-interface-21112026).
Esta aferição é distinta da regressão automatizada D03 e **não comprova
liquidação bancária nem homologação de produção**.

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
foi alterado em resposta; a causa da falha remota não foi estabelecida. No commit
documental `cbdbd20`, os dois jobs passaram ([execução #44850079836](https://github.com/RobsonSilverioSantos/srm-credit-engine/actions/runs/44850079836));
o PR #29 foi posteriormente integrado com os dois checks aprovados. As falhas
anteriores permanecem registradas como histórico.
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

## Documentação de arquitetura do MVP

As decisões arquiteturais do sistema estão registradas nos documentos em `docs/adr/`, no [modelo C4](docs/arquitetura/C4_MVP.md) e na [proposta EDA para liquidação](docs/arquitetura/EDA_LIQUIDACAO.md). Esses materiais descrevem escolhas, restrições e possibilidades de evolução da implementação atual, sem representar funcionalidades adicionais executadas.

## Exercícios independentes de avaliação Staff / Tech Lead

Os materiais avulsos estão em `docs/exercicios/` e **não descrevem funcionalidades implantadas no MVP**:

- [HLD de alta escala — 1 milhão de transações/minuto](docs/exercicios/HLD_ALTA_ESCALA_1M_TPM.md): arquitetura proposta, com [diagramas SVG e fontes Mermaid](docs/exercicios/diagramas/); meta ainda não comprovada por benchmark.
- [Code review reverso — Anexo A](docs/exercicios/REVIEW.md): parecer técnico com achados e decisão `REQUEST CHANGES` sobre código fornecido para o exercício.
- [Post-mortem — Anexo B](docs/exercicios/POST_MORTEM_ANEXO_B.md): simulação de incidente, hipóteses e plano de resposta; não representa incidente real.
- [Rastreabilidade Staff/Tech Lead](docs/defesa/RASTREABILIDADE_STAFF_TECH_LEAD.md): evidências, ressalvas e roteiro de defesa.

## Por que Git Flow simplificado nesta entrega

**Estratégia:** `main` representa versões deliberadamente liberadas; `develop` integra features revisadas; cada ID do `TASKS.md` produz `feature/<id>-<slug-do-trabalho>` em **worktree independente**. Os agentes são autorizados a fazer commit, push e abrir PR **sempre para `develop`**; **apenas o responsável técnico revisa e decide merge**. O trabalho de um agente não aparece automaticamente na branch de outro: dependências precisam estar integradas antes de iniciar uma tarefa.

- **Trunk-based:** excelente em equipes maduras com testes e feature flags, porém sessões paralelas de agentes sem gates humanos trariam maior risco de misturar alterações e dificultariam evidenciar cada decisão durante o exercício.
- **GitHub Flow:** válido e mais leve, mas não separa `main` de uma área explícita de integração; aqui `develop` permite validar backend, frontend e migrações antes da liberação final.
- **Custo da escolha:** mais branches e PRs. Evitar long-lived features e reuniões desnecessárias; não usar `git flow feature finish` (que faz merge local) nem obrigar `release/*` e `hotfix/*` neste prazo.
- **PR didático:** cada PR deve explicar motivação, responsabilidades, alternativas, testes realizados, riscos e um ponto de aprendizado para um júnior. Template em `.github/PULL_REQUEST_TEMPLATE.md`; exemplo **fictício** em `docs/engenharia/EXEMPLO_PR_DIDATICO.md`.
- **Review do próprio autor:** em um repositório individual onde a mesma identidade abre o PR, o GitHub não permite aprovação formal do próprio PR. Fazer a revisão humana do diff e executar o merge manualmente; **não ativar requisito de aprovação por terceiro que não existe**. Verificar disponibilidade das proteções conforme o plano GitHub e configuração do repositório.


### Proteção compensatória de branches — restrição de plano (ADR-022)

O repositório é privado e a conta atualmente não dispõe de Rulesets para esta configuração: uma consulta de 20/11/2026 à API retornou HTTP 403 com a orientação de usar GitHub Pro ou tornar o repositório público. **O repositório permanece privado, sem contratação adicional de proteção para branches.**

- `.github/workflows/guard-main.yml`: ao detectar PR para `main`, permite somente origem `develop` do mesmo repositório; fecha outras origens automaticamente, quando GitHub Actions estiver executando e autorizado.
- `.githooks/pre-push`: impede pushes locais diretamente para `main` e `develop`, desde que o clone tenha executado `git config core.hooksPath .githooks`. Hooks não se ativam automaticamente com clone/pull.
- Instruções dos agentes: apenas push em `feature/*`, PR com base `develop`, jamais merge/automerge. O responsável técnico revisa e integra manualmente.
- **Limitação aceita:** sem Ruleset/branch protection no servidor, esses controles não impedem todos os pushes/merges por CLI, interface ou API. O workflow pode executar depois da tentativa de merge, e hooks podem ser contornados. Não descrever como bloqueio absoluto.
- **Evidências de governança:** [PR #1](https://github.com/RobsonSilverioSantos/srm-credit-engine/pull/1) e [PR #2](https://github.com/RobsonSilverioSantos/srm-credit-engine/pull/2) integraram a configuração inicial; o [PR de teste #3](https://github.com/RobsonSilverioSantos/srm-credit-engine/pull/3), direcionado indevidamente a `main`, foi fechado automaticamente pelo workflow. A recusa do hook local foi registrada na baseline. A ausência de Ruleset no servidor continua sendo limitação documentada em `docs/adr/ADR-022-controles-github-free.md`.

## Agentes e governança

- Cursor: `.cursor/agents/` e `.cursor/rules/srm-standards.mdc`.
- Copilot: `.github/agents/` e `.github/copilot-instructions.md`.
- Mantêm-se os sete papéis: analyst, architect, backend, frontend, devops, qa, reviewer. Eles trabalham **somente no escopo de tarefas autorizado**; revisão de PR/merge e decisões financeiras são humanas.
- Não modificar simultaneamente `SPEC.md`, `DECISIONS.md`, `TASKS.md` e `AI_USAGE.md` em vários worktrees; centralizar mudanças documentais normativas após PR.

## Defesa Staff / Tech Lead

ADRs: `docs/adr/ADR-005-*.md`, `ADR-018-*.md` e `ADR-019-*.md`; EDA: `docs/arquitetura/EDA_LIQUIDACAO.md`; matriz de rastreabilidade: `docs/defesa/RASTREABILIDADE_STAFF_TECH_LEAD.md`. Não há benchmark de escala executado. A ausência de Keycloak e de deploy em nuvem integra os limites do MVP, registrados em `DECISIONS.md`.

**Bootstrap:** este repositório já foi criado e versionado. Para reproduzir a
configuração inicial em outro repositório, consulte [`INICIO_GIT.md`](INICIO_GIT.md);
para executar este checkout, use os guias do backend e do frontend acima.

