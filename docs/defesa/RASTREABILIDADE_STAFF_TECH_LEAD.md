# Rastreabilidade — expectativas Staff / Tech Lead

**Avaliação documental de partida:** 20/11/2026. Diferenciar **arquivo existente**, **revisão humana pendente**, **implementação futura** e **evidência operacional real**. Esta tabela não declara conclusão do desenvolvimento.

| Critério do enunciado | Onde encontrar | Estado em 20/11/2026 | Evidência exigida na defesa |
|---|---|---|---|
| SQL versus NoSQL | `docs/adr/ADR-018-postgresql-vs-nosql.md` e `DECISIONS.md` | ADR redigido; validação humana pendente | alternativas, integridade, custos e critérios de revisão |
| Monólito versus microserviços | `docs/adr/ADR-005-monolito-modular-vs-microservicos.md` | ADR redigido; validação humana pendente | fronteira ACID, trade-offs e modularização |
| Síncrono versus eventos | `docs/adr/ADR-019-sincrono-vs-eventos.md` | ADR redigido; validação humana pendente | qual transação permanece síncrona e por quê |
| Proposta EDA de liquidação | `docs/arquitetura/EDA_LIQUIDACAO.md` | Proposta documental disponível; revisão humana pendente; não implementada | outbox/inbox, duplicatas, erro externo e conciliação |
| 1 milhão de transações/min | `docs/exercicios/ALTA_ESCALA_STATUS.md` | **PENDENTE: HLD ainda não produzido; versão anterior rejeitada** | futuro desenho independente com caching, shards, consistência eventual e semântica de idempotência |
| Post-mortem — Anexo B | `docs/exercicios/POST_MORTEM_ANEXO_B.md` | Exercício independente disponível; revisão humana pendente | war room, hipóteses versus evidência, contenção e prevenção |
| Code review reverso — Anexo A | `docs/exercicios/REVIEW.md` | Exercício independente disponível; revisão humana pendente | priorização, severidade, impacto e correções |
| Git Flow justificado | `README.md`, `INICIO_GIT.md`, `docs/arquitetura/GIT_WORKTREES.md` | Processo documentado; nenhum PR novo observado | branches reais, PRs para `develop`, revisão humana |
| Governança GitHub Free (ADR-022) | `DECISIONS.md`, `docs/adr/ADR-022-controles-github-free.md`, `.github/workflows/guard-main.yml`, `.githooks/pre-push` | Controles propostos em PR; ainda dependem de merge, execução do workflow e ativação local do hook | registro do HTTP 403, captura de PR de teste `feature/*` → `main` fechado, push local recusado e risco residual sem Ruleset |
| PRs didáticos | `.github/PULL_REQUEST_TEMPLATE.md`, `docs/engenharia/EXEMPLO_PR_DIDATICO.md` | Template e exemplo fictício disponíveis; PR real pendente | ao menos um PR real com justificativas, testes, riscos e aprendizado |
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
- [ ] Ao menos um PR real para `develop` ensina decisões, alternativas, testes e riscos.
- [ ] Validar o gate do GitHub Actions com um PR de teste `feature/*` → `main` e registrar fechamento automático; confirmar permissões do `GITHUB_TOKEN`.
- [ ] Ativar `core.hooksPath=.githooks` no clone local e comprovar recusa de push para `main`/`develop`, sem afetar o remoto.
- [ ] Registrar na apresentação que Actions + hook são controles compensatórios **não mandatórios no servidor**; a regra nativa continua indisponível na configuração atual.
- [ ] AI_USAGE inclui erro real de IA e como foi detectado, além de limites e custos verificáveis.

## Justificativa da decisão de governança — ADR-022

O repositório privado foi configurado com `develop` como branch padrão, `main` para releases e Git Flow simplificado para worktrees de agentes. Em 20/11/2026, a consulta à API de Rulesets retornou HTTP 403: **"Upgrade to GitHub Pro or make this repository public to enable this feature."** As branches não estavam protegidas. Em vez de publicar o código ou assumir custo adicional durante o desafio, adotam-se controles compensatórios: workflow acionado por PR para `main`, hook local de bloqueio de push em `main`/`develop` e instruções explícitas de PR para `develop`.

**Risco aceito:** o workflow atua após abertura/edição de PR e não é uma regra de admissão/merge; o hook depende de instalação em cada clone, pode ser ignorado e não atua na interface/API. Não há garantia contra pushes diretos ou merge feito antes da execução. A integração dos arquivos em `develop` e a validação posterior com PR de teste são evidências separadas — não declarar proteção efetiva antes dos testes. Se a colaboração aumentar ou houver exigência de governança produtiva, habilitar proteção nativa de branches por plano/configuração adequada.
