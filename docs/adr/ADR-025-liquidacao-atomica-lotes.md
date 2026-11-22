# ADR-025 — Liquidação financeira atômica por lote (B17)

**Estado:** **APROVADA pelo responsável técnico em 22/11/2026 — alternativa A (tudo-ou-nada por lote)**. A implementação somente fica operacionalmente liberada após o **merge humano do PR documental de liberação** e a presença do status literal `Liberada para iniciar` em `origin/develop:TASKS.md`.
**Escopo:** liquidação explícita de recebíveis já cadastrados; distinta da importação/cadastro atômico da ADR-024.
**Referências:** [B17](../engenharia/B17_LIQUIDACAO_LOTE_CONCORRENCIA.md), [issue #65](https://github.com/RobsonSilverioSantos/srm-credit-engine/issues/65), [ADR-010](ADR-010-controle-otimista-sql-versionado.md), [ADR-024](ADR-024-cadastro-atomico-lotes.md), [B16 PR #71](https://github.com/RobsonSilverioSantos/srm-credit-engine/pull/71).

## Contexto, escolha e alternativas

A B16 importa e cadastra lotes de recebíveis sem executar operações financeiras. A B17 adiciona consulta de prévia e confirmação **financeira separada**, com concorrência, idempotência e rastreabilidade.

**Escolha humana expressa: opção A — uma transação PostgreSQL para todo o lote.** Todos os recebíveis elegíveis são liquidados e auditados em conjunto, ou nenhum é liquidado; erro de validação, cotação, versão, estado, gravação ou conflito em qualquer título exige rollback total. A alternativa B (sucesso parcial por recebível) foi examinada e **não é adotada na B17**: exigiria estado parcial, checkpoints, conciliação e semântica de replay mais complexos. A escolha de atomicidade do cadastro na ADR-024 é independente desta decisão.

## Regras de aceitação da B17

1. **Dois atos explícitos:** `Confirmar cadastro` (B16) nunca liquida; `Liquidar lote` (B17) depende de **prévia de 100% dos itens válida**, conferência e confirmação adicional separada. Não liquida automaticamente ao subir CSV/XLSX.
2. **Prévia sem persistência:** apresentar quantidade, todos os itens, estado/versão, moeda, valor presente, deságio, taxa base, spread, fonte/validade do câmbio quando aplicável e totais por moeda. Preço/cotação **não ficam reservados** pela prévia. A confirmação deve carregar identificação e hash/revisão da prévia para detectar alterações.
3. **Revalidação integral:** antes de escrever, validar organização autenticada, autorização, lote/itens, versões, estado `DISPONIVEL`, precificação e elegibilidade das taxas/cotações. Alteração de estado ou preço entre prévia e confirmação → **rejeição integral e nova prévia/reconfirmação**, sem substituição silenciosa de valores e sem sucesso parcial.
4. **Uma transação ACID:** atualização dos títulos, registros financeiros, snapshots, auditoria e conclusão da idempotência são atômicos no PostgreSQL. Falha após a primeira ou penúltima atualização exige rollback de **todos** os itens e efeitos correlatos. Nunca dividir a confirmação em commits por recebível.
5. **Controle otimista:** utilizar a ADR-010, com `UPDATE` SQL JDBC condicionado por organização, ID, versão esperada e estado `DISPONIVEL`; exigir uma linha afetada por título, defesa adicional por `UNIQUE` de liquidação por recebível, sem `SELECT FOR UPDATE` explícito como estratégia principal. Duas requisições distintas sobre o mesmo título com chaves diferentes devem resultar em um sucesso e um conflito.
6. **Idempotência e recuperação:** chave de `Idempotency-Key` + hash canônico do comando por organização/tipo; mesma chave/payload da liquidação confirmada retorna o **resultado original** sem novas escritas (inclusive após perda da resposta). Mesma chave/payload diferente → `409`; conflito de estado/versão não recebe retry financeiro automático ou nova chave em silêncio. Tentativas não confirmadas não devem mascarar sucesso.
7. **Contratos API/BFF:** reutilizar os casos de uso hexagonais no servidor (sem HTTP interno); prévia e confirmação em endpoints distintos. A proposta de rota `POST /api/v1/lotes/{loteId}/liquidacoes` e espelho `/bff/v1` requer verificação do contrato real da B16 antes do código. `2xx` nunca representa processamento parcial; códigos e payloads devem ser revisados no PR técnico.
8. **Escala inicial sugerida:** até **200 títulos por confirmação**, teto configurável e validado no servidor, coerente com as massas B16; aferir tempo de transação e contenção no PostgreSQL real. O parâmetro operacional é ponto de partida de implementação, **não limite produtivo homologado**.
9. **Precificação, segurança e observabilidade:** `BigDecimal`, `HALF_EVEN`, golden cases C1–C3, snapshots auditáveis, organização proveniente da identidade validada, logs sem tokens/segredos/identificadores sensíveis; sem HTTP externo durante a transação. Reutilizar a política cambial **vigente**, sem antecipar a B15 ou permitir fallback silencioso.
10. **Migração:** adicionar Flyway somente quando necessária, na próxima versão livre coordenada com B15, sem reescrever migrations já aplicadas.

## Verificações obrigatórias e limites da evidência

- Massa A com 200 títulos; após liquidação de A, as quatro referências repetidas do arquivo B devem aparecer como `JA_LIQUIDADO` (sem segunda liquidação). Cadastro dos 196 restantes exige correção/novo comando da B16 conforme ADR-024. **Isto é detecção sequencial, não prova concorrência.**
- Corrida **determinística** em PostgreSQL real sobre **o mesmo recebível** com **duas chaves diferentes**: uma confirmação, um conflito e uma única liquidação persistida; validar tabelas e extrato, não apenas HTTP.
- **Duas liquidações concorrentes do mesmo lote inteiro (200 recebíveis), mesmo payload financeiro, chaves idempotentes distintas:** sincronizar ambas após leitura da mesma prévia/revisão e induzir disputa real na transação PostgreSQL com barreiras/latches controladas; **uma única confirmação integral (200 títulos) e uma rejeição/conflito integral (zero títulos adicionais)**. Conferir por SQL as exatas 200 liquidações do lote vencedor, 200 títulos `LIQUIDADO` uma vez cada, nenhum lançamento/snapshot/auditoria de sucesso duplicado, nenhuma escrita financeira sobrevivente do perdedor e consistência da idempotência. Nunca inferir atomicidade apenas de HTTP `201/409` ou de `http.batch` sem garantir sobreposição da transação.
- **Duas confirmações concorrentes do mesmo lote com MESMA `Idempotency-Key` e mesmo payload:** somente uma execução financeira e uma chave/hash persistida; ambas as tentativas devem convergir ao mesmo resultado confirmado, diretamente ou após retry/consulta segura da chave se houver estado transitório `em andamento`. Nunca executar duas vezes; não confundir essa corrida de idempotência com o conflito esperado de chaves distintas.
- **Falha tardia após processamento inicial de títulos na transação perdedora:** provocar conflito em um título posterior à gravação provisória de outros, para verificar que PostgreSQL reverte todas as escritas daquela tentativa (inclusive idempotência, snapshots e auditoria financeira), sem retirar as 200 liquidações legitimamente confirmadas pelo vencedor.
- Falha induzida na metade e perto do fim do lote: nenhuma liquidação, snapshot ou marca de conclusão indevida; rollback integral verificável.
- Mesmo comando + mesma chave → replay; mesma chave + payload diferente → `409`; perda de resposta após commit → consulta/replay seguro, sem duplicação.
- Prévia incompleta, cotação inelegível ou vencida, mudança de preço/versão, organização cruzada, autorização negada, timeout, casos C1–C3 e cenário de sobreposição de lotes.
- Testes de integração com PostgreSQL/Testcontainers, CI e revisão dos agentes `architect`, `qa` e `reviewer`; registrar **apenas resultados efetivamente observados**. O k6 integrado pelo PR #70 é sobre **cadastro B16** e não substitui a prova de concorrência financeira B17.

## Fronteira de autorização

A escolha A foi aprovada expressamente pelo responsável técnico. A implementação pelo agente `backend` está **condicionada** à integração humana do PR de liberação e à verificação literal de `origin/develop:TASKS.md`. Revisor/agent não fazem merge. **B15, F06 e F08 permanecem bloqueadas**, conforme suas próprias dependências; esta decisão não aprova política PTAX nem movimentação financeira real.

**Evidência de escopo:** aprovação de política e autorização para preparar a liberação documental; nenhum teste B17 foi executado ou código B17 implementado por esta ADR.
