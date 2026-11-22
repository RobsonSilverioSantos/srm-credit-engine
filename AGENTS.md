# Diretrizes para agentes do SRM Credit Engine

## Fontes e precedência

Este arquivo é um ponto de entrada, não uma fonte normativa adicional. Aplicar a precedência vigente: [enunciado original](docs/requisitos/ENUNCIADO_ORIGINAL_SRM_CREDIT_ENGINE_V2.md) > [SPEC.md](SPEC.md) > [DECISIONS.md](DECISIONS.md) > modelos e migrations > [TASKS.md](TASKS.md).

Antes de atuar, ler as [instruções gerais do repositório para Copilot](.github/copilot-instructions.md), o enunciado, `SPEC.md`, `DECISIONS.md` e os ADRs relevantes indicados neste último, `TASKS.md`, [PROMPTS_INICIAIS.md](PROMPTS_INICIAIS.md), [AI_USAGE.md](AI_USAGE.md) e o [protocolo Git Flow e worktrees](docs/arquitetura/GIT_WORKTREES.md). Consultar também as regras e o perfil do agente aplicáveis ao executor e à tarefa.

## Escolha do ambiente

Dot, Codex Cloud, Cursor e Copilot não herdam automaticamente as mesmas diretrizes. Antes de editar, cada executor deve confirmar quais instruções foram carregadas, ler as que faltarem e conferir o repositório, o ambiente selecionado, a branch e o diretório de trabalho. Consultar, quando aplicável, as [regras Cursor](.cursor/rules/srm-standards.mdc) e os perfis de agentes em `.cursor/agents/` e `.github/agents/`. Uma sessão em nuvem não comprova acesso a sessões ou worktrees da máquina local; não presumir que o Dot consiga operá-las sem uma integração verificada.

## Arquitetura e regras de negócio

Preservar a arquitetura hexagonal no backend e no frontend: domínio, aplicação e adaptadores separados. O backend usa Kotlin, Java 21, Spring Boot e PostgreSQL/Flyway; o frontend usa React/TypeScript. Preservar os contratos, a precisão com `BigDecimal` e `HALF_EVEN` e as decisões financeiras vigentes. Não reescrever migrations aplicadas nem alterar comportamento funcional ou financeiro fora do escopo aprovado.

## Liberação e controle Git

1. Atualizar e consultar `origin/develop:TASKS.md`. Só iniciar implementação quando a tarefa contiver o status literal `Liberada para iniciar` e todas as dependências estiverem integradas (ou operacionais, conforme o backlog). Uma issue ou PR de planejamento ainda aberto não libera a tarefa. Não alterar ou antecipar os gates de B17, B15, F06 ou F08.
2. Derivar `feature/<id>-<slug-do-trabalho>` das colunas ID e Trabalho de `TASKS.md`. Usar um worktree exclusivo por tarefa, conforme o [protocolo](docs/arquitetura/GIT_WORKTREES.md); não editar o worktree de outro agente.
3. Commitar e dar push só na própria feature, com o hook de proteção verificado; abrir PR **somente contra `develop`**. Não modificar `main` ou `develop` diretamente. Não executar merge, auto-merge, force-push, reset destrutivo, deploy nem assumir custos. Revisão final, decisões de arquitetura e financeiras, e integração cabem exclusivamente ao responsável técnico.

## Evidências e uso de IA

Identificar no PR a tarefa, a atuação dos agentes, as decisões humanas, os commits, os comandos e testes efetivamente executados, seus resultados e as verificações que não puderam ser realizadas. Distinguir evidência local, CI e revisão humana. Preservar os registros históricos; propor consolidação serial no `AI_USAGE.md`, sem editar arquivos normativos compartilhados em paralelo nem declarar validação não observada.
