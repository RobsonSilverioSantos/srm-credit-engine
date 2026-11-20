# DECISIONS.md — Decisões vigentes no novo repositório

**Versão:** 2.3 (bootstrap do repositório novo, 20/11/2026). O repositório anterior possui histórico técnico documentado em `AI_USAGE.md`. Esta versão é a autoridade normativa **deste novo repositório**, não uma declaração de que código foi migrado.

| ID | Decisão | Justificativa / limites |
|---|---|---|
| ADR-001 | Repositório novo, sem transportar histórico Git anterior | PRs independentes por tarefa e worktrees desde o bootstrap. O histórico antigo permanece identificado no AI_USAGE, sem falsificar implementação no novo repositório |
| ADR-002 | Git Flow simplificado: `main`, `develop`, `feature/*` e worktrees | Cada tarefa usa branch exclusiva gerada de `TASKS.md` ID + Trabalho; PR obrigatório para `develop`; só o candidato revisa e decide merge |
| ADR-003 | Agentes podem criar branch/worktree, executar testes, commitar, dar push e abrir PR | Autorização operacional explícita; mensagens descritivas e evidência. Proibidos merges, force-push, resets destrutivos, pushes diretos em `main` e `develop`, alterações de regras financeiras sem aprovação |
| ADR-004 | Kotlin + Spring Boot, Gradle Kotlin DSL, Java toolchain 21 | Tipagem estática, ecossistema financeiro, build reproduzível; hexagonal em backend e frontend |
| ADR-005 | Monólito modular + PostgreSQL/Flyway | Fronteiras claras com custo operacional menor que microserviços; migrations novas desde V1 com `organizacao_id` |
| ADR-006 | `Organizacao` define o isolamento de dados; `Cedente` é a parte que cede recebíveis | Preservar papéis distintos no domínio: a organização delimita autenticação, autorização e idempotência; cada cedente é vinculado à organização correspondente. Não introduzir entidade adicional para a parte compradora no MVP |
| ADR-007 | **Remover Keycloak do MVP**; autenticação local com Spring Security, BCrypt e token assinado | Corte consciente por restrição de prazo e capacidade de revisão; não prometer SSO, MFA ou prontidão produtiva. Organização deriva apenas do principal autenticado |
| ADR-008 | Estratégias por modalidade, `BigDecimal`, HALF_EVEN e golden cases C1–C3 | Valores oficiais e arredondamento não podem mudar para fazer testes passarem |
| ADR-009 | Simulação financeira **pré-cadastro** sem persistência | Respeita painel reativo; cadastro obrigatório antes de liquidar; preço não reservado e revalidado na liquidação |
| ADR-010 | Liquidação ACID com `@Version`, unicidade e idempotência | Identidade `(organizacaoId, tipoOperacao, chave)` e hash canônico; mesma resposta no replay, divergência 409; conflito por versão demonstrado em PostgreSQL real |
| ADR-011 | Câmbio BRL/USD com corte UTC único, snapshot e validade 30m configurável | Hipótese de demonstração P0; sem fallback silencioso e sem HTTP externo dentro da transação |
| ADR-012 | BFF e API REST chamam os mesmos casos de uso | Evitar chamadas HTTP internas e duplicação de lógica financeira; decimal em string nos contratos |
| ADR-013 | **Frontend React/TypeScript + Material UI Core** com tema e componentes definidos | Entrega consistente e rápida; evitar MUI X Pro/Premium e custos de licenças. Frontend hexagonal; apresentação sem aritmética financeira |
| ADR-014 | Extrato com filtros e paginação server-side | SQL nativo parametrizado via adaptador de leitura, inicialmente; comprovar plano/índices antes de afirmar otimização |
| ADR-015 | Evidência real em AI_USAGE.md | Preservar custo e interrupção do Cursor, mudanças de ferramenta, regressão documental, merge problemático relatado e replanejamento; sem tratar logs do repositório anterior como testes atuais |
| ADR-016 | Code review, post-mortem e exercício de alta escala são **três entregáveis independentes** | Versionar em `docs/exercicios/`, fora do `TASKS.md`; não vincular Review/Post-mortem ao backend Kotlin nem tratar a proposta de alta escala como evolução automática do MVP |
| ADR-017 | Escopo sob prazo apertado: priorizar core, BFF, extrato e frontend | Cortar Keycloak, federação, deploy cloud e refinamentos de baixa prioridade; não cortar integridade financeira, isolamento, autenticação mínima nem evidência dos golden cases |
| ADR-018 | PostgreSQL versus NoSQL para dados financeiros: PostgreSQL é a fonte de verdade | Integridade referencial, unicidade, transação ACID e consulta analítica. Ver `docs/adr/ADR-018-postgresql-vs-nosql.md`; NoSQL pode servir a read models futuros, não à autoridade da liquidação |
| ADR-019 | Operação financeira síncrona no MVP; EDA com transactional outbox como evolução | Evita inconsistência de dupla escrita banco/broker e diferencia liquidação lógica de pagamento externo. Ver `docs/adr/ADR-019-sincrono-vs-eventos.md` e `docs/arquitetura/EDA_LIQUIDACAO.md` |
| ADR-020 | **Proposta de alta escala independente — pendente** | A proposta anterior foi rejeitada; nenhum HLD está aprovado. Elaborar futuramente com o responsável técnico e ChatGPT, sem agente e sem vínculo de dependência com o MVP. `docs/exercicios/ALTA_ESCALA_STATUS.md` é apenas marcador de pendência |
| ADR-021 | PRs devem justificar escolhas e ensinar raciocínio | Modelo obrigatório com motivação, alternativas, testes executados, riscos e explicação didática; o agente publica PR, o responsável aprova/integra |
| ADR-022 | **Controles compensatórios de branches em repositório privado sem GitHub Pro** | Rulesets/branch protection não disponíveis na configuração atual (API retornou 403 exigindo GitHub Pro ou repositório público); adotar GitHub Actions para fechar PRs indevidos para `main`, hook local para recusar push direto em `main`/`develop` e instruções explícitas dos agentes. Controles preventivos parciais, não equivalentes a proteção no servidor. Ver `docs/adr/ADR-022-controles-github-free.md` |

