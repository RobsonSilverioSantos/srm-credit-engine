# B16: teste de concorrência do cadastro em lote

**Estado reconciliado pela G02 em 22/11/2026:** scripts integrados em `develop` pelo [PR #70](https://github.com/RobsonSilverioSantos/srm-credit-engine/pull/70), merge `9043732b86a4671bf7ea368e288bf68151205fdb`; backend B16 integrado pelo [PR #71](https://github.com/RobsonSilverioSantos/srm-credit-engine/pull/71). **Execução real de k6 contra o backend ainda pendente**; os testes do substituto local relatados abaixo não a substituem. B17, B15, F06 e F08 permanecem bloqueadas.

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

## Evidência e uso de IA

- **Tarefa QA-B16-k6**, de escopo independente da implementação backend [B16 / #63](https://github.com/RobsonSilverioSantos/srm-credit-engine/issues/63), liberada para cadastro atômico pelo [PR #69](https://github.com/RobsonSilverioSantos/srm-credit-engine/pull/69) e pela [ADR-024](../../../docs/adr/ADR-024-cadastro-atomico-lotes.md).
- O agente QA (ChatGPT) preparou o roteiro, o teste e as verificações contra um substituto local do contrato coordenado com o agente backend. Os artefatos não substituem a revisão, a validação integrada nem o merge pelo responsável técnico. O histórico de [uso de IA](../../../AI_USAGE.md) não foi reescrito.
- Executados localmente `node --input-type=module --check < qa/k6/b16/concorrencia.js` e `node --test qa/k6/b16/contrato.test.mjs`: cinco testes aprovados, incluindo vencedor A/B, persistência indevida após 409, ambiente com referências pré-existentes e rejeição de destino externo. O substituto não abre rede e não comprova transporte, concorrência real, k6 executado ou PostgreSQL.
- Não foi executado k6 contra a aplicação na entrega do PR #70. Após a integração B16 no PR #71, o teste real continua pendente de preparação de ambiente descartável, conferência do contrato integrado e execução com publicação dos resultados verdadeiros. A G02 é documental e não executou k6.
