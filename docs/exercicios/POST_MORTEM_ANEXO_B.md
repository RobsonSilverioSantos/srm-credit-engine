# Post-mortem — Suspeita de liquidações duplicadas

**Serviço:** antecipação de recebíveis  
**Alerta:** sexta-feira, 18h40; três cedentes relatam recebimento duplicado  
**War room:** Tech Lead de plantão (coordenação), desenvolvedor pleno (investigação), Operações e Financeiro  
**Severidade:** SEV-1 provisória, condicionada à confirmação de saída financeira  
**Natureza:** simulação operacional. Horários posteriores ao alerta e ações abaixo são propostas, não eventos comprovados.

## 1. Situação e impacto inicial

A mesa de operações relata possível pagamento em duplicidade a três cedentes. O endpoint `POST /settlements`, em produção há duas semanas, não demonstra idempotência, transação atômica ou controle de concorrência e responde sucesso mesmo ao capturar erros. Também não demonstra **logs estruturados, trilha de auditoria ou métricas**. Não é possível concluir, apenas pelo handler, que houve duas transferências bancárias: separar **dois registros de liquidação**, **duas ordens de pagamento** e **dois créditos efetivos**. A exposição financeira permanece indeterminada até conciliação.

## 2. Linha do tempo do war room (proposta)

| Horário | Ação e responsável | Resultado esperado |
|---|---|---|
| **18h40** | Tech Lead abre incidente e canal único; Operações informa os casos | IDs, valores relatados e responsáveis registrados |
| **18h45** | Tech Lead autoriza bloqueio reversível de novas liquidações/ordens, conforme capacidade operacional | Contenção documentada, sem apagar evidências |
| **18h50** | Desenvolvedor preserva registros, logs disponíveis e estado das filas e pagamentos | Fontes preservadas para investigação |
| **19h05** | Desenvolvedor e Financeiro conciliam recebível → liquidação → ordem → comprovante | Distinguir duplicidade lógica de financeira |
| **19h20** | Tech Lead comunica situação e exposição confirmada ou pendente | Decisão registrada sobre manter contenção |
| **Após estabilização** | Equipe reproduz, testa correção e retoma gradualmente | Liberação com métricas e plano de rollback |

## 3. Causa raiz: hipóteses e evidências

**H1 — Retry e concorrência (mais provável no código):** ausência de chave idempotente e `UPDATE` sem versão/estado permitem que chamadas simultâneas tentem registrar a mesma liquidação (**CR-03/CR-04 no `REVIEW.md`**). Verificar constraints reais antes de concluir duplicidade.

**H2 — Inconsistência transacional:** `INSERT` seguido de `UPDATE` sem transação visível; exceções engolidas e resposta `200 OK` podem mascarar falhas e provocar retentativas (**CR-02/CR-05**).

**H3 — Desembolso externo duplicado:** mesmo um único registro de liquidação pode originar duas ordens se o sistema de pagamentos também não for idempotente. O código analisado não mostra essa etapa.

**Deficiência que agrava a investigação:** sem demonstração de logs com correlação (**AL-13**), auditoria da confirmação (**AL-14**) ou métricas/alertas (**ME-15**), a equipe pode não conseguir reconstruir a sequência diretamente. Não presumir que essas fontes existem em outras camadas.

**Evidências a buscar:** primeiro inventariar fontes disponíveis. Correlacionar logs de API/gateway (se existentes), status e versões no banco, constraints e registros de liquidação, ledger, ordens, mensagens e confirmações bancárias. Examinar as duas semanas de operação. **Se alguma trilha não existir, registrar a limitação e reconstruir por identificadores e tempo, com nível de confiança explícito.** Causa raiz definitiva: **não estabelecida sem essas verificações**.

## 4. Contenção e recuperação

Suspender novas liquidações e reprocessamentos perigosos pelo mecanismo reversível disponível, preservando consultas e conciliação. Não corrigir registros financeiros com `DELETE` ou atualizações manuais destrutivas. Quantificar casos por **organização, cedente e moeda**. Para créditos efetivamente duplicados, Financeiro/Compliance define estorno ou recuperação formal, evitando disparar uma terceira transferência. Registrar todas as autorizações e comunicações.

## 5. Correção definitiva e prevenção

- **Integridade:** transação única para reserva idempotente, `UPDATE` com versão/estado, gravação de liquidação e auditoria; rollback integral; índice `UNIQUE` por recebível (**CR-02 a CR-04**).
- **Idempotência ponta a ponta:** mesma chave e pedido retornam resposta original; ordens ao provedor usam identificador estável e conciliação para resultados desconhecidos. Envio externo somente após commit, com mecanismo confiável (ex.: outbox).
- **Código seguro:** SQL parametrizado, autorização por organização, cálculo decimal correto, câmbio elegível, tratamento de erro honesto e funções com responsabilidades distintas (**CR-01/CR-05/CR-06/AL-07 a AL-12**).
- **Logs estruturados (AL-13):** `correlationId`, operação, status, duração e erro sanitizado; registrar sucesso somente após commit, sem tokens, dados pessoais ou payload completo.
- **Auditoria durável (AL-14):** evento de confirmação na **mesma transação** que a liquidação, com organização, recebível, liquidação, operador, estados, instante UTC e referências financeiras. Rollback não deixa confirmação falsa; replay não duplica evento.
- **Métricas (ME-15):** confirmações, replays, conflitos, rejeições e latência; alertas para desvios entre liquidações, ordens e créditos. Não contabilizar replay/rollback como nova liquidação nem usar IDs como labels.

| Ação / responsável | Evidência antes de reabrir |
|---|---|
| Backend + QA | Testes reais de concorrência, retry e rollback; uma confirmação e um conflito |
| Backend + Banco | Constraint, auditoria transacional e rastreabilidade demonstradas |
| Sustentação + Observabilidade | Logs correlacionáveis, métricas, alertas e exercício de falhas |
| Financeiro + Operações | Três casos conciliados e exposição financeira tratada |
| Tech Lead | PR com causa, testes, plano de rollback e aprovação formal |

## 6. Encerramento e aprendizado

Retomar gradualmente apenas após prova de integridade e idempotência, reconciliação dos casos afetados, testes de falha e acompanhamento da operação. O incidente somente se encerra quando a exposição estiver determinada e o tratamento financeiro autorizado. **Logs apoiam a investigação; auditoria comprova o fato contábil; métricas detectam desvios.** Nenhum deles substitui atomicidade e idempotência. A análise é técnica, sem culpabilização individual.
