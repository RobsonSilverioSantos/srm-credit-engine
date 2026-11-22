# B16 — API de importação de recebíveis em lote por arquivo

**Status:** P1 — **integrada em `develop` pelo [PR #71](https://github.com/RobsonSilverioSantos/srm-credit-engine/pull/71)** em 22/11/2026, merge `186786e27320e83700a81e3922c72d096cefcb86`. Cadastro atômico aprovado na [ADR-024](../adr/ADR-024-cadastro-atomico-lotes.md). A B17 permanece bloqueada por política de liquidação própria.

**Issue:** [#63](https://github.com/RobsonSilverioSantos/srm-credit-engine/issues/63).  
**Dependências técnicas:** B04, B08, B08-S01, B14 e B12 já integradas. Interação futura com B15 exige isolar o câmbio de aferição e evitar conflito entre migrations.  
**Execução registrada:** agente backend em feature/worktree exclusivos, com revisão independente relatada no PR. Não reiniciar a B16. F08 continua bloqueada por B17, contratos homologados e nova liberação literal.

## Reconciliação G02 — 22/11/2026

O PR #71 integrou pré-validação CSV/XLSX sem persistência, confirmação atômica/idempotente API/BFF, OpenAPI e migration V6. Fontes implementadas: [controller](../../backend/app/src/main/kotlin/br/com/srm/creditengine/http/adaptador/entrada/LotesRecebiveisHttp.kt), [caso de uso](../../backend/aplicacao/src/main/kotlin/br/com/srm/creditengine/lotes/aplicacao/ServicoImportacaoLote.kt), [persistência](../../backend/app/src/main/kotlin/br/com/srm/creditengine/lotes/adaptador/saida/LotesJdbc.kt) e [testes PostgreSQL](../../backend/app/src/test/kotlin/br/com/srm/creditengine/lotes/ImportacaoLoteHttpPostgresTest.kt).

**Evidência remota:** CI backend/PostgreSQL, frontend e Compose aprovada no PR #71; o agente relatou compilação/smokes locais parciais e impedimento de toolchain para concluir `clean check` local. A G02 não reexecutou esses testes. Os [scripts QA k6](../../qa/k6/b16/README.md) foram integrados pelo [PR #70](https://github.com/RobsonSilverioSantos/srm-credit-engine/pull/70), merge `9043732b86a4671bf7ea368e288bf68151205fdb`, mas **a execução real contra o backend continua pendente**. Cadastro concorrente não comprova controle otimista financeiro da B17.

As seções abaixo preservam o escopo e os critérios redigidos no planejamento. Verbos no futuro e caixas desmarcadas não significam que B16 continua sem implementação, nem constituem evidência de testes; conferir o PR integrado e o [adendo G02 do AI_USAGE](../../AI_USAGE.md). Nenhuma regra financeira foi alterada por esta reconciliação.

## Motivação e enquadramento no enunciado

O [enunciado original](../requisitos/ENUNCIADO_ORIGINAL_SRM_CREDIT_ENGINE_V2.md) descreve receber **um lote de recebíveis**, calcular o deságio e registrar uma transação auditável no contexto de negócio. Os requisitos técnicos explícitos exigem motor, API REST, liquidação ACID/idempotente e extrato, mas **não estabelecem obrigatoriedade de upload CSV/XLSX**. Esta tarefa amplia a cobertura operacional sem declarar uma exigência técnica não formulada.

Antes da B16, a solução aceitava apenas cadastro e liquidação individuais. O cadastro em lote foi integrado no PR #71; **importar um lote não equivale a liquidar títulos automaticamente**. A fronteira entre importação e liquidação financeira é relevante para autorização, snapshots, idempotência e integridade. A liquidação explícita posterior de um lote será tratada na [B17](B17_LIQUIDACAO_LOTE_CONCORRENCIA.md), [issue #65](https://github.com/RobsonSilverioSantos/srm-credit-engine/issues/65), com concorrência e semântica transacional próprias.

## Fluxo proposto — duas fases

1. **Pré-validação e pré-simulação sem persistência:** envio de arquivo via `POST /api/v1/lotes/recebiveis/validacoes` e `/bff/v1/lotes/recebiveis/validacoes` (`multipart/form-data`). Extrair e normalizar linhas com adaptador de arquivo, validar organização, cedentes e tipos, calcular valores indicativos no backend pelos casos de uso de simulação existentes, devolver relatório de erros por linha e um modelo canônico dos itens. Não criar recebíveis, não liquidar e não reservar taxa/câmbio nessa fase.
2. **Confirmação de cadastro:** operador revisa a prévia e confirma o payload canônico em `POST /api/v1/lotes/recebiveis` e `/bff/v1/lotes/recebiveis` com `Idempotency-Key`. O servidor revalida conteúdo, tenant, duplicados e permissões; persiste resultado do lote com IDs criados, autor e instante. **Decisão aprovada — tudo-ou-nada:** nenhuma linha cadastrada se qualquer item falhar, incluindo referências duplicadas e conflitos simultâneos; rollback integral em PostgreSQL e replay exato com a mesma chave/payload. Chave igual/payload divergente resulta em `409`. O resultado distingue claramente recebível **cadastrado** de **liquidado**.

A implementação **não poderá usar a sessão HTTP ou a camada de apresentação como autoridade financeira**. API REST e BFF chamam casos de uso idênticos, sem HTTP interno. O backend reconstrói a validação; nunca confia no resultado enviado pelo browser.

## Formatos de arquivo

**Formato primário para MVP:** CSV UTF-8, `;` como delimitador e cabeçalhos em ordem definida, sem `sep=` de Excel; tolerar BOM UTF-8 conforme contrato testado. A carga deve tratar `valorFaceBrl` como **string decimal canônica com ponto**, por exemplo `25000.00`, nunca `Number` ou `Double`.

**Formato adicional incluído na B16:** modelo `.xlsx` com primeira aba padronizada, sem macros, fórmulas, links externos, células calculadas ou objetos embarcados. CSV e XLSX devem gerar os mesmos comandos canônicos e erros. É permitido desenvolver/testar CSV antes de XLSX **na mesma feature B16**, mas o PR somente poderá afirmar suporte a ambos após testes dos dois formatos; a interface não pode prometer XLSX antes do contrato backend disponível.

Cabeçalho proposto:

```text
referenciaExterna;cedenteId;modalidade;valorFaceBrl;prazoMeses;vencimento;moedaPagamento
```

- `referenciaExterna`: identificador de negócio **único por organização entre todos os lotes**, estável para deduplicação/auditoria, não derivado da posição no arquivo nem somente do `idLote`.
- `cedenteId`: UUID do cedente **já cadastrado** na organização autenticada. Não criar automaticamente cedentes a partir de números fornecidos no arquivo; `B14` permanece autoridade do cadastro.
- `modalidade`: `DUPLICATA_MERCANTIL` ou `CHEQUE_PRE_DATADO`.
- `valorFaceBrl`: string decimal positiva e representável na escala financeira já definida.
- `prazoMeses`: inteiro positivo; `vencimento` opcional ISO `AAAA-MM-DD` e **informativo**, sem conversão implícita para prazo.
- `moedaPagamento`: `BRL` ou `USD`, usada **somente na prévia indicativa do lote**; o cadastro `NovoRecebivel` não persiste moeda de pagamento, que é escolhida na liquidação. Preservar esse limite de contrato.

Campos adicionais, como número documental do recebível ou origem bancária, exigem justificativa e migração compatível, não inclusão silenciosa.

## Cenário demonstrativo — dois arquivos de 200 linhas

Os arquivos [Lote A](../validacao/lotes/SRM_LOTE_A_200_RECEBIVEIS.csv) e [Lote B](../validacao/lotes/SRM_LOTE_B_200_RECEBIVEIS_COM_4_REPETIDOS.csv) contêm 200 linhas cada, com quatro referências idênticas **entre os dois arquivos** (duas nas mesmas posições e duas realocadas). Em conjunto, há **396 referências de negócio distintas**. O teste deve ser reprodutível com o mesmo cedente e a mesma organização, desde que o UUID do arquivo exista no ambiente; o UUID é derivado de captura de demonstração anterior e não garante persistência em bancos reiniciados.

**Regra de identidade indispensável:** `referenciaExterna` deve ser única e consultável **por organização em todos os lotes**, não somente no mesmo `idLote`. Persistir o vínculo estável `(organizacao_id, referencia_externa) → recebivel_id` e proteger por constraint/índice UNIQUE no PostgreSQL. O backend deve distinguir `NOVO`, `JA_CADASTRADO` e `JA_LIQUIDADO` e devolver a correspondência de linha/identidade com motivo seguro. **Não considerar igualdade de valor, prazo, modalidade ou moeda prova de título duplicado:** títulos diferentes podem ter termos financeiros idênticos. Não aceitar nova referência para burlar identidade de um recebível que tenha identificador documental de negócio distinto; evoluir regra quando existir documento de título específico.

A validação do Lote B após cadastro e liquidação do Lote A deve acusar **quatro `JA_LIQUIDADO`**, sem nova gravação desses títulos. Na política de cadastro **tudo-ou-nada aprovada**, o lote B não pode ser confirmado enquanto contiver duplicidades; o operador corrige ou exclui as linhas apontadas, confirma somente as 196 novas e pode então seguir para a liquidação B17 com nova confirmação. **Não ignorar duplicidades automaticamente** nem declarar que um processamento sequencial desses arquivos constitui teste de concorrência; a corrida controlada sobre o mesmo `recebivel_id` exige teste separado na B17.


## Integridade, segurança e operação

- Limite operacional inicial **configurável**: no máximo 200 linhas e 2 MiB por arquivo, para incluir integralmente cada massa de demonstração; rejeitar arquivos acima do limite antes de consumir integralmente em memória. Retornar `413` para tamanho e erros `400/422` estruturados para formato/linhas, preservando `401/403/409/503` de acordo com causa.
- Identificar repetição dentro do arquivo **e entre lotes preexistentes por identidade de negócio persistida e única por organização**; distinguir `JA_CADASTRADO` de `JA_LIQUIDADO`, verificar usuário, tenant e cedente em **todas** as linhas. Não permitir `organizacaoId` livre no payload nem inserir em lote linhas de outros tenants.
- Reutilizar `BigDecimal`, Strategy, arredondamento `HALF_EVEN`, golden cases C1–C3 e regras de elegibilidade de taxas. Cotação da **prévia** não é compromisso de liquidação futura. Compatibilidade com B15 requer fonte/momento explícitos na simulação, nunca fallback oculto.
- Abrir nova migration Flyway com **próxima versão disponível na data do desenvolvimento**, considerando B14 V5 e uma possível migration B15 concorrente. Registrar lote, referência, identidade por organização, hash, chave idempotente, autor, carimbo UTC, estado e vínculos dos recebíveis sem reescrever V1–V5. Especificar unicidade e travas para evitar importações simultâneas duplicadas.
- Não logar conteúdo completo, dados fiscais, JWT ou planilhas; relatar problemas sem PII desnecessária. Exportações CSV devem neutralizar fórmulas. XLSX exige defesa contra ZIP bombs, fórmulas e referências externas. Nunca interpretar célula de fórmula como valor confiável.
- Parser, validação, aplicação e persistência em portas/adaptadores separados, um tipo Kotlin top-level por arquivo; evitar parser no controller e regras financeiras no adaptador.

## Política B16 aprovada e gate de implementação

**Decisão de 22/11/2026:** a confirmação do cadastro é **atômica (tudo-ou-nada)**. O upload é pré-validado sem persistência, o cadastro requer confirmação própria e não liquida títulos. A referência externa é única por organização **entre lotes**; erros e duplicidades bloqueiam o **lote inteiro**, sem descarte silencioso. A repetição exata de uma confirmação com mesma chave e comando retorna a resposta persistida; chave igual/comando diferente produz `409`. Política formalizada na [ADR-024](../adr/ADR-024-cadastro-atomico-lotes.md).

**Escopo técnico para implementação:** CSV UTF-8 delimitado por `;` e XLSX em modelo padronizado, ambos convertidos ao mesmo comando canônico; limite inicial configurável de **200 linhas e 2 MiB** por arquivo. O suporte XLSX pode ser implementado após CSV na mesma feature. Toda a massa de dados A/B pode usar a API quando o cedente estiver cadastrado no tenant de demonstração.

**Separação da simulação cambial:** ausência de cotação USD elegível pode impedir a **simulação** de valores, mas **não invalida isoladamente o cadastro** de um recebível BRL com moeda de pagamento futura ainda não escolhida no domínio. Retornar o problema de simulação como alerta distinto do erro de cadastro; a B17 exigirá 100% das simulações válidas antes da confirmação da **liquidação**, revalidando taxas e câmbio. Não associar cotação mockada com obrigação financeira permanente.

**Gate histórico da B16:** a liberação documental ocorreu no PR #69 e a implementação foi integrada no PR #71. O gate de início já foi consumido; não abrir nova feature B16 a partir deste documento. A B17 ainda requer decisão humana independente sobre liquidação parcial versus atômica e nova liberação literal em `origin/develop:TASKS.md`.

A liquidação de lote é a tarefa **B17**, planejada de forma independente. Exige confirmação distinta, regras de taxa/câmbio, política de atomicidade e falha parcial, idempotência por título/lote e testes concorrentes sobre **o mesmo recebível**. A política da B16 para cadastro não determina a política da B17 para liquidação. Não implementar liquidação automática por inferência a partir da palavra "lote" do enunciado.

## Critérios de aceite da B16

- [ ] Pré-validação não persiste dados, identifica linha/campo/motivo e calcula prévia pelo motor existente com C1–C3.
- [ ] Cadastro confirmado é **atômico (todos ou nenhum)** e idempotente; falhas de tenant, duplicidade e concorrência geram rollback completo de recebíveis, identidade de lote e referências.
- [ ] API/BFF/OpenAPI e testes PostgreSQL reais cobrem limites, formato, BOM, encoding, decimal, CNPJ indireto, autorização e replay.
- [ ] CSV e XLSX usam o mesmo contrato canônico, com rejeição de fórmulas, macros, links, XML externo e operações inseguras, e limites 200 linhas/2 MiB configuráveis.
- [ ] Nenhuma liquidação bancária, transação financeira ou pagamento é criado implicitamente no upload.
- [ ] PR e CI separados, integração só pelo responsável técnico.
