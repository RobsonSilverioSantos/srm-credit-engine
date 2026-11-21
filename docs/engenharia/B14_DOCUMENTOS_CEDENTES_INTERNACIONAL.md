# B14 — Identificação empresarial e documentos de cedentes

**Estado:** **integrada em `develop` — PR #56, 21/11/2026** (P1). Esta especificação registra o planejamento histórico; o comportamento efetivamente implementado é descrito em [backend/docs/B14_DOCUMENTOS_CEDENTES.md](../../backend/docs/B14_DOCUMENTOS_CEDENTES.md) e na [ADR-023](../adr/ADR-023-identificacao-empresarial-por-pais-e-documento.md).  
**Tarefa de origem:** `TASKS.md`, B14; rastreamento [issue #45](https://github.com/RobsonSilverioSantos/srm-credit-engine/issues/45).  
**Gate histórico de início (cumprido):** B13 integrada pelo PR #48, B14 liberada pelo PR #49 e implementação integrada pelo PR #56. Não reiniciar a B14 nem usar esta especificação como autorização para alterações adicionais.  
**Agentes:** `backend` como coordenador/executor em um único worktree; `frontend` como apoio à especificação/validação da UI; `architect`, `qa` e `reviewer` como revisores independentes. Não executar edições simultâneas no mesmo worktree.

## Resultado efetivo da implementação

A B14 integrou no PR #56: `BR/CNPJ` numérico e alfanumérico com verificação dos DVs; identificador estrangeiro `OUTRO` aceito como `NAO_VERIFICADO`, sem validação fiscal; `BR/OUTRO` e `<país estrangeiro>/CNPJ` rejeitados; novos POST antigos inferem `BR/CNPJ`; legados sem país/tipo permanecem `DESCONHECIDO`. A migration V5 preserva dados históricos e trata colisões de forma conservadora. As provas locais declaradas no PR não equivalem à homologação operacional completa. O limite normativo está na ADR-023.

## Problema observado

A entidade `Cedente` apenas exige `documento.isNotBlank()` e limite de 32 caracteres. A API aceita o texto diretamente; o React não identifica país/tipo e o PostgreSQL impõe unicidade por `(organizacao_id, documento)`, permitindo que documentos equivalentes com e sem máscara sejam inseridos separadamente.

O SRM Credit Engine lida com BRL e USD, mas ser **multimoedas** não pressupõe domínio de validação tributária mundial. É necessário entregar **validação completa do CNPJ brasileiro** e preparar extensibilidade internacional sem alegar verificação real de registros de outros países.

## Escopo funcional fechado

1. **Documento empresarial estruturado:** apresentar `pais` (ISO 3166-1 alpha-2), `tipoDocumento` e `numeroDocumento` (ou nome equivalente aprovado no contrato) como conceitos distintos. Preservar a identidade de `Organizacao` como tenant e `Cedente` como entidade de negócio; país do documento não determina tenant, moeda, autorização ou taxas.
2. **CNPJ brasileiro:** aceitar formato numérico histórico e formato alfanumérico. Exigir exatamente 14 posições canônicas, com 12 primeiras alfanuméricas e 2 últimos dígitos numéricos; validar **ambos os dígitos verificadores** conforme o manual oficial, inclusive conversão dos caracteres por ASCII menos 48 e módulo 11. Rejeitar padrões inválidos e entradas malformadas; não confiar no frontend. Incluir casos de teste derivados de documentação oficial, além de valores de teste sintéticos construídos corretamente. Não gravar CNPJ fictício com DV inválido apenas para agilizar a demonstração.
3. **Normalização:** validar e persistir representação canônica para comparação e unicidade, sem pontuação e com caixa consistente, usando o mesmo algoritmo em entrada/API/migração. A formatação exibida pode aplicar pontuação de CNPJ (inclusive alfanumérico), **sem alterar o valor canônico**. Não mudar identificador pela localização do navegador.
4. **Internacionalização extensível:** definir contrato de validação/normalização por país e tipo (Strategy, com portas/adaptadores conforme fronteiras). Para países sem validador implementado, decidir explicitamente com o responsável técnico entre (a) bloquear cadastro com mensagem "não suportado" e (b) aceitar como **identificador não verificado** com regra sintática limitada; **não chamar isso de documento fiscal validado**. Não implementar dezenas de validadores, integração com serviços oficiais, EIN/VAT/Company Number sem testes e fonte normativa.
5. **Frontend React:** campo de país, seleção de tipo e entrada do número, acessibilidade e mensagens claras; máscara contextual apenas para tipo compatível, aceitando digitação/colagem com ou sem pontuação. Manter feedback durante edição e validação definitiva no backend; não inserir uma dependência pesada de máscaras sem justificativa. A interface mantém o fluxo de cadastrar cedente e selecionar cedente para operações financeiras.
6. **API/BFF:** evoluir de modo deliberado `POST/GET /api/v1/cedentes` e `/bff/v1/cedentes`; documentar a compatibilidade do campo legado `documento` e a estratégia de evolução de payloads/respostas. **Não fazer mudança silenciosamente incompatível**: onde necessária, adicionar campos opcionais ou versionar contrato com testes de regressão. Os campos de identidade/documento não permitem selecionar a organização.
7. **Persistência:** Flyway **nova migration**; nunca reescrever `V1` ou documentos históricos. O schema atual tem `documento VARCHAR(32)` e `UNIQUE(organizacao_id, documento)`. Planejar novas colunas e unicidade `(organizacao_id, pais, tipo_documento, documento_canonico)` sem perder relacionamentos de recebíveis existentes. Tratar registros legados sem país/tipo como **desconhecidos** até validação; não presumir `BR/CNPJ` só pelo tamanho, e não converter registros inválidos em CNPJs aceitos. Inventariar colisões ao normalizar e fornecer migração/reconciliação segura que falhe de modo informativo em conflitos, sem exclusão ou fusão silenciosa de dados. Evitar que antigos índices inadequados impeçam representações válidas de identificadores distintos.
8. **Erros e segurança:** documento inválido → erro semântico 400; duplicidade canônica → 409 (sem revelar detalhes de outros tenants); manter escopo da organização autenticada. Não gravar números de documentos em logs de erro nem alterar JWT/RBAC.
9. **Convenção de código:** respeitar um tipo Kotlin top-level por arquivo, arquitetura hexagonal `:dominio` / `:aplicacao` / `:app`, e frontend hexagonal. A B14 **não** executa a refatoração geral da B13.

## Plano de implementação

- **Fase 1 — desenho/compatibilidade:** inspecionar B13 já integrada, contrato atual, migrations, dados legados e testes; entregar proposta técnica curta de normalização, persistência, estratégia para países não suportados e retrocompatibilidade. Obter decisão humana quando houver mudança de API incompatível ou política para países desconhecidos.
- **Fase 2 — backend:** implementar valor/estratégia de documento CNPJ no domínio, uso no serviço de cadastro, migração aditiva, unicidade e tratamento HTTP 400/409, com testes de unidade e PostgreSQL.
- **Fase 3 — frontend:** adaptar interface e adaptador BFF usando os mesmos contratos confirmados; máscara brasileira contextual e validação de entrada; testes React e acessibilidade. O agente `frontend` faz revisão especializada sem editar simultaneamente os mesmos arquivos do executor.
- **Fase 4 — regressão:** exercitar operações existentes com cedentes, recebíveis, simulação e liquidação, incluindo tenants distintos e registro anterior à migration. Documentar riscos, evidências e passos de migração; abrir PR para `develop`, sem merge por agentes.

## Critérios objetivos de aceite

- [ ] CNPJ numérico e alfanumérico: formato, normalização e dois DVs validados conforme Receita Federal, com vetores oficiais e negativos, inclusive casos limítrofes.
- [ ] Documento com máscara/sem máscara/caixa diferente não permite duplicidade dentro da mesma organização; a mesma identidade em organizações distintas permanece isolada.
- [ ] País/tipo explícitos no modelo e apresentação; um tipo estrangeiro não recebe máscara CNPJ nem passa por um validador brasileiro.
- [ ] Política de país/tipo não suportado está claramente definida, documentada e não transmite falsa confirmação tributária.
- [ ] Migration nova preserva cedentes legados e referências a recebíveis; histórico inválido/ambíguo é identificado, sem reclassificação automática nem perda de dados.
- [ ] APIs `/api/v1` e `/bff/v1` têm testes para contrato anterior e novo; respostas 400/409 e validação de organização autenticada corretas.
- [ ] React apresenta país/tipo/identificador com máscara contextual, feedback de erro, acessibilidade e testes sem depender apenas da UI para validação.
- [ ] Build/CI, `./gradlew clean check`, testes reais PostgreSQL, frontend lint/test/build e golden cases C1–C3 seguem aprovados.
- [ ] Mudanças limitadas ao domínio de documentos/cedentes; nenhuma mudança de precificação, câmbio, transações ACID ou liquidação, nem reescrita de migrations.
- [ ] Um PR de implementação para `develop`, com evidências de revisão dos agentes `architect`, `frontend`, `qa`, `reviewer`, sem merge automático.

## Referências oficiais de CNPJ

- [Receita Federal — cálculo do DV alfanumérico](https://www.gov.br/receitafederal/pt-br/centrais-de-conteudo/publicacoes/documentos-tecnicos/cnpj)
- [Receita Federal — manual técnico de DV](https://www.gov.br/receitafederal/pt-br/centrais-de-conteudo/publicacoes/documentos-tecnicos/cnpj/manual-dv-cnpj.pdf)
- [Receita Federal — primeiro CNPJ alfanumérico emitido em julho/2026](https://www.gov.br/receitafederal/pt-br/assuntos/noticias/2026/julho/receita-federal-gera-o-primeiro-cnpj-em-formato-alfanumerico)

Os documentos de referência devem ser usados para a implementação e para montar testes; a adoção de um padrão de sintaxe internacional ou a existência de um número não constitui prova de registro ativo em órgão competente.

## Governança

O agente deve confirmar B13 e a especificação B14 integradas em `origin/develop`, usar um worktree próprio e derivar a branch da linha **ID + Trabalho** do `TASKS.md`. A implementação pode ter commits incrementais, com um PR de B14 para `develop`. `SPEC.md`, `DECISIONS.md` e ADRs normativas não devem ser alterados sem aprovação. Não misturar correções do bootstrap, seeds financeiros ou exercícios independentes com B14. Somente o responsável técnico decide o merge.
