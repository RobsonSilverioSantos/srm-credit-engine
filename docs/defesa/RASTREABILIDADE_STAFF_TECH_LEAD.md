# Rastreabilidade — expectativas Staff / Tech Lead

**Avaliação documental de partida:** 20/11/2026. **Atualização de evidências de Git/PR:** 20/11/2026; documentos e PRs não substituem execução independente de testes. Diferenciar **arquivo existente**, **revisão humana pendente**, **implementação futura** e **evidência operacional real**. Esta tabela não declara conclusão do desenvolvimento.

| Critério do enunciado | Onde encontrar | Estado em 20/11/2026 | Evidência exigida na defesa |
|---|---|---|---|
| SQL versus NoSQL | `docs/adr/ADR-018-postgresql-vs-nosql.md` e `DECISIONS.md` | ADR redigido; validação humana pendente | alternativas, integridade, custos e critérios de revisão |
| Monólito versus microserviços | `docs/adr/ADR-005-monolito-modular-vs-microservicos.md` | ADR redigido; validação humana pendente | fronteira ACID, trade-offs e modularização |
| Síncrono versus eventos | `docs/adr/ADR-019-sincrono-vs-eventos.md` | ADR redigido; validação humana pendente | qual transação permanece síncrona e por quê |
| Proposta EDA de liquidação | `docs/arquitetura/EDA_LIQUIDACAO.md` | Proposta documental disponível; revisão humana pendente; não implementada | outbox/inbox, duplicatas, erro externo e conciliação |
| 1 milhão de transações/min | `docs/exercicios/ALTA_ESCALA_STATUS.md` | **PENDENTE: HLD ainda não produzido; versão anterior rejeitada** | futuro desenho independente com caching, shards, consistência eventual e semântica de idempotência |
| Post-mortem — Anexo B | `docs/exercicios/POST_MORTEM_ANEXO_B.md` | Exercício independente disponível; revisão humana pendente | war room, hipóteses versus evidência, contenção e prevenção |
| Code review reverso — Anexo A | `docs/exercicios/REVIEW.md` | Exercício independente disponível; revisão humana pendente | priorização, severidade, impacto e correções |
| Git Flow justificado | `README.md`, `INICIO_GIT.md`, `docs/arquitetura/GIT_WORKTREES.md` | **Comprovado em PRs reais**: [#1](https://github.com/RobsonSilverioSantos/srm-credit-engine/pull/1), [#4](https://github.com/RobsonSilverioSantos/srm-credit-engine/pull/4) e [#5](https://github.com/RobsonSilverioSantos/srm-credit-engine/pull/5) integrados em `develop`; #2 sincronização deliberada para `main` | branches reais, PRs para `develop`, revisão humana |
| Governança GitHub Free (ADR-022) | `DECISIONS.md`, `docs/adr/ADR-022-controles-github-free.md`, `.github/workflows/guard-main.yml`, `.githooks/pre-push` | **Validado operacionalmente para PRs**: [#2](https://github.com/RobsonSilverioSantos/srm-credit-engine/pull/2) permitido ([Action](https://github.com/RobsonSilverioSantos/srm-credit-engine/actions/runs/44787664195)) e [#3](https://github.com/RobsonSilverioSantos/srm-credit-engine/pull/3) fechado pelo bot ([Action](https://github.com/RobsonSilverioSantos/srm-credit-engine/actions/runs/44789563476)); hook local testado pelo responsável | risco residual sem proteção nativa de branch permanece; não equivale a bloqueio server-side |
| PRs didáticos | `.github/PULL_REQUEST_TEMPLATE.md`, `docs/engenharia/EXEMPLO_PR_DIDATICO.md` e PRs [#4](https://github.com/RobsonSilverioSantos/srm-credit-engine/pull/4) / [#5](https://github.com/RobsonSilverioSantos/srm-credit-engine/pull/5) | Dois PRs reais, integrados, com justificativas, alternativas, resultados de testes **declarados pelos agentes**, riscos e explicações para juniores; CI ainda pendente | evidência de revisão humana e conteúdo didático nos PRs |
| Cortes conscientes | `DECISIONS.md` ADR-007/017 e `AI_USAGE.md` | Decisões documentadas; execução ainda pendente | por que Keycloak/cloud foram excluídos e quais riscos restam |
| Observabilidade e C4 do MVP | `TASKS.md` B10/D01; `docs/arquitetura/C4_MVP.md` | Diagramas iniciais conceituais e tarefas pendentes | logs/métricas testados, desenho C4 validado contra código real |

**Os três exercícios independentes não integram `TASKS.md`.** O exercício de escala será feito em conversa futura entre o responsável técnico e ChatGPT, **sem ocupar agentes Cursor/Copilot**. Arquivo placeholder não atende à exigência de design.

## Checklist para a entrega

- [ ] Golden cases C1–C3 passaram em execução real.
- [ ] Liquidação concorrente, idempotência, rollback e isolamento testados com PostgreSQL real.
- [ ] CI e setup reproduzidos; README atualizado com comandos observados.
- [ ] C4 nível 1/2 validado de acordo com a implementação final.
- [ ] ADRs e proposta EDA revisados pelo responsável técnico.
- [ ] Exercício de arquitetura de alta escala elaborado, revisado e entregue (atualmente pendente).
- [x] Ao menos um PR real para `develop` ensina decisões, alternativas, testes e riscos: #4 e #5 integrados.
- [x] Gate do GitHub Actions validado com PR #3 de `feature/*` → `main` fechado automaticamente pelo bot e comentário público no PR; permissões de execução demonstradas no caso testado.
- [x] Responsável ativou `core.hooksPath=.githooks` e demonstrou recusa local em testes simulados para `main` e `develop` (sem push remoto).
- [ ] Registrar na apresentação que Actions + hook são controles compensatórios **não mandatórios no servidor**; a regra nativa continua indisponível na configuração atual.
- [ ] AI_USAGE inclui erro real de IA e como foi detectado, além de limites e custos verificáveis.

## Justificativa da decisão de governança — ADR-022

O repositório privado foi configurado com `develop` como branch padrão, `main` para releases e Git Flow simplificado para worktrees de agentes. Em 20/11/2026, a consulta à API de Rulesets retornou HTTP 403: **"Upgrade to GitHub Pro or make this repository public to enable this feature."** As branches não estavam protegidas. Em vez de publicar o código ou assumir custo adicional durante o desafio, adotam-se controles compensatórios: workflow acionado por PR para `main`, hook local de bloqueio de push em `main`/`develop` e instruções explícitas de PR para `develop`.

**Risco aceito:** o workflow atua após abertura/edição de PR e não é uma regra de admissão/merge; o hook depende de instalação em cada clone, pode ser ignorado e não atua na interface/API. Não há garantia contra pushes diretos ou merge feito antes da execução. A integração dos arquivos em `develop` e a validação posterior com PR de teste são evidências separadas — não declarar proteção efetiva antes dos testes. Se a colaboração aumentar ou houver exigência de governança produtiva, habilitar proteção nativa de branches por plano/configuração adequada.

## Evidências de integração — G00, B02 e F00

- **Baseline/documentação:** commit inicial `05b9528`; PRs de governança #1 para `develop` e #2 para `main` integrados.
- **B02:** PR [#5](https://github.com/RobsonSilverioSantos/srm-credit-engine/pull/5) integrado; build Gradle, JUnit e testes Compose/healthcheck foram **registrados no corpo do PR** pelo agente e requerem CI independente B12.
- **F00:** PR [#4](https://github.com/RobsonSilverioSantos/srm-credit-engine/pull/4) integrado; testes de componentes, lint e build foram **registrados no corpo do PR** pelo agente e requerem CI independente B12.
- **Bloqueio seguro:** PR [#3](https://github.com/RobsonSilverioSantos/srm-credit-engine/pull/3) fechado automaticamente sem merge e com falha intencional no workflow. O hook local foi testado pelo responsável para as duas branches; **não há proteção total do servidor**.
- **Pendências:** B01, B03 e B12 ainda não concluídas; **`gh auth status` local não foi comprovado diretamente**. A matriz mantém a arquitetura de 1 milhão/min como exercício independente pendente.
