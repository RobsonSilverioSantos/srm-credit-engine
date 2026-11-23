# AI_USAGE.md — SRM Credit Engine

**Exceção editorial:** este documento registra explicitamente o uso de IA, inclusive prompts, conversas, verificações, limitações e correções, quando houver evidências. A restrição de linguagem metadiscursiva aplicada aos documentos técnicos não se aplica a este registro.

## 1. Objetivo e transparência

Este documento registra a estratégia de colaboração com IA no desafio técnico **SRM Credit Engine — Edição AI-native (v2)**. O uso de IA é uma ferramenta de engenharia, não uma delegação de responsabilidade técnica. As decisões finais, a validação dos resultados e a defesa do código cabem ao candidato.

**Estado deste registro:** planejamento e parecer **Gate 0** do agente de arquitetura recebidos e avaliados; implementação B01 ainda não iniciada. Não representa funcionalidades implementadas, migrations aplicadas ou testes executados. Atualizar com evidências do repositório antes da entrega.

## 2. Contexto e decisões de colaboração

A partir de conversas com ChatGPT, foram explorados: (a) Strategy e persistência/configuração de regras financeiras, incluindo possibilidade de novas fórmulas; (b) cálculo de valor presente em Java e necessidade de precisão aos centavos; (c) agentes especializados no Cursor, monorepo, opções de hospedagem, custos e autenticação com Keycloak, Google e Microsoft.

A opção por **monorepo** busca facilitar rastreabilidade, integração e revisão. A proposta inicial de AWS Amplify/ECS/Fargate/Terraform foi reavaliada em função do caráter temporário do desafio, do custo e do esforço-alvo. A publicação em nuvem não será tratada como pré-requisito sem exigência explícita do enunciado. **Keycloak é um diferencial escolhido pelo candidato, não um requisito do enunciado**, e deve ser limitado para não prejudicar o core.

## 3. Estratégia de agentes no Cursor

| Agente | Responsabilidade | Saída esperada | Limite |
|---|---|---|---|
| analyst | Ambiguidades, perguntas ao negócio, critérios de aceite | proposta de `SPEC.md` | não decide premissas sozinho |
| architect | Domínio, Strategy, ACID, idempotência, ADRs e C4 | desenho, contratos e ADRs | não impõe arquitetura excessiva |
| backend | API, precificação, câmbio, liquidação e auditoria | implementação e testes | não define política financeira unilateralmente |
| frontend | Simulação e grid com filtros/paginação | interface e testes | não replica cálculo financeiro |
| devops | Compose, CI, deploy opcional e custos | configuração reproduzível | sem deploy ou gastos sem aprovação |
| reviewer | Revisão independente e Anexo A | `REVIEW.md` e achados | não altera código durante review |
| qa | Golden cases, concorrência, integração e regressão | testes e evidências | não altera expectativas para fazer testes passarem |

O agente principal distribui tarefas; o candidato aprova premissas, contratos e decisões de risco. O revisor e o QA devem confrontar o código com a especificação, não apenas confirmar que compila.

## 4. Specs/prompts estratégicos

### P1 — Análise antes de implementação

> Analise o enunciado SRM Credit Engine v2. Liste ambiguidades sobre unidade do prazo, taxa base, vigência cambial, arredondamento, liquidação e idempotência. Proponha alternativas, impactos e perguntas ao negócio. Produza um rascunho de SPEC.md com decisões marcadas como pendentes de aprovação. Não escreva código.

**Critério de validação:** cada premissa deve ser explícita, justificável e compatível com os golden cases de aferição, que possuem regras próprias.

### P2 — Motor financeiro e golden cases

> Implemente Strategy para Duplicata Mercantil e Cheque Pré-datado, separando regras de precificação de persistência e HTTP. Use BigDecimal e arredondamento HALF_EVEN somente no momento especificado. Crie testes C1, C2 e C3 do enunciado sem alterar os valores esperados. Mostre cálculos intermediários e explique escala, unidade das taxas e conversão cambial.

