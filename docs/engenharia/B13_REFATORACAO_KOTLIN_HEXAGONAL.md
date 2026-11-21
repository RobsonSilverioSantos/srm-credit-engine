# B13 — Padronização Kotlin e auditoria de arquitetura hexagonal do backend

**Estado:** liberada para iniciar (P1) pelo `TASKS.md`; implementação não iniciada. **Fonte de autorização:** linha B13 de [TASKS.md](../../TASKS.md).  
**Execução:** agente `backend` em um único worktree exclusivo. **Revisões independentes:** `architect`, `qa` e `reviewer`.  
**Pré-requisito:** esta tarefa e as suas dependências devem estar integradas em `origin/develop` antes de criar a feature B13.

## Objetivo

Consolidar a organização de código do backend Kotlin em **um tipo top-level nomeado por arquivo** e revisar acoplamentos relevantes à arquitetura hexagonal, preservando integralmente o comportamento financeiro, a segurança, a persistência e os contratos HTTP existentes.

É uma **refatoração de manutenção**, não uma autorização para ampliar o MVP, introduzir frameworks ou alterar requisitos.

## Diagnóstico inicial (referência: `develop`, 21/11/2026)

A estrutura Gradle possui três módulos: `:dominio` sem Spring/JDBC, `:aplicacao` dependente de `:dominio`, e `:app` contendo adaptadores e composição Spring. Há uma verificação de fronteiras pelo Gradle em `backend/build.gradle.kts`.

A inspeção inicial encontrou **50 arquivos Kotlin de produção**, dos quais **25 contêm mais de um tipo top-level nomeado**; 24 têm um tipo e um contém apenas função de nível superior. O agente deverá **recontar** sobre o commit-base antes de implementar, pois o repositório pode ter mudado.

Exemplos concretos a revisar:

- `backend/dominio/src/main/kotlin/br/com/srm/creditengine/precificacao/dominio/Precificacao.kt`: Strategy, motor, DTOs e objetos no mesmo arquivo.
- `backend/dominio/src/main/kotlin/br/com/srm/creditengine/taxas/dominio/HistoricoFinanceiro.kt`: múltiplos tipos de histórico.
- `backend/aplicacao/src/main/kotlin/br/com/srm/creditengine/extrato/aplicacao/Extrato.kt`: filtros, resultado, portas e serviço.
- `backend/aplicacao/src/main/kotlin/br/com/srm/creditengine/usuarios/aplicacao/ServicoGestaoUsuarios.kt`: serviços e exceções.
- `backend/app/src/main/kotlin/br/com/srm/creditengine/http/adaptador/entrada/ContratosHttp.kt`: DTOs HTTP e várias responsabilidades de entrada.
- `backend/app/src/main/kotlin/br/com/srm/creditengine/usuarios/ProvisionamentoMasterRunner.kt`: runner, terminal e fluxo interativo.
- `backend/app/src/main/kotlin/br/com/srm/creditengine/liquidacao/adaptador/saida/LiquidacaoJdbc.kt`: um adaptador implementa portas de persistência, transação, idempotência e auditoria.
- `backend/dominio/src/main/kotlin/br/com/srm/creditengine/liquidacao/dominio/Liquidacao.kt`: `ResultadoLiquidacao.statusHttp` (`201`) introduz conceito de transporte HTTP no domínio.

## Regras obrigatórias de implementação

1. **Um tipo Kotlin top-level nomeado por arquivo:** inclui `class`, `data class`, `enum class`, `sealed class`, `interface`, `fun interface`, `object`, `typealias` e variações. O arquivo deve ter o nome do tipo principal. Funções de extensão/top-level sem declaração de tipo podem existir em arquivos próprios com nomes descritivos. Não é necessário desmembrar tipos aninhados que constituem detalhes internos de uma classe, mas não usar aninhamento artificial para contornar a regra.
2. **Preservar API Kotlin durante o movimento:** manter `package`, visibilidade e nomes de tipos, salvo justificativa técnica e teste de compatibilidade. Organizar DTOs, contratos e exceções em arquivos distintos, mantendo os pacotes existentes quando possível. Não remover tipos de DTO ou mudar nomes de campos JSON.
3. **Respeitar portas/adaptadores:** `:dominio` não deve importar Spring, JDBC, HTTP ou DTOs de transporte; `:aplicacao` apenas define portas, casos de uso e objetos de aplicação; `:app` provê controladores, DTOs, JDBC, identidade/JWT, métricas e configurações. Dependências continuam apontando para o núcleo.
4. **Retirar HTTP do domínio:** eliminar `statusHttp` de `ResultadoLiquidacao` e implementar o mapeamento de status no adaptador HTTP/infraestrutura apropriada. **Preservar** respostas `201`, replay idempotente, coluna `resposta_http`, serialização histórica e tratamento de conflito `409`. Não alterar schema nem migrations aplicadas.
5. **Modularizar adaptadores de entrada:** separar os contratos HTTP de simulação, cadastro, recebíveis e liquidação em controllers/DTOs por responsabilidade, preservando os prefixos `/api/v1` e `/bff/v1`, paths, verbos, status, OpenAPI, mensagens e JSON. Manter a composição dos mesmos casos de uso, sem HTTP interno.
6. **Avaliar (não dividir automaticamente) `LiquidacaoJdbc`:** documentar suas quatro portas e considerar separação somente se ficar comprovado que transação ACID, locks otimistas, idempotência e auditoria permanecem atômicos. Uma decomposição com risco de alteração semântica deve ser proposta à revisão, não aplicada incidentalmente.
7. **Proteção contra regressão:** adicionar verificação automática da convenção para arquivos Kotlin **de produção** (incluindo `dominio`, `aplicacao`, `app`) e executá-la dentro de `./gradlew check` / CI. O mecanismo deve identificar declarações top-level reais, sem contar falsamente tipos aninhados; documentar eventuais limitações. Evitar dependências externas desnecessárias.
8. **Documentação factual:** atualizar o [C4 do MVP](../arquitetura/C4_MVP.md) para refletir as funcionalidades já implementadas e a estrutura após a refatoração. Documentar a divergência entre `@Version` na ADR-010 e o `UPDATE ... WHERE versao = ?` atual, **sem modificar `DECISIONS.md` ou a ADR normativa sem aprovação explícita**.
9. **Sem mudança funcional:** não alterar fórmulas, HALF_EVEN, spreads, taxas, validade cambial, lógica de autenticidade/organização, credenciais, endpoints, migrations, idempotência ou lógica de liquidação. Não alterar frontend sem dependência necessária e justificada. Nenhum pagamento externo real.

