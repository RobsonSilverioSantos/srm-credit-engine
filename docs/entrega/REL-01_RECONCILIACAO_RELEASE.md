# REL-01 — Reconciliação de histórico e preparação de entrega

**Data:** 23/11/2026  
**Repositório:** `RobsonSilverioSantos/srm-credit-engine`  
**Origem de release:** `develop`  
**Destino de release:** `main` (integração exclusivamente humana)

## Objetivo

Restabelecer a ancestralidade compartilhada das branches sem apagar, sobrescrever ou reescrever o histórico da `main` ou da `develop`. A presente reconciliação prepara o PR de release para revisão e merge humano.

## Divergência identificada

- `main` apontava para `5e7a87bf551a26ee7dfebd5e6cf7a1a127f0fce1` (merge inaugural do PR #2, governança G00).
- `develop` apontava para `8aa4564aaac2fa38ad34685dcc548da473cd9060`, com 81 commits à frente e um commit exclusivo na `main`.
- Nove arquivos alterados na `main` foram conferidos nas duas branches. Os arquivos `.githooks/pre-push`, `.github/workflows/guard-main.yml`, `INICIO_GIT.md`, `docs/adr/ADR-022-controles-github-free.md` e `docs/arquitetura/GIT_WORKTREES.md` tinham conteúdo idêntico nas duas pontas.
- `.github/copilot-instructions.md`, `DECISIONS.md`, `README.md` e `docs/defesa/RASTREABILIDADE_STAFF_TECH_LEAD.md` já haviam evoluído na `develop`; prevalece o conteúdo mais recente e integrado nela.
- A reconciliação é um **commit de integração de dois ancestrais** com o conteúdo da `develop` preservado, acrescido somente deste registro de entrega. Não se trata de cherry-pick, rebase, force-push ou sobrescrita da branch `main`.

## Conteúdo funcional

MVP de motor de recebíveis em Kotlin/Java 21, Spring Boot, PostgreSQL/Flyway e React/TypeScript/MUI, com arquitetura hexagonal, precificação por Strategy, autenticação/JWT, câmbio PTAX/MANUAL/DEMO conforme política versionada, liquidação ACID idempotente, concorrência otimista, auditoria e extrato. As tarefas do backlog constam como 37/37 integradas/operacionais em `TASKS.md`; isso não equivale a certificação para produção.

## Evidência adicional de homologação local

- B16: k6 1.6.1 com **50/50 verificações aprovadas** em PostgreSQL QA, corrida de cadastro com respostas `201` e `409`, replay idempotente e cadastro posterior das referências exclusivas.
- Verificação independente via SQL: **396 registros**, **396 referências distintas**, **396 recebíveis distintos**; lotes de 200 e 196.
- B17: prévia inicial do lote de 200 aprovada com 159 títulos BRL e 41 USD, totais por moeda e hash. Em tentativa posterior, os 41 USD ficaram indisponíveis com o vencimento dos dados DEMO (prazo padrão de 30 minutos): requisição de confirmação não enviada, nenhuma liquidação realizada nesse cenário. A regra PTAX de 96h é distinta da janela da DEMO.
- A primeira execução k6 registrou verificações no console, mas não conseguiu salvar o resumo JSON por permissão do volume Docker. Não atribuir evidência de artefato inexistente.

## Pendências e limites

- A liquidação concorrente B17 em ambiente de homologação integrado ainda não foi reproduzida nesta rodada; permanecem os testes de integração PostgreSQL versionados e a validação funcional complementar.
- A navegação ponta a ponta, cenários de falha, segurança operacional e acessibilidade em dispositivos reais exigem homologação adicional.
- A meta arquitetural de 1 milhão de transações/minuto é proposta de desenho, não benchmark.
- A demonstração não transfere dinheiro real e não é declaração de prontidão para produção.

## Revisão antes da entrega

1. Revisar o PR de reconciliação `feature/rel-01-reconciliar-main-develop` → `develop`, incluindo seu commit de dois pais.
2. Verificar CI e realizar **merge humano** do PR de reconciliação.
3. Aguardar atualização da origem do PR de release `develop` → `main` e verificar que está sem conflitos.
4. Rever diff e checks do PR de release e realizar **merge humano** para `main`.
5. Compartilhar acesso privado com avaliadores usando permissões mínimas adequadas, sem tornar o repositório público.
