# ADR-005 — Monólito modular versus microserviços

- **Status:** aprovado para o MVP; evolução descrita, não implementada
- **Decisão principal:** `DECISIONS.md` ADR-005
- **Contexto:** motor de crédito multimoedas com precificação, câmbio, recebíveis, liquidação, identidade e extrato; prazo restrito e necessidade de demonstrar invariantes financeiras.

## Alternativas consideradas

| Alternativa | Benefício | Risco/custo |
|---|---|---|
| Monólito modular com arquitetura hexagonal | Uma transação ACID para invariantes locais; implantação, testes e troubleshooting simples | Escala de deploy compartilhada; exige disciplina para evitar acoplamento entre módulos |
| Microserviços independentes | Escala e deploy por capacidade; fronteiras operacionais explícitas | Consistência distribuída, mensageria, observabilidade e gestão de falhas muito mais complexas |
| Monólito sem módulos explícitos | Menor código inicial | Dificulta separação futura, isolamento de regras e revisão |

## Decisão e razões

Adotar **monólito modular**. Preservar módulos de negócio (`organizacoes`, `cedentes/recebiveis`, `precificacao`, `cambio`, `liquidacoes`, `seguranca`) e portas/adaptadores; API REST e BFF chamam os mesmos casos de uso, sem HTTP interno. O domínio não deve importar JPA, Spring nem infraestrutura. Isso mantém **atômicas** atualização versionada do recebível, criação da liquidação, idempotência e auditoria.

## Consequências / riscos assumidos

O dimensionamento de recursos e as releases são compartilhados. Requer testes de fronteira e revisão para impedir dependência direta entre detalhes de persistência de módulos. Módulos não significam processos separados, e o modelo atual não prova operação a 1 milhão de transações/minuto.

## Gatilhos para reavaliar

Extração de um serviço só é considerada se medições mostrarem gargalo isolável, escalabilidade ou autonomia operacional justificar a complexidade, e os contratos/eventos e o modelo de consistência estiverem maduros. **Não separar transação de liquidação em serviços sem redesenhar garantias**: possível adoção de outbox/inbox, idempotência de consumidores, estados de pagamento e conciliação.

## Evidências de aceite

`SPEC.md`, testes de arquitetura hexagonal, transação ACID e decisão no README. Evidência de implementação/benchmark somente após PRs e execuções reais; este ADR, por si só, é documentação.
