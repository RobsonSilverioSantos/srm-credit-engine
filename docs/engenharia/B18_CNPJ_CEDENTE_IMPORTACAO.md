# B18 — Identificar cedente por CNPJ na importação de recebíveis

**Situação:** política funcional aprovada em 22/11/2026 pelo responsável técnico; **execução somente após integração humana do PR de liberação** e confirmação literal de `Liberada para iniciar` em `origin/develop:TASKS.md`.
**Issue:** [#82](https://github.com/RobsonSilverioSantos/srm-credit-engine/issues/82).
**Dependências técnicas:** B14 (PR #56), B16 (PR #71), B17 (PR #79), F08 (PR #81), todas integradas.
**Executor:** agente `backend`; revisores `architect`, `qa`, `reviewer`.

## Motivação e decisão

No contrato atual, o operador é obrigado a conhecer o `cedenteId` (UUID interno), embora o cadastro já possua identificação empresarial B14. A implementação de B18 deve resolver o cedente pelo **CNPJ brasileiro**, sem introduzir vínculo com organização informado por planilha.

**`referenciaExterna` é a identificação do próprio recebível no sistema de origem, não da organização nem do cedente.** Permanecem vigentes a unicidade por `(organizacao_id, referencia_externa)`, a pré-validação sem persistência e a confirmação atômica B16, bem como a liquidação financeira separada B17/ADR-025. Esta tarefa **não** aprova alteração para unicidade por `organizacao+cedente+referencia`, que exigiria decisão própria.

## Contratos propostos — preservar compatibilidade

O upload B16 continua `POST /api/v1/lotes/recebiveis/validacoes` e espelho BFF, `multipart/form-data` parte `arquivo`. A confirmação continua `POST /api/v1/lotes/recebiveis` e espelho BFF, JSON canônico e `Idempotency-Key`.

**Modelo anterior, obrigatório manter:**
```csv
referenciaExterna;cedenteId;modalidade;valorFaceBrl;prazoMeses;vencimento;moedaPagamento
NF-2026-001245;483e5e35-d464-4fda-9d36-3fd1b7c4aa54;DUPLICATA_MERCANTIL;25000.00;2;2027-02-04;BRL
```

**Modelo adicional B18, para CNPJ de cedente BR:**
```csv
referenciaExterna;cedenteDocumento;modalidade;valorFaceBrl;prazoMeses;vencimento;moedaPagamento
NF-2026-001245;90021382000122;DUPLICATA_MERCANTIL;25000.00;2;2027-02-04;BRL
```

A interpretação do arquivo deve distinguir cabeçalhos completos e exatos, sem aceitar `cedenteId` e `cedenteDocumento` simultaneamente, alias ambíguo ou fallback silencioso. CSV e XLSX padronizado devem ter semântica idêntica e preservar as demais seis colunas, tipos e limites de 200 linhas / 2 MiB configuráveis.

`cedenteDocumento` identifica exclusivamente `BR/CNPJ` numérico ou alfanumérico conforme B14/ADR-023; para cedentes estrangeiros `OUTRO`, registros legados sem identificação canônica e integrações existentes, o modelo `cedenteId` continua disponível. Inclusão de país/tipo do cedente no novo arquivo está fora da B18.

## Segurança, resolução e integridade

1. Normalizar e validar CNPJ com os validadores existentes B14, preservando zeros à esquerda e caracteres alfanuméricos. A validade estrutural/DV **não equivale a consulta de situação cadastral na Receita Federal**.
2. Buscar somente `organizacao_id` da identidade autenticada, `pais=BR`, `tipo_documento=CNPJ`, `documento_canonico` igual ao documento validado e `ativo=true`; usar consulta parametrizada, porta de persistência e índice de unicidade B14. Não aceitar ou inferir `organizacaoId` no CSV/body.
3. Documento não encontrado, de outra organização, legado/ambíguo, inativo ou inválido deve gerar erro de linha seguro e impedir confirmação do lote inteiro. Não revelar a qual organização pertence CNPJ externo nem imprimir documentos no log.
4. Na **pré-validação**, resolver documento → `cedenteId` no backend sem gravar recebível ou reserva. Produzir comando canônico interno idêntico ao B16 anterior. A confirmação B16 existente revalida que esse UUID permanece ativo e pertence à organização.
5. Continuar exigindo referência única por organização em todos os lotes, com `NOVO`, `JA_CADASTRADO`, `JA_LIQUIDADO` e rejeição integral; idempotência/replay mantidos. Não modificar migrations aplicadas; nova migration somente se comprovadamente necessária e autorizada.

## Verificações de aceite

- CSV/XLSX CNPJ numérico e alfanumérico, mascarado/canônico nos formatos B14 aceitos, zeros à esquerda, caracteres ilegais, DV inválido, campo ausente.
- Mesma identidade empresarial em organizações distintas sem vazamento; cedente inativo, inexistente ou legado `DESCONHECIDO` rejeitado de modo seguro; proteção em caso de mudança entre prévia e confirmação.
- Regressões do modelo de UUID existente e seus endpoints JSON; equivalência de comando canônico e idempotência; prévia sem escrita, confirmação ACID.
- Massas A/B 200+200 com quatro `JA_LIQUIDADO`/repetições por referência externa; dois cedentes com referência externa igual **na mesma organização** continuam conflitando segundo política vigente.
- PostgreSQL real, isolamento, concorrência, rollback e CI; PR com testes efetivamente executados; revisão independente dos agentes.

## Fronteira de F09

A F09 ajustará a interface React, os modelos baixáveis e a experiência de entrada pelo CNPJ **após a B18 integrada**. Não editar a F08 na mesma worktree/backend; não liberar F09 por antecipação.

**Evidência do planejamento:** na elaboração deste documento, nenhuma implementação funcional ou teste B18 havia sido executado. Os resultados da implementação devem ser registrados separadamente no PR.

## Contrato técnico B18

Os cabeçalhos UUID e CNPJ são alternativos e completos; o segundo campo da
prévia confirmável é convertido para `cedenteId` no comando JSON B16. A
confirmação não aceita documento como substituto do UUID. O CNPJ deve ser
informado como texto no XLSX para preservar zeros à esquerda; não é possível
recuperar zeros perdidos ao armazenar uma célula como número. Documento
validado indica apenas formato e dígitos verificadores, não situação fiscal.

A consulta usa a organização autenticada e restringe a identificação
canônica `BR/CNPJ` a cedentes ativos; reservas de legado impedem a resolução.
A confirmação continua revalidando o UUID e a atividade sob a transação B16,
sem reservar cedentes ou gravar recebíveis na prévia.
