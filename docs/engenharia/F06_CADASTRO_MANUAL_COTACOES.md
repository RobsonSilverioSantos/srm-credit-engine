# F06 — Cadastro manual e consulta administrativa de cotações

**Situação:** planejada (P1), implementação não iniciada.  
**Rastreamento:** [issue #55](https://github.com/RobsonSilverioSantos/srm-credit-engine/issues/55); `TASKS.md`, F06.  
**Executor:** agente `frontend`; revisores `architect`, `qa`, `reviewer`.  
**Gate:** **B15 e F05 integradas em `origin/develop`** e situação literal da F06 **`Liberada para iniciar`** na mesma branch. Como F05 define o menu e B15 os contratos, não iniciar a F06 antes de ambos.

## 1. Estado atual e problema

O caso de uso Kotlin `TaxasPorta.registrarCotacao(taxaBrlPorUsd, vigenteEm)` já permite criar uma cotação na aplicação, mas **não há endpoint REST/BFF nem formulário administrativo de câmbio**. O bootstrap DEMO insere cotações fictícias diretamente no banco; isso não é entrada manual para operadores.

A B15 passa a fornecer integração PTAX de fechamento **e** entrada manual segura, com proveniência e políticas distintas; a F05 reorganiza a navegação principal. A F06 acrescenta a UI após os contratos desses PRs estarem estáveis.

**Política B15 aprovada em 22/11/2026, ainda não implementada (ADR-026):** modo padrão `BCB_PTAX` Fechamento VENDA, último boletim elegível até **96h corridas inclusive**; exceção `MANUAL` requer cadastro e ativação/autorização explícita por MASTER/ADMIN da organização, elegível da autorização até **00:00 exclusive do dia seguinte no fuso `America/Sao_Paulo`**. O OPERADOR só pode consumir a exceção já autorizada. Nunca substituir PTAX por MANUAL ou DEMO automaticamente. A F06 deve exibir em tela fonte efetiva, expiração, escopo de autorização e estado ativo/inativo conforme contratos B15 reais; não arbitrar origem por conta própria.

## 2. Escopo da interface

- **Menu "Câmbio"** visível apenas aos perfis com capacidade de visualizar; área `Câmbio / Histórico` e `Câmbio / Nova cotação manual` com breadcrumbs responsivos. Evitar repetir a lógica de menu/layout da F05.
- Formulário somente para usuários autorizados a cadastrar/ativar, segundo capacidades obtidas do backend e verificadas em todo endpoint: política aprovada `MASTER` e `ADMIN`; `OPERADOR` não cadastra nem ativa, somente pode consumir cotação autorizada.
- Campos: par BRL/USD fixo no MVP, **taxa BRL por 1 USD** (ex.: `5,4321`) como string decimal positiva e escala compatível com o banco; vigência/registro e autorização com instante confiável, sem agendamento futuro ou vigência retroativa; validade até **meia-noite exclusiva do dia seguinte** em `America/Sao_Paulo`, segundo contrato B15; **justificativa obrigatória**. Registro e **ativação explícita** são ações distintas de negócio, ainda que realizadas pelo mesmo MASTER/ADMIN. Não confundir fonte `MANUAL` com `BCB_PTAX` ou `DEMO`. Exibir fonte e definição de compra/venda quando aplicável.
- Usar campo numérico localizado no formato pt-BR/en-US e converter apenas para **string decimal canônica**; nunca calcular taxas com `Number`/`parseFloat`. Suportar colagem, seleção, teclado móvel, validações e mensagens acessíveis.
- Exibir resumo e solicitar **confirmação explícita** antes de registrar a cotação; desabilitar reenvio durante o processamento, apresentar o ID da versão criada e o momento de vigência.
- Histórico paginado/filtrado por período, origem e situação; mostrar identificador, taxa, origem, boletim/lado para BCB, autor da inclusão MANUAL, justificativa, vigência, instante de registro e validade; distinguir cotação elegível, vencida e não publicada.
- Histórico **somente leitura**: não editar nem excluir versões anteriores. Para corrigir erro, cadastrar **nova versão**; não sobrescrever cotação anterior.
- Erros: validação `400`, não autenticado `401`, sem permissão `403`, conflito de versão/vigência `409`, indisponibilidade `503` e retry seguro, sem novo envio automático que cause duplicação.
- A **seleção da cotação para liquidação é do backend**; o frontend não escolhe a última por conta própria nem mistura PTAX/MANUAL/DEMO. Exibir fonte e validade do valor retornado sem afirmar que a PTAX equivale a preço negociado.

## 3. Contrato a reutilizar da B15

A B15 deve entregar contratos documentados para **registrar manualmente** e **consultar histórico**, com autenticação/autorizações efetivas, validação de string decimal, proveniência/auditoria e seleção por fonte explícita. Exemplo de rotas a confirmar durante implementação B15: `POST /api/v1/cotacoes-cambiais/manuais` / `POST /bff/v1/cotacoes-cambiais/manuais`, `GET /bff/v1/cotacoes-cambiais`. **As rotas são proposta, não contrato já existente.**

Não criar API fictícia na F06 nem modificar backend ou Flyway em seu worktree. A B15 é fonte de verdade para nomes de campos, regras de uso, janelas e condições de erro.

## 4. Testes e aceite

- [ ] Menu e breadcrumbs em desktop/mobile, acesso direto, teclado e leitores de tela.
- [ ] RBAC: cadastro acessível e permitido apenas a perfis autorizados; `401`, `403` e expiração tratados; sessão JWT somente em memória.
- [ ] Entrada `5,4321` em pt-BR vai como `5.4321` (string) e leitura equivalente em en-US; testes de zeros, negativo, limites de escala, dígitos grandes, copiar/colar, edição/caret.
- [ ] Vigência/expiração corretamente interpretadas com fuso, UTC transportado e horário local visível; não aceitar ambiguidade silenciosa.
- [ ] Confirmação antes de registrar; sem múltiplos envios, sem edição/exclusão, sem sobreposição de fontes.
- [ ] Consulta de histórico com paginação, origem, estado vazio, carregando e falhas; autoria/justificativa visíveis somente a perfis autorizados.
- [ ] Integração com contrato real B15 e navegação F05; sem alterar C1/C2/C3, política de taxa ou operações já liquidadas.
- [ ] `cd frontend && npm ci && npm run lint && npm test && npm run build`, CI e PR de implementação para `develop` com revisão independente.

## 5. Limites e governança

A F06 é evolução **exclusivamente frontend**; o servidor B15 mantém as regras de negócio, autorização, precisão e histórico. Não ativar formulário sem endpoint B15 integrado. Não iniciar implementação enquanto B14/B15/F05 não estiverem integradas e status F06 não for liberado no `TASKS.md`. Um worktree exclusivo; merge exclusivamente humano.
