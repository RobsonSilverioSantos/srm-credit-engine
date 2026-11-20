# ADR-005 — Monólito modular versus microserviços

**Status:** aprovado para o MVP; referência normativa: [DECISIONS](../../DECISIONS.md), ADR-005. Revisão factual: `develop` `3cd2bde`.

## Contexto

O motor precisa preservar precisão, isolamento por organização e, futuramente, atomicidade da liquidação. O prazo é limitado e não há necessidade demonstrada de operação independente por capacidade. B02–B05 já fornecem núcleo, cadastro condicionado e histórico de taxas, mas não o fluxo completo de liquidação.

## Alternativas

| Alternativa | Benefícios | Custos e riscos |
|---|---|---|
| Monólito modular hexagonal | Uma implantação e uma fronteira transacional local; testes e diagnóstico mais simples | Recursos e releases compartilhados; acoplamento exige disciplina |
| Microserviços | Autonomia de implantação e escala por capacidade | Rede, observabilidade, operação e consistência distribuída; coordenação e falhas parciais |
| Monólito sem fronteiras | Menor custo inicial | Regra financeira e infraestrutura acopladas; mudanças e testes mais difíceis |

## Decisão

Manter **monólito modular** com PostgreSQL/Flyway. Domínio, aplicação e adaptadores têm dependências dirigidas ao núcleo; o frontend segue separação equivalente. API e BFF previstos devem invocar os mesmos casos de uso em processo, sem HTTP interno. Capacidade de negócio é fronteira lógica, não promessa de deploy separado.

## Consequências e evidências

O backend contém módulos Gradle `dominio`, `aplicacao` e `app`, estratégias de preço e portas/adaptadores de cadastro e taxas ([guia](../../backend/README.md)); o frontend F00 compõe uma porta com adaptador em memória ([guia](../../frontend/README.md)). A ausência de autenticação e de liquidação impede afirmar que a transação financeira completa já exista. A unicidade e as FKs do esquema apoiam, mas não substituem, autorização, versionamento e rollback no caso de uso futuro. O [C4](../arquitetura/C4_MVP.md) separa estado atual e alvo.

O desenho favorece integridade local, mas não garante disponibilidade, throughput ou isolamento de falhas de infraestrutura. Extração de serviços antes de definir propriedade de dados e contratos pode quebrar a atomicidade; outbox não elimina a necessidade de conciliação.

## Gatilhos de revisão

Medir contenção, latência, capacidade e acoplamento real; reavaliar se uma capacidade possuir demanda operacional/autonomia que justifique o custo adicional. Antes de extraí-la, definir dono dos dados, invariantes, contratos, recuperação e consistência entre fronteiras. Nenhuma extração ou capacidade para 1 milhão/min está aprovada por este ADR.
