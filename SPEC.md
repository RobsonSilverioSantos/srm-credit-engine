# SPEC.md — SRM Credit Engine (MVP)

**Versão:** 3.4 — 22/11/2026. **Estado:** especificação funcional vigente da implementação demonstrativa; capacidades e evidências de execução devem ser verificadas no código e nos PRs integrados, sem representar homologação pelo negócio ou por órgão fiscal. **Referências:** enunciado em `docs/requisitos/`, decisões em `DECISIONS.md`, ambiguidades em `docs/negocio/MATRIZ_AMBIGUIDADES.md`. O planejamento anterior foi arquivado em `docs/historico/planejamento-anterior/` e não substitui este contrato.

## 1. Escopo e papéis

O MVP permite **simular**, cadastrar e liquidar logicamente recebíveis de **cedentes** em BRL, com pagamento registrado em BRL ou USD, e consultar extrato analítico. Não movimenta dinheiro real nem implementa estorno, liquidação parcial ou cobrança. **Organização** é o limite de autorização/isolamento (`organizacao_id`); **Cedente** é quem cede os recebíveis e pertence a uma organização. A UI recebe identidade/autorização do servidor, nunca de `organizacaoId` livre no body.

### Identificação de cedentes e limite de validação documental (B14)

O cadastro possui **país ISO 3166-1 alfa-2**, **tipo de documento** e **número/documento canônico**. Apenas o **CNPJ brasileiro (`BR/CNPJ`)** possui validação implementada de formato e **ambos os dígitos verificadores**, incluindo CNPJ numérico e alfanumérico. `statusDocumento = VALIDADO_DV` indica somente conformidade estrutural; **não comprova existência, situação ativa, titularidade ou regularidade fiscal** e não resulta de consulta à Receita Federal.

Para **país estrangeiro**, o tipo `OUTRO` permite identificador de 1–32 caracteres `[A-Z0-9]`, registrado explicitamente como `NAO_VERIFICADO`, sem validação fiscal internacional. `BR/OUTRO` e CNPJ associado a outro país não são aceitos. País do cedente não determina organização, moeda de pagamento ou política cambial.

Novos `POST /cedentes` com apenas `nome` e `documento` são interpretados como `BR/CNPJ` (restrição deliberada de entradas antes arbitrárias); país e tipo explícitos devem ser fornecidos juntos quando o documento não for CNPJ brasileiro. Registros **anteriores à migration V5** sem país/tipo permanecem `DESCONHECIDO`, sem classificação automática por aparência. A unicidade e a resolução de possíveis colisões são por organização e identidade canônica, com reconciliação manual de legados ambíguos. Política completa: [ADR-023](docs/adr/ADR-023-identificacao-empresarial-por-pais-e-documento.md) e [documentação da B14](backend/docs/B14_DOCUMENTOS_CEDENTES.md).

## 2. Premissas financeiras e ambiguidades

| Tema | Premissa demonstrativa adotada | Ponto a validar com o negócio |
|---|---|---|
| Prazo | `prazoMeses` inteiro positivo, informado pelo operador; vencimento opcional/informativo, sem conversão implícita de dias | calendário, dias úteis/corridos e convenção de prazo |
| Base e spread | Base inicial **1,00% a.m.** versionada; duplicata **1,50% a.m.**; cheque pré-datado **2,50% a.m.** | política por produto/cedente, vigência e riscos |
| Cálculo | `VP = valorFace / (1 + taxaBase + spread)^prazoMeses`; juros compostos mensais | fórmula contratual e mudanças futuras de Strategy |
| Precisão | Kotlin `BigDecimal`, PostgreSQL `NUMERIC`, **HALF_EVEN**, VP BRL arredondado a **2 casas** ao final do cálculo | escala interna e governança de arredondamento |
| Câmbio | Cotação em **BRL por 1 USD**; converter **VP BRL já arredondado** por divisão, arredondar USD a 2 casas | PTAX, intradiária ou negociada; fixação por contrato |
| Tempo | Taxa/cotação vigentes em um único instante UTC da liquidação; cotação mockada com idade máxima configurável, hipótese **30 min** | janela, timezone de referência e tratamento de taxas indisponíveis |
| Elegibilidade | Recebível da organização autenticada, no estado `DISPONIVEL`; transição única para `LIQUIDADO` | documentação de cessão, alçadas e impedimentos adicionais |

**Aferições obrigatórias e independentes das demais premissas:** C1 duplicata 100.000 BRL, 3 meses → **VP 92.859,94 BRL**, deságio **7.140,06**; C2 cheque 25.000 BRL, 2 meses → **VP 23.337,77 BRL**; C3 C1 com cotação **5,4321 BRL/USD** → **17.094,67 USD**. O deságio é sempre calculado em BRL; não antecipar arredondamento intermediário. Montantes/taxas nos DTOs como **strings decimais** com moeda explícita.

