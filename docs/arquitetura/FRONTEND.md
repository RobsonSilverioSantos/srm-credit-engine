# Arquitetura frontend e design system

**Stack:** React + TypeScript + Vite, Material UI Core (`@mui/material`, Emotion), React Router (se houver mais de uma rota), Vitest + React Testing Library. Componentes adicionais apenas quando demonstrada necessidade. **Não usar bibliotecas premium.**

## Design visual (decisão aprovada)

- Design system baseado em **Material UI**, interface administrativa financeira, responsiva e acessível, com tema único `createTheme`/`ThemeProvider`.
- Tokens do tema: `palette.primary`, `secondary`, `error`, `warning`, `success`, `background`; `typography`, `shape.borderRadius`, `spacing` e breakpoints. Definir contraste e variantes antes das páginas; não espalhar valores hexadecimais.
- Componentes core: `AppBar`, `Drawer` (se necessário), `Container`, `Card`, `Stack`, `Grid`, `TextField`, `Select`, `FormControl`, `Alert`, `Snackbar`, `Dialog`, `Button`, `Table`, `TablePagination`, `CircularProgress`, `Chip`. Preferir `Table`/`TablePagination` para MVP, sem dependência de Data Grid paga.
- **F00:** criar layout responsivo, menu e estados de acesso, catálogo de elementos, tema e mock de telas. Revisão visual e acessibilidade antes de integrar F01/F03.

## Telas e comportamentos

1. **Autenticação simples:** login de demonstração, erros sem expor detalhes de contas, guarda de sessão; organização vem do contexto autenticado, sem campo livre para autorização.
2. **Painel do operador:** entrada de valor de face, tipo (duplicata/cheque), prazo em meses inteiro, vencimento informativo, moeda BRL/USD e cedente; ao digitar, simular no BFF **antes de cadastrar**, com debounce e descarte de respostas antigas. A prévia é indicativa e não persiste.
3. **Confirmação:** cadastrar recebível apenas quando usuário confirmar; pedir liquidação com chave idempotente estável por intenção e reuso na retentativa da mesma intenção. Exibir valor e moeda em strings formatadas, sem cálculo financeiro no navegador.
4. **Extrato:** filtros de período, cedente e moeda; ordenação estável, `TablePagination` paginando no servidor, estados loading, vazio e erro, detalhes de snapshot se existentes.

## Hexagonal em React

```text
frontend/src/
  dominio/        # entidades e tipos sem React ou HTTP
  aplicacao/      # casos de uso e portas do BFF
  infraestrutura/ # adaptadores fetch/client HTTP e DTO mapping
  apresentacao/   # hooks, páginas e componentes MUI
  tema/           # theme tokens e componentes visuais compartilhados
```

- Hook/componentes não chamam `fetch` diretamente e não recalculam preço/taxa/câmbio; portas/casos de uso coordenam o contrato BFF.
- Armazenar valores monetários como strings decimais no transporte. Formatação somente para exibição; não usar `Number`/`parseFloat` para aritmética financeira.
- React state local por padrão; estado global somente com justificativa. Testar debounce, respostas obsoletas, validação, carregamento, falhas e paginação server-side.
- Confirmar contratos BFF antes de conectar o frontend. Mock responsivo permitido em F00; não marcar backend como funcional por telas mockadas.
