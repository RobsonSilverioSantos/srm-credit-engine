# F07 — Extrato com nome do cedente e detalhes em janela modal

**Prioridade:** P1 — melhoria de usabilidade aprovada para o fluxo de extrato.  
**Issue:** [#62](https://github.com/RobsonSilverioSantos/srm-credit-engine/issues/62).  
**Dependências:** F05 (PR #61 integrado), B08-E01 (consulta SQL paginada), B14 (identificação de cedentes).  
**Coordenação:** agente `frontend` como executor de uma única feature/worktree; `backend` na integração do contrato; `architect`, `qa` e `reviewer` revisam. Uma única branch/PR, sem edições concorrentes no mesmo worktree.

## Problema reproduzido

No grid de liquidações da F05, a coluna `ID do cedente` mostra UUID técnico e a coluna `Snapshot` usa `<details><summary>Detalhes</summary>` dentro da célula. A expansão aumenta a altura da linha e torna o restante da tabela difícil de consultar. A captura de execução foi inspecionada, mas não integra os arquivos versionados desta especificação.

O contrato de `ItemExtrato` atualmente possui `cedenteId`, não `cedenteNome`. A consulta `ExtratoJdbc` já relaciona `liquidacoes` e `recebiveis`, mas não junta `cedentes`. Portanto, o nome **não deve ser inventado no frontend** nem resolvido por chamadas individuais por linha.

## Contrato backend e persistência

1. Acrescentar `cedenteNome: String` como **campo aditivo** em `backend/aplicacao/.../extrato/aplicacao/ItemExtrato.kt`, `ItemExtratoResposta.kt`, `ExtratoHttp` e `frontend/src/dominio/extrato.ts`. Preservar `cedenteId` nos retornos existentes e no filtro atual por UUID, sem alteração de rota, paginação ou query params.
2. Obter `cedenteNome` por JOIN único de `liquidacoes → recebiveis → cedentes`, incluindo `c.organizacao_id = l.organizacao_id` e `c.id = r.cedente_id` nas condições de ligação. Preservar o filtro `l.organizacao_id = :organizacao` derivado do principal. Não introduzir consultas N+1, logar documentos ou afrouxar o isolamento.
3. A nomenclatura exibida será **nome atual do cadastro do cedente**, pois o snapshot existente não guarda seu nome histórico. Não afirmar que o nome foi congelado na data da operação. A identidade `cedenteId` continua estável para auditoria. Se surgir exigência de nome histórico imutável, especificar migração/snapshot separado, sem reconstruir retroativamente nomes sem evidência.

## Apresentação React/MUI

1. Alterar o cabeçalho `ID do cedente` para **`Cedente`** e exibir `cedenteNome` com quebra de linha controlada; manter o UUID disponível no modal, inclusive para copiar quando necessário.
2. Na coluna de ação, apresentar botão `Ver detalhes` com rótulo acessível por liquidação; ao clicar, abrir **`Dialog` do Material UI** fora da célula, em largura responsiva e com `scroll="paper"`, título `Detalhes da liquidação`, ícone/botão Fechar e suporte Escape.
3. O diálogo deve listar campos existentes do snapshot por seções legíveis: recebível/identidade, valores, taxas/spread, câmbio, vigências e momentos UTC/local. Preservar todos os campos atualmente visíveis; sem novo request HTTP nem dados inventados.
4. Gerenciar uma única seleção no estado de `PaginaExtrato`; ao fechar, devolver foco ao botão que abriu quando disponível; comportamento de fechamento ao trocar de página, filtro ou sessão precisa ser previsível e testado.
5. Mostrar modalidades com rótulos humanos (`CHEQUE_PRE_DATADO` → `Cheque pré-datado`; `DUPLICATA_MERCANTIL` → `Duplicata mercantil`), preservando o enum canônico da API.

## Testes e aceite

- [ ] A tabela conserva alturas de linha estáveis independentemente do painel de detalhes, com modal responsivo e acessível (teclado, foco, Escape, tela estreita).
- [ ] Nome exibido corresponde ao cedente e organização da liquidação; casos com dois tenants e nomes iguais/diferentes cobertos por testes PostgreSQL; retorno aditivo não remove `cedenteId`.
- [ ] API/BFF e filtros/paginação mantidos; ausência de N+1 e regressão SQL confirmadas.
- [ ] Todos os campos de snapshot e seus rótulos permanecem acessíveis e os instantes preservam ISO original para auditoria.
- [ ] Regressões financeiras C1–C3, frontend lint/test/build e CI aprovadas; evidências citadas no PR.
- [ ] Só o responsável técnico integra PR à `develop`; nenhum agente altera `SPEC.md` ou política financeira por conta própria.

**Limite:** correção de exibição e leitura do extrato. Não muda transações, precificação, status financeiro ou semântica dos dados históricos.
