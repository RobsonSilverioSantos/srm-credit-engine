# Proposta independente — arquitetura para 1 milhão de transações/minuto

**Estado: PENDENTE — arquitetura ainda não desenhada nem aprovada.**

Este arquivo é somente um marcador de pendência e de separação de escopo. **Não constitui um HLD, solução de referência, prova de capacidade ou evidência de atendimento ao requisito.** A versão anterior da proposta foi rejeitada pelo responsável técnico e não é utilizada como baseline.

## Natureza

- Exercício independente do SRM Credit Engine MVP, assim como `REVIEW.md` e `POST_MORTEM_ANEXO_B.md`.
- Será elaborado em discussão específica entre o responsável técnico e ChatGPT, **sem alocação de agente Cursor/Copilot**.
- O artefato final deverá abordar arquitetura de software apropriada à carga alvo, critérios de latência/disponibilidade, modelagem de carga e hotspots, caching, sharding, consistência eventual, idempotência em ambiente particionado, diagramas, falhas, riscos, segurança, custos e validação por testes de capacidade.
- Não existe obrigatoriedade de seguir o monólito, o banco ou as decisões técnicas do MVP; apenas o domínio financeiro pode ser reaproveitado como cenário.

**Próxima ação, fora deste ciclo de implementação:** produzir e revisar o documento de arquitetura de alta escala. Até lá, a matriz Staff/Tech Lead deve marcar este item como **não atendido / pendente**.