**Critério de validação:** C1 = BRL 92.859,94; C2 = BRL 23.337,77; C3 = USD 17.094,67, conforme a tabela fornecida; conferir também deságios e coerência dos dados de entrada. Se a fórmula e a tabela divergirem, registrar e investigar antes de ajustar implementação ou expectativas.

### P3 — Liquidação robusta

> Proponha e implemente liquidação atômica, idempotente e auditável. Inclua testes para retry da mesma chave, requisições concorrentes sobre o mesmo recebível, rollback e imutabilidade do registro. Identifique restrições de banco necessárias e efeitos de falhas do serviço de câmbio.

**Critério de validação:** evidências de testes de integração e concorrência; ausência de liquidação duplicada; nenhuma escrita parcial.

### P4 — Revisão reversa

> Faça code review do Anexo A do enunciado. Ordene problemas por severidade e, para cada achado, apresente impacto em produção, evidência no trecho e correção proposta. Não modifique o trecho analisado. Gere proposta para REVIEW.md.

**Critério de validação:** revisão humana da priorização e dos impactos; foco em SQL injection, precisão, transação, idempotência, concorrência, tratamento de falhas e semântica financeira.

### P5 — Arquitetura e defesa

> Proponha ADRs enxutos, C4 níveis 1 e 2, evolução orientada a eventos e desenho conceitual para 1 milhão de transações/minuto. Distingua claramente implementação atual de evolução hipotética. Prepare perguntas para uma alteração ao vivo de Strategy, moeda ou arredondamento.

**Critério de validação:** trade-offs claros, sem alegar que a escala projetada foi implementada ou medida.

## 5. Verificação independente

- Testes unitários e de integração executados por ferramentas, não apenas descritos pela IA.
- Golden cases preservados como oráculo externo de aferição; resultados divergentes são bloqueadores.
- Revisão humana de transações, isolamento, índices/constraints, locking e trilha de auditoria.
- PRs pequenos com motivação, evidências e alternativas rejeitadas.
- Registro dos comandos executados, seus resultados e links para commits/PRs.

## 6. Caso concreto de recomendação incorreta da IA — detectado na revisão do enunciado

**Estado da evidência:** erro real de orientação arquitetural durante o planejamento, identificado e corrigido antes da implementação. Não se trata de defeito executado, bug em produção ou teste automatizado já realizado. A evidência de código/commit e de teste deverá ser anexada após implementação.

- **Contexto e pedido:** ao definir concorrência, idempotência e limites transacionais da liquidação, a IA propôs bloqueio pessimista do recebível, além de constraint de unicidade e reserva de chave idempotente. O candidato buscava uma solução segura para o desafio e sua defesa.
- **Recomendação incorreta:** usar bloqueio pessimista (`SELECT FOR UPDATE`) para serializar liquidações simultâneas do mesmo recebível. A solução poderia evitar duplicidade, mas não demonstrava o mecanismo exigido pelo enunciado.
- **Detecção humana:** o candidato releu a seção “Sênior — operação” do enunciado e confrontou a proposta com o requisito textual: “optimistic locking na liquidação, com um teste que demonstra o conflito sendo tratado (duas liquidações simultâneas do mesmo recebível)”. Questionou explicitamente se o bloqueio pessimista prejudicaria a avaliação.
- **Correção aprovada (17/11/2026):** optimistic locking com versão no `Recebivel` (`@Version` no adaptador JPA), atualização versionada dentro da transação, rollback do perdedor e HTTP 409; manter índice `UNIQUE` em liquidação e idempotência por `(tenant, tipo_operacao, Idempotency-Key)` com hash canônico do payload.
- **Validação planejada:** teste de integração com PostgreSQL/Testcontainers e duas transações sincronizadas lendo a mesma versão, comprovando conflito otimista, exatamente uma liquidação e ausência de estado parcial. O teste ainda não foi executado neste registro.
- **Mudança no processo:** confrontar recomendações de agentes com requisitos explícitos e critérios de avaliação antes de aprová-las; incluir o requisito de optimistic locking no contrato do agente backend e na revisão QA.

**Evidências a anexar no repositório:** referência ao requisito no enunciado; decisão em `DECISIONS.md` §19; teste de integração e sua execução; commit/PR de implementação. O histórico da conversa é evidência da detecção humana, mas não substitui testes.

