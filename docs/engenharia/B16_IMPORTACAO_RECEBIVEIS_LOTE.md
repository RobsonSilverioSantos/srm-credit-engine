# B16 — API de importação de recebíveis em lote por arquivo

**Status:** proposta P1, sem autorização de implementação até homologação de política de lote.  
**Issue:** [#63](https://github.com/RobsonSilverioSantos/srm-credit-engine/issues/63).  
**Dependências técnicas:** B04, B08, B08-S01, B14 e B12 já integradas. Interação futura com B15 exige isolar o câmbio de aferição e evitar conflito entre migrations.  
**Executor proposto:** agente `backend` em worktree exclusivo; `architect`, `qa` e `reviewer` como revisores. Frontend F08 só pode iniciar após integração e documentação do contrato B16.

## Motivação e enquadramento no enunciado

O [enunciado original](../requisitos/ENUNCIADO_ORIGINAL_SRM_CREDIT_ENGINE_V2.md) descreve receber **um lote de recebíveis**, calcular o deságio e registrar uma transação auditável no contexto de negócio. Os requisitos técnicos explícitos exigem motor, API REST, liquidação ACID/idempotente e extrato, mas **não estabelecem obrigatoriedade de upload CSV/XLSX**. Esta tarefa amplia a cobertura operacional sem declarar uma exigência técnica não formulada.

A solução atual aceita cadastro de recebíveis individualmente e operações de liquidação individuais. **Importar um lote não deve equivaler a liquidar títulos automaticamente**. A fronteira entre importação e liquidação financeira é relevante para autorização, snapshots, idempotência e integridade. A liquidação explícita posterior de um lote será tratada na [B17](B17_LIQUIDACAO_LOTE_CONCORRENCIA.md), [issue #65](https://github.com/RobsonSilverioSantos/srm-credit-engine/issues/65), com concorrência e semântica transacional próprias.

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

## Cenário demonstrativo — dois arquivos de 200 linhas

Os arquivos [Lote A](../validacao/lotes/SRM_LOTE_A_200_RECEBIVEIS.csv) e [Lote B](../validacao/lotes/SRM_LOTE_B_200_RECEBIVEIS_COM_4_REPETIDOS.csv) contêm 200 linhas cada, com quatro referências idênticas **entre os dois arquivos** (duas nas mesmas posições e duas realocadas). Em conjunto, há **396 referências de negócio distintas**. O teste deve ser reprodutível com o mesmo cedente e a mesma organização, desde que o UUID do arquivo exista no ambiente; o UUID é derivado de captura de demonstração anterior e não garante persistência em bancos reiniciados.

**Regra de identidade indispensável:** `referenciaExterna` deve ser única e consultável **por organização em todos os lotes**, não somente no mesmo `idLote`. Persistir o vínculo estável `(organizacao_id, referencia_externa) → recebivel_id` e proteger por constraint/índice UNIQUE no PostgreSQL. O backend deve distinguir `NOVO`, `JA_CADASTRADO` e `JA_LIQUIDADO` e devolver a correspondência de linha/identidade com motivo seguro. **Não considerar igualdade de valor, prazo, modalidade ou moeda prova de título duplicado:** títulos diferentes podem ter termos financeiros idênticos. Não aceitar nova referência para burlar identidade de um recebível que tenha identificador documental de negócio distinto; evoluir regra quando existir documento de título específico.

A validação do Lote B após cadastro e liquidação do Lote A deve acusar **quatro `JA_LIQUIDADO`**, sem nova gravação desses títulos. Na política de cadastro tudo-ou-nada proposta, o lote B não pode ser confirmado enquanto contiver duplicidades; o operador corrige ou exclui as linhas apontadas, confirma somente as 196 novas e pode então seguir para a liquidação B17 com nova confirmação. **Não ignorar duplicidades automaticamente** nem declarar que um processamento sequencial desses arquivos constitui teste de concorrência; a corrida controlada sobre o mesmo `recebivel_id` exige teste separado na B17.


## Integridade, segurança e operação

- Tamanho inicial **proposto**: no máximo 200 linhas e 2 MiB, configurável e sujeito a confirmação; rejeitar arquivos acima do limite antes de consumir integralmente em memória. Retornar `413` para tamanho e erros `400/422` estruturados para formato/linhas, preservando `401/403/409/503` de acordo com causa.
- Identificar repetição dentro do arquivo **e entre lotes preexistentes por identidade de negócio persistida e única por organização**; distinguir `JA_CADASTRADO` de `JA_LIQUIDADO`, verificar usuário, tenant e cedente em **todas** as linhas. Não permitir `organizacaoId` livre no payload nem inserir em lote linhas de outros tenants.
- Reutilizar `BigDecimal`, Strategy, arredondamento `HALF_EVEN`, golden cases C1–C3 e regras de elegibilidade de taxas. Cotação da **prévia** não é compromisso de liquidação futura. Compatibilidade com B15 requer fonte/momento explícitos na simulação, nunca fallback oculto.
- Abrir nova migration Flyway com **próxima versão disponível na data do desenvolvimento**, considerando B14 V5 e uma possível migration B15 concorrente. Registrar lote, referência, identidade por organização, hash, chave idempotente, autor, carimbo UTC, estado e vínculos dos recebíveis sem reescrever V1–V5. Especificar unicidade e travas para evitar importações simultâneas duplicadas.
- Não logar conteúdo completo, dados fiscais, JWT ou planilhas; relatar problemas sem PII desnecessária. Exportações CSV devem neutralizar fórmulas. XLSX exige defesa contra ZIP bombs, fórmulas e referências externas. Nunca interpretar célula de fórmula como valor confiável.
- Parser, validação, aplicação e persistência em portas/adaptadores separados, um tipo Kotlin top-level por arquivo; evitar parser no controller e regras financeiras no adaptador.

## Política ainda não aprovada — gate de implementação

Antes de alterar `SPEC.md`, `DECISIONS.md` ou iniciar a B16, o responsável deve aprovar expressamente:
1. **Resultado do upload:** prévia + confirmação de **cadastro**, sem liquidação automática, ou outra operação.
2. **Atomicidade:** proposta de **tudo-ou-nada** versus aceitação parcial com erros por linha.
3. **Duplicidade e replay:** `referenciaExterna` globalmente única por organização **entre lotes**, apontando ao mesmo recebível; distinguir já cadastrado/já liquidado, semântica de chave idempotente e retenção de status.
4. **Escopo e limites:** CSV no primeiro incremento; XLSX na mesma entrega ou posterior; limite de linhas/tamanho e política para USD sem cotação elegível.

A liquidação de lote é a tarefa **B17**, planejada de forma independente. Exige confirmação distinta, regras de taxa/câmbio, política de atomicidade e falha parcial, idempotência por título/lote e testes concorrentes sobre **o mesmo recebível**. A política da B16 para cadastro não determina a política da B17 para liquidação. Não implementar liquidação automática por inferência a partir da palavra "lote" do enunciado.

## Critérios de aceite condicionais

- [ ] Pré-validação não persiste dados, identifica linha/campo/motivo e calcula prévia pelo motor existente com C1–C3.
- [ ] Cadastro confirmado é atômico/idempotente; falhas de tenant, duplicidade e concorrência não geram títulos parciais.
- [ ] API/BFF/OpenAPI e testes PostgreSQL reais cobrem limites, formato, BOM, encoding, decimal, CNPJ indireto, autorização e replay.
- [ ] CSV e, se aprovado, XLSX usam o mesmo contrato sem fórmula, macros, links ou operações inseguras.
- [ ] Nenhuma liquidação bancária, transação financeira ou pagamento é criado implicitamente no upload.
- [ ] PR e CI separados, integração só pelo responsável técnico.
