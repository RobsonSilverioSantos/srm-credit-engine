# ADR-024 — Importação e cadastro atômico de recebíveis em lote

**Estado:** decisão aprovada para a B16; implementação integrada em `develop` pelo [PR #71](https://github.com/RobsonSilverioSantos/srm-credit-engine/pull/71) em 22/11/2026 (merge `186786e27320e83700a81e3922c72d096cefcb86`). Reconciliação factual G02, sem alteração da decisão.

**Data:** 22/11/2026.  
**Escopo:** cadastro de recebíveis por arquivo; **não** determina a política de liquidação financeira em lote da B17.  
**Referências:** [B16](../engenharia/B16_IMPORTACAO_RECEBIVEIS_LOTE.md), [issue #63](https://github.com/RobsonSilverioSantos/srm-credit-engine/issues/63), [B17](../engenharia/B17_LIQUIDACAO_LOTE_CONCORRENCIA.md), [fixtures A/B](../validacao/lotes/README.md).

## Contexto

O requisito operacional de importar múltiplos recebíveis deve permitir validar e conferir dados antes de efetuar cadastro, inclusive detectar duplicidades entre arquivos distintos. Na aprovação desta decisão, a solução cadastrava recebíveis apenas individualmente; o cadastro em lote foi integrado posteriormente pelo PR #71. O cadastro do lote não equivale à liquidação dos seus títulos.

## Decisão de atomicidade — aprovada pelo responsável técnico

**O cadastro do lote adota semântica tudo-ou-nada**: a confirmação persiste todos os recebíveis elegíveis ou **nenhum**, sob uma transação PostgreSQL. Se qualquer item contiver dado inválido, cedente de outra organização, referência externa duplicada, conflito de concorrência ou falha de persistência, a operação deve ser rejeitada integralmente e reverter qualquer inserção parcial, inclusive registros de controle de lote e referências.

A pré-validação do arquivo não grava recebíveis, liquidações nem snapshots financeiros. O cadastro requer confirmação explícita posterior e revalidação no servidor. O upload **jamais liquida automaticamente**. Uma prévia de deságio/câmbio é apenas indicativa; taxa/cotação e autorização financeira são reavaliadas na etapa B17.

### Parâmetros operacionais iniciais da implementação B16

- Formatos solicitados: **CSV UTF-8 delimitado por `;` e XLSX padronizado**, ambos convertidos ao mesmo comando canônico no adaptador; XLSX pode ser desenvolvido após o parser CSV dentro **da mesma B16 e do mesmo PR**. A API não deve anunciar suporte XLSX até sua implementação e seus testes estarem prontos. Desabilitar macros, fórmulas, links externos, XML externo e objetos incorporados.
- **Limites técnicos iniciais configuráveis:** 200 linhas de dados por arquivo e 2 MiB; tratar excesso antes de materializar entradas desnecessárias. Esses parâmetros comportam cada massa de 200 linhas, sem tornar limites de produção homologados.
- Identidade de recebível: vínculo de referência externa **única por organização entre todos os lotes** `(organizacao_id, referencia_externa)` com `UNIQUE` transacional e relação estável com `recebivel_id`. Não inferir identidade apenas de valores, prazo, modalidade ou moeda iguais.
- Retorno de pré-validação por linha: `NOVO`, `JA_CADASTRADO`, `JA_LIQUIDADO` e erros estruturados, incluindo linha/campo. **Nenhuma duplicidade é ignorada automaticamente**. Repetição dentro do arquivo ou entre lotes impede a confirmação do lote inteiro.
- Idempotência da confirmação: chave HTTP e hash do comando canônico escopados por organização/tipo; mesmo comando e mesma chave repetidos retornam **o mesmo resultado original**, sem nova inserção; mesma chave e payload divergente → `409`. Com chaves distintas para referência externa já persistida, rejeitar duplicidade. Pré-validação não reserva chave nem garante exclusividade até a confirmação.
- Confirmação somente para lote com todas as linhas cadastralmente válidas. Erros de simulação opcional de USD por ausência de cotação **não invalidam automaticamente o cadastro BRL de um título**; devem ser exibidos como impedimento à **liquidação futura**, cuja confirmação exige simulações válidas conforme B17.
- A camada de aplicação e o domínio não dependem de parser CSV/XLSX, HTTP, Spring ou JDBC; portas/adaptadores hexagonais e organização derivada da autenticação. Não inserir `organizacaoId` arbitrário por payload.
- Migration Flyway nova com a próxima versão livre, sem reescrever versões aplicadas; verificar numeração e conflitos com B15 antes do PR de implementação.

## Evidência de aceite e implicações

A massa [Lote A](../validacao/lotes/SRM_LOTE_A_200_RECEBIVEIS.csv) possui 200 referências novas; [Lote B](../validacao/lotes/SRM_LOTE_B_200_RECEBIVEIS_COM_4_REPETIDOS.csv) possui 200 linhas, com quatro referências já presentes no primeiro (duas na mesma posição e duas realocadas). A integração deverá demonstrar em PostgreSQL real: confirmação de A com 200 títulos, rejeição **integral de B** se as quatro referências existirem, nenhuma escrita parcial, relatório explícito dos quatro conflitos, e importação dos 196 itens restantes **somente após arquivo corrigido e nova confirmação**. O caso `JA_LIQUIDADO` somente é aplicável depois que A tiver sido liquidado por mecanismo existente ou futuro B17.

Essa sequência prova deduplicação e atomicidade de cadastro; **não demonstra por si só conflito de optimistic locking de liquidação**. A B17 testará duas operações financeiras simultâneas distintas sobre o mesmo recebível.

## Alternativas rejeitadas e fronteiras

- **Cadastro parcial automático:** rejeitado. Evita estado misto e elimina a necessidade de determinar quais linhas sobreviveram a uma falha.
- **Ignorar linhas duplicadas silenciosamente:** rejeitado. Esconde problemas de origem do lote e reduz a auditabilidade.
- **Liquidar imediatamente ao carregar o arquivo:** rejeitado. Mistura autorização financeira e importação; B17 define prévia completa e confirmação financeira separada.
- **Determinar atomicidade da liquidação B17 a partir da B16:** não autorizado. A liquidação poderá ter política própria (tudo-ou-nada ou parcial), ainda pendente de decisão.

**Evidência na aprovação:** a decisão referia-se ao **cadastro atômico**, com validação funcional, migrations e testes ainda futuros naquele momento. **Atualização G02 (22/11/2026):** o PR #71 integrou a B16 e a migration V6, com CI backend/PostgreSQL, frontend e Compose aprovada; os testes não foram reexecutados nesta atualização documental. Scripts QA k6 integrados pelo PR #70 não equivalem a execução real, que permanece pendente. Este ADR isoladamente não comprova execução da API ou liquidação financeira; a política da B17 continua independente e bloqueada.