## 7. O que não foi delegado à IA

- Escolha final das premissas financeiras e sua justificativa perante o negócio.
- Aceitação de divergências dos golden cases e definição da política de arredondamento.
- Aprovação de mudanças em semântica de liquidação, idempotência e auditoria.
- Aceitação final de riscos de segurança, segredos, permissões e gastos de nuvem.
- Decisão sobre escopo e cortes documentados em `DECISIONS.md`.
- Defesa ao vivo e explicação/autoria do código entregue.

A IA pode sugerir opções e implementar alternativas; o candidato precisa compreender, testar e aprovar.

## 8. Registro de evidências antes da entrega

| Item | Evidência a acrescentar | Estado |
|---|---|---|
| SPEC.md revisado | commit/PR | Pendente |
| Golden cases | comando, resultado e commit | Pendente |
| Idempotência/concorrência | testes e resultado | Pendente |
| REVIEW.md | commit e revisão humana | Pendente |
| ADRs/DECISIONS.md | commits e decisões | Pendente |
| Erro real de IA | recomendação pessimista identificada na revisão do enunciado; acrescentar diff/teste após implementação | Evidência de planejamento registrada; teste pendente |
| CI | URL de execução | Pendente |
| Regressão de recuperação de artefatos | seção REC-01; ZIPs da baseline v2.2 e agente frontend existente | Evidência documental registrada; testes não executados |

## 9. Registro verificável das interações dos agentes

Este documento registra **interações relevantes**, não transcrições integrais. Cada entrada deve ser acrescentada ao final da tarefa, com revisão do candidato. Os agentes não devem inventar ou reconstruir prompts exatos que não estejam disponíveis; nesse caso, identificar o registro como **resumo**.

### GATE-0 — revisão arquitetural antes do B01 (17/11/2026)

- **Agente:** `architect` no Cursor. **Origem:** parecer apresentado pelo candidato; a transcrição literal do prompt submetido ao Cursor não foi incorporada a este registro.
- **Objetivo resumido:** validar conformidade de enunciado, SPEC/DECISIONS, modelo físico e migrations antes da implementação B01; identificar conflitos de tenant, idempotência, locking e temporalidade.
- **Saída real informada:** parecer “aprovado com ressalvas”, com hierarquia de artefatos, verificação estática de FKs/constraints e quatro aprovações requeridas (prevalência, catálogo `tipoOperacao`, seleção temporal de taxas/cotações e FK de cotação/moeda).
- **Revisão humana registrada:** aprovada prevalência enunciado > SPEC > DECISIONS > modelo físico/migrations > referências; aprovado catálogo `CADASTRAR_CEDENTE`, `CADASTRAR_RECEBIVEL`, `REGISTRAR_COTACAO`, `SUBSTITUIR_TAXA_BASE`, `LIQUIDAR_RECEBIVEL`; hash SHA-256 de comando normalizado; instante UTC único; FK composta para cotação/moeda com constraint de referência; índice de auditoria por tenant na migration V2; separação de administração global e tenant. Política de 30 min segue premissa P0.
- **Arquivos de código alterados pelo agente:** nenhum, conforme seu parecer. **Migrations executadas:** não. **Testes executados:** não. **Commit/PR:** não informado.
- **Próxima evidência obrigatória:** B01 deve informar arquivos/diffs, comandos reais, saída das migrations em PostgreSQL de teste e testes das constraints antes de qualquer declaração de aprovação técnica.


### REC-01 — Regressão na recuperação de artefatos e auditoria do agente frontend (17/11/2026)

