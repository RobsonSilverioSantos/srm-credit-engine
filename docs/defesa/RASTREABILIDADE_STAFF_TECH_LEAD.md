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

- [x] Golden cases C1–C3 passaram na regressão backend local com PostgreSQL descartável; ver `AI_USAGE.md` — D03.
- [x] Liquidação concorrente, idempotência, rollback e isolamento incluídos na regressão local com PostgreSQL real e na evidência do PR [#19](https://github.com/RobsonSilverioSantos/srm-credit-engine/pull/19).
- [x] Setup e checks locais reproduzidos; CI remota dos PRs [#7](https://github.com/RobsonSilverioSantos/srm-credit-engine/pull/7), [#25](https://github.com/RobsonSilverioSantos/srm-credit-engine/pull/25), [#26](https://github.com/RobsonSilverioSantos/srm-credit-engine/pull/26) e [#29](https://github.com/RobsonSilverioSantos/srm-credit-engine/pull/29) passou. No commit `6a61fda`, o job frontend falhou na primeira execução e passou em uma repetição; causa não estabelecida. Reconfirmar o CI do head mais recente.
- [ ] C4 nível 1/2 validado de acordo com a implementação final.
- [ ] ADRs e proposta EDA revisados pelo responsável técnico.
- [ ] Exercício de arquitetura de alta escala elaborado, revisado e entregue (atualmente pendente).
- [x] Ao menos um PR real para `develop` ensina decisões, alternativas, testes e riscos: #4 e #5 integrados.
- [x] Gate do GitHub Actions validado com PR #3 de `feature/*` → `main` fechado automaticamente pelo bot e comentário público no PR; permissões de execução demonstradas no caso testado.
- [x] Responsável ativou `core.hooksPath=.githooks` e demonstrou recusa local em testes simulados para `main` e `develop` (sem push remoto).
- [x] O roteiro abaixo registra que Actions + hook são controles compensatórios **não mandatórios no servidor**; a regra nativa continua indisponível na configuração atual.
- [x] `AI_USAGE.md` registra a recomendação arquitetural incorreta histórica da IA e sua detecção, separada do erro operacional D03, além do limite de uso do Cursor e da ausência de valores de assinatura/custos verificados.

## Roteiro de defesa técnica — evidência, demonstração e limites

1. **Delimitar o que está sendo defendido.** Apresentar `SPEC.md` e `DECISIONS.md`: trata-se de MVP demonstrativo, sem transferência bancária, com premissas financeiras ainda não homologadas pelo negócio. Não apresentar o C4 como arquitetura de alta escala.
2. **Demonstrar precisão financeira.** Mostrar os testes C1–C3 e seus valores esperados. Explicar que o frontend exibe valores do backend e não calcula montantes. A evidência local da regressão backend está registrada em `AI_USAGE.md`; ela não homologa as premissas de negócio.
3. **Explicar a simulação pré-cadastro.** Percorrer o BFF/API e os testes de [B08-S01 #23](https://github.com/RobsonSilverioSantos/srm-credit-engine/pull/23): a prévia é indicativa, não persiste recebível/liquidação e não reserva taxa/câmbio.
4. **Provar integridade transacional.** Executar `cd backend && bash scripts/test-cadastro-postgres.sh` em ambiente local com Docker. A execução D03 reportou 70 testes, sem falhas, erros ou ignorados; o banco temporário é removido pelo script. Usar [B07 #19](https://github.com/RobsonSilverioSantos/srm-credit-engine/pull/19) para localizar os cenários de concorrência, replay idempotente, rollback e isolamento. Distinguir a regressão atualmente reexecutada do relato original do PR.
5. **Apresentar contratos e extrato.** Relacionar BFF/API e OpenAPI ao [B08 #20](https://github.com/RobsonSilverioSantos/srm-credit-engine/pull/20), e filtros/paginação server-side aos [B08-E01 #22](https://github.com/RobsonSilverioSantos/srm-credit-engine/pull/22) e [F03 #25](https://github.com/RobsonSilverioSantos/srm-credit-engine/pull/25). Os filtros de datas seguem dias inclusivos em UTC; paginação por offset pode deslocar registros entre consultas.
6. **Não prometer integração de UX ainda ausente.** F01 e F03 estão em PRs distintos ([#26](https://github.com/RobsonSilverioSantos/srm-credit-engine/pull/26) e [#25](https://github.com/RobsonSilverioSantos/srm-credit-engine/pull/25)). O frontend mantém entradas e autenticação independentes; não alegar navegação ou sessão compartilhada nem demonstrar como fluxo único sem validação adicional.
7. **Separar testes locais, CI e revisão.** Mostrar `AI_USAGE.md` para comandos e resultados locais e os checks dos PRs para execução remota. Os merges foram feitos pelo responsável técnico; isso registra intervenção humana, mas não equivale a teste manual ponta a ponta ou validação de negócio. O commit inicial D03 passou em [#44848917116](https://github.com/RobsonSilverioSantos/srm-credit-engine/actions/runs/44848917116); no commit seguinte, um teste frontend falhou uma vez e passou ao repetir o job ([#44849306596](https://github.com/RobsonSilverioSantos/srm-credit-engine/actions/runs/44849306596)). A causa não foi estabelecida e o CI do head mais recente deve ser conferido.
8. **Declarar custos e limitações.** Nenhum deploy, pagamento real, benchmark de desempenho ou custo monetário de assinatura foi comprovado nesta etapa. O teste de PostgreSQL foi efêmero; o aviso de cota do Cursor não prova cobrança. Actions e hook local não são proteção server-side mandatória, como detalha ADR-022.

## Justificativa da decisão de governança — ADR-022

O repositório privado foi configurado com `develop` como branch padrão, `main` para releases e Git Flow simplificado para worktrees de agentes. Em 20/11/2026, a consulta à API de Rulesets retornou HTTP 403: **"Upgrade to GitHub Pro or make this repository public to enable this feature."** As branches não estavam protegidas. Em vez de publicar o código ou assumir custo adicional durante o desafio, adotam-se controles compensatórios: workflow acionado por PR para `main`, hook local de bloqueio de push em `main`/`develop` e instruções explícitas de PR para `develop`.

**Risco aceito:** o workflow atua após abertura/edição de PR e não é uma regra de admissão/merge; o hook depende de instalação em cada clone, pode ser ignorado e não atua na interface/API. Não há garantia contra pushes diretos ou merge feito antes da execução. A integração dos arquivos em `develop` e a validação posterior com PR de teste são evidências separadas — não declarar proteção efetiva antes dos testes. Se a colaboração aumentar ou houver exigência de governança produtiva, habilitar proteção nativa de branches por plano/configuração adequada.

## Evidências de integração — G00, B02 e F00

- **Baseline/documentação:** commit inicial `05b9528`; PRs de governança #1 para `develop` e #2 para `main` integrados.
- **B02:** PR [#5](https://github.com/RobsonSilverioSantos/srm-credit-engine/pull/5) integrado; build Gradle, JUnit e testes Compose/healthcheck foram **registrados no corpo do PR** pelo agente e requerem CI independente B12.
- **F00:** PR [#4](https://github.com/RobsonSilverioSantos/srm-credit-engine/pull/4) integrado; testes de componentes, lint e build foram **registrados no corpo do PR** pelo agente e requerem CI independente B12.
- **Bloqueio seguro:** PR [#3](https://github.com/RobsonSilverioSantos/srm-credit-engine/pull/3) fechado automaticamente sem merge e com falha intencional no workflow. O hook local foi testado pelo responsável para as duas branches; **não há proteção total do servidor**.
- **Pendências:** B01, B03 e B12 ainda não concluídas; **`gh auth status` local não foi comprovado diretamente**. A matriz mantém a arquitetura de 1 milhão/min como exercício independente pendente.
