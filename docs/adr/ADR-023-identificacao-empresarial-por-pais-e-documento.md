# ADR-023 — Identificação empresarial por país e tipo de documento

**Status:** aprovada para o escopo implementado na B14 (PR #56, integrada em `develop` em 21/11/2026).  
**Data:** 21/11/2026.  
**Autoridade normativa:** [DECISIONS.md](../../DECISIONS.md), ADR-023.  
**Rastreamento:** [B14, issue #45](https://github.com/RobsonSilverioSantos/srm-credit-engine/issues/45), [PR #56](https://github.com/RobsonSilverioSantos/srm-credit-engine/pull/56), [evidências técnicas da B14](../../backend/docs/B14_DOCUMENTOS_CEDENTES.md).

## Contexto

O SRM Credit Engine identifica cedentes vinculados a uma organização. O uso de BRL e USD no motor financeiro **não** implica validação fiscal de pessoas jurídicas de qualquer país. Antes da B14, `documento` era texto livre sem identidade de país/tipo, o que permitia representações equivalentes e não comprovava a integridade estrutural do CNPJ.

A B14 introduziu país, tipo e documento canônico, validação de dígitos verificadores para CNPJ brasileiro, unicidade por organização e tratamento conservador de registros anteriores. Não houve integração a cadastro de empresas, consulta à situação fiscal ou validação de documentos estrangeiros em órgãos competentes.

## Alternativas consideradas

| Alternativa | Benefício | Limite / motivo da rejeição |
|---|---|---|
| Restringir todo cadastro a empresas brasileiras com CNPJ | Regras e UI simples | Impede identificar cedentes estrangeiros mesmo quando o identificador não precisa ser verificado fiscalmente |
| Aceitar documentos de qualquer país como fiscalmente validados | Aparente cobertura internacional | Afirma verificação sem validador e sem fonte normativa; risco de dados e conclusões incorretos |
| **CNPJ do Brasil validado por formato/DV; identificador estrangeiro `OUTRO` não verificado** | Integridade do CNPJ e extensibilidade explícita por país/tipo | A identidade estrangeira permanece sem comprovação fiscal; regras precisam ser expandidas para cada país/tipo quando necessário |

## Decisão

1. **País e tipo são campos independentes**, com país ISO 3166-1 alfa-2. País do documento não altera a organização do cedente, a autorização ou a moeda da liquidação. A organização é estabelecida exclusivamente pelo contexto autenticado.
2. **`BR/CNPJ` é o único documento com validador de formato e dígitos verificadores implementado.** Aceitar CNPJ numérico histórico e CNPJ alfanumérico (14 posições canônicas: primeiras 12 alfanuméricas ASCII, últimos dois dígitos numéricos), com validação dos dois DVs conforme regra oficial e normalização para caixa alta sem pontuação. A entrada aceita formato canônico ou máscara completa válida, não remoção indiscriminada de caracteres.
3. **Identificadores estrangeiros:** para país ISO diferente de `BR`, aceitar apenas `tipoDocumento = OUTRO` e número canônico de 1 a 32 caracteres `[A-Z0-9]`, com `statusDocumento = NAO_VERIFICADO`. A aceitação representa **validação sintática restrita**, não validação fiscal, de existência ou atividade empresarial.
4. **Combinações rejeitadas:** `BR/OUTRO`, `US/CNPJ` e quaisquer tipos fiscais estrangeiros específicos (`EIN`, `VAT`, etc.) sem validador homologado. O sistema não trata CNPJ como documento de outro país.
5. **`VALIDADO_DV` significa apenas que o CNPJ passou pelas regras estruturais e dos dígitos verificadores.** Não atesta existência do cadastro, situação ativa, titularidade, regularidade tributária ou autenticidade da empresa. A aplicação não consulta a Receita Federal nem serviços fiscais internacionais.
6. **Contratos de cadastro:** `POST /api/v1/cedentes` e `POST /bff/v1/cedentes` aceitam `pais` e `tipoDocumento` juntos, usando o campo `documento` para o número. Na entrada legada apenas com `nome`/`documento`, a **nova inclusão** é interpretada como `BR/CNPJ`, não como documento arbitrário. Trata-se de restrição intencional do antigo comportamento e exige que consumidores com identificadores estrangeiros adotem o contrato estruturado. Respostas preservam os campos anteriores e acrescentam identificação/status.
7. **Persistência e legado:** a migration `V5__documentos_empresariais_cedentes.sql` introduz país, tipo e documento canônico para novos registros; **não presume `BR/CNPJ` para o legado**. Registros históricos sem identificação permanecem com país/tipo/canônico desconhecidos e `statusDocumento = DESCONHECIDO`. Colisões históricas exigem reconciliação humana e podem impedir a migration; não se fundem, reclassificam ou apagam documentos automaticamente. Novas identidades são únicas por organização, país, tipo e documento canônico, com controles adicionais de reservas conservadoras contra conflito com legado.
8. **Arquitetura e confiança:** manter Strategy por tipo/país no domínio, validação definitiva no backend e controles PostgreSQL, com adaptação de máscara/feedback no React. Não deduzir validade exclusivamente da UI. Sem dados fiscais em logs de erro.

## Consequências

- CNPJ estruturalmente inválido e país/tipo não suportados são rejeitados na API/BFF (`400`), e colisões canônicas ou possíveis coincidências com histórico recebem `409`, sem exposição de outro tenant.
- Consumidores da API antiga que enviavam identificadores arbitrários devem adaptar o payload; a manutenção da forma antiga **não** significa manutenção de sua permissividade anterior.
- É possível cadastrar identificação de empresa estrangeira como **não verificada**, mas não afirmar conformidade fiscal internacional nem exibir sinalização de aprovação por autoridade tributária.
- Registros anteriores à V5 mantêm identidade histórica desconhecida. Migração e reconciliação são atividades operacionais restritas; uma aparente correspondência textual não autoriza conversão automática.
- Multimoedas e documentos estrangeiros são conceitos distintos: pagamento em USD não determina país do cedente nem exige um identificador estrangeiro.

## Gatilhos de revisão

- Necessidade homologada de EIN, VAT, Company Number ou outro documento estrangeiro, com fonte oficial, validador, casos de teste, política por país e decisão explícita para comprovação cadastral.
- Inclusão de consulta confiável de existência/situação no CNPJ brasileiro, diferenciando estados como `VALIDADO_DV` e `VERIFICADO_FONTE_OFICIAL`; não alterar significado retroativo dos status existentes.
- Exigência de KYC/AML ou verificação documental legalmente obrigatória, incluindo trilha de evidências, consentimentos, segurança e governança.
- Política aprovada de reconciliação e migração do legado ambíguo; não usar reclassificação automática sem critérios verificáveis.

## Evidência e limites de validação

A decisão descreve comportamento observado no código da B14 integrado em `develop`, especialmente `DocumentoEmpresarial`, `ValidadorCnpj`, `ValidadorIdentificadorNaoVerificado`, `ServicoCadastro`, migração V5 e [documento técnico B14](../../backend/docs/B14_DOCUMENTOS_CEDENTES.md). A descrição do [PR #56](https://github.com/RobsonSilverioSantos/srm-credit-engine/pull/56) relata testes Kotlin/PostgreSQL e frontend; a presença do código/PR não comprova homologação fiscal, consulta a órgãos externos ou teste ponta a ponta manual em ambiente produtivo.