- **Natureza:** erro real de recuperação de contexto/artefatos e interpretação de configuração pela IA durante a fase documental; **não é defeito de código executado, incidente de produção nem falha demonstrada por teste**.
- **Contexto:** o planejamento havia alcançado o Gate 0, com parecer do agente `architect`, documentação do frontend React/TypeScript e arquivos ZIP preparados para continuar a B01. Nenhum repositório Git ou código de aplicação havia sido criado.
- **Erro verificável:** ao recuperar a última solicitação após interrupção, a IA apresentou como referência arquivos soltos `SPEC.md` v2.1 e `DECISIONS.md` sem a decisão 30, concluindo incorretamente que o pacote de planejamento estava incompleto quanto ao frontend. Essas cópias eram anteriores aos artefatos `SRM_ATUALIZACAO_PRE_B01.zip` e `SRM_PLANEJAMENTO_B01_COMPLETO.zip`.
- **Detecção humana:** o candidato identificou a ausência das decisões de frontend no material recuperado e informou que a revisão do agente de arquitetura e os ZIPs completos já haviam sido produzidos. Mais tarde esclareceu que os dois ZIPs eram os artefatos mais recentes e pediu um reinício limpo, sem herança de sessões antigas.
- **Investigação e evidência:** inspeção direta dos ZIPs existentes mostrou `SPEC.md` v2.2, `DECISIONS.md` §30, `TASKS.md` B08/F01, relatório do Gate 0 e `.cursor/agents/frontend.md`. Portanto, o agente frontend **já existia**; a deficiência real era o detalhamento insuficiente de seu contrato, agravado pela recuperação de versões antigas. Os arquivos soltos não tinham prioridade sobre o ZIP consolidado.
- **Decisão humana e correção:** manter `SRM_PLANEJAMENTO_B01_COMPLETO.zip` como **baseline documental de origem** e `SRM_ATUALIZACAO_PRE_B01.zip` como conferência; elaborar um novo pacote limpo de início de repositório preservando documentos e histórico de decisões, sem incluir sessões do Cursor, arquivos de build, commits inexistentes ou contexto de execução anterior. Fortalecer o agente frontend e fornecer `docs/arquitetura/FRONTEND.md`; usar B08 como dependência para F01. Não apagar registros autênticos de uso da IA.
- **Verificação desta correção:** listagem e comparação do conteúdo dos dois ZIPs, presença das decisões de frontend e checagem de integridade do novo pacote. **Não houve** execução de backend, frontend, migrations, testes, CI ou comandos Git na recuperação.
- **Prevenção:** (a) inventariar e comparar artefatos por nome/versão/data e conteúdo antes de declarar perda; (b) trabalhar a partir de uma baseline consolidada e imutável, com hash SHA-256; (c) verificar `SPEC.md` §6, `DECISIONS.md` §30, `.cursor/agents/frontend.md` e B08/F01 na abertura; (d) não interpretar ausência em arquivos avulsos como ausência no pacote; (e) documentar o resultado e separar decisões aprovadas de implementação testada.
- **Evidências de código/testes para anexar depois:** commits e execução efetiva de B01, B08 e F01. Não existentes no momento deste registro.

### Modelo de entrada para as próximas tarefas (copiar e preencher apenas após executar)

```markdown
### [ID/TAREFA] — [título] ([data])
- Agente / ferramenta: ...
- Prompt relevante: [citação fiel se preservada; senão "resumo", não entre aspas]
- Objetivo e instruções/limites fornecidos: ...
- Resultado produzido: ...
- Revisão humana: o que foi aprovado, rejeitado ou corrigido e por quê
- Arquivos alterados / diff: ...
- Execuções verificadas: comando; ambiente; resultado (passou/falhou/não executado)
- Evidência: commit/PR, log ou arquivo de teste, se existente
- Pendências e riscos: ...
```

**Política de atualização:** não gravar tokens, chaves idempotentes reais, dados de clientes ou segredos. Agentes em paralelo fornecem resumos ao orquestrador; apenas um responsável edita `AI_USAGE.md` por vez, para evitar conflitos. O candidato valida cada registro.

## 10. Escopo e priorização

O enunciado fixa prazo de até cinco dias e esforço-alvo de 8–16 horas, privilegiando julgamento sobre volume. Ordem de prioridade: SPEC → golden cases/core → ACID/idempotência/auditoria → REVIEW → testes/CI → ADRs/DECISIONS e defesa → extras como Keycloak e hospedagem. Registrar em `DECISIONS.md` qualquer redução de escopo deliberada.