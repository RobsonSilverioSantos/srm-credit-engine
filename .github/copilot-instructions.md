# Instruções gerais — SRM Credit Engine

**Prioridade:** enunciado original > SPEC.md > DECISIONS.md > modelos e migrations > TASKS.md (tarefas e dependências). Evidências do ciclo anterior não comprovam a implementação vigente; consulte o histórico em `AI_USAGE.md`.

## Arquitetura e domínio

- Kotlin + Spring Boot/Gradle Kotlin DSL + PostgreSQL/Flyway; monólito modular; hexagonal também em React/TypeScript.
- **Convenção Kotlin obrigatória:** um único tipo top-level nomeado por arquivo (`class`, `data class`, `enum class`, `interface`, `fun interface`, `object` e `typealias`); nome do arquivo igual ao tipo. Funções top-level sem tipo ficam em arquivo descritivo próprio; não agrupar DTOs, exceções ou portas somente por conveniência. Tipos aninhados genuínos não exigem arquivos adicionais. A tarefa B13 consolida o legado e adiciona verificação automática. Nenhuma refatoração autoriza alterar contratos ou regras financeiras.
- Modelo em pt-BR: `Organizacao`/`organizacaoId`/`organizacao_id` define o escopo de autenticação, autorização e isolamento. `Cedente` é a entidade de negócio que cede o recebível, vinculada à organização. Não confundir cedente com organização nem criar entidade adicional para a parte compradora neste MVP.
- Auth local Spring Security/BCrypt + token assinado, segredos em ambiente, organização **somente da identidade validada**; nenhum Keycloak ou federação no MVP.
- `BigDecimal`, HALF_EVEN e golden cases C1–C3; spreads e taxas em fracões, JSON como strings. Estratégias separadas da persistência.
- Liquidação ACID e idempotência `(organizacaoId,tipoOperacao,chave)` + hash. O controle otimista usa JDBC e `UPDATE` condicional por organização, ID, versão esperada e estado `DISPONIVEL`. Exatamente uma linha atualizada permite prosseguir; zero linhas gera conflito HTTP 409. Preservar `UNIQUE` por recebível, rollback integral e ausência de `SELECT FOR UPDATE` explícito no recebível ([ADR-010](../docs/adr/ADR-010-controle-otimista-sql-versionado.md)).
- FX mockado com corte UTC, snapshot e validade 30m configurável, sem chamada HTTP dentro da transação de liquidação.
- API `/api/v1/*` e BFF `/bff/v1/*` chamam os mesmos casos de uso; simulação **pré-cadastro** sem persistência. Extrato filtrado e paginado no servidor.
- UI React + Material UI Core, design tokens/tema e componentes descritos em docs/arquitetura/FRONTEND.md; sem bibliotecas premium nem cálculo monetário no navegador.
- Testes reais, logs/métricas/auditoria e CI, sem inventar execução. Não editar migration já aplicada em ambiente compartilhado.

## Git Flow e delegação expressa

- Agent pode inspecionar Git, **criar branch/worktree, implementar, testar, fazer git add/commit, dar push na sua feature e abrir PR para develop**, sem pedir autorização adicional para cada comando não destrutivo dentro da tarefa autorizada.
- Nome `feature/<id>-<slug extraído de Trabalho>`; ler exatamente a linha do TASKS.md. Criar com `git worktree add -b feature/... <diretorio> develop`; não usar `git flow feature finish`.
- Abrir PR via `gh pr create --base develop --head feature/...`, com diffs, motivação, riscos e resultados reais. **Antes de criar, conferir explicitamente a branch base; nunca abrir PR contra `main`.** Se falhar teste ou faltar gh/autorização, comunicar.
- Respeitar o hook versionado `.githooks/pre-push` e verificar se o clone ativou `git config core.hooksPath .githooks`. Se o hook não estiver ativo, comunicar ao responsável antes do primeiro push; não contornar hooks com `--no-verify`. O workflow `.github/workflows/guard-main.yml` é apenas mitigação: GitHub Free privado ainda não dispõe de bloqueio server-side obrigatório. Consulte ADR-022.
- **Somente candidato aprova/integra PRs.** Sem merge, auto-merge, pushes em main/develop, force-push, reset/clean destrutivo, alteração de especificação financeira sem autorização, gastos ou deploy.
- Dependências **devem estar integradas em develop** antes de iniciar; conferir status e worktrees existentes. Nunca editar pasta de outro agente.
- Arquivos globais TASKS/AI_USAGE/SPEC/DECISIONS apenas por coordenação serial; evidências de tarefa no PR para consolidação depois do merge.
- Code review TypeScript, post-mortem e arquitetura de 1 milhão/min são três exercícios independentes em `docs/exercicios/`, **fora de `TASKS.md` e dos agentes**. O documento de alta escala ainda está pendente; não usar proposta anterior rejeitada.

Veja docs/arquitetura/GIT_WORKTREES.md para sequência e limites.

## Evidências Staff / Tech Lead

- Ao executar D01, revisar **somente C4, ADR-005/018/019 e EDA do MVP**; arquitetura de alta escala é exercício independente, futuro, sem agente.
- PR deve explicar motivo, alternativas, testes *realmente executados*, riscos e aprendizado para júnior conforme `.github/PULL_REQUEST_TEMPLATE.md`; exemplo editorial em `docs/engenharia/EXEMPLO_PR_DIDATICO.md` **não é PR real**.
- Não vincular os documentos independentes em `docs/exercicios/` ao código Kotlin nem os converter em tarefas `TASKS.md`.
