# F09 — Modelos e interface de importação por CNPJ do cedente

**Situação:** B18 integrada em `develop` pelo [PR #86](https://github.com/RobsonSilverioSantos/srm-credit-engine/pull/86) em 22/11/2026. **F09 autorizada para iniciar somente após merge humano deste PR documental de liberação**, quando a linha F09 de `origin/develop:TASKS.md` contiver `Liberada para iniciar`. Implementação frontend ainda não iniciada.
**Issue:** [#83](https://github.com/RobsonSilverioSantos/srm-credit-engine/issues/83).
**Dependências:** F08 integrada pelo PR #81 e B18 integrada pelo PR #86.
**Executor:** `frontend`; revisores `architect`, `qa`, `reviewer`.

## Objetivo
A operação `Operações / Importação em lote` não deve exigir que operadores obtenham UUID interno de cedente quando possuírem seu CNPJ brasileiro. Com B18 integrada, a UI poderá oferecer modelo com `cedenteDocumento` e manter o modelo original `cedenteId` para compatibilidade.

## Contrato backend B18 integrado (PR #86)

A B18 permite **dois cabeçalhos completos e exatos** no upload de pré-validação CSV/XLSX: `cedenteId` (UUID legado) ou `cedenteDocumento` (CNPJ brasileiro). O backend não aceita mistura dos dois nem substituição de `cedenteId` por CNPJ no JSON de confirmação: a **prévia retorna o comando canônico já resolvido para UUID**, que é reenviado ao cadastrar. A busca por documento usa validação B14 e restringe-se a cedente ativo do tenant autenticado, sem novas migrations. O frontend não deve resolver CNPJ diretamente nem criar endpoint de lookup por documento.

## Contratos e comportamento pretendidos
- Consumir os mesmos endpoints B16 de pré-validação `POST /bff/v1/lotes/recebiveis/validacoes` e confirmação `POST /bff/v1/lotes/recebiveis`. Backend devolve o comando canônico resolvido para UUID, revalidado no cadastro.
- Oferecer modelos CSV UTF-8 (`;`) e XLSX padronizado por CNPJ e preservar os modelos UUID originais. Cabeçalho novo: `referenciaExterna;cedenteDocumento;modalidade;valorFaceBrl;prazoMeses;vencimento;moedaPagamento`.
- Exibir que `referenciaExterna` identifica **o recebível de origem**, não a organização; `cedenteDocumento` é o CNPJ brasileiro do cedente, validado pelo backend; organização deriva da sessão.
- Mostrar mensagem por linha para CNPJ inválido, não encontrado/inativo ou legado ambíguo sem revelar dados de terceiros, preservando outras linhas e rejeição **integral** do cadastro.
- Manter dois modais independentes B16/B17, o gate de 100% de simulações aptas, idempotência, proteção contra duplo clique, UX de resultado incerto e política financeira A da ADR-025.
- Sem mudança na chave de deduplicação `(organizacao_id, referencia_externa)`, conversão de CNPJ em UUID no frontend ou regra de câmbio B15/F06.

## Testes obrigatórios
- Modelo CSV/XLSX por CNPJ numérico/alfanumérico, preservação literal de strings e zeros à esquerda, compatibilidade por UUID.
- Pré-validação com erro por linha e 200 linhas mantidas; CNPJ não pode ser exposto em URL/logs/storage; processamento de B com referências já liquidadas de A.
- 401, 403, 409, 422, 503, erro de rede e retry idempotente; confirmações independentes; acessibilidade, teclado, mobile, sessão, lint/test/build e revisão independente.

## Gate
A integração B18 foi comprovada no PR #86. **A F09 somente pode começar após o merge humano do presente PR documental em `develop` e leitura da expressão literal `Liberada para iniciar` na própria linha F09 de `origin/develop:TASKS.md`.** Usar branch/worktree exclusivas e revisões `architect`, `qa` e `reviewer`, abrir PR apenas contra `develop` e nunca executar merge pelo agente. F09 não altera B15, F06, contratos financeiros, política de câmbio ou regras de deduplicação. 

**Evidências:** testes B18 PostgreSQL reais (137 testes em 24 suítes) foram relatados no PR #86; a interface F09 e testes reais navegador/BFF/PostgreSQL ainda não foram executados.
