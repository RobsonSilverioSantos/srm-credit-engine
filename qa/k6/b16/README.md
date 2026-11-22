# B16: teste de concorrência do cadastro em lote

**Histórico:** scripts integrados pelo [PR #70](https://github.com/RobsonSilverioSantos/srm-credit-engine/pull/70) e backend B16 pelo [PR #71](https://github.com/RobsonSilverioSantos/srm-credit-engine/pull/71). **Relatório de QA de 22/11/2026:** execução real em ambiente descartável sobre o snapshot `78b5abf` registrou 32/33 verificações no k6 versionado por falso negativo da comparação de replay via `res.json()`. Diagnóstico com cópia isolada do teste reportou 50/50 verificações e 396 referências distintas no PostgreSQL. Este PR corrige o comparador e adiciona regressões; **a execução real do script corrigido sobre a `develop` atual permanece pendente**.

O cenário exercita a API pública de cadastro em ambiente de teste, sem alterações no backend, no contrato ou nas políticas financeiras. Os dois [CSVs sintéticos versionados](../../../docs/validacao/lotes/README.md), com 200 linhas cada, repetem quatro referências em posições iguais e diferentes. O teste não chama endpoints de liquidação, não exige cotação USD elegível para o cadastro e não demonstra carga ou lock otimista de liquidação (B17).

## Execução

Pré-requisitos: Grafana k6 instalado de fonte oficial, implementação B16 disponível em loopback, PostgreSQL descartável e uma organização de teste inicialmente sem as referências dos arquivos. Use um JWT de teste autorizado a cadastrar e o UUID de um cedente sintético da mesma organização. As cópias em memória dos dois CSVs recebem esse UUID, sem editar os arquivos versionados.

Na raiz do repositório, com as variáveis já preenchidas localmente:

```bash
export SRM_B16_BASE_URL=http://127.0.0.1:8080
export SRM_B16_AMBIENTE_DESCARTAVEL=sim
# Definir SRM_B16_JWT e SRM_B16_CEDENTE_ID no ambiente sem registrar o token.
k6 run qa/k6/b16/concorrencia.js
```

A API grava dados de teste. Não encaminhar a porta para produção, não usar `k6 cloud`, não habilitar saída de depuração HTTP, não gravar o JWT e não reutilizar uma organização com as mesmas referências para outra rodada. O script bloqueia destinos fora de loopback e redirecionamentos, mas não tem como provar que um proxy disponibilizado em loopback aponta para ambiente descartável.

## Critérios verificados

1. Fazer as duas prévias sem escrita e verificar que os dois arquivos continuam confirmáveis, com 200 itens novos cada.
2. Enviar as duas confirmações concorrentes com `http.batch` e chaves idempotentes distintas. Qualquer lote pode vencer. Exigir uma resposta 201 com 200 recebíveis e uma 409.
3. Consultar novamente os dois arquivos. As 200 referências do vencedor devem estar como `JA_CADASTRADO`. Das 200 do perdedor, 4 devem estar como `JA_CADASTRADO` e todas as 196 exclusivas como `NOVO`. As consultas são observações da API das referências sob teste, não uma contagem direta em SQL. O teste PostgreSQL da B16 continua necessário para provar atomicidade no armazenamento e ausência de efeitos laterais.
4. Reenviar a confirmação vencedora com a mesma chave e o mesmo comando, exigindo o resultado original. Remover explicitamente as quatro referências compartilhadas do arquivo perdedor em memória, refazer sua prévia e confirmar 196 linhas por nova requisição e nova chave. As duas massas originais então devem mostrar 396 identidades cadastradas na união.

O teste falha se não conseguir observar o estado esperado. O disparo HTTP em paralelo não garante barreira na transação. Para a prova determinística sobre versões de recebível, preserve os testes específicos de PostgreSQL: o [B07](../../../backend/app/src/test/kotlin/br/com/srm/creditengine/liquidacao/IntegridadeB07PostgresTest.kt) exerce este princípio em liquidação individual; o cenário em lote permanece fora deste PR.

## Diagnóstico do replay e evidência reportada em 22/11/2026

- **Causa observada pelo agente QA:** no k6 1.6.1, as duas respostas HTTP do cadastro original e de seu replay tinham corpos idênticos, mas o uso de `JSON.stringify()` sobre os objetos retornados por `res.json()` os comparava com ordem variável de propriedades. Isso produziu uma falha de teste, sem evidência de violação de idempotência do backend.
- **Correção:** comparar diretamente os **corpos HTTP originais** (`response.body`) após conferir ambos os status 201 e os campos de negócio por `conferirCadastro()`. O contrato exige replay com mesmo comando/chave devolvendo a resposta original; uma resposta HTTP realmente divergente continua reprovada. Não normalizar nem ordenar campos para mascarar alteração real da resposta.
- **Regressões locais sem rede:** `node --test qa/k6/b16/contrato.test.mjs` cobre replay com JSON reordenado no parser, mas corpo HTTP igual, e replay com corpo HTTP diferente que deve falhar. `node --input-type=module --check < qa/k6/b16/concorrencia.js` valida sintaxe.
- **Relatório de QA anterior ao ajuste (snapshot `78b5abf`, antes de B18):** 43 testes backend e 50 frontend aprovados; Chrome→BFF→PostgreSQL com 200 recebíveis cadastrados e liquidados em confirmações distintas, quatro duplicados A/B bloqueando integralmente o lote B; XLSX real com 200 itens validado; k6 original 32/33 e cópia diagnóstica fora do repositório 50/50. Esses números são **resultados informados na saída do agente QA**, não reproduzidos neste PR nem evidências de execução na `develop` atual.
- **Limites:** não estão anexados ao repositório os logs/XMLs/capturas/hashes originais da sessão do agente. A cópia diagnóstica não foi integrada; não confundir sua execução com a do arquivo versionado corrigido. Para homologação final, repetir o k6 deste PR contra um banco descartável e preservar resultados auditáveis, sem tokens e sem vazar documentos fiscais.
## Evidência e uso de IA

- **Tarefa QA-B16-k6**, de escopo independente da implementação backend [B16 / #63](https://github.com/RobsonSilverioSantos/srm-credit-engine/issues/63), liberada para cadastro atômico pelo [PR #69](https://github.com/RobsonSilverioSantos/srm-credit-engine/pull/69) e pela [ADR-024](../../../docs/adr/ADR-024-cadastro-atomico-lotes.md).
- O agente QA (ChatGPT) preparou o roteiro, o teste e as verificações contra um substituto local do contrato coordenado com o agente backend. Os artefatos não substituem a revisão, a validação integrada nem o merge pelo responsável técnico. O histórico de [uso de IA](../../../AI_USAGE.md) não foi reescrito.
- Executados localmente `node --input-type=module --check < qa/k6/b16/concorrencia.js` e `node --test qa/k6/b16/contrato.test.mjs`: cinco testes aprovados, incluindo vencedor A/B, persistência indevida após 409, ambiente com referências pré-existentes e rejeição de destino externo. O substituto não abre rede e não comprova transporte, concorrência real, k6 executado ou PostgreSQL.
- **Histórico da G02/PR #70:** naquele momento não havia execução real de k6. A validação QA reportada em 22/11/2026 ocorreu depois, em snapshot isolado, e identificou o falso negativo agora corrigido. Não foi realizada nesta alteração uma nova execução real do k6 versionado já corrigido; o teste de contrato Node e a CI têm finalidade distinta.
