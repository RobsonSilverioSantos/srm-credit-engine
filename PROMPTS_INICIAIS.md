# Prompts de partida — copie para o agente escolhido

**Premissa:** antes de iniciar qualquer prompt, o responsável já criou o repositório privado, fez commit da baseline em `main`, publicou `develop` e concluiu G00. Os agentes devem ler `.github/copilot-instructions.md` (também aplicável ao Cursor), `SPEC.md`, `DECISIONS.md` e a linha exata de `TASKS.md`. Nunca executar tarefas de outros IDs por iniciativa própria.

## Agente backend — B02 (primeiro worktree)

> Você é o agente backend no novo SRM Credit Engine. Execute **somente B02** do `TASKS.md`: preparar Kotlin/Spring Boot/Gradle Java 21 e Docker Compose PostgreSQL, com estrutura hexagonal, domínio sem dependência de Spring/JPA, healthcheck e build reproduzível. Antes de alterar arquivos, confirme que G00 está publicado e que está em um **worktree exclusivo** de branch `feature/b02-...` derivada da coluna ID + Trabalho. Não implemente migrations B01, precificação B03, autenticação S01 nem regras financeiras além das interfaces necessárias. Documente comandos/testes realmente executados. Você está autorizado a commitar, dar push da **própria feature** e abrir PR exclusivamente contra `develop`, com descrição didática baseada no template. Não faça merge e não altere branches protegidas. Se faltar ambiente, dependência ou teste falhar, relate sem inventar evidência.

## Agente frontend — F00 (paralelo ao B02)

> Você é o agente frontend do novo SRM Credit Engine. Execute **somente F00**: iniciar React/TypeScript/Vite com Material UI Core gratuito, tema, componentes compartilhados, layout responsivo e mocks explicitamente identificados. Leia `docs/arquitetura/FRONTEND.md`. Não implementar chamadas reais ao BFF, cálculos financeiros nem dependências premium. Confirme G00 integrado e crie/use worktree exclusivo para `feature/f00-...` derivado de ID + Trabalho. Faça testes/lint disponíveis; reporte o que executou de fato. Você pode commitar, dar push da **feature** e abrir PR para `develop`; merge e aprovação humana não lhe pertencem.

## Agente backend — B13 (refatoração Kotlin e hexagonal)

> Você é o agente backend do SRM Credit Engine. Execute **exclusivamente a B13** do `TASKS.md` e leia integralmente `docs/engenharia/B13_REFATORACAO_KOTLIN_HEXAGONAL.md` e as instruções globais do repositório. Antes de iniciar, confirme que a B13 e as dependências estão integradas em `origin/develop`. Utilize um único worktree exclusivo e branch derivada do ID e da coluna Trabalho; não trabalhe na pasta de outro agente. Realize inventário de arquivos Kotlin com múltiplos tipos, extraia um tipo top-level por arquivo, separe contratos HTTP/DTOs, corrija o acoplamento `ResultadoLiquidacao.statusHttp` e mantenha a arquitetura hexagonal. **Não altere** regras financeiras, payloads, endpoints, autenticação, idempotência, transações ou migrations. Acrescente verificação automática da convenção no Gradle, atualize o C4 de modo factual e registre a divergência da ADR-010 sem modificar documentos normativos. Execute `./gradlew clean check` e `bash scripts/test-cadastro-postgres.sh` ou documente precisamente qualquer bloqueio; solicite revisões de `architect`, `qa` e `reviewer`. Abra **um PR da feature B13 para develop** com evidências e riscos, sem efetuar merge.

## Agente backend — B14 (CNPJ e identificação internacional de cedentes)

> Você é o agente executor backend do SRM Credit Engine. Execute **somente B14** do `TASKS.md`, depois de ler integralmente `docs/engenharia/B14_DOCUMENTOS_CEDENTES_INTERNACIONAL.md`, `SPEC.md`, `DECISIONS.md` e as regras Cursor/Copilot. Antes de criar a feature, confirme que **B13 e o planejamento B14 foram integrados em `origin/develop`**; se B13 estiver pendente, encerre o início com bloqueio documentado. Use **um único worktree exclusivo**, branch derivada do ID e coluna Trabalho. Primeiro proponha contrato, migração segura de registros legados e política para países sem validador; não suponha que todo documento legado é CNPJ. Implemente Strategy com validação oficial de CNPJ numérico e alfanumérico (dois DVs), normalização canônica e proteção de unicidade tenant/país/tipo, nova migration Flyway sem alterar V1, controllers API/BFF preservando compatibilidade e UI React com seleção país/tipo e formatação contextual. Peça revisão especializada de `frontend`, `architect`, `qa` e `reviewer`, **sem edições concorrentes no mesmo worktree**. Teste CNPJ válido/inválido, mascarado/sem máscara, alfanumérico, duplicidades, tenants, legado e contratos; execute testes PostgreSQL, Gradle check e frontend lint/test/build com resultados reais. Não alterar regras financeiras, autenticação, liquidação nem migrations existentes. Se uma decisão alterar API de maneira incompatível ou aceitar documentos não validados, submeta alternativa ao responsável antes de aplicar. Abra PR **exclusivamente para `develop`** e não execute merge.

## Agente QA / revisão de PR

> Leia o enunciado, `SPEC.md`, `DECISIONS.md`, o ID do `TASKS.md` e o diff do PR aberto. Faça análise independente de requisitos, arquitetura hexagonal, segurança, organização/cedente, erros, golden cases e evidência de testes. Classifique achados por severidade, distinga observação de hipótese e formule comentários didáticos. **Não** faça merge ou alteração corretiva fora da tarefa autorizada. Os exercícios independentes de `docs/exercicios/` não são trabalho de QA do backend.

## Checkpoint humano antes do merge

- Branch e worktree correspondem à tarefa e nenhuma outra tarefa foi misturada?
- PR realmente aponta para `develop`?
- Diferenças financeiras/segurança estão de acordo com SPEC/DECISIONS?
- Os testes foram executados? Qual comando e resultado observável?
- O PR ensina a decisão, riscos e alternativa rejeitada?
- Não há segredos nem mudanças destrutivas?

Somente após a revisão humana integrar o PR, desbloqueie suas tarefas dependentes.
