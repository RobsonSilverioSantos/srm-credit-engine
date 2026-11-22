# F08 — Interface de importação de recebíveis em lote

**Status:** planejada P1 e bloqueada até API B16 integrada.  
**Issue:** [#64](https://github.com/RobsonSilverioSantos/srm-credit-engine/issues/64).  
**Dependências:** F05 integrada pelo PR #61; B16 com contratos e autorização homologados e implementação integrada à `develop`.  
**Executor proposto:** agente `frontend`, revisores `architect`, `qa` e `reviewer`; worktree independente de F07/F06.

## Objetivo de experiência

Criar `Operações / Importação em lote` no menu operacional, com os seguintes estados:
1. **Obter modelo:** link de modelo CSV UTF-8 `;` (compatível com planilhas) e, após suporte real na API, modelo XLSX com validação das colunas e instruções claras. Sem dados de cedentes reais e sem exemplos com UUIDs que pareçam existentes.
2. **Selecionar arquivo:** indicar extensões efetivamente suportadas pela B16, limites, formato decimal canônico `25000.00`, período ISO e tipo de modalidade. Não fazer parsing autoritativo financeiro no React.
3. **Conferir lote:** enviar arquivo à porta de aplicação de pré-validação, mostrar totais, referências, cedentes, modalidade, valor de face, prazo, moeda escolhida para **simulação**, deságio e valor presente **indicativos**; exibir erros por linha/campo e permitir reenvio corrigido.
4. **Confirmar cadastro:** habilitar somente com todas as linhas válidas e revisão explícita; apresentar resumo antes de enviar o payload canônico confirmado, manter uma chave de idempotência estável para retry do mesmo comando e bloquear duplo clique. Não liquidar automaticamente nem criar recebíveis na etapa de prévia.
5. **Resultado:** exibir `idLote` e contagem de recebíveis cadastrados, IDs para auditoria, status e eventuais avisos. Falhas de conexão com resultado incerto não autorizam nova chave automática para repetir inserção.
6. **Acessibilidade:** MUI Core, foco, teclado, etiquetas, progresso anunciável, linhas inválidas navegáveis, viewport mobile e sessão JWT somente em memória, respeitando `403` e `401`.

## Arquitetura frontend

- `:dominio` contém tipos de arquivo canônico, lote, resultado de linha e estados válidos; `:aplicacao` contém caso de uso/porta `prevalidarLote` e `confirmarLote`, incluindo proteção contra duplicidade; `:infraestrutura` adapta upload `multipart/form-data`, JSON BFF e tratamento de status; `:apresentacao` contém página e componentes.
- Nenhuma lógica de Strategy, arredondamento ou consulta a taxa no navegador; valores financeiros chegam como **strings decimais** da API e são somente formatados por locale.
- Não persistir conteúdo do arquivo, token, dados de recebíveis ou histórico bancário no `localStorage`; não enviar credenciais em URL/logs.

## Critérios de aceite

- [ ] Modelo publicado com cabeçalhos B16, instruções e formatos coerentes com API.
- [ ] Upload CSV e XLSX somente conforme suporte backend aprovado; erro por linha/campo e totais visíveis.
- [ ] Prévia não faz escrita; confirmação única com replays idempotentes e estado incerto seguro.
- [ ] C1–C3 podem compor prévias de demonstração sem contaminar taxas de produção.
- [ ] Testes React para arquivo inválido, cancelamento, retry, expiração de token, telas estreitas e navegação.
- [ ] Lint/test/build/CI e evidências reais no PR; merge humano.

**Limite:** a importação **cadastra recebíveis**, não liquida automaticamente. Uma operação futura de liquidação de lote exigirá decisão e tarefa financeiras próprias.
