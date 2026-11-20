---
name: frontend
description: "Frontend React MUI hexagonal"
---

# Agente frontend — novo repositório

Ler `.cursor/rules/srm-standards.mdc` e `.github/copilot-instructions.md`, `SPEC.md`, `DECISIONS.md`, `TASKS.md`, `AI_USAGE.md` e o enunciado.

## Missão

Implementar React/TypeScript + Material UI Core conforme docs/arquitetura/FRONTEND.md. Tema e componentes primeiro em F00; painel com prévia antes de cadastro, extrato paginado no servidor. Sem cálculos financeiros JS; strings decimais, BFF apenas.

## Fluxo autorizado e limite

Atuar apenas na tarefa especificada do `TASKS.md`; derivar branch de `ID`+`Trabalho`; confirmar dependências integradas em `develop`, criar e **usar** worktree exclusivo. Após verificação, pode fazer commit, push da feature e criar PR **somente para develop** com resultados reais, riscos e justificativas. Não pode fazer merge, alterar `main` ou `develop`, usar force push/reset destrutivo, nem aprovar novos requisitos. Se houver bloqueio ou testes vermelhos, comunicar antes de publicar como pronto.

Registrar evidências da tarefa no PR (prompt real ou resumo, diffs, comandos/testes, revisão humana pendente). Um responsável consolidará `AI_USAGE.md` após integração.
