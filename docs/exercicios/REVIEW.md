# REVIEW.md — Revisão técnica do PR de liquidação

**Componente:** `settlement.controller.ts` — `POST /settlements`  
**Papel do revisor:** Tech Lead responsável pela qualidade, segurança e consistência financeira  
**Decisão:** **REQUEST CHANGES — merge bloqueado**  
**Base da análise:** código TypeScript apresentado no PR. Sem acesso ao esquema do banco, middlewares, logs ou testes executados, controles externos não podem ser presumidos nem descartados.

## Parecer executivo

**Este PR não está pronto para aprovação.** O handler concentra validação HTTP, acesso ao banco, precificação, conversão cambial e mudança de estado; interpola dados recebidos por HTTP em SQL, utiliza taxas em unidade incorreta, não apresenta transação, controle de concorrência ou idempotência, ignora exceções e retorna sucesso mesmo após falha. Também não demonstra logs estruturados, auditoria financeira ou métricas para acompanhar e investigar a operação. Em um fluxo de dinheiro, não se trata de ajustes estéticos: a implementação abre caminhos para fraude, precificação errada e liquidações duplicadas ou inconsistentes.

A expectativa para o autor é corrigir as garantias essenciais e demonstrá-las com testes. A responsabilidade da revisão é avaliar o comportamento do código, não atribuir intenção ou características pessoais ao desenvolvedor.

**Convenção de linguagem:** “organização” é o limite de autorização e isolamento das operações, distinto de “cedente”. Essa exigência depende de o produto operar para mais de uma organização; o trecho isolado não demonstra sua configuração de acesso.

## Achados, do maior para o menor risco

### CR-01 — Interpolação de entrada HTTP em SQL (**Crítica; bloqueia merge**)

- **Evidência:** `WHERE id = ${receivableId}`; `VALUES (${receivableId}, ${finalAmount.toFixed(2)}, '${currency}')`.
- **Impacto:** injeção de SQL e manipulação indevida de consultas ou escritas, conforme os privilégios da conta de banco.
- **Correção exigida:** queries parametrizadas para **todas** as operações, validação de tipo/enum e privilégios mínimos.
- **Teste de aceite:** entradas maliciosas não executam SQL arbitrário e não alteram registros.

### CR-02 — Sem transação entre inserção e alteração de estado (**Crítica; bloqueia merge**)

- **Evidência:** `INSERT INTO settlements` seguido de `UPDATE receivables` em chamadas independentes.
- **Impacto:** pode existir liquidação gravada sem mudança de estado, ou vice-versa em implementações futuras; retentativas agravam a inconsistência.
- **Correção exigida:** transação ACID única, na mesma conexão, envolvendo validações e todas as gravações relacionadas; `ROLLBACK` em qualquer erro.
- **Teste de aceite:** falha forçada após o `INSERT` não deixa registro ou estado parcial.

### CR-03 — Nenhuma garantia de idempotência (**Crítica; bloqueia merge**)

- **Evidência:** o handler não recebe ou consulta chave de idempotência; toda chamada tenta inserir uma liquidação.
- **Impacto:** retry após timeout, clique duplo e redelivery podem produzir liquidações repetidas.
- **Correção exigida:** `Idempotency-Key` com escopo de organização + operação, hash canônico do comando, reserva/finalização transacional e replay da resposta já concluída; `UNIQUE` por recebível como defesa complementar.
- **Teste de aceite:** mesma chave e mesmo pedido retornam a resposta original sem nova gravação; mesma chave com pedido diferente retorna conflito.

### CR-04 — Concorrência sem controle por versão/estado (**Crítica; bloqueia merge**)

- **Evidência:** `UPDATE receivables SET status = 'SETTLED' WHERE id = ...`; não há condição de estado, versão ou verificação de linhas afetadas.
- **Impacto:** duas requisições podem considerar o mesmo recebível elegível e criar registros concorrentes.
- **Correção exigida:** atualização condicional por versão, verificação de `rowCount === 1` e erro de conflito; constraint de unicidade para proteger o banco.
- **Teste de aceite:** duas transações leem a mesma versão; apenas uma confirma e a outra sofre rollback por conflito de versão.

### CR-05 — Erros descartados e sucesso falso (**Crítica; bloqueia merge**)

