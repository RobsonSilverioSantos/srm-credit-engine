# Protocolo Git Flow e worktrees

**Fronteira de autorização:** agentes podem criar feature/worktree, editar código, testar, commitar, push da sua própria feature e abrir PR para `develop`. O candidato é o único responsável por revisar e integrar PRs. `main` recebe apenas release deliberada.

## Nomenclatura determinística

De `TASKS.md`: `feature/<ID normalizado>-<resumo do campo Trabalho em kebab-case>`. Remover acentos, pontuação e conectivos, limitar a ~70 caracteres com ID preservado, evitar colisões. Informar a descrição original e o slug escolhido. Não renomear tarefas do backlog apenas para gerar branch.

## Gate operacional

1. Verificar dependências integradas em `develop` e worktree/branch existentes; ambiente autenticado (`gh auth status`). Não basear uma tarefa em código somente pendente de PR sem aprovação.
2. Criar worktree exclusivo com `git worktree add -b feature/... ../srm-<id> develop`.
3. Trabalhar **dentro** do novo diretório, não na pasta original. Um agente não edita o worktree de outro.
4. Rodar testes e apresentar resultado real; selecionar arquivos específicos; commit descritivo com ID e mensagem explicando o porquê, não só o quê.
5. Dar push apenas em `feature/*`. Abrir PR com `gh pr create --base develop --head feature/...` contendo: objetivo, motivação, contratos alterados, testes, evidências, riscos e checklist. Se `gh` não estiver instalado/autenticado, parar e comunicar.
6. Esperar revisão e merge humanos. Não encerrar a feature via `git flow feature finish`; não excluir worktrees/branches sem autorização.

**Nunca:** force push, checkout de outra tarefa no mesmo worktree, commits que misturem tarefas, atualização de `main`/`develop`, auto-merge, rebase sem autorização, exclusão de histórico, scripts destrutivos. Mudanças em migrations já aplicadas exigem novas versões.

## Paralelo sem conflito documental

Agentes independentes podem trabalhar em código de módulos diferentes, mas não devem editar simultaneamente `AI_USAGE.md`, `TASKS.md`, `SPEC.md` ou `DECISIONS.md`; incluir evidência de execução no corpo do PR, para consolidação serial após merge. O histórico do repositório anterior não autoriza marcar trabalho do novo como feito.
