# F09 — Modelos e interface de importação por CNPJ do cedente

**Situação:** planejada; **bloqueada** até B18 integrada em `develop` e nova liberação documental literal.
**Issue:** [#83](https://github.com/RobsonSilverioSantos/srm-credit-engine/issues/83).
**Dependências:** F08 integrada pelo PR #81 e B18 ainda pendente.
**Executor:** `frontend`; revisores `architect`, `qa`, `reviewer`.

## Objetivo
A operação `Operações / Importação em lote` não deve exigir que operadores obtenham UUID interno de cedente quando possuírem seu CNPJ brasileiro. Após integração B18, a UI poderá oferecer modelo com `cedenteDocumento` e manter o modelo original `cedenteId` para compatibilidade.

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
Somente iniciar após `origin/develop:TASKS.md` registrar B18 integrada e F09 com status literal `Liberada para iniciar` em PR documental humano posterior. O planejamento e a issue não autorizam branch/worktree F09. Sem alteração financeira nem merge por agente.
