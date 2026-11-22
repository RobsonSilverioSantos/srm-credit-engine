# Frontend — SRM Credit Engine

> **Sistema completo com Docker (sem Node no computador):** utilize os scripts para macOS/Linux ou Windows descritos no [`README.md` principal](../README.md). Os comandos Vite abaixo são apenas para desenvolvimento do frontend isolado.

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

Após autenticar na página principal, utilize o menu responsivo e os
breadcrumbs para navegar entre Cedentes, Simulação, Liquidação, Extrato e
Usuários. As rotas operacionais ficam no fragmento (`/#/simulacao`, por
exemplo), portanto podem ser abertas diretamente ou atualizadas sem exigir
fallback de rota no Nginx. A navegação mantém o estado em memória. Atualizar ou
fechar a página pede confirmação do navegador quando há solicitação em curso ou
operação pendente; se a saída for confirmada, a sessão termina e os dados em
memória são descartados.
O Catálogo visual permanece separado do menu operacional e identificado como
demonstração em `/catalogo.html`.

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
estado da operação no servidor antes de prosseguir. A troca entre páginas
operacionais mantém a simulação, a intenção, a chave de idempotência e o estado
incerto no React; nenhuma navegação dispara cadastro ou liquidação.
O botão Sair fica desabilitado durante uma solicitação. Com intenção pendente
ou cadastro incerto, sair exige confirmação e avisa que o estado em memória será
descartado; antes de iniciar outro cadastro, o operador deve conferir o estado
no servidor.

O valor de face usa os separadores e dígitos do locale do navegador, com
fallback para `pt-BR`; a normalização valida o agrupamento daquele locale.
Enquanto o campo está em edição, o texto permanece selecionável e sem máscara
monetária; ao sair do campo, uma entrada válida é exibida como moeda localizada.
O foco restaura o texto editável e a posição do cursor. Simulação e liquidação
continuam enviando ao BFF somente strings decimais canônicas.

## Identificação de cedentes (B14)

No cadastro, informe o país por código ISO alpha-2 e selecione o tipo de
documento. O país sugere o tipo, que pode ser alterado: `BR` exige `CNPJ`;
países estrangeiros exigem `OUTRO`. Combinações incompatíveis bloqueiam o
envio. A sintaxe do código é verificada localmente, mas somente o servidor
confirma se o país é aceito.

O CNPJ numérico ou alfanumérico recebe máscara contextual, inclusive na
colagem; entradas inválidas não são limpas silenciosamente. O frontend verifica
o formato, não os dígitos verificadores: o backend faz a validação definitiva.
`OUTRO` aceita de 1 a 32 letras ASCII ou números, em maiúsculas, sem espaços
ou pontuação. É um identificador **não verificado**, não uma validação fiscal.
Alterar país ou tipo preserva o texto para correção.

O BFF recebe `nome`, `documento`, `pais` e `tipoDocumento`; a organização vem
da autenticação, nunca do formulário. Cedentes legados com país e tipo nulos
continuam selecionáveis e aparecem como desconhecidos. Erros 400 orientam
conferir os campos; 409 informa possível duplicidade ou outro conflito, sem
presumir sua causa.

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

Na sessão principal, abra **Extrato** pelo menu para reutilizar o JWT mantido em
memória, sem um segundo login. A entrada legada `/extrato.html` continua
disponível para acesso direto e mantém autenticação própria; nenhum token é
transferido entre páginas, gravado em URL ou persistido no navegador.

Respostas HTTP 401 exigem nova autenticação.

O extrato consome o BFF e permite filtrar liquidações por período, cedente
(UUID) e moeda. Os filtros de datas seguem o contrato atual da API,
que considera dias inclusivos em UTC.

A paginação e a ordenação são realizadas no servidor. Inserções entre
consultas de páginas diferentes podem deslocar registros. As datas dos filtros
continuam inclusivas em UTC; timestamps são apresentados no locale e fuso
horário do navegador. O elemento temporal conserva o valor ISO UTC original
para inspeção e auditoria.

Os valores financeiros e os snapshots históricos são exibidos conforme
recebidos do backend, sem recálculo no frontend.
O nome do cedente é o nome atual do cadastro. O botão de detalhes abre um
diálogo com o identificador do cedente e o snapshot, sem expandir a linha.

A apresentação usa `navigator.languages` e `pt-BR` como fallback. Valores,
taxas e cotações são formatados somente para exibição, preservando as strings
decimais do BFF; nenhum cálculo financeiro é realizado no navegador.

## Catálogo de componentes

O catálogo visual de componentes utiliza dados exclusivamente fictícios e
permanece isolado dos fluxos financeiros. Ele não fornece dados para operações
reais nem é necessário para executar o painel, a administração ou o extrato.


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
  espaço disponível. O extrato F03 permanece acessível por URL independente.

## Fronteiras


`main.tsx` compõe os adaptadores HTTP do BFF (`infraestrutura/`), as portas e os casos de uso (`aplicacao/`) e a apresentação autenticada. O layout operacional usa o fragmento do navegador para navegação; a tela de extrato recebe a porta BFF e o token em memória da sessão.

`extrato.tsx` mantém a entrada legada com autenticação própria. `catalogo.tsx` compõe a demonstração visual independente dos fluxos financeiros.

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

F01, F03 e F04 reutilizam a sessão em memória da interface principal, cada qual mantendo porta e adaptador independentes. O acesso direto a `extrato.html` continua isolado e exige autenticação própria.


Preferiu-se MUI Core a CSS/componentes próprios para aproveitar semântica,
foco e controles já implementados. `Table` substitui a necessidade de um Data
Grid; a navegação usa o fragmento e o histórico nativos do navegador, sem
dependência de roteador. O catálogo não introduz cálculos ou fluxos de negócio
como dependências do design.
