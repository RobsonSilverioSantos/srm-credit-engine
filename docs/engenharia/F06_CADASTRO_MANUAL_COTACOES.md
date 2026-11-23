# F06 — Interface administrativa de câmbio e histórico de cotações

**Situação:** **F06 integrada em `develop` pelo [PR #92](https://github.com/RobsonSilverioSantos/srm-credit-engine/pull/92)** (merge `9153e5d8cefb51008e19b5089664bfbb9d1c4b5e` em 22/11/2026), após F05 PR #61, B15 PR #90 e liberação documental PR #91. Implementação encerrada; **homologação integrada real ainda pendente**.
**Rastreamento:** [issue #55](https://github.com/RobsonSilverioSantos/srm-credit-engine/issues/55); `TASKS.md`, F06.
**Executor:** `frontend`; revisões `architect`, `qa`, `reviewer` em worktree própria.
**Referências:** [ADR-026](../adr/ADR-026-ptax-96h-manual-diaria.md), [contrato B15 integrado](../../backend/docs/B15_CAMBIO.md), `CambioHttp.kt`, `CotacaoCambialResposta.kt`, `SelecaoFonteCambial.kt`, documentação F05.

## 1. Escopo e regras aprovadas

A F06 cria a seção administrativa **Câmbio**, com histórico de cotações, histórico de seleções/autorização, cadastro de cotação MANUAL, autorização explícita do uso MANUAL, retorno explícito à PTAX e sincronização administrativa de PTAX. Reutilizar menu responsivo, breadcrumbs, capacidades do usuário e sessão JWT **somente em memória** da F05; manter a arquitetura hexagonal de frontend (domínio, aplicação e infraestrutura/adaptadores).

- Fonte padrão **BCB_PTAX**, boletim Fechamento **VENDA**, último conhecido até **96 horas corridas inclusive** desde referência oficial. Feriados/fins de semana não prorrogam esse limite.
- MANUAL exige **duas ações distintas**: cadastrar cotação e, depois, selecionar/autorizá-la para a organização. Apenas `MASTER`/`ADMIN` podem realizar ambas; `OPERADOR` somente consulta e utiliza a seleção autorizada, sem visualizar autoria/justificativa de auditoria.
- A cotação MANUAL é elegível **desde a autorização até a próxima meia-noite exclusiva de `America/Sao_Paulo`**; o servidor determina instantes. Cotação cadastrada não fica automaticamente ativa. Expiração não provoca retorno automático à PTAX; indicar situação sem propor fallback fictício.
- DEMO é exclusivamente modo de aferição/testes. **Nunca oferecer** alternativa DEMO como contingência administrativa, nem selecionar a fonte no frontend por ordenação de timestamps; a autoridade sobre fonte efetiva é o backend.
- Histórico somente inclusão; nenhuma UI para editar, excluir ou retroagir cotação/seleção. Para corrigir, cadastrar nova versão e autorizar explicitamente. Uma cotação MANUAL não pode ser reutilizada em nova autorização.

## 2. Contratos B15 existentes — não inventar endpoints

Prefixo BFF `/bff/v1/cotacoes-cambiais`, disponível também sob `/api/v1/cotacoes-cambiais`, com JWT autenticado, organização derivada da identidade. Os nomes abaixo são **contratos já integrados**, não propostas:

| Método/rota BFF | Autorização | Entrada e resultado |
|---|---|---|
| `POST /manuais` | MASTER/ADMIN | JSON `{ "taxaBrlPorUsd": "5.4321", "justificativa": "Motivo operacional" }`; `201`, cotação cadastrada **inativa** |
| `POST /selecoes` | MASTER/ADMIN | Autorizar MANUAL: `{ "fonte": "MANUAL", "cotacaoId": "<UUID>", "justificativa": "Autorização explícita" }`; ou retornar: `{ "fonte": "BCB_PTAX", "justificativa": "Retorno explícito" }`; `201`, registro de seleção |
| `POST /ptax/sincronizacoes` | MASTER/ADMIN | Sem body; solicita atualização controlada dos sete dias anteriores, pode falhar `503`, não altera a fonte selecionada |
| `GET ?pagina=0&tamanho=20&fonte=MANUAL` | Perfis autenticados | Histórico paginado de cotações com filtro opcional por fonte |
| `GET /selecoes?pagina=0&tamanho=20` | Perfis autenticados | Histórico paginado de autorizações/seleções, mais recentes primeiro |

**Atenção ao contrato:** `POST /manuais` **não aceita `vigenteEm`, `validaAte`, `origem`, `autorId` nem horário escolhido pelo cliente**; os instantes e a proveniência vêm do backend. Entrada decimal textual canônica exige padrão `[0-9]{1,10}(\.[0-9]{1,10})?`, taxa positiva, justificativa não vazia até 1000 caracteres. Mostrar máscara localizada `5,4321` no pt-BR, enviando `"5.4321"` como **string**. Não calcular câmbio com `Number`, `parseFloat` ou binário em ponto flutuante no frontend.

Resposta `CotacaoCambialResposta`: `id`, `taxaBrlPorUsd` (string), `moedaBase`, `moedaCotada`, `fonte`, `lado`, `versao`, `referencia`, `observadaEm`, `dataBoletim`, `horarioOriginal`, `boletim`, `fuso`, `autorId` e `justificativa` (os últimos dois **nulos para OPERADOR**).

Resposta de seleção `SelecaoFonteCambial`: `id`, `fonte`, `cotacaoId`, `autorizadoEm`, `validaAte`, `politicaId`, `autorId`, `justificativa`, com os dois campos de auditoria restritos ao administrador. Os timestamps trafegam como instantes UTC; exibir localmente com fuso explicitado. `validaAte` não deve ser recalculado pelo navegador como autoridade financeira. Listas incluem `itens`, `pagina`, `tamanho` e `total`; página inicial zero, tamanho de 1 a 100.

Tratamento de erros: `400` para valor/payload inválido, `401` não autenticado, `403` permissão insuficiente, `409` conflito de seleção/cotação, `503` sincronização indisponível ou ausência de câmbio elegível. Garantir retry seguro, proteção contra reenvio ao recuperar resposta incerta e desabilitar submissão duplicada. Não armazenar JWT, CNPJ ou payloads sensíveis em URL, storage ou logs.

## 3. Experiência e estados da interface

- Área **Câmbio / Histórico** com filtros, paginação, fonte, lado, taxa, data de boletim/referência, instante de observação, seleção atual, vencimento exibido segundo os campos do servidor e histórico de autorizações. Sinalizar dados não disponíveis sem presumir que PTAX de mercado é preço negociado.
- Área **Câmbio / Nova cotação manual** somente para MASTER/ADMIN, com moeda fixa BRL por USD, campo decimal localizado, justificativa e confirmação de cadastro; após sucesso, apresentar ID e aviso inequívoco: **cadastrada, ainda não autorizada**.
- Ação **Autorizar uso da MANUAL** separada, escolhendo uma cotação MANUAL cadastrada da organização, com `cotacaoId`, justificativa e confirmação própria. Mostrar autorização, validade até a meia-noite local e necessidade de nova prévia em operações financeiras.
- Ação **Retornar à PTAX** exige justificativa e confirmação explícitas, sem alterar histórico. Expor **Sincronizar PTAX** exclusivamente aos administradores, com estado de execução, resultado, indisponibilidade e proteção contra múltiplos envios.
- Navegação responsiva desktop/mobile, teclado, foco, leitores de tela, máscaras, colagem, seleção e restauração de estado; não interferir no fluxo de simulação/liquidação nem reimplementar cálculo financeiro.

## 4. Testes realizados e critérios de homologação pendentes

**Evidência do PR #92:** o executor reportou `npm ci`, `npm run lint`, `npm test` (**221 testes em 16 arquivos**), `npm run build` e validações Chrome desktop/mobile (390/1280 px) com **BFF simulado**, incluindo fluxo separado de cadastro MANUAL e autorização, RBAC, histórico e regressão F09. Os revisores `architect`, `qa` e `reviewer` emitiram pareceres; QA reportou **55 testes focados**. A execução do GitHub Actions vinculada ao commit de feature teve **4/4 jobs aprovados**. Essas evidências não equivalem a uma jornada real Chrome→BFF B15→PostgreSQL; manter os itens abaixo para aferição integrada sem declará-los fechados retroativamente.


- [ ] BFF real ou mocks fiéis aos contratos de B15: registrar MANUAL **sem ativar**, autorizar separadamente e voltar explicitamente a PTAX; não enviar propriedades inexistentes nem acionar sincronização por digitação.
- [ ] RBAC: MASTER/ADMIN podem cadastrar/autorizar/sincronizar; OPERADOR não; autor/justificativa ocultos em consultas; sessão JWT em memória.
- [ ] Decimal localizado pt-BR/en-US como string, máscara/colar/seleção/caret, zeros, negativos, comprimento e escala; impedir cálculo monetário com float/Number.
- [ ] Validade à meia-noite `America/Sao_Paulo`, transição de dia e datas UTC: usar `validaAte` do backend como autoridade, sem seleção automática PTAX/MANUAL/DEMO.
- [ ] Páginas zero-based e tamanho limite, filtros, erros 400/401/403/409/503, rede, loading, idempotência operacional e resposta incerta.
- [ ] Confirmações separadas, histórico imutável, acessibilidade mobile/desktop e regressões das telas F05/F08/F09.
- [ ] `cd frontend && npm ci && npm run lint && npm test && npm run build`, registrar resultados reais, solicitar revisões `architect`, `qa`, `reviewer`, PR só para `develop` e **sem merge pelo agente**.

## 5. Limites e governança

A F06 foi integrada isoladamente, sem alterar backend, schema Flyway, política financeira, fontes de autoridade, snapshots existentes, contratos B15 ou implementação da F09. B15 e F09 foram integradas nos PRs #90 e #89. O gate documental PR #91 foi satisfeito antes de iniciar a feature. **Integração real ponta a ponta da UI F06 com BFF/PostgreSQL, consulta BCB ao vivo e homologação humana/mobile físico permanecem pendentes**; a execução da CI e os testes de browser com BFF simulado não substituem essas provas. Respostas incertas de POST exigem reconciliação humana pelo histórico, pois a B15 não dispõe de idempotência para essas escritas administrativas; o bloqueio preventivo da UI vale para a sessão atual, não entre abas ou após recarregamento.