## Critérios de arquitetura e defesa

- **ADRs completos:** este índice mantém decisões concisas; análise de opções, rejeições, consequências e gatilhos de mudança em `docs/adr/`. A decisão ADR-005 é detalhada em `docs/adr/ADR-005-monolito-modular-vs-microservicos.md`.
- **EDA:** documento de evolução opcional do fluxo do MVP; não prometer broker ou pagamento externo implementados.
- **Alta escala:** exercício arquitetural **independente e ainda não produzido**. Não confundir a futura proposta com ADRs do MVP, nem marcar como atendido o requisito de 1 milhão/min.
- **Git Flow:** escolha aprovada pelos PRs pequenos e revisáveis, custo de autonomia dos agentes e necessidade de auditoria. A justificativa versus trunk-based/GitHub Flow aparece no README; processos paralelos devem usar worktrees para isolamento.
- **Limite das proteções GitHub (ADR-022):** na configuração atual (repositório privado sem GitHub Pro), não há Ruleset aplicado. O workflow fecha PRs de origem diferente da branch `develop` apontados a `main`, quando executado; o hook `pre-push` bloqueia pushes locais para `main` e `develop` apenas onde estiver habilitado. **Nenhum desses mecanismos impede tecnicamente todo push direto ou merge na interface/API do GitHub.** A validação real do workflow e a ativação local do hook devem ser registradas após a integração. O risco residual é aceito apenas para o exercício; a proteção nativa no servidor deve ser reavaliada antes de colaboração ampliada ou produção.
- **Corte consciente:** ADR-007 e ADR-017 excluem Keycloak, federação e deploy cloud deste MVP. Autenticação mínima e invariantes financeiras permanecem exigidas; nenhum corte implica produção pronta.
- **Exercícios separados:** code review TypeScript, war room e proposta futura de alta escala permanecem em `docs/exercicios/`; nenhum deles é tarefa de desenvolvimento do `TASKS.md`. A rastreabilidade está em `docs/defesa/RASTREABILIDADE_STAFF_TECH_LEAD.md`.

## Autorizações e limites

O responsável técnico autoriza **criação da feature, worktree, commits, push da própria feature e abertura de PR para `develop`** pelos agentes, desde que o escopo, os testes e a branch sejam conferidos. A integração permanece humana. Se uma tarefa precisar modificar `SPEC.md` ou alterar regra financeira/segurança, apresentar plano e aguardar aprovação antes de implementar.

## Revisões futuras

Qualquer alteração de decisão será datada, com ID novo, motivo e riscos. Não editar retroativamente decisões históricas do projeto anterior para sugerir que eram vigentes na nova baseline.