- **Evidência:** `catch (e) { /* se falhar... segue o jogo */ }` e `res.status(200).json({ ok: true })`.
- **Impacto:** cliente e sustentação podem acreditar que houve liquidação quando a operação falhou. Também não há tratamento claro para erros anteriores ao `try`.
- **Correção exigida:** nunca engolir falhas financeiras; rollback, logging com correlação, resposta apropriada (`201`, `400/422`, `404`, `409`, `500/503`) e retorno de sucesso somente após confirmação.
- **Teste de aceite:** erros injetados em cada etapa jamais produzem `ok: true`.

### CR-06 — Taxas convertidas incorretamente (**Crítica; bloqueia merge**)

- **Evidência:** `BASE_RATE = 1.0`, spread `1.5` ou `2.5`; fórmula soma essas parcelas diretamente a `1`.
- **Impacto:** o cálculo trata 1% como 100%, 1,5% como 150% e 2,5% como 250%. O valor pago pode ficar drasticamente incorreto.
- **Correção exigida:** taxas fracionárias (`0.01`, `0.015`, `0.025`) conforme contrato financeiro, com configuração versionada e política de precificação por tipo.
- **Teste de aceite:** cálculo de duplicata, cheque e deságio confrontado com exemplos independentes de aferição.

### AL-07 — `number` e `toFixed(2)` não garantem precisão decimal bancária (**Alta; bloqueia merge**)

- **Evidência:** `Math.pow`, divisão com números binários e `toFixed(2)` como único arredondamento.
- **Impacto:** diferenças de centavos e impossibilidade de demonstrar `HALF_EVEN` na aritmética financeira.
- **Correção exigida:** biblioteca decimal (como `decimal.js`), valores monetários serializados sem conversão a `number`, política explícita de escala e arredondamento.
- **Teste de aceite:** limites de meio centavo, juros compostos e reconciliação de deságio.

### AL-08 — Conversão cambial antes de fechar o VP em reais (**Alta; bloqueia merge**)

- **Evidência:** `finalAmount = presentValue / rate`, com `toFixed(2)` somente no fim.
- **Impacto:** se a política de preço determina primeiro o VP em BRL e depois a conversão, a ordem atual pode gerar valores diferentes.
- **Correção exigida:** fechar `valorPresenteBrl` em duas casas com `HALF_EVEN`; depois dividir pela cotação BRL/USD e arredondar a moeda de pagamento.
- **Teste de aceite:** cenários de conversão próximos à fronteira do centavo.

### AL-09 — Câmbio sem controles de elegibilidade demonstrados (**Alta**)

- **Evidência:** `fxService.getLatestRate("USD")` sem verificar vigência, direção, valor positivo ou vínculo com a liquidação.
- **Impacto:** cotação obsoleta/inválida e dificuldade de reproduzir a operação no atendimento.
- **Correção exigida:** instante de referência, cotação elegível com validade definida, validação da direção BRL/USD e snapshot imutável da taxa utilizada.
- **Teste de aceite:** cotações ausentes, futuras, expiradas, zero ou negativas.

### AL-10 — Não valida entradas, elegibilidade ou existência (**Alta**)

- **Evidência:** `req.body` sem validação; acesso a `receivable.type` sem verificar `null`; qualquer tipo que não seja `DUPLICATA` vira cheque; qualquer moeda que não seja `USD` passa como BRL.
- **Impacto:** precificação por política errada, registros indevidos e falhas inesperadas.
- **Correção exigida:** contrato validado de entrada, tipos e moedas explícitos, status permitido para liquidar e retorno controlado para IDs inexistentes.
- **Teste de aceite:** IDs, prazos, valores, tipos, moedas e estados inválidos não produzem liquidação.

### AL-11 — Autorização por organização não demonstrada (**Alta; Crítica se houver múltiplas organizações**)

- **Evidência:** leitura e escrita restringem somente `id`; não há verificação de propriedade do recebível no trecho.
- **Impacto:** em aplicação multi-organização, risco de liquidação de registros de outra organização.
- **Correção exigida:** obter identidade da sessão autenticada; validar vínculo do recebível e aplicar escopo em consultas e atualizações; jamais confiar na organização enviada pelo cliente.
- **Teste de aceite:** operador de uma organização não lê nem liquida recebível de outra.

