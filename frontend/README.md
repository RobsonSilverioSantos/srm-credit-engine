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

Abra a URL exibida pelo Vite. O catálogo identifica dados fictícios, permite testar
o formulário e alternar os estados da tabela. Não há sessão, requisições ao BFF,
persistência nem cálculo financeiro.

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
  espaço disponível. Os links atuais são âncoras de catálogo, não rotas
  autenticadas.

## Fronteiras

`main.tsx` compõe o adaptador em memória (`infraestrutura/`), a porta e a consulta
(`aplicacao/`) e a página (`apresentacao/`). `dominio/` descreve apenas os dados
da demonstração; não é um contrato do BFF. Não importe o mock em F01/F03: crie
portas para os contratos aprovados e injete os adaptadores em `main.tsx`. A
apresentação não deve executar HTTP nem aritmética monetária. O ESLint bloqueia
imports diretos indevidos entre as camadas.

Preferiu-se MUI Core a CSS/componentes próprios para aproveitar semântica,
foco e controles já implementados. `Table` substitui a necessidade de um Data
Grid; âncoras evitam adicionar um roteador para uma só página. O catálogo não
reintroduz cálculos, sessão ou fluxos de negócio como dependências do design.
