# Revisão de PR — mudança pequena e explicável

## 1. Identificação e motivo

- **ID/Trabalho:** (copiar exatamente de `TASKS.md`)
- **Branch `feature/*` → base `develop`:**
- **Problema/risco que motivou o PR:**
- **Por que esta mudança é necessária agora?**
- **Resultado esperado (em linguagem de negócio):**

## 2. Como foi resolvido

- **Resumo da solução e responsabilidades de cada componente:**
- **Arquivos/contratos/migrations relevantes:**
- **Alternativas consideradas e por que foram rejeitadas:**
- **Impacto em organização/segurança, cálculo monetário, idempotência ou transação:**
- **Decisões/ADRs envolvidos:**

## 3. Como testar — fatos observados

- **Comando(s) executado(s) e ambiente:**
- **Resultado(s) real(is), testes novos e regressão:**
- **CI (link e status, se já existir):**
- **Não testado e motivo:**
- **Como o revisor pode reproduzir:**

## 4. Riscos e aprendizado para a revisão

- **Cenário de falha mais importante e como é contido:**
- **Limites de escopo e possível evolução:**
- **Uma explicação didática para quem é júnior:**
- **Pontos específicos para revisão humana:**

## 5. Checklist do agente

- [ ] A feature foi criada com ID + Trabalho do `TASKS.md` em worktree isolado
- [ ] PR tem como base `develop` e dependências já integradas
- [ ] Cada alegação de teste/CI tem evidência verificável; nenhum sucesso inventado
- [ ] Sem segredos, PII ou tokens em diff/logs
- [ ] Golden cases e controles financeiros preservados quando aplicável
- [ ] Nenhuma migração aplicada em ambiente compartilhado foi reescrita
- [ ] PR não mistura IDs/tarefas independentes
- [ ] Não foi realizado merge: decisão e integração exclusivamente humanas

Consulte `docs/engenharia/EXEMPLO_PR_DIDATICO.md` para um modelo **hipotético** de qualidade editorial (não é evidência de execução).