### AL-12 — Responsabilidades concentradas no controller (**Alta; correção exigida antes do merge**)

- **Evidência:** a única função de `POST /settlements` lê `req.body`, consulta o banco, decide spread, calcula valor presente, chama o provedor cambial, insere a liquidação, atualiza o recebível, trata exceções e monta a resposta HTTP.
- **Impacto:** alto acoplamento entre transporte, regras financeiras e persistência; dificulta testar os cálculos sem servidor/banco, identificar a fronteira transacional, substituir serviços externos e revisar alterações sem regressões. Também favorece caminhos de falha tratados de forma desigual.
- **Correção exigida:** aplicar separação de responsabilidades sem criar camadas vazias: **controller** valida o contrato e traduz HTTP; **caso de uso de liquidação** coordena o processo e suas invariantes; **serviço de precificação** calcula somente valores e aplica políticas financeiras; **serviço/porta de câmbio** fornece cotação elegível; **repositórios/adaptadores** executam SQL parametrizado; **gerenciador transacional/idempotência** garante reserva, locking, persistência e replay de forma atômica. A implementação pode usar funções ou classes pequenas, conforme coesão e necessidade de estado/injeção de dependências.
- **Teste de aceite:** a precificação tem testes unitários sem Express/PostgreSQL; o controller tem testes HTTP com caso de uso simulado; a liquidação possui testes de integração para rollback, conflito de versão, idempotência e persistência. Um defeito no provedor cambial não deve forçar mudança na lógica HTTP.

### AL-13 — Logs estruturados e correlação de requisições não demonstrados (**Alta**)

- **Evidência:** o handler não registra início, conclusão, conflito ou falha da operação; o `catch (e)` silencia exceções. Não aparecem `correlationId`, duração, evento ou status de processamento. Middlewares externos podem existir, mas não foram apresentados.
- **Impacto:** a sustentação não consegue associar uma tentativa HTTP à alteração financeira; diagnósticos de timeout, replay, falha cambial e duplicidade passam a depender de investigação manual demorada.
- **Correção exigida:** logging **estruturado** com carimbo UTC, `correlationId`/`traceId`, evento, operação, identificadores autorizados de organização/recebível/liquidação, resultado e duração. Registrar exceções com causa e contexto no tratamento centralizado; registrar sucesso **após o commit**, e não ao começar a gravar. Redigir ou excluir tokens, chaves idempotentes, documentos, dados pessoais e payloads financeiros sensíveis.
- **Teste de aceite:** uma requisição bem-sucedida, uma rejeição por concorrência e uma falha de banco produzem logs correlacionáveis ao resultado real; nenhuma falha aparece como liquidação concluída nem expõe segredos.

### AL-14 — Trilha de auditoria financeira não demonstrada (**Alta; bloqueia merge**)

- **Evidência:** o código altera `receivables` e insere em `settlements` sem gravar explicitamente evento auditável, identidade de quem iniciou a operação, momento de referência ou mudanças de estado. O trecho não demonstra auditoria por trigger ou outra camada.
- **Impacto:** dificuldade de reconstituir quem autorizou e quando uma liquidação foi confirmada, quais valores/cotação foram aplicados e quais estados mudaram; compromete conciliação, investigações e prestação de contas.
- **Correção exigida:** persistir, **na mesma transação da liquidação**, registro auditável da confirmação com `organizationId`, `receivableId`, `settlementId`, operador autenticado, status anterior/novo, instante UTC, identificadores de regra/taxa/cotação e correlação. Impedir alteração indevida da trilha, proteger acesso e definir retenção. Tentativas rejeitadas, cujas transações sofrem rollback, devem gerar evidência operacional independente — sem registrar falsamente uma liquidação confirmada.
- **Teste de aceite:** sucesso deixa exatamente uma liquidação e seu evento de auditoria; rollback não deixa auditoria de *confirmação*; replay idempotente não gera segunda confirmação; integridade e autorização da trilha são verificadas.

### ME-15 — Métricas operacionais e alertas não demonstrados (**Média; requisito operacional de entrada em produção**)

