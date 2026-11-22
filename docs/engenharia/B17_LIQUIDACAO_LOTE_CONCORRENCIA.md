# B17 — Liquidação em lote com concorrência e controle otimista

**Status:** planejada P1, **bloqueada por política financeira ainda não aprovada**.  
**Issue:** [#65](https://github.com/RobsonSilverioSantos/srm-credit-engine/issues/65).  
**Dependências:** B16 (cadastro/importação de lote) integrada; B06/B07/B08/B12 já integradas. F08 depende da integração B16 e B17 para oferecer o fluxo completo.  
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

## Semântica financeira a decidir antes da implementação

| Alternativa | Comportamento em conflito de 1 título | Benefício | Custo e risco |
|---|---|---|---|
| **A. Atômica por lote (tudo-ou-nada)** | Rollback de todas as liquidações da tentativa | Demonstra ACID de lote com resultado binário | Um conflito impede a liquidação de todos os outros títulos; risco de maior duração de transação e contenção |
| **B. Atômica por recebível, parcial por lote** | Título conflitante falha, outros títulos podem liquidar | Mais aderente a processamento operacional e recuperação granular | Exige checkpoint por item, replay idempotente, relatório de resultado parcial confiável e reconciliação |

**Não confundir atomicidade do cadastro B16 com a da liquidação B17**: ambas exigem definição própria. Para a demonstração, a alternativa B oferece resultados por item, porém a alternativa A torna o rollback global mais visível. Nenhuma deve ser assumida sem aprovação expressa.

## Testes de concorrência e idempotência obrigatórios

- **Duas requisições diferentes, mesmo recebível:** usar barreira/latch em teste de integração PostgreSQL real para disparo simultâneo com **chaves diferentes**. O resultado deve ter **uma liquidação confirmada**, uma operação rejeitada por conflito de versão/estado (`409` quando contrato de API aplicável), uma trilha auditável, nenhuma dupla liquidação; conferir tabelas, não apenas respostas HTTP.
- **Replay do mesmo comando:** mesma chave de idempotência **e payload idêntico** devolvem resposta já persistida, sem novo lançamento; mesma chave com payload diferente → `409`.
- **Sobreposição de lotes:** um recebível não deve ser liquidado duas vezes mesmo que apareça em comandos de lote concorrentes, nem por reenvio/duplo clique da UI.
- **Conflito no meio do lote:** cobrir rollback de todo o lote para alternativa A ou resultado parcial e recuperação segura para alternativa B, com asserts do estado de cada título.
- **Falhas e segurança:** simular queda após commit com resposta perdida, indisponibilidade de câmbio/taxa, timeout, usuário inativo e organização cruzada; conferir ausência de efeitos financeiros adicionais após retry.
- **Precificação:** manter golden cases C1–C3 com cotação DEMO fixa para aferição; cenários PTAX/MANUAL futuros são testes distintos e não alteram snapshots históricos.

## Implementação proposta

- Usar o domínio/aplicação como autoridade financeira, adaptadores Spring HTTP/JDBC nas bordas; API e BFF reaproveitam casos de uso sem HTTP interno; Kotlin `BigDecimal` e PostgreSQL `NUMERIC`.
- Executar controle de concorrência no PostgreSQL com SQL condicional por versão/estado e constraints, **não apenas mutex em memória**. Qualquer mecanismo adicional de serialização deve ser justificado e testado.
- Evitar HTTP externo na transação; utilizar política de elegibilidade de taxa/câmbio existente, sem fallback cambial silencioso.
- Aplicar limites operacionais configuráveis ao lote; identificar autoria e contexto de organização autenticado, nunca livre do corpo do request.
- Persistir estado do processamento de modo coerente com alternativa aprovada. Nova migration deve usar versão Flyway disponível e coordenação com B15/B16; não reescrever V1–V5.
- Logs correlacionados sem payload bancário integral, JWT, CNPJ nem segredos; métricas sem labels de cardinalidade por recebível.

## Aceite e gate

- [ ] Aprovar formalmente opção A/B, política de retry e partial failures, limite de itens, câmbio, segurança e contrato API/BFF.
- [ ] Confirmar o cenário de contenção **do mesmo recebível**, com chaves distintas; provar 1 sucesso, 1 conflito e 1 registro financeiro.
- [ ] Confirmar replay de mesma chave/payload e 409 para payload diferente.
- [ ] Cobrir casos de falha induzida e rollback/continuação conforme a política escolhida, em PostgreSQL real e testes de integração.
- [ ] Garantir consistência do extrato, autoria, snapshots, versão do recebível, auditoria e não exposição entre organizações.
- [ ] Executar CI backend, frontend e Compose; registrar resultados reais e abrir PR para `develop` sem merge pelo agente.

**Gate:** a B17 não poderá iniciar antes de B16 integrada, política de processamento aprovada e status literal `Liberada para iniciar` em `origin/develop:TASKS.md`.
