# B17 — Liquidação em lote com concorrência e controle otimista

**Status:** planejada P1; **política A — liquidação atômica por lote — aprovada pelo responsável técnico em 22/11/2026 (ADR-025)**. Implementação somente após integração humana do PR documental de liberação e reconhecimento literal do gate em `origin/develop:TASKS.md`.  
**Issue:** [#65](https://github.com/RobsonSilverioSantos/srm-credit-engine/issues/65).  
**Dependências:** B16 (cadastro/importação de lote) integrada pelo [PR #71](https://github.com/RobsonSilverioSantos/srm-credit-engine/pull/71), merge `186786e27320e83700a81e3922c72d096cefcb86`; B06/B07/B08/B12 já integradas. A dependência técnica está atendida; a política financeira A foi aprovada na [ADR-025](../adr/ADR-025-liquidacao-atomica-lotes.md), mas a execução exige integração do PR de liberação. F08 ainda depende da integração B17, contratos homologados e nova liberação literal.

**Executor proposto:** agente `backend`, revisores `architect`, `qa` e `reviewer` em feature/worktree exclusivo.

## Motivação

O enunciado descreve recebimento de lote, cálculo do deságio e transação auditável. O processamento em lote, acompanhado de um teste de contenção sobre **o mesmo recebível**, permite demonstrar integridade transacional, idempotência, controle otimista de concorrência e auditoria em PostgreSQL real. Apenas importar ou liquidar vários recebíveis distintos **não demonstra disputa concorrente**.

A importação e o cadastro de recebíveis (B16) **não** realizam liquidação financeira. A B17 acrescenta a operação financeira explícita, posterior à confirmação do lote, sem misturar parsing de arquivo com transações financeiras.

## Fluxo proposto

1. **Cadastro via B16:** lote importado e validado, com `idLote`, referências externas e títulos vinculados à organização autenticada, inicialmente `DISPONIVEL`.
2. **Prévia financeira:** consulta do lote retorna itens, moeda de pagamento selecionada para cada recebível, valores brutos, VP, deságio e câmbio indicativos, sem gravar liquidações e **sem reservar taxa ou câmbio**.
3. **Confirmação explícita:** operador confere e envia requisição ao endpoint proposto `POST /api/v1/lotes/{loteId}/liquidacoes` / `POST /bff/v1/lotes/{loteId}/liquidacoes` com `Idempotency-Key` e comandos por item. O backend revalida situação, autorização, cotação/vigência e política financeira no instante efetivo.
4. **Processamento:** reutilizar casos de uso/portas existentes para precificação e liquidação. Manter a evolução `DISPONIVEL → LIQUIDADO` protegida por atualização condicional de estado/versão, `UNIQUE` de liquidação por recebível, auditoria e snapshots consistentes.
5. **Resultado auditável:** consultar `idLote`, operações confirmadas, conflitos, rejeições, códigos e motivo seguro por item, carimbo UTC, autoria e chaves idempotentes. Não devolver sucesso geral falso se houver execução parcial.

O endpoint, formato exato de payload/resposta e semântica de códigos HTTP são **contratos propostos**, não implementados. O executor deve submetê-los à revisão humana antes de codificar.

## Gate de revisão de **todas** as simulações antes da liquidação

**Decisão de UX aprovada em 22/11/2026:** o fluxo apresenta a prévia financeira **de todos os itens do lote**, com valor presente, deságio, moeda, taxa base, spread e cotação/fonte quando aplicável, e um resumo por moeda. O botão **`Liquidar lote`** somente pode ser habilitado após retorno completo e válido das simulações, sem erros de dado, cedente, elegibilidade, cotação ou título já liquidado. Uma prévia parcial/falha **não autoriza confirmação**. Mostrar no UI contagem conferida, total de itens e aviso de que a prévia não reserva câmbio/taxa.

A confirmação exige **etapa deliberada adicional** (modal contendo resumo e quantidade, com ação `Confirmar liquidação`). O backend **revalida todas as condições no momento efetivo da escrita**; a verificação da UI não substitui autorização, controle de versão nem transação. Se taxa/câmbio/estado variar entre prévia e confirmação, aplicar a política financeira aprovada: devolver erro/novo snapshot para reconfirmação ou resultado por item conforme a semântica atômica/parcial homologada; **não liquidar silenciosamente com preço diferente**. O request deve identificar uma versão/revisão da prévia ou hash canônico para detectar prévia obsoleta, sujeito ao desenho definitivo do contrato.

**Separação:** a aprovação do botão e da conferência total **não escolhe** se a transação do lote será tudo-ou-nada ou parcial por recebível. Essa decisão permanece pendente.

### Arquivos de aferição de duplicidades

Os [CSV A e B](../validacao/lotes/README.md) possuem 200 linhas cada e quatro referências de negócio repetidas entre arquivos: duas nas mesmas posições e duas em ordem diferente. Após liquidar integralmente A, pré-validar B deve acusar quatro títulos `JA_LIQUIDADO` pela relação persistida `(organizacao, referenciaExterna)`; nenhum dos quatro pode criar uma segunda liquidação. Para cadastrar os outros 196 no modelo tudo-ou-nada, corrigir/remover previamente as quatro linhas rejeitadas. **Esse é um teste de duplicidade sequencial, não o teste concorrente.** A prova do lock otimista é feita disparando duas requisições financeiras diferentes simultaneamente sobre o mesmo `recebivelId`, com chaves distintas.


## Semântica financeira aprovada — atômica por lote (ADR-025)

| Alternativa | Comportamento em conflito de 1 título | Benefício | Custo e risco |
|---|---|---|---|
| **A. Atômica por lote (tudo-ou-nada)** | Rollback de todas as liquidações da tentativa | Demonstra ACID de lote com resultado binário | Um conflito impede a liquidação de todos os outros títulos; risco de maior duração de transação e contenção |
| **B. Atômica por recebível, parcial por lote (não adotada)** | Título conflitante falha, outros títulos podem liquidar | Mais aderente a processamento operacional e recuperação granular | Exige checkpoint por item, replay idempotente, relatório de resultado parcial confiável e reconciliação |

**Decisão expressa do responsável técnico em 22/11/2026: alternativa A.** A liquidação de cada lote é uma única transação ACID PostgreSQL, tudo-ou-nada; qualquer falha, conflito, preço/câmbio alterado ou prévia inválida provoca rollback de todas as liquidações, snapshots e efeitos financeiros da tentativa. **Alternativa B rejeitada no escopo B17**. O cadastro B16 continua regido separadamente pela ADR-024.

## Testes de concorrência e idempotência obrigatórios

- **Antes do botão de liquidação:** simulações de 100% dos itens presentes e válidas; qualquer linha inválida/sem câmbio/duplicada impede confirmação; conferir contagem, hash de prévia e revalidação no backend em testes.
- **Duas requisições diferentes, mesmo recebível:** usar barreira/latch em teste de integração PostgreSQL real para disparo simultâneo com **chaves diferentes**. O resultado deve ter **uma liquidação confirmada**, uma operação rejeitada por conflito de versão/estado (`409` quando contrato de API aplicável), uma trilha auditável, nenhuma dupla liquidação; conferir tabelas, não apenas respostas HTTP.
- **Duas confirmações simultâneas e idênticas de todo o lote A de 200, mas com chaves diferentes:** ambas devem carregar a mesma prévia e disputar as transações efetivamente em PostgreSQL (barreira/latch dentro do cenário); exatamente **uma tentativa confirma os 200**, e a outra retorna conflito/rejeição integral, persistindo **zero** liquidações adicionais. Conferir contagens SQL por lote, estado/versão de cada título, snapshots, auditoria, extrato e idempotência; não basta ver um 201 e um 409.
- **Duas confirmações simultâneas do lote inteiro com a MESMA chave/payload:** somente uma transação confirma e a outra obtém o mesmo resultado por replay ou consulta/retry seguro caso a primeira ainda esteja em andamento; nunca ocorre segunda liquidação. Mesma chave com payload diferente continua retornando 409.
- **Disputa que ocorre tardiamente após gravações provisórias:** forçar a requisição perdedora a atualizar alguns títulos antes de sofrer conflito em outro título, e provar rollback de **todas** as suas escritas financeiras, preservando somente o resultado completo do vencedor. Injeção de falha simples sem concorrência continua sendo cenário independente.
- **Replay do mesmo comando:** mesma chave de idempotência **e payload idêntico** devolvem resposta já persistida, sem novo lançamento; mesma chave com payload diferente → `409`.
- **Sobreposição de lotes:** um recebível não deve ser liquidado duas vezes mesmo que apareça em comandos de lote concorrentes, nem por reenvio/duplo clique da UI.
- **Conflito no meio do lote:** cobrir rollback integral obrigatório (opção A aprovada), inclusive falha após gravações anteriores e perto do fim do lote; conferir estado de todos os títulos, snapshots e idempotência em PostgreSQL.
- **Falhas e segurança:** simular queda após commit com resposta perdida, indisponibilidade de câmbio/taxa, timeout, usuário inativo e organização cruzada; conferir ausência de efeitos financeiros adicionais após retry.
- **Precificação:** manter golden cases C1–C3 com cotação DEMO fixa para aferição; cenários PTAX/MANUAL futuros são testes distintos e não alteram snapshots históricos.

## Implementação proposta

- Usar o domínio/aplicação como autoridade financeira, adaptadores Spring HTTP/JDBC nas bordas; API e BFF reaproveitam casos de uso sem HTTP interno; Kotlin `BigDecimal` e PostgreSQL `NUMERIC`.
- Executar controle de concorrência no PostgreSQL com SQL condicional por versão/estado e constraints, **não apenas mutex em memória**. Qualquer mecanismo adicional de serialização deve ser justificado e testado.
- Evitar HTTP externo na transação; utilizar política de elegibilidade de taxa/câmbio existente, sem fallback cambial silencioso.
- Aplicar limite técnico inicial sugerido de até 200 títulos por confirmação, configurável e testado em PostgreSQL real, sem afirmar homologação produtiva; identificar autoria e organização autenticada, nunca livre do corpo do request.
- Persistir transação única de liquidação por lote conforme alternativa A aprovada (sem sucesso parcial). Nova migration deve usar versão Flyway disponível e coordenação com B15/B16; não reescrever migrations já aplicadas.
- Logs correlacionados sem payload bancário integral, JWT, CNPJ nem segredos; métricas sem labels de cardinalidade por recebível.

## Aceite e gate

- [x] **Opção A aprovada pelo responsável técnico (22/11/2026; ADR-025):** rollback integral e nenhum processamento parcial. [ ] Revisar detalhes técnicos de retry/replay, limite inicial, câmbio vigente, segurança e contrato API/BFF na implementação, sem alterar política financeira.
- [ ] Confirmar o cenário de contenção **do mesmo recebível**, com chaves distintas; provar 1 sucesso, 1 conflito e 1 registro financeiro.
- [ ] Confirmar replay de mesma chave/payload e 409 para payload diferente.
- [ ] Cobrir casos de falha induzida e rollback/continuação conforme a política escolhida, em PostgreSQL real e testes de integração.
- [ ] Garantir consistência do extrato, autoria, snapshots, versão do recebível, auditoria e não exposição entre organizações.
- [ ] Executar CI backend, frontend e Compose; registrar resultados reais e abrir PR para `develop` sem merge pelo agente.

**Gate B17 (22/11/2026):** B16 integrada; **política financeira A aprovada**, conforme ADR-025. A implementação somente pode começar depois do **merge humano do PR documental de liberação** e do status literal `Liberada para iniciar` em `origin/develop:TASKS.md`. Até lá não iniciar worktree B17. Os scripts QA k6 do PR #70 aferem cadastro B16 e não comprovam concorrência financeira em lote.
