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

Inicie também o backend em `localhost:8080`, seguindo `backend/README.md`. O
servidor Vite encaminha `/api` e `/bff` ao backend; em produção, configure o
proxy da mesma origem para esses caminhos e use HTTPS.

Abra a URL exibida pelo Vite e entre com um usuário local já cadastrado no
backend. A organização é determinada pelo token validado pelo servidor. O
painel simula sem persistir e sem reservar taxas; após a prévia, permite
cadastrar o recebível e confirmar sua liquidação. Valores e taxas são exibidos
como strings retornadas pelo backend, sem cálculo financeiro no navegador.

Uma chave de idempotência é mantida em memória para as retentativas da mesma
liquidação, inclusive após expiração e novo login na mesma conta. Ela não
sobrevive ao recarregamento ou fechamento da página: anote o identificador do
recebível e verifique seu estado no servidor antes de iniciar outra operação.
Cadastro de recebível não é idempotente; se seu resultado for incerto, o painel
bloqueia novas tentativas e orienta a consulta ao servidor.

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

`main.tsx` injeta o adaptador BFF (`infraestrutura/`) na interface. Tipos de
operação ficam em `dominio/`; portas e coordenação da confirmação, em
`aplicacao/`; HTTP e validação das respostas ficam em `infraestrutura/`. A
apresentação não executa HTTP nem aritmética monetária. O catálogo visual e
seu adaptador em memória permanecem isolados para demonstração e testes.

Preferiu-se MUI Core a CSS/componentes próprios para aproveitar semântica,
foco e controles já implementados. `Table` substitui a necessidade de um Data
Grid; ainda não há roteador para a página única. O catálogo não introduz
cálculos ou fluxos de negócio como dependências do design.
