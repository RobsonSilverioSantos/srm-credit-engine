# Massa de teste — importação e liquidação de lotes (200 + 200)

**Status (G02, 22/11/2026):** arquivos de aferição para B16/B17/F08; **API de importação/cadastro B16 integrada pelo [PR #71](https://github.com/RobsonSilverioSantos/srm-credit-engine/pull/71)**. B17 e F08 continuam bloqueadas e sem implementação. Os [scripts QA k6](../../../qa/k6/b16/README.md) foram integrados no PR #70, mas execução real contra o backend permanece pendente. As massas isoladamente não comprovam transação ou concorrência.

## Arquivos e contrato

- [Lote A — 200 recebíveis](SRM_LOTE_A_200_RECEBIVEIS.csv)
- [Lote B — 200 recebíveis e quatro referências já presentes em A](SRM_LOTE_B_200_RECEBIVEIS_COM_4_REPETIDOS.csv)

Formato: **CSV UTF-8 sem BOM**, separador `;`, cabeçalho com sete colunas:

```text
referenciaExterna;cedenteId;modalidade;valorFaceBrl;prazoMeses;vencimento;moedaPagamento
```

- `referenciaExterna`: chave de negócio determinística da massa; **única por organização entre todos os lotes**, apontando sempre ao mesmo recebível. Não confundir com `Idempotency-Key` HTTP nem com `liquidacaoId`.
- `cedenteId`: valor `483e5e35-d464-4fda-9d36-3fd1b7c4aa54`, proveniente de uma captura de tela de ambiente de demonstração. **Só pode ser utilizado se esse cedente ainda existir na organização autenticada; substituir o UUID dos dois arquivos se o banco ou tenant tiver mudado.** Os arquivos não criam cedentes automaticamente.
- `valorFaceBrl`: decimal canônico ASCII com ponto e duas casas (nunca vírgula, `R$` ou float). `vencimento` ISO e informativo; `prazoMeses` é a base efetiva da fórmula.
- `moedaPagamento`: BRL ou USD como intenção de simulação, preservada em metadados do item B16; não faz parte de `NovoRecebivel` nem realiza liquidação. O consumo financeiro pela B17 depende de política aprovada e confirmação própria.

O Lote A inclui as premissas de **C1, C2 e C3 nas três primeiras linhas**, sem exigir que a cotação de demonstração esteja elegível. O câmbio USD é **condicional a taxa elegível no ambiente**; uma prévia falha por cotação vencida não deve autorizar liquidação e precisa indicar causa. Os dois arquivos contêm 318 linhas BRL e 82 USD. **Nenhum preço ou liquidação está pré-calculado no arquivo.**

## Quatro referências repetidas entre arquivos

| Referência de negócio | Posição de dados A | Linha física A | Posição de dados B | Linha física B | Tipo |
|---|---:|---:|---:|---:|---|
| `SRM-DEMO-A-0020` | 20 | 21 | 20 | 21 | Mesma posição |
| `SRM-DEMO-A-0075` | 75 | 76 | 75 | 76 | Mesma posição |
| `SRM-DEMO-A-0120` | 120 | 121 | 33 | 34 | Fora de ordem |
| `SRM-DEMO-A-0175` | 175 | 176 | 190 | 191 | Fora de ordem |

Todas as sete colunas das quatro linhas repetidas são **idênticas** nos dois arquivos. Cada arquivo contém 200 linhas sem repetições internas. A união dos arquivos representa **396 identidades de recebível distintas**; quatro aparecem em ambos os arquivos.

## Roteiro de demonstração

**Limite vigente:** cadastro e pré-validação podem usar a API B16 integrada. As etapas abaixo que dependem de prévia/liquidação **de lote B17** e botões F08 são roteiro futuro, não funcionalidades disponíveis ou testes executados.

1. Conferir `cedenteId`, JWT/tenant e taxa base; para USD, garantir **taxa/cotação elegível** ou testar legitimamente a rejeição. Nunca modificar taxas persistidas para forçar golden cases.
2. Importar e **pré-validar A (200 linhas)**. Conferir os resultados indicativos e erros por linha, sem persistência. O cadastro usa confirmação separada com `Idempotency-Key`, sob política atômica B16 já aprovada; falha isolada de simulação USD não impede o cadastro de título BRL válido. A conferência financeira completa antes de liquidar depende da B17.
3. Após cadastro A, todas as 200 referências tornam-se títulos `DISPONIVEL`. Consultar a prévia B17; **`Liquidar lote` só fica habilitado quando 100% das simulações estão concluídas e válidas**. Confirmar liquidação por modal separado; backend revalida tudo no instante da escrita.
4. Com A liquidado, **pré-validar B (200 linhas)**. Esperado: **quatro linhas identificadas por referência externa como `JA_LIQUIDADO`**, com mesma identidade de recebível do Lote A, sem novos lançamentos. Se A estiver apenas cadastrado, o esperado é `JA_CADASTRADO`. Na política tudo-ou-nada da B16, B não deve ser cadastrado enquanto essas quatro referências persistirem; remover/corrigir as quatro linhas e importar apenas os **196 novos**, com confirmação própria.
5. **Separadamente**, executar o cenário concorrente: duas requisições com **chaves idempotentes distintas** sobre o **mesmo `recebivelId`**, sincronizadas por barreira em teste PostgreSQL real. Verificar uma liquidação e um conflito de estado/versão, uma trilha de auditoria e nenhum duplo lançamento. Repetir comando com **mesma** chave/payload representa replay e não deve gerar segundo registro.
6. Confirmar a política de liquidação do lote **tudo-ou-nada ou parcial por recebível** antes da execução. A seleção desta política não é determinada pelos arquivos ou pela aprovação da confirmação de UI.

**Segurança e limites:** estes são dados sintéticos de teste e devem ser usados em ambiente de demonstração; não carregar em produção. Nomes ou números documentais reais de pessoas jurídicas não estão presentes no CSV. A ocorrência de mesmo valor, prazo e modalidade **sem a mesma referência externa** não caracteriza automaticamente o mesmo recebível.

**Documentos:** [B16](../../engenharia/B16_IMPORTACAO_RECEBIVEIS_LOTE.md), [B17](../../engenharia/B17_LIQUIDACAO_LOTE_CONCORRENCIA.md), [F08](../../engenharia/F08_TELA_IMPORTACAO_LOTE.md).