## Plano de execução dentro de uma única feature

- **Fase 1 — Inventário:** listar arquivos com múltiplos tipos, identificar referências cruzadas e registrar contratos e testes existentes. Apresentar ao agente `architect` a análise do acoplamento HTTP/domínio.
- **Fase 2 — Refatoração mecânica:** extrair tipos para arquivos, conservar símbolos/pacotes, reorganizar DTOs/controllers e corrigir imports. Commit(s) pequenos e rastreáveis, sem misturar alterações funcionais.
- **Fase 3 — Fronteira e prevenção:** ajustar `ResultadoLiquidacao` e mapeamento HTTP sem mudança observável; integrar check de nomenclatura/uma declaração no Gradle.
- **Fase 4 — Evidências:** testes, atualização C4 factual, revisão independente de `architect`, `qa` e `reviewer`; abrir **um PR da B13** para `develop`, sem merge automático.

## Critérios objetivos de aceite

- [ ] Inventário de todos os arquivos Kotlin de produção no commit-base e relação dos arquivos desmembrados no PR.
- [ ] Nenhum arquivo de produção contém duas ou mais declarações top-level nomeadas; verificação automática é executada via `./gradlew check`.
- [ ] Build Gradle, testes unitários e `verificarFronteiraHexagonal` aprovados.
- [ ] Golden cases C1=92859.94, C2=23337.77 e C3=17094.67 preservados com `BigDecimal`/`HALF_EVEN`.
- [ ] Testes reais em PostgreSQL demonstram idempotência, replay com status/corpo idênticos, `201` no cadastro/liquidação, `409` de conflito, rollback, concorrência e isolamento de organização.
- [ ] Testes de contratos cobrem os dois prefixos `/api/v1` e `/bff/v1`, incluindo OpenAPI, autenticação e extrato; JSON existente permanece compatível.
- [ ] Nenhum conceito de HTTP permanece nas entidades de domínio; nenhuma dependência invertida entre módulos.
- [ ] C4 atualizado de acordo com código atual, sem reivindicar desempenho/alta escala não testados; desvio da ADR-010 explicitamente relatado para decisão humana.
- [ ] CI do PR com checks de backend, frontend e Compose (quando disponíveis) aprovada; comandos e resultados reais documentados.
- [ ] Análise de risco para `LiquidacaoJdbc` e comentários/pareceres dos revisores anexados ao PR; sem merge por agente.

**Comandos mínimos a registrar no PR (sem alegar aprovação antes da execução):**

```sh
cd backend
./gradlew clean check
bash scripts/test-cadastro-postgres.sh
```

O agente deve conferir a CI do PR e registrar quaisquer testes adicionais de contrato. `bash scripts/test-cadastro-postgres.sh` utiliza recursos PostgreSQL temporários; **não executar `docker compose down -v` sobre a stack de desenvolvimento**. Nunca publicar senha, token JWT ou `backend/.env`.

## Governança e delegação

**Agente executor:** `backend` (Cursor ou Copilot), com um único worktree e branch conforme `ID` e `Trabalho` do `TASKS.md`.  
**Agente architect:** revisar plano/diff e direção das dependências sem criar feature concorrente.  
**Agente qa:** auditar invariantes financeiras, concorrência, contratos e evidências de execução.  
**Agente reviewer:** revisão final e comentários por severidade.

O agente pode implementar, testar, commitar e abrir PR **somente para `develop`**. Não pode mergear, atualizar `main` ou `develop` diretamente, reescrever migrations ou modificar decisões normativas sem autorização humana.

**Gatilho:** iniciar implementação **após o PR que registra a B13 ser integrado à `develop`**, e depois confirmar dependências e ausência de outro worktree B13 em execução.