- **Evidência:** nenhuma instrumentação de contadores, duração ou estado da liquidação aparece no handler apresentado. Não há referência a dashboards/alertas externos.
- **Impacto:** picos de rejeição, conflito, falha cambial, latência e divergência entre liquidações e ordens financeiras podem passar despercebidos até reclamação da mesa de operações.
- **Correção exigida:** métricas de negócio com resultado bem definido, por exemplo `liquidacoes_confirmadas_total`, `liquidacoes_rejeitadas_total{motivo}`, `liquidacoes_conflitos_total`, `liquidacoes_replays_total` e `liquidacoes_duracao_segundos` (histograma); alertas para anomalias e reconciliação. Incrementar confirmações **somente depois do commit**; replay conta como replay, não como nova liquidação. Evitar IDs de operação, recebível, usuário ou organização como *labels*, para não gerar cardinalidade excessiva.
- **Teste de aceite:** simular sucesso, replay, conflito e rollback e conferir os contadores; nenhum rollback ou replay aumenta `liquidacoes_confirmadas_total`.

## Sugestões concretas ao autor

**1. SQL parametrizado e escopo da organização (ilustrativo):**

```typescript
const result = await client.query(
  `SELECT id, type, face_value, term, status, version
     FROM receivables
    WHERE id = $1 AND organization_id = $2`,
  [receivableId, organizationIdFromAuth]
);
```

**2. Versão otimista dentro de transação (ilustrativo):**

```typescript
const updated = await client.query(
  `UPDATE receivables
      SET status = 'SETTLED', version = version + 1
    WHERE id = $1 AND organization_id = $2
      AND version = $3 AND status = 'AVAILABLE'
    RETURNING id`,
  [receivableId, organizationIdFromAuth, versionRead]
);

if (updated.rowCount !== 1) throw new ConflitoLiquidacao();
```

Ambos os trechos pressupõem campos e tipos a confirmar no esquema real. **A implementação completa também precisa de reserva idempotente e `INSERT` na mesma transação**, além de captura e tratamento do conflito.

**3. Exemplo de divisão de responsabilidades (TypeScript ilustrativo):**

| Função/componente | Responsabilidade única | O que não deve fazer |
|---|---|---|
| `validarComandoLiquidacao` | Interpretar e validar dados de entrada, moeda e chave idempotente | Executar SQL ou calcular preços |
| `liquidar` (caso de uso) | Coordenar pré-condições, idempotência e confirmação da liquidação | Conhecer `Request`/`Response` do Express |
| `calcularValorPresente` | Aplicar taxa, spread, prazo e arredondamento decimal | Chamar HTTP ou gravar banco |
| `obterCotacaoElegivel` | Selecionar cotação pela moeda e instante, validando vigência | Persistir liquidação |
| `confirmarLiquidacaoAtomica` | Atualizar versão, inserir liquidação, auditar e finalizar idempotência na mesma transação | Buscar cotação em rede ou construir resposta HTTP |
| `mapearErroHttp` | Traduzir erros conhecidos para códigos HTTP corretos | Suprimir falhas ou decidir regra financeira |

```typescript
import type { Request, Response, NextFunction } from "express";

// Exemplo de fronteira HTTP: funções de aplicação e persistência
// têm implementações próprias; não são executadas pelo controller.
interface ComandoLiquidacao {
  receivableId: string;
  currency: "BRL" | "USD";
  idempotencyKey: string;
  organizationId: string; // derivado da autenticação
}

interface ResultadoLiquidacao {
  status: 201;
  body: { settlementId: string; amount: string; currency: "BRL" | "USD" };
}

interface LiquidarRecebivel {
  executar(comando: ComandoLiquidacao): Promise<ResultadoLiquidacao>;
}

declare function validarComandoLiquidacao(req: Request): ComandoLiquidacao;

function criarSettlementController(casoDeUso: LiquidarRecebivel) {
  return async (req: Request, res: Response, next: NextFunction) => {
    try {
      const comando = validarComandoLiquidacao(req); // valida, não persiste
      const resultado = await casoDeUso.executar(comando);
      return res.status(resultado.status).json(resultado.body);
    } catch (erro) {
      return next(erro); // middleware central traduz e registra
    }
  };
}
```

