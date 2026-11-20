# C4 — Contexto e contêineres do MVP (rascunho inicial)

**Status:** conceitual; revisar após integração B02/F00/B04/B06/B08. **Não é** o exercício independente de alta escala e não comprova componentes implementados.

## Nível 1 — contexto

```mermaid
flowchart LR
  op[Operador autenticado] -->|Preencher, simular, registrar e consultar| app[SRM Credit Engine — sistema demonstrativo]
  app -->|Consulta de taxas mockada| fx[Origem de câmbio simulada]
```

**Fronteira:** operador utiliza a aplicação para antecipação lógica de recebíveis; não há transferência bancária real. A taxa de câmbio é mockada e versionada.

## Nível 2 — contêineres

```mermaid
flowchart LR
  op[Operador] -->|HTTPS no ambiente que vier a ser configurado| spa[SPA React/TypeScript + MUI]
  spa -->|REST /bff/v1| api[Backend Kotlin Spring Boot — API + BFF + casos de uso]
  api -->|JDBC/JPA em transação local| pg[(PostgreSQL: recebíveis, FX, liquidações, auditoria, idempotência)]
  api -->|Atualização/cotação mockada| mock[Adaptador de FX mockado]
```

**Por que monólito:** BFF e API não se comunicam por HTTP entre si; usam os mesmos casos de uso dentro do mesmo processo. O PostgreSQL mantém uma única fronteira transacional para liquidação. A divisão interna em módulos/portas/adaptadores não representa microserviços.

## O que validar após implementar

- Endpoints reais, canais de autenticação e limites entre SPA e backend.
- Se o FX mockado é in-process ou um serviço HTTP simulado (ajustar diagrama).
- Módulos efetivos, esquema de banco e mecanismo de auditoria.
- Portas, volume/healthcheck, observabilidade e ambiente de execução.

Não utilizar este desenho como prova de 1 milhão de transações por minuto.
