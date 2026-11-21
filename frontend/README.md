# Frontend — SRM Credit Engine

## Executar

Com Node.js 22.13+ (na linha 22) ou 24 e npm:

```sh
cd frontend
npm ci
npm run dev
npm run lint
npm test
npm run build
```


Inicie também o backend em `localhost:8080`, seguindo `backend/README.md`.
Em desenvolvimento, o Vite encaminha `/api` e `/bff` para
`http://localhost:8080`. Em produção, publique ambas as rotas na mesma
origem do frontend, utilizando HTTPS e um proxy reverso.

## Painel de simulação e liquidação (F01)

Abra a URL principal exibida pelo Vite e autentique-se com um usuário
local previamente cadastrado no backend.

A organização é determinada pelo token JWT validado pelo servidor.

O painel permite simular um recebível sem persistir dados ou reservar taxas.
Após a simulação indicativa, o operador pode cadastrar o recebível e
confirmar sua liquidação.

Os valores financeiros e as taxas são exibidos conforme retornados pelo
backend, sem cálculos financeiros no navegador.

Uma chave de idempotência é mantida em memória para as tentativas da mesma
liquidação, inclusive após expiração da sessão e nova autenticação na mesma
conta. Essa chave não sobrevive ao recarregamento ou fechamento da página.

O cadastro de recebível não é idempotente. Se seu resultado for incerto,
o painel bloqueia novas tentativas e orienta o operador a consultar o
estado da operação no servidor antes de prosseguir.

## Administração de usuários (F04)

Depois de autenticar na tela principal, MASTER e ADMIN podem administrar os
usuários permitidos para sua organização. As permissões e os perfis disponíveis
vêm de `/bff/v1/usuarios/me`; o servidor continua sendo a autoridade de acesso.
MASTER pode criar ADMIN e OPERADOR; ADMIN pode criar somente OPERADOR. OPERADOR
não recebe a interface administrativa. A listagem, criação e alteração de status
usam os endpoints do BFF, que obtém a organização da identidade autenticada.

A tela não oferece cadastro público, criação de MASTER ou seleção de
`organizacaoId`. Senhas iniciais ficam somente no estado transitório do formulário
e são limpas após a submissão. Erros 401 solicitam nova autenticação; 403 e 409
são apresentados como mensagens de acesso ou conflito. Inativar exige confirmação.
O formulário verifica formato e tamanho mínimo/máximo antes de enviar; a API
revalida os campos e continua sendo a autoridade final.

## Extrato de liquidações (F03)

Acesse `/extrato.html` para utilizar o extrato de liquidações.

O extrato possui autenticação própria nesta etapa, mantendo o token JWT
somente em memória. Respostas HTTP 401 exigem nova autenticação.

O extrato consome o BFF e permite filtrar liquidações por período, cedente
(UUID) e moeda. Os filtros de datas seguem o contrato atual da API,
que considera dias inclusivos em UTC.

A paginação e a ordenação são realizadas no servidor. Inserções entre
consultas de páginas diferentes podem deslocar registros.

Os valores financeiros e os snapshots históricos são exibidos conforme
recebidos do backend, sem recálculo no frontend.

A apresentação usa `navigator.languages` e `pt-BR` como fallback. Valores,
taxas e cotações são formatados somente para exibição, preservando as strings
decimais do BFF. O campo de valor aceita os separadores de `pt-BR` e `en-US`,
valida agrupamento/escala e envia ao BFF uma string decimal canônica com ponto
decimal; nenhum cálculo financeiro é realizado no navegador.

A tela permanece como entrada independente para evitar conflitos entre
as implementações paralelas F01 e F03. A integração posterior deverá
compartilhar a sessão autenticada, conectar a navegação e reutilizar
a porta `PortaExtrato` na `PaginaExtrato`.

## Catálogo de componentes

A URL principal também poderá disponibilizar o catálogo fictício de
componentes conforme a configuração de navegação e build integrada.
Os dados do catálogo são exclusivamente demonstrativos e não devem
ser utilizados como fonte para operações financeiras reais.


## Reutilizar

- **Tema:** mantenha um único `ThemeProvider` com `tema/tema.ts` e `CssBaseline`.
  Use tokens semânticos, como `text.secondary`, e espaçamento/breakpoints do tema.
  Marinho destaca ações primárias; petróleo diferencia o contexto
  administrativo. Estados usam texto além de cor; números da tabela ficam
  alinhados à direita com dígitos tabulares.
- **Formulários:** use `CampoFormulario` com `label`, `helperText` e `erro`; para
  seleção, passe `opcoes` com pares `valor`/`rotulo`. A página controla valores,
  validação e submissão; o campo conecta a mensagem ao controle.
- **Tabelas:** passe `legenda`, `colunas`, `chaveLinha` estável e `estado` para
  `TabelaDados`. Use `cabecalhoLinha` no identificador, `alinhar: 'right'` em
  valores e passe strings prontas para exibir. A rolagem horizontal fica na
  tabela, acessível por teclado. Em F03, paginação e filtros devem ser
  controlados pela apresentação e processados no servidor; a tabela não busca,
  pagina nem ordena dados por conta própria.
- **Feedback e layout:** use `Mensagem` para severidades, `EstadoVazio` para
  orientar o próximo passo e `Carregando` com rótulo específico. `Painel`
  oferece um título semântico e `LayoutAdministrativo` adapta a navegação ao
  espaço disponível. A integração de navegação com F03 requer coordenação.

## Fronteiras


`main.tsx` compõe os adaptadores HTTP do BFF (`infraestrutura/`), as portas e os casos de uso (`aplicacao/`) e a apresentação da F01.

`extrato.tsx` compõe os adaptadores HTTP de autenticação e consulta, as portas e a apresentação independente da F03.

A F04 reutiliza a sessão em memória da entrada principal. `infraestrutura/bffUsuarios.ts`
implementa `PortaGestaoUsuarios`; `aplicacao/gestaoUsuarios.ts` valida capacidades
antes de chamar a porta, e `apresentacao/PaginaAdministracaoUsuarios.tsx` compõe
a tela MUI. O BFF continua responsável pela autorização definitiva e pelo
isolamento por organização.

As duas implementações seguem as mesmas fronteiras da arquitetura hexagonal:

- **Domínio (`dominio/`):** define tipos e contratos de dados, sem dependências de React, HTTP ou infraestrutura.
- **Aplicação (`aplicacao/`):** define portas, validações e coordenação dos casos de uso, sem conhecer os detalhes dos adaptadores.
- **Infraestrutura (`infraestrutura/`):** implementa as portas e a comunicação HTTP com o BFF, incluindo validação das respostas.
- **Apresentação (`apresentacao/`):** implementa componentes e interações de interface, sem realizar chamadas HTTP diretamente nem cálculos financeiros.

O catálogo visual permanece isolado para demonstração e testes, utilizando dados fictícios. Seus mocks não devem ser utilizados nas operações reais da F01 ou F03.

O ESLint bloqueia imports diretos indevidos entre as camadas.

A integração futura entre F01 e F03 deverá compartilhar a sessão autenticada e a navegação, preservando a separação das portas e dos adaptadores.


Preferiu-se MUI Core a CSS/componentes próprios para aproveitar semântica,
foco e controles já implementados. `Table` substitui a necessidade de um Data
Grid; ainda não há roteador para a página única. O catálogo não introduz
cálculos ou fluxos de negócio como dependências do design.