`validarComandoLiquidacao` deve ser implementada com esquema de validação e identidade autenticada confiável. `criarSettlementController` é propositalmente uma **fronteira exemplificativa**, não uma implementação completa: o caso de uso ainda precisa coordenar idempotência, precificação, cotação elegível e a transação; o middleware deve tratar erros tipados. Não abrir transação para aguardar HTTP externo. A separação deve melhorar coesão e testes, não apenas transferir o mesmo bloco de código para uma função com outro nome.

**4. Exemplos de instrumentação e auditoria (TypeScript ilustrativo):**

**Logs estruturados:** incluir contexto técnico em cada evento, sem registrar segredos ou payloads completos. Não utilizar o log como substituto de auditoria de banco.

```typescript
logger.info({
  event: "settlement.confirmed",
  correlationId,
  organizationId,
  receivableId,
  settlementId,
  durationMs
}, "Liquidação confirmada após commit");
```

**Auditoria durável:** dentro da mesma transação que grava a liquidação, sem chamadas externas. Exemplo pressupõe tabela de auditoria com colunas compatíveis:

```typescript
await client.query(
  `INSERT INTO settlement_audit
   (organization_id, receivable_id, settlement_id,
    operator_id, previous_status, new_status, occurred_at)
   VALUES ($1, $2, $3, $4, $5, $6, $7)`,
  [organizationId, receivableId, settlementId,
   authenticatedOperatorId, "AVAILABLE", "SETTLED", referenceInstant]
);
// O COMMIT ocorre somente depois desta escrita e da finalização idempotente.
```

**Métricas:** contabilizar confirmação somente após `COMMIT`. Replay e conflitos têm contadores distintos, sem IDs como labels:

```typescript
if (resultado.tipo === "CONFIRMADA_APOS_COMMIT") {
  metrics.counter("liquidacoes_confirmadas_total").inc();
} else if (resultado.tipo === "REPLAY") {
  metrics.counter("liquidacoes_replays_total").inc();
}
// Métricas são observacionais; não substituem ledger nem auditoria.
```

Os trechos acima exemplificam pontos de instrumentação e **não constituem um endpoint completo ou código testado**. A implementação deve preservar a atomicidade, classificar resultados corretamente e escolher uma estratégia adequada para perda eventual de telemetria pós-commit.

## Critérios para nova revisão

| Evidência exigida | Condição de aprovação |
|---|---|
| Queries parametrizadas e autorização | Nenhuma consulta/escrita baseada em SQL interpolado; isolamento comprovado quando aplicável |
| Testes de liquidação com PostgreSQL | Commit atômico e rollback diante de falha |
| Concorrência sincronizada | Uma confirmação, uma falha por versão, nenhum registro parcial |
| Idempotência | Retry igual não duplica; payload diferente conflita |
| Precificação | Taxas na unidade correta, decimal e `HALF_EVEN`, deságio reconciliado |
| Câmbio | Cotação elegível e snapshot financeiro reproduzível |
| Erros HTTP | Nenhuma falha retorna sucesso |
| Logs estruturados | Eventos correlacionados com resultado real, falhas registradas sem dados sensíveis |
| Auditoria transacional | Confirmação auditada no mesmo commit; rollback sem evento falso; replay sem duplicação |
| Métricas e alertas | Sucesso, conflito, rejeição, replay e latência; sem dupla contagem ou labels de alta cardinalidade |
| Responsabilidades separadas | Regras financeiras testáveis sem Express/banco; controller fino; transação concentrada no caso de uso/adaptador |

## Decisão do revisor

**REQUEST CHANGES.** Não aprovo o merge nem recomendo liberar esse endpoint até que os achados críticos e os altos bloqueantes — inclusive a trilha de auditoria financeira — sejam corrigidos e demonstrados em testes. O controller deve concentrar validação e resposta HTTP; precificação, câmbio, persistência e liquidação transacional devem ter funções e contratos próprios, sem introduzir camadas vazias. A próxima revisão deve trazer um diff enxuto, evidência de concorrência/rollback e uma explicação de por que a operação não pode ser confirmada duas vezes.

*Limite desta revisão:* parecer baseado no código submetido. Não houve execução de testes nem acesso à infraestrutura da aplicação.
