# F05 — Navegação autenticada, formatação e clareza de operações no frontend

**Estado:** planejada (P1); implementação não iniciada.\
**Fonte de verdade:** `TASKS.md`, F05; rastreamento [issue #50](https://github.com/RobsonSilverioSantos/srm-credit-engine/issues/50).\
**Executor:** agente `frontend` em worktree exclusivo; revisores `architect`, `qa`, `reviewer`.\
**Gate de início:** aguardar a **B14 integrada em `origin/develop`**, além do PR de registro F05 integrado. A B14 altera o cadastro de cedentes no mesmo componente `Aplicacao.tsx`; evitar commits simultâneos e merges com conflitos que reintroduzam validação antiga. O planejamento F05 pode ser revisado em paralelo, mas **não** a implementação.

## Evidências do frontend atual

- Em `frontend/src/apresentacao/Aplicacao.tsx`, o campo `Valor de face em BRL` usa `type="text"`, `inputMode="decimal"` e `normalizarEntradaMonetaria` para validar, porém não aplica máscara visual enquanto o usuário digita.
- `Valores` apresenta a propriedade `Simulacao.referencia` como string ISO (por exemplo `2026-11-21T21:15:58.766998Z`) e `cotacaoValidaAte` da mesma forma. A referência da simulação **não equivale** ao instante `Liquidacao.liquidadaEm`; os campos têm semânticas distintas.
- `Aplicacao.tsx` reúne cadastro de cedentes, simulação, cadastro de recebível, confirmação de liquidação e `PaginaAdministracaoUsuarios` em um painel longo.
- `frontend/src/extrato.tsx` monta `AcessoExtrato` em `extrato.html` com login independente; porém `PaginaExtrato` já recebe `token`, `porta` e `aoExpirar`, permitindo reutilização dentro de sessão principal.
- `LayoutAdministrativo.tsx` é um exemplo para **catálogo visual** de componentes (o menu diz “Design system”), não navegação operacional; deve ser adaptado ou reaproveitado sem transformar a navegação real num catálogo.
- O resultado atual apresenta `Identificador: {liquidacao.liquidacaoId}`.
- A cotação demonstrativa é armazenada como decimal canônico, por exemplo `5.4321000000`; a exibição ao operador deve apresentar `1 USD = R$ 5,4321` em `pt-BR`, sem zeros artificiais da escala de armazenamento. O hotfix da apresentação é rastreado em PR próprio; a F05 deve preservá-lo.

## Escopo da entrega

### 1. Máscara monetária acessível e precisa

- Campo de valor de face em BRL com exibição localizada, por exemplo **`R$ 25.000,00`** para `pt-BR`, com `Intl.NumberFormat` ou equivalente seguro. Em `en-US`, apresentar formato compatível com o idioma.
- Preservar digitação natural de números e centavos, backspace, seleção, cursor/caret, texto colado com separadores, teclado móvel, composição e colagem. Não bloquear o preenchimento de valores válidos quando o usuário está editando.
- Pode-se usar apresentação formatada no **blur** e texto editável sem símbolo no **focus** se máscara permanente tornar o cursor instável; a experiência precisa ser previsível, documentada e testada. Não enviar `R$`, pontos ou vírgulas no JSON canônico.
- Manter `normalizarEntradaMonetaria` e seus testes como base do contrato; enviar `valorFaceBrl` **string decimal canônica**, sem usar `Number`, `parseFloat` nem `float` para cálculo financeiro. Preservar 17 dígitos inteiros, até dois decimais, validações e formatos pt-BR/en-US.

### 1A. Cotação cambial em escala de apresentação

- Na visualização da simulação e liquidação, **não usar formatação monetária de centavos** para cotação BRL/USD: taxa é razão entre moedas e pode exigir mais que duas casas.
- Apresentar claramente a direção **`1 USD = R$ 5,4321`** para a string canônica `5.4321000000` com locale `pt-BR`; em `en-US`, apresentar separador decimal correspondente `5.4321`. Usar ao menos **quatro casas decimais**, remover zeros à direita **somente após a quarta casa** e preservar todas as casas adicionais significativas, sem arredondar o valor usado no motor financeiro.
- O formatter trabalha sobre string decimal, sem `Number`/`parseFloat`, e jamais altera a cotação original recebida do servidor, os contratos financeiros ou a precisão de `BigDecimal`. Preservar este ajuste caso tenha sido integrado antes da implementação da F05.

### 2. Datas e fuso com semântica correta

- Introduzir formatação central reutilizável de instantes UTC por `Intl.DateTimeFormat` para o locale e **fuso do usuário**, com indicação explícita da zona/fuso. Exemplo verificável: `2026-11-21T21:15:58.766998Z` corresponde a **21/11/2026 18:15:58** em `America/Sao_Paulo`.
- Na prévia, rotular `referencia` como **“Referência da simulação”**; não afirmar que ocorreu liquidação. Na liquidação concluída, usar `liquidadaEm` como **“Data/hora da liquidação”**.
- Formatar também `cotacaoValidaAte` e timestamps da tabela/detalhes do extrato; preservar os valores ISO UTC recebidos e o instante original em tooltip, `title`, `datetime` acessível ou detalhe equivalente quando útil para auditoria.
- **Não modificar filtros de período do extrato:** o backend define período inclusivo em UTC; a interface deve distinguir claramente filtros de data UTC e exibição local de data/hora. Não converter datas sem horário da busca para outro dia.

### 3. Navegação operacional com menu, breadcrumbs e páginas separadas

- Criar layout autenticado responsivo com navegação por menu lateral desktop e Drawer mobile, indicação de página ativa, botão sair, skip link, breadcrumbs acessíveis e título de cada página.
- Seções mínimas:
  1. **Cedentes** — cadastro e seleção de cedentes, respeitando os campos e a validação da B14.
  2. **Simulação** — prévia indicativa não persistente com modalidade, valor de face, prazo, moeda e vencimento.
  3. **Liquidação** — cadastro do recebível vinculado à prévia, confirmação explícita, sucesso e tratamento de operações incertas.
  4. **Extrato / Relatórios** — `PaginaExtrato` com filtros e paginação existentes.
  5. **Usuários** — administração organizacional apenas para perfis e capacidades autorizados.
- Breadcrumbs adequados: por exemplo `Início / Operações / Simulação` e `Início / Relatórios / Extrato`. Navegação deve funcionar via teclado, mobile, browser back/forward ou solução de estado/histórico documentada; atualização e abertura direta de URLs não podem cair em 404 por falta de configuração Nginx. Se adotar roteador, justificar dependência e configurar fallback apropriadamente.
- Reaproveitar tema, componentes MUI, tratamento de feedback e separação **hexagonal** (estado/casos de uso na aplicação, adaptadores HTTP na infraestrutura, apresentação em páginas/componentes).
- Não criar um frontend adicional nem exigir JavaScript de frameworks premium. O catálogo visual F00 deve continuar acessível e identificado como **demonstração**, sem entrar no menu operacional.

### 4. Sessão única para relatórios e respeito à autorização

- Incorporar `PaginaExtrato` à aplicação autenticada, reutilizando token JWT **somente em memória**; não copiar para `localStorage`, query string ou URL e não gravar credenciais em logs.
- Remover a necessidade de novo login ao abrir o extrato pelo menu. Tratar expiração e logout numa fonte de estado única e manter roteamento consistente com as capacidades retornadas por `/bff/v1/usuarios/me`.
- Esconder navegação administrativa de usuários quando o perfil não possuir capacidade, **sem** substituir a verificação do backend. A listagem/extrato segue protegida pelo BFF.
- Preservar a entrada legada `/extrato.html`: mantê-la funcional e documentar se continua com autenticação própria para acesso direto ou se redireciona para o shell de forma segura; não inventar transmissão de JWT entre páginas. Não mudar a política de expiração.

### 5. Preservação do fluxo financeiro

- O backend continua fazendo toda precificação; a simulação não registra recebível e o usuário deve confirmar antes de liquidar.
- Ao avançar de Simulação para Liquidação, preservar o contexto da prévia; não recalcular silenciosamente por mudança de página.
- Não perder `Idempotency-Key`, `IntencaoLiquidacao`, bloqueio de duplicidade, indicação de resultado incerto nem identidade do recebível ao navegar/voltar. Uma transição de tela não deve gerar novo cadastro/liquidação automaticamente. Se houver operação pendente, preservar estado no React ou impedir saída com aviso e ação segura; documentar comportamento de refresh/expiração sem armazenar segredos.
- Após sucesso, manter ação de nova simulação; não chamar operação de escrita novamente a partir do relatório.

### 6. Nomenclatura sem mudança no contrato

- Substituir exclusivamente o rótulo `Identificador` do resultado da liquidação por **`Código da liquidação`**.
- Manter `liquidacaoId` como UUID, payload/contrato BFF e resposta do backend. Títulos da tabela de extrato podem adotar a mesma nomenclatura, sem mudar atributos do JSON.

## Matriz de testes e critérios de aceite

- [ ] `pt-BR`: entrada `25000` / `25000,0` / `25.000,00` exibe **`R$ 25.000,00`** em estado formatado e envia `25000.00` ao BFF; `en-US` equivalente; centavos, muitos dígitos e valores inválidos sem uso de ponto flutuante.
- [ ] Cotação `5.4321000000` exibe **`1 USD = R$ 5,4321`** em `pt-BR`; `en-US` exibe separador decimal de seu locale. Exibir ao menos 4 casas, eliminar zeros não significativos após a quarta, preservar precisão maior quando presente e evitar arredondar para 2 casas.
- [ ] Edição, cursor/caret, backspace, colagem, blur/focus e teclado móvel testados em React Testing Library; acessibilidade e uso mobile.
- [ ] UTC `2026-11-21T21:15:58.766998Z` exibe horário local correto para `America/Sao_Paulo` (18:15:58), com zona identificável e preservação ISO para auditoria; cenário adicional envolvendo outra zona.
- [ ] “Referência da simulação” e “Data/hora da liquidação” não se confundem; snapshot e cotação expirada exibem instante correto; filtros do extrato continuam UTC.
- [ ] Menu/Drawer, breadcrumbs e página ativa para Cedentes, Simulação, Liquidação, Extrato e Usuários; sem tela única longa. Testes de teclado, foco, back/forward e viewport mobile.
- [ ] Extrato abre sem pedir novo login após autenticação principal, usa mesmo JWT em memória; logout, expiração e acesso direto via `extrato.html` seguem documentados e testados.
- [ ] Navegação restrita por capacidade/perfil e erros 401/403 permanecem seguros; back-end continua a fonte de autorização.
- [ ] Simulação → cadastro → liquidação continua idempotente; chave pendente e estado incerto não são perdidos ao navegar. Não há duplicidade por remount, duplo clique ou retry.
- [ ] Rótulo “Código da liquidação” exibido, sem alteração de identificador, payloads ou APIs.
- [ ] `cd frontend && npm ci && npm run lint && npm test && npm run build` e CI completa aprovados. Registrar números reais e falhas, sem alegar sucesso não observado.

## Limites e dependências

Esta tarefa não implementa CNPJ ou schema (pertencem à **B14**), não altera módulos Kotlin, regras financeiras, migrations, cálculos, taxas, política JWT ou autorização. Caso a B14 já tenha alterado o cadastro/contrato de cedentes, preservar integralmente o resultado integrado em `develop`.

**Regras operacionais:** confirmar B14 `Integrada em` no `TASKS.md` de `origin/develop`; só iniciar a F05 quando a situação for `Liberada para iniciar`. Um worktree/branch derivado de ID + coluna Trabalho; agente frontend implementa e testa, architect/qa/reviewer revisam, PR para `develop`, merge exclusivamente humano.
