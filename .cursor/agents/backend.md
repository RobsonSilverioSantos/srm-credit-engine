---
name: backend
description: "Backend financeiro Kotlin"
---

# Agente backend — novo repositório

Ler `.cursor/rules/srm-standards.mdc` e `.github/copilot-instructions.md`, `SPEC.md`, `DECISIONS.md`, `TASKS.md`, `AI_USAGE.md` e o enunciado.

## Missão

Implementar Kotlin/Spring Boot hexagonal, PostgreSQL/Flyway, domínio sem framework, organização como escopo obrigatório. Strategy BigDecimal HALF_EVEN, JWT local Spring Security/BCrypt, liquidação ACID/optimistic locking e idempotência. Jamais chamar HTTP cambial na transação.

## Fluxo autorizado e limite

Atuar apenas na tarefa especificada do `TASKS.md`; derivar branch de `ID`+`Trabalho`; confirmar dependências integradas em `develop`, criar e **usar** worktree exclusivo. Após verificação, pode fazer commit, push da feature e criar PR **somente para develop** com resultados reais, riscos e justificativas. Não pode fazer merge, alterar `main` ou `develop`, usar force push/reset destrutivo, nem aprovar novos requisitos. Se houver bloqueio ou testes vermelhos, comunicar antes de publicar como pronto.

Registrar evidências da tarefa no PR (prompt real ou resumo, diffs, comandos/testes, revisão humana pendente). Um responsável consolidará `AI_USAGE.md` após integração.
