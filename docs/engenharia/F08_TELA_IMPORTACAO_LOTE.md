# F08 — Interface de importação e liquidação explícita de recebíveis em lote

**Status:** planejada P1 e bloqueada até as APIs B16 (importação) e B17 (liquidação em lote) integradas.  
**Issue:** [#64](https://github.com/RobsonSilverioSantos/srm-credit-engine/issues/64).  
**Dependências:** F05 integrada pelo PR #61; B16 e B17 com contratos e autorização homologados e implementações integradas à `develop`. A etapa de importação pode ser desenvolvida separadamente apenas se uma atualização de escopo e gates for aprovada.  
**Executor proposto:** agente `frontend`, revisores `architect`, `qa` e `reviewer`; worktree independente de F07/F06.

## Objetivo de experiência

Criar `Operações / Importação em lote` no menu operacional, com **cadastro e liquidação como ações distintas**, nos seguintes estados:
1. **Obter modelo:** link de modelo CSV UTF-8 `;` (compatível com planilhas) e, após suporte real na API, modelo XLSX com validação das colunas e instruções claras. Sem dados de cedentes reais e sem exemplos com UUIDs que pareçam existentes.
2. **Selecionar arquivo:** indicar extensões efetivamente suportadas pela B16, limites, formato decimal canônico `25000.00`, período ISO e tipo de modalidade. Não fazer parsing autoritativo financeiro no React.
3. **Conferir lote:** enviar arquivo à porta de aplicação de pré-validação, mostrar totais, referências, cedentes, modalidade, valor de face, prazo, moeda escolhida para **simulação**, deságio e valor presente **indicativos**; exibir erros por linha/campo e permitir reenvio corrigido.
4. **Confirmar cadastro:** habilitar somente com todas as linhas válidas e revisão explícita; apresentar resumo antes de enviar o payload canônico confirmado, manter uma chave de idempotência estável para retry do mesmo comando e bloquear duplo clique. Não liquidar automaticamente nem criar recebíveis na etapa de prévia.
5. **Resultado do cadastro:** exibir `idLote` e contagem de recebíveis cadastrados, IDs para auditoria, status e eventuais avisos. Falhas de conexão com resultado incerto não autorizam nova chave automática para repetir inserção.
6. **Conferir liquidação:** após cadastro, acessar prévia da B17 por lote, mostrando recebíveis `DISPONIVEL`, moedas, VP, deságio e câmbio **indicativos**. Não sugerir que a importação realizou qualquer liquidação.
7. **Confirmar liquidação:** mostrar todas as simulações e um resumo financeiro por moeda; **bloquear `Liquidar lote` enquanto alguma simulação não estiver concluída e válida**. Após conferência explícita, o botão `Liquidar lote`, distinto de `Confirmar cadastro`, abre modal com resumo, quantidade e aviso de preços indicativos; somente `Confirmar liquidação` aciona o backend revalidando elegibilidade/taxas, com chave de idempotência estável e sem duplo clique. Exibir status por item, inclusive conflito, repetição de recebível liquidado e falha parcial/rollback conforme B17 aprovada. Nunca inferir sucesso de item por HTTP 2xx parcial.
8. **Acessibilidade:** MUI Core, foco, teclado, etiquetas, progresso anunciável, linhas inválidas navegáveis, viewport mobile e sessão JWT somente em memória, respeitando `403` e `401`.

## Arquitetura frontend

- `:dominio` contém tipos de arquivo canônico, lote, resultado de linha e estados válidos; `:aplicacao` contém caso de uso/porta `prevalidarLote` e `confirmarLote`, além de `previsualizarLiquidacaoLote` e `liquidarLote`, com proteção contra duplicidade e idempotência específica de cada operação; `:infraestrutura` adapta upload `multipart/form-data`, JSON BFF e tratamento de status; `:apresentacao` contém página e componentes.
- Nenhuma lógica de Strategy, arredondamento ou consulta a taxa no navegador; valores financeiros chegam como **strings decimais** da API e são somente formatados por locale.
- Não persistir conteúdo do arquivo, token, dados de recebíveis ou histórico bancário no `localStorage`; não enviar credenciais em URL/logs. A prévia não reserva taxas, e a confirmação financeira usa a cotação elegível no instante efetivo segundo B17. Mostrar explicitamente diferenças entre prévia e resultado efetivo.

## Massa de teste e inspeção

Disponibilizar ao operador os dois [arquivos de aferição](../validacao/lotes/README.md), cada um com 200 linhas, e testar a identificação de quatro referências iguais entre A e B: duas nas mesmas posições, duas fora de ordem. Após liquidar o A, a pré-validação do B apresenta quatro `JA_LIQUIDADO` **antes** de qualquer botão de confirmação. A interface permite identificar as quatro linhas, baixar erros e corrigir o arquivo; não omite títulos já existentes. A comparação ocorre no backend por referência estável de negócio por organização, não por valor/preço coincidentes.


## Critérios de aceite

- [ ] Modelo publicado com cabeçalhos B16, instruções e formatos coerentes com API.
- [ ] Upload CSV e XLSX somente conforme suporte backend aprovado; erro por linha/campo e totais visíveis.
- [ ] Prévias de importação e liquidação não fazem escrita; **100% das simulações devem estar concluídas e válidas antes de habilitar `Liquidar lote`**; cadastro e liquidação exigem confirmações explícitas separadas, replays idempotentes e tratamento seguro de estado incerto.
- [ ] `Liquidar lote` aparece apenas após cadastro confirmado; conflitos do **mesmo recebível** e resultado atômico/parcial são apresentados segundo B17, sem inventar sucesso.
- [ ] C1–C3 podem compor prévias de demonstração sem contaminar taxas de produção.
- [ ] Testes React para arquivo inválido, cancelamento, retry, expiração de token, telas estreitas e navegação.
- [ ] Lint/test/build/CI e evidências reais no PR; merge humano.

**Limite:** o upload **não liquida automaticamente**. A liquidação em lote é operação financeira **separada**, implementada pela B17 somente após decisão humana sobre atomicidade, falhas e replay. F08 integra as duas experiências por etapas, não une suas transações.