**Perguntas antes de produção:** Qual fonte, direção e janela oficial do câmbio? O prazo começa em qual evento e como tratar feriados? Há spreads por risco/cedente? Quando um recebível se torna elegível juridicamente? Qual a alçada de aprovação por organização? Como se processam cancelamentos, ajustes e reconciliação bancária? **Estas respostas não foram homologadas**; o MVP não presume uma regra oficial.

## 3. Fluxos e garantias

1. **Pré-simulação:** painel React/TypeScript + MUI envia dados válidos ao BFF; backend calcula prévia **sem criar recebível, liquidação ou simulação persistente** e sem chave de idempotência. Prévia é indicativa, não reserva preço/câmbio. Cadastro vem **depois** da prévia.
2. **Cadastro:** organização sempre vem de principal autenticado (Spring Security/BCrypt/token local assinado); vincular cedente e recebível à mesma organização. Não usar Keycloak no MVP. `prazoMeses` é obrigatório; vencimento não determina prazo automaticamente.
3. **Liquidação:** `Idempotency-Key` obrigatória por escrita crítica, escopo `(organizacaoId, tipoOperacao, chave)` com hash do comando canônico; replay idêntico devolve resposta original e payload divergente recebe **409**. Atualização otimista `DISPONIVEL → LIQUIDADO` implementada via **SQL condicional JDBC por organização, versão e estado** (`UPDATE ... WHERE organizacao_id = ? AND id = ? AND versao = ? AND status = 'DISPONIVEL'`), e não pela anotação JPA `@Version`. Uma atualização de zero linhas indica conflito; a proteção combina unicidade por recebível, transação PostgreSQL ACID, rollback e resposta **409** da requisição concorrente perdedora. Sem lock pessimista explícito nem HTTP externo dentro da transação. Ver [ADR-010](docs/adr/ADR-010-controle-otimista-sql-versionado.md).
4. **Câmbio:** manter histórico `vigente_em` e `criado_em`, taxa base versionada; selecionar elegibilidade no mesmo instante UTC, rejeitar cotação futura, vencida, ausente ou não positiva; registrar snapshot imutável de valor, spread, taxa base, câmbio e timestamps. Alterações posteriores não reprecificam liquidações.
5. **Extrato:** filtrar período, cedente e moeda por SQL parametrizado com escopo de organização, ordenação estável e paginação **server-side**. API `/api/v1` e BFF `/bff/v1` reutilizam casos de uso no mesmo processo; sem chamadas HTTP entre módulos.

## 4. Aceite verificável e limites

| Categoria | Critério de aceite proposto |
|---|---|
| **Corretude** | C1–C3 automatizados com valores em centavos; rejeição de prazo/valor/moeda inválidos; ausência de cálculo financeiro no navegador |
| **Integridade** | Concorrência real em PostgreSQL: dois comandos distintos sobre o mesmo recebível, **um confirma**; o outro recebe conflito 409 e rollback; mesma chave/payload retorna replay; nenhum estado financeiro parcial e nenhuma duplicação de liquidação |
| **Segurança** | Autenticação verificada, BCrypt, expiração e assinatura do token, autorização de organização cruzada negada; segredos fora do Git; logs sem credenciais e dados pessoais |
| **Usabilidade** | Formulário acessível e responsivo com mensagens por campo; prévia reativa com debounce e descarte de respostas obsoletas; extrato com carregando/vazio/erro; valores e moedas legíveis |
| **Desempenho** | **Meta demonstrativa, não medida:** executar teste local documentado de simulação a 10 req/s por 60 s, sem falhas, visando p95 até 500 ms; publicar ambiente/metodologia/resultado verdadeiro, ou registrar não executado |
| **Operação** | Logs estruturados, auditoria consistente (confirmada na transação), ao menos métricas de liquidações confirmadas e latência; CI com testes/lint reais quando implementada |

**Cortes conscientes:** Keycloak, SSO/MFA, infraestrutura cloud e transferências bancárias reais. Esses cortes substituem escolhas do planejamento histórico, sem alterar o significado de liquidação lógica nem afirmar que fluxos foram homologados para produção. **Arquitetura de alta escala para 1 milhão/min, code review TypeScript e post-mortem** são exercícios **independentes**, fora do `TASKS.md`; a proposta de alta escala ainda está pendente (`docs/exercicios/ALTA_ESCALA_STATUS.md`). O C4 do MVP, os ADRs e a EDA evolutiva são documentação arquitetural, não funcionalidade distribuída implementada.
