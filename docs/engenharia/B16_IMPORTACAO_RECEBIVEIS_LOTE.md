# B16 — API de importação de recebíveis em lote por arquivo

**Status:** proposta P1, sem autorização de implementação até homologação de política de lote.  
**Issue:** [#63](https://github.com/RobsonSilverioSantos/srm-credit-engine/issues/63).  
**Dependências técnicas:** B04, B08, B08-S01, B14 e B12 já integradas. Interação futura com B15 exige isolar o câmbio de aferição e evitar conflito entre migrations.  
**Executor proposto:** agente `backend` em worktree exclusivo; `architect`, `qa` e `reviewer` como revisores. Frontend F08 só pode iniciar após integração e documentação do contrato B16.

## Motivação e enquadramento no enunciado

O [enunciado original](../requisitos/ENUNCIADO_ORIGINAL_SRM_CREDIT_ENGINE_V2.md) descreve receber **um lote de recebíveis**, calcular o deságio e registrar uma transação auditável no contexto de negócio. Os requisitos técnicos explícitos exigem motor, API REST, liquidação ACID/idempotente e extrato, mas **não estabelecem obrigatoriedade de upload CSV/XLSX**. Esta tarefa amplia a cobertura operacional sem declarar uma exigência técnica não formulada.

A solução atual aceita cadastro de recebíveis individualmente e operações de liquidação individuais. **Importar um lote não deve equivaler a liquidar títulos automaticamente**. A fronteira entre importação e liquidação financeira é relevante para autorização, snapshots, idempotência e integridade.

## Fluxo proposto — duas fases

1. **Pré-validação e pré-simulação sem persistência:** envio de arquivo via `POST /api/v1/lotes/recebiveis/validacoes` e `/bff/v1/lotes/recebiveis/validacoes` (`multipart/form-data`). Extrair e normalizar linhas com adaptador de arquivo, validar organização, cedentes e tipos, calcular valores indicativos no backend pelos casos de uso de simulação existentes, devolver relatório de erros por linha e um modelo canônico dos itens. Não criar recebíveis, não liquidar e não reservar taxa/câmbio nessa fase.
2. **Confirmação de cadastro:** operador revisa a prévia e confirma o payload canônico em `POST /api/v1/lotes/recebiveis` e `/bff/v1/lotes/recebiveis` com `Idempotency-Key`. O servidor revalida conteúdo, tenant, duplicados e permissões; persiste resultado do lote com IDs criados, autor e instante. **Proposta: tudo-ou-nada**; nenhuma linha gravada se qualquer item falhar, rollback integral em PostgreSQL e replay exato com a mesma chave/payload. Chave igual/payload divergente resulta em `409`. O resultado distingue claramente recebível **cadastrado** de **liquidado**.

A implementação **não poderá usar a sessão HTTP ou a camada de apresentação como autoridade financeira**. API REST e BFF chamam casos de uso idênticos, sem HTTP interno. O backend reconstrói a validação; nunca confia no resultado enviado pelo browser.

## Formatos de arquivo

**Formato primário para MVP:** CSV UTF-8, `;` como delimitador e cabeçalhos em ordem definida, sem `sep=` de Excel; tolerar BOM UTF-8 conforme contrato testado. A carga deve tratar `valorFaceBrl` como **string decimal canônica com ponto**, por exemplo `25000.00`, nunca `Number` ou `Double`.

**Formato adicional:** modelo `.xlsx` com primeira aba padronizada, sem macros, fórmulas, links externos, células calculadas ou objetos embarcados. CSV e XLSX devem gerar os mesmos comandos canônicos e erros; o suporte XLSX será etapa própria dentro da B16 **somente após aprovação do custo/escopo**, sem permitir que a tela prometa suporte não implementado.

Cabeçalho proposto:

```text
referenciaExterna;cedenteId;modalidade;valorFaceBrl;prazoMeses;vencimento;moedaPagamento
```

- `referenciaExterna`: identificador de negócio único por organização e lote, estável para deduplicação/auditoria, não derivado da posição no arquivo.
- `cedenteId`: UUID do cedente **já cadastrado** na organização autenticada. Não criar automaticamente cedentes a partir de números fornecidos no arquivo; `B14` permanece autoridade do cadastro.
- `modalidade`: `DUPLICATA_MERCANTIL` ou `CHEQUE_PRE_DATADO`.
- `valorFaceBrl`: string decimal positiva e representável na escala financeira já definida.
- `prazoMeses`: inteiro positivo; `vencimento` opcional ISO `AAAA-MM-DD` e **informativo**, sem conversão implícita para prazo.
- `moedaPagamento`: `BRL` ou `USD`, usada **somente na prévia indicativa do lote**; o cadastro `NovoRecebivel` não persiste moeda de pagamento, que é escolhida na liquidação. Preservar esse limite de contrato.

Campos adicionais, como número documental do recebível ou origem bancária, exigem justificativa e migração compatível, não inclusão silenciosa.

## Integridade, segurança e operação

- Tamanho inicial **proposto**: no máximo 200 linhas e 2 MiB, configurável e sujeito a confirmação; rejeitar arquivos acima do limite antes de consumir integralmente em memória. Retornar `413` para tamanho e erros `400/422` estruturados para formato/linhas, preservando `401/403/409/503` de acordo com causa.
- Identificar repetição dentro do arquivo e contra registros persistidos; verificar usuário, tenant e cedente em **todas** as linhas. Não permitir `organizacaoId` livre no payload nem inserir em lote linhas de outros tenants.
- Reutilizar `BigDecimal`, Strategy, arredondamento `HALF_EVEN`, golden cases C1–C3 e regras de elegibilidade de taxas. Cotação da **prévia** não é compromisso de liquidação futura. Compatibilidade com B15 requer fonte/momento explícitos na simulação, nunca fallback oculto.
- Abrir nova migration Flyway com **próxima versão disponível na data do desenvolvimento**, considerando B14 V5 e uma possível migration B15 concorrente. Registrar lote, referência, identidade por organização, hash, chave idempotente, autor, carimbo UTC, estado e vínculos dos recebíveis sem reescrever V1–V5. Especificar unicidade e travas para evitar importações simultâneas duplicadas.
- Não logar conteúdo completo, dados fiscais, JWT ou planilhas; relatar problemas sem PII desnecessária. Exportações CSV devem neutralizar fórmulas. XLSX exige defesa contra ZIP bombs, fórmulas e referências externas. Nunca interpretar célula de fórmula como valor confiável.
- Parser, validação, aplicação e persistência em portas/adaptadores separados, um tipo Kotlin top-level por arquivo; evitar parser no controller e regras financeiras no adaptador.

## Política ainda não aprovada — gate de implementação

Antes de alterar `SPEC.md`, `DECISIONS.md` ou iniciar a B16, o responsável deve aprovar expressamente:
1. **Resultado do upload:** prévia + confirmação de **cadastro**, sem liquidação automática, ou outra operação.
2. **Atomicidade:** proposta de **tudo-ou-nada** versus aceitação parcial com erros por linha.
3. **Duplicidade e replay:** semântica de `referenciaExterna`, unicidade por organização, token/chave idempotente e retenção de status.
4. **Escopo e limites:** CSV no primeiro incremento; XLSX na mesma entrega ou posterior; limite de linhas/tamanho e política para USD sem cotação elegível.

Liquidação de lote é uma **evolução financeira separada**, que precisará de especificação de confirmação, instantâneo de taxa, atomicidade, comportamento de falha parcial, idempotência por título/lote e testes de concorrência reais. Não implementar por inferência a partir da palavra "lote" do enunciado.

## Critérios de aceite condicionais

- [ ] Pré-validação não persiste dados, identifica linha/campo/motivo e calcula prévia pelo motor existente com C1–C3.
- [ ] Cadastro confirmado é atômico/idempotente; falhas de tenant, duplicidade e concorrência não geram títulos parciais.
- [ ] API/BFF/OpenAPI e testes PostgreSQL reais cobrem limites, formato, BOM, encoding, decimal, CNPJ indireto, autorização e replay.
- [ ] CSV e, se aprovado, XLSX usam o mesmo contrato sem fórmula, macros, links ou operações inseguras.
- [ ] Nenhuma liquidação bancária, transação financeira ou pagamento é criado implicitamente no upload.
- [ ] PR e CI separados, integração só pelo responsável técnico.
