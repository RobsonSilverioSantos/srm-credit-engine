# Validação funcional dos golden cases C1, C2 e C3 — 21/11/2026

**Sistema:** SRM Credit Engine (novo repositório).  
**Escopo:** aferição manual de resultados de **pré-simulação** pela interface React/BFF, com comparação aos casos oficiais do enunciado AI-native v2.  
**Resultado:** os três casos C1, C2 e C3 foram **confirmados pelo responsável técnico como coincidentes até os centavos**. A captura apresentada para C3 confirma visualmente os valores indicados abaixo.  
**Natureza das evidências:** relato humano de C1/C2; relato humano **e captura visual** de C3; testes automatizados versionados em Kotlin são **evidência independente distinta**. Este documento não declara execução de suíte, homologação de produção ou liquidação bancária real.

## 1. Premissas fixas do enunciado

Os golden cases usam taxa base de **1,00% ao mês**, spread de **1,50% a.m.** para duplicata mercantil e **2,50% a.m.** para cheque pré-datado; o prazo é contado em meses e a cotação fixa do caso C3 é **5,4321 BRL por 1 USD**. O resultado usa valor presente em BRL arredondado a dois decimais antes da conversão USD, conforme `SPEC.md`, ADR-008 e [enunciado original](../requisitos/ENUNCIADO_ORIGINAL_SRM_CREDIT_ENGINE_V2.md).

| Caso | Modalidade | Valor de face | Prazo | Pagamento | Câmbio de aferição | VP/pagamento esperado | Deságio BRL esperado |
|---|---|---:|---:|---|---:|---:|---:|
| **C1** | Duplicata mercantil | R$ 100.000,00 | 3 meses | BRL | — | **R$ 92.859,94** | **R$ 7.140,06** |
| **C2** | Cheque pré-datado | R$ 25.000,00 | 2 meses | BRL | — | **R$ 23.337,77** | **R$ 1.662,23** |
| **C3** | Duplicata mercantil | R$ 100.000,00 | 3 meses | USD | 5,4321 | **US$ 17.094,67** | **R$ 7.140,06** |

## 2. Resultado da validação manual

| Caso | Evidência disponível | Resultado reportado/observado | Avaliação |
|---|---|---|---|
| **C1** | Responsável técnico informou execução e coincidência exata em centavos na interface; **sem captura individual anexada ao repositório** | **R$ 92.859,94**, conforme confirmação humana do caso C1 | **Conferido pelo responsável**, não reproduzido independentemente nesta coleta |
| **C2** | Responsável técnico informou execução e coincidência exata em centavos na interface; **sem captura individual anexada ao repositório** | **R$ 23.337,77**, conforme confirmação humana do caso C2 | **Conferido pelo responsável**, não reproduzido independentemente nesta coleta |
| **C3** | Captura de tela da pré-simulação USD apresentada pelo responsável em 21/11/2026; imagem compartilhada para revisão, **não versionada neste PR** | VP BRL **R$ 92.859,94**; deságio BRL **R$ 7.140,06**; pagamento **US$ 17.094,67**; cotação exibida `5.4321000000` | **Conferência visual direta** dos valores do enunciado |

A captura C3 mostra modalidade **Duplicata mercantil**, valor de face **100000**, prazo **3 meses**, moeda de pagamento **USD**, taxa base **1,00000000%**, spread **1,50000000%**, referência temporal UTC e prazo de validade da cotação de demonstração. O instante de referência retornado pelo backend é **`2026-11-21T22:26:22.110780Z`**. O vencimento apresentado na interface é **informativo** e não define o prazo do cálculo.

**Separação de validações:** a resposta visual C3 atesta a exibição do resultado da **pré-simulação**, não a criação de recebível, a confirmação de liquidação, o snapshot persistido, a conciliação bancária ou a elegibilidade de PTAX de fechamento. A cotação 5,4321 é **fictícia e determinística**, não taxa oficial de mercado.

## 3. Evidências automatizadas já existentes, distintas do teste manual

- `backend/app/src/test/kotlin/br/com/srm/creditengine/precificacao/PrecificacaoTest.kt`: contém testes nomeados de **C1**, **C2** e **C3**, com as expectativas numéricas exatas acima, além de testes de `HALF_EVEN`, entradas inválidas e extensibilidade do Strategy.
- `AI_USAGE.md` (registro D03): contém evidência de **regressão local PostgreSQL** declarada/executada naquela etapa, com seu ambiente, comandos, contagem de testes e limites. Esta validação pela interface em **21/11/2026** constitui **nova evidência humana**, não repetição dos comandos D03.
- [PR #56 — B14](https://github.com/RobsonSilverioSantos/srm-credit-engine/pull/56): registra em seu relato a preservação dos golden cases nas regressões da B14, sem substituir a evidência de execução manual C1–C3 pela interface.

Nenhum comando de teste ou consulta ao banco foi executado **para elaborar este registro documental**. Portanto, não declarar CI ou suíte mais recente como aprovada por causa desta atualização.

## 4. Pendências, limites e reprodutibilidade

1. **Arquivar capturas específicas C1 e C2 e a captura C3 em local controlado**, se for necessário um pacote reproduzível de evidências para a defesa; a imagem de C3 existe no registro de validação fornecido pelo responsável, mas não foi incorporada ao Git por este PR.
2. Registrar, em reexecução independente, a versão/commit da aplicação, o ambiente e os payloads/respostas BFF dos três casos **sem JWT, documentos pessoais, segredos ou dados sensíveis**. Não inferir esses elementos pela captura.
3. Verificar também cenários adicionais: valores de face com centavos, taxas com maior precisão, extremos válidos, empates de arredondamento, datas/validade de cotação, indisponibilidade financeira, concorrência e revalidação de taxa na liquidação.
4. A **F05** trata formatação monetária e temporal da interface; a questão visual da cotação `5.4321000000` foi registrada no [PR #59](https://github.com/RobsonSilverioSantos/srm-credit-engine/pull/59), que propõe `1 USD = R$ 5,4321`. Trata-se de **formatação de apresentação**, sem alterar C3.
5. A **B15** planeja PTAX de fechamento, cuja taxa real pode divergir de **5,4321**. Os golden cases permanecem **isolados do mercado** com referência fixa, para assegurar que uma variação de PTAX não altere resultados de aferição.

**Conclusão verificável:** o responsável confirmou **3/3 golden cases compatíveis com o enunciado até os centavos** na pré-simulação; há inspeção visual direta da tela de C3, além de testes automatizados versionados. **Não** há base nesta coleta para afirmar homologação financeira produtiva, validação manual ponta a ponta de liquidação ou ausência de falhas em todas as combinações de entrada.
