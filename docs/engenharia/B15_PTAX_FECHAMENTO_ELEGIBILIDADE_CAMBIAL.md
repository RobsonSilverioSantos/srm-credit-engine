# B15 — PTAX de fechamento e política de elegibilidade cambial

**Estado:** planejamento P1; implementação não iniciada.  
**Tarefa:** `TASKS.md`, B15; [issue #52](https://github.com/RobsonSilverioSantos/srm-credit-engine/issues/52).  
**Executor:** agente `backend`, em um único worktree exclusivo. **Revisões:** `architect`, `qa`, `reviewer`.  
**Gate:** B15 e o documento devem estar integrados em `origin/develop`; **B14 deve estar integrada** antes de iniciar B15 para evitar colisões na numeração Flyway e conflitos de integração. A tarefa não fica automaticamente liberada com o merge deste documento: a coluna `Situação` precisa conter exatamente `Liberada para iniciar` após cumprimento das dependências.

## 1. Fundamentação e decisão

O enunciado original requer armazenar câmbio com data/hora de vigência, usando endpoint de atualização manual **ou** integração mockada, e explicita a ambiguidade sobre qual taxa aplicar no instante de liquidação. `SPEC.md` e ADR-011 documentam a hipótese atual de uma cotação mockada com duração configurável de **30 minutos**, não uma regra oficial do dólar.

**Decisão de produto aprovada:** para consulta de referência USD/BRL, adotar a **PTAX de Fechamento** publicada pelo Banco Central do Brasil (BCB). A PTAX é uma taxa de **referência**, não representa uma oferta executável de câmbio. O fechamento é uma publicação por dia com boletim, não uma cotação intradiária que deva vencer automaticamente após 30 minutos.

**Ponto ainda sujeito à revisão humana:** o BCB publica **compra e venda**. Para converter valor presente em BRL em valor de pagamento em USD, propõe-se `cotacaoVenda` (perspectiva de aquisição de USD com BRL), com o valor expresso em **BRL por 1 USD**. A confirmação dessa escolha, e eventual aplicação de spread cambial contratual, deve ser registrada por decisão humana/ADR **antes de implementar como regra de negócio**. Não inferir compra/venda silenciosamente. O resultado deverá usar exatamente o lado aprovado e indicá-lo no snapshot.

**Premissas que NÃO são regras oficiais BCB:** janela máxima de defasagem do fechamento, seleção de último dia disponível em fins de semana/feriados, horário de corte e eventuais políticas de fallback. Essas escolhas serão **configuráveis, explícitas e auditáveis**; não declarar que o BCB exige, por exemplo, 30 minutos ou uma janela fixa de N dias.

## 2. Contratos e fronteiras hexagonais

- `:dominio`: conceitos de fonte (`BCB_PTAX`, `MANUAL`, `DEMO`), tipo de boletim (`FECHAMENTO`), lado (`COMPRA`/`VENDA`), taxa positiva `BigDecimal`, data efetiva, referência temporal e política de elegibilidade. Um tipo top-level por arquivo Kotlin.
- `:aplicacao`: porta de saída de consulta PTAX, porta de histórico versionado, caso de uso de sincronização controlada e caso de uso de seleção. Referenciar interfaces; não importar `RestClient`/JDBC no núcleo.
- `:app`: adaptador HTTP BCB/PTAX, política de timeout/retry, agendamento/controlador de atualização autorizado, persistência JDBC e wiring Spring. **Nenhum HTTP externo durante a transação financeira**, inclusive na liquidação. Não usar `RestTemplate` nem novas dependências sem justificativa.
- Preservar o fluxo já validado de snapshot imutável, idempotência, ACID, isolamento organizacional, `BigDecimal` e arredondamento `HALF_EVEN` da quantia BRL antes da conversão para USD; não enviar `Double` nem `Float` pelos adaptadores financeiros.

## 3. Fonte oficial e interpretação de boletins

Fonte oficial: [Dólar comercial (venda e compra) — cotações diárias](https://dadosabertos.bcb.gov.br/dataset/dolar-americano-usd-todos-os-boletins-diarios), com API OData [PTAX v1](https://olinda.bcb.gov.br/olinda/servico/PTAX/versao/v1/documentacao).

- Consumir a função documentada de **cotação do dólar por data ou período** no serviço PTAX. Confirmar no catálogo a sintaxe e a codificação OData, os limites de paginação e o formato de data antes de escrever o adaptador; não usar scraping do site.
- Os campos oficiais incluem `cotacaoCompra`, `cotacaoVenda`, `dataHoraCotacao` e `tipoBoletim`. **Selecionar apenas `tipoBoletim == "Fechamento"`**, rejeitando `Abertura`, `Intermediário` e `Fechamento Interbancário`. Não selecionar o último objeto de forma arbitrária.
- Confirmar a semântica do timezone retornado pela API antes de converter para `Instant`; preservar a data de referência do BCB e a string/data/hora original quando necessário. Distinguir instante de publicação, dia do boletim e instante de coleta pelo sistema.
- Validar `BRL/USD`, taxas `> 0`, escala persistível, campos obrigatórios, consistência compra/venda e formato de retorno. Tratar `[]` como ausência de boletim, não como cotação `0`.

## 4. Elegibilidade temporal e calendário

A taxa PTAX de fechamento é **diária**, não sofre automaticamente a janela global `PT30M`.

- Selecionar **o boletim de fechamento mais recente já conhecido e efetivamente publicado no instante de corte da operação**, com fonte e lado definidos, sem usar fechamento futuro nem registro criado após o instante de referência.
- Em sábado, domingo, feriado e dia sem publicação, procurar o **último fechamento elegível**, dentro de uma **defasagem máxima parametrizada**. Não escolher retroativamente taxa futura nem considerar dia útil sem dados como se o boletim existisse.
- Antes de codificar: especificar e obter aprovação para o parâmetro de defasagem máxima, o tratamento dos feriados/suspensões BCB, limite de pesquisa retroativa e como calcular o instante de disponibilidade do boletim. Não considerar o último fechamento de sexta como válido indefinidamente.
- Diferenciar `BCB_PTAX` de `DEMO` e `MANUAL`. Cotações intradiárias podem ter TTL próprio. **Desativar a regra uniforme de 30 minutos apenas para PTAX de fechamento**, mantendo validações para fontes apropriadas. Nunca fazer fallback silencioso da fonte PTAX para `5,4321` ou outra cotação fictícia.
- Se um fechamento elegível não existir: erro explícito **503/indisponibilidade de dados financeiros**, com mensagem informativa para o frontend. Sem liquidação ou snapshot se faltarem dados.

## 5. Histórico PostgreSQL e atualização

- Criar **nova migration Flyway**, escolhendo o próximo número após B14 em `develop`; jamais editar uma migration aplicada. O schema atual contém `cotacoes_cambiais` com taxa, `vigente_em`, `criado_em` e validade controlada por configuração global.
- Preservar históricos existentes e adicionar metadados/proveniência: moeda base/cotada, fonte, boletim, lado, dia BCB, instante de divulgação (quando verificável), instante de coleta, vigência e identidade da publicação. Não reclassificar como PTAX os registros `DEMO` ou históricos de origem não comprovada.
- Estabelecer unicidade/idempotência por **fonte + data do boletim + tipo + lado + moedas**, na granularidade correta; registros idênticos reimportados não geram nova versão. Se o mesmo boletim for revisado pela fonte, registrar **nova versão auditável** com decisão de precedência, nunca sobrescrever silenciosamente a versão antiga.
- Realizar consulta HTTP antes da transação de gravação curta e validar o conteúdo antes de persistir. Em instâncias concorrentes, evitar registros duplicados via constraint e transação PostgreSQL; não aplicar lock global para toda simulação.
- Oferecer sincronização controlada em intervalos configuráveis **fora** da liquidação e mecanismo administrativo restrito (CLI/endpoint RBAC conforme design). Não chamar o Bacen a cada atualização de campo do React nem iniciar consulta externa dentro de transação.
- Timeouts, retries limitados com backoff, limite de concorrência, diagnóstico e monitoramento; falhas transitórias não repetem inserts financeiros, e falhas permanentes não são mascaradas.
- Não incluir segredo, JWT, documento fiscal ou payload de cliente em logs de integração.

## 6. Modos de execução e aferição

- **DEMO/C3:** manter a opção explícita de usar exatamente **5,4321 BRL/USD** em testes/fonte mock, para reproduzir **C3 = US$ 17.094,67** sem depender de cotações de mercado. A fonte utilizada deve ser distinguível de PTAX e a política de elegibilidade deve estar documentada.
- **PTAX:** usar taxa de fechamento variável publicada pelo BCB; valores USD **não** precisam coincidir com C3 quando a taxa real é diferente. C1 (**R$ 92.859,94**) e C2 (**R$ 23.337,77**) em BRL permanecem invariantes.
- A integração com PTAX não movimenta dinheiro real e não altera a fórmula de precificação, dados de face, taxas base ou spreads.

## 7. Testes obrigatórios e critérios de aceite

- [ ] Seleção somente do boletim `Fechamento`, em fixtures com outros tipos; compra/venda explicitamente decididos por ADR, e conversão usa `BigDecimal`.
- [ ] Validação do parser OData: campo ausente, objeto inválido, arrays vazios, erro HTTP, data/timezone, escala, `0` e valores negativos.
- [ ] Testes determinísticos com HTTP stub (WireMock/MockWebServer ou servidor de testes sem dependência extra, conforme stack) e amostras oficiais conferidas; **CI não depende de rede Bacen ao vivo**.
- [ ] Teste de seleção antes e depois do fechamento, dias úteis sem publicação, sábado/domingo, feriados, fuso BCB/UTC, limite de defasagem e fechamento publicado tardiamente.
- [ ] Testar indisponibilidade da API, timeout, retry limitado, backoff, falha permanente, cotações repetidas/revisadas, concorrência e unicidade.
- [ ] Testes PostgreSQL reais: persistência de proveniência, integridade da migration após B14, imutabilidade, idempotência, corte temporal, snapshot de liquidação e replay.
- [ ] Nenhum HTTP externo dentro de transação de liquidação; teste de transação/timeout com indisponibilidade; falha não produz liquidação parcial.
- [ ] Verificar C1/C2/C3 exatos com fonte DEMO, sem rede. Adicionar cenário PTAX com taxa de fixture diferente, comprovando resultado variável e snapshot correto.
- [ ] Erros informativos na API/BFF, sem expor atributos internos; preservar contratos existentes ou documentar evolução compatível com testes.
- [ ] `cd backend && ./gradlew clean check`, `bash scripts/test-cadastro-postgres.sh`, `verificarFronteiraHexagonal` e CI backend/frontend/Compose aprovadas; registrar comandos e resultados reais.
- [ ] Atualizar documentação factual e **propor** revisão formal de ADR-011/`SPEC.md` compatível com PTAX de fechamento e política de defasagem, sem alterar decisões normativas sem aprovação humana.
- [ ] Um único PR de implementação B15 para `develop`, com revisão `architect`, `qa`, `reviewer`, sem merge por agente.

## 8. Decisões pendentes para revisão técnica

1. **Compra versus venda:** proposta `cotacaoVenda` para BRL→USD, requer confirmação humana.
2. **Defasagem máxima:** duração ou dias úteis permitidos para último fechamento e regras para feriados/falhas prolongadas, precisam ser aprovados e parametrizados.
3. **Disponibilidade/publicação:** como considerar a data/hora exata de divulgação e eventual revisão de boletim; verificar semântica oficial e definir proteção contra look-ahead.
4. **Atualização:** agendamento e/ou acionamento sob autorização; critério de sincronização inicial sem sobrecarregar o BCB.
5. **Manutenção de legado:** como etiquetar cotações históricas sem proveniência; não falsificar que vieram do BCB.

É permitido iniciar investigação/planejamento de desenho sobre a B15 depois da liberação, mas **não concluir implementação sem essas decisões**.

## Referências oficiais

- [BCB — Dólar comercial, todos os boletins diários](https://dadosabertos.bcb.gov.br/dataset/dolar-americano-usd-todos-os-boletins-diarios).
- [BCB — API PTAX v1, documentação OData](https://olinda.bcb.gov.br/olinda/servico/PTAX/versao/v1/documentacao).
- [Resolução BCB 45/2020 — metodologia da PTAX](https://www.bcb.gov.br/estabilidadefinanceira/exibenormativo?numero=45&tipo=Resolu%C3%A7%C3%A3o+BCB).

**Governança:** todas as alterações de código, migrations e ADRs pertencem ao PR de implementação B15, não a este registro. O agente respeita `SPEC.md`, `DECISIONS.md`, arquitetura hexagonal e um tipo Kotlin top-level por arquivo. Apenas o responsável humano pode realizar merge.
