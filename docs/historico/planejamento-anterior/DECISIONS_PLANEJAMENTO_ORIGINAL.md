# DECISIONS.md — SRM Credit Engine

**Versão:** 0.9 — 17/11/2026  
**Natureza:** registro de decisões de implementação e priorização; não integra a especificação funcional.

## 1. Limites da implementação inicial

A primeira entrega concentra-se em precificação de recebíveis, câmbio, liquidação íntegra e idempotente, auditoria e consulta de extratos. Não contempla transferência bancária efetiva, liquidação parcial, estornos, múltiplas moedas de origem, motor de risco nem infraestrutura distribuída. Esses cortes reduzem a superfície de falhas e preservam foco na correção financeira.

## 2. Evoluções documentadas, não implementadas

A arquitetura para escala de 1 milhão de transações por minuto e uma proposta de arquitetura orientada a eventos (EDA) serão tratadas como exercícios de desenho e evolução arquitetural. Não justificam introduzir filas, brokers ou microserviços na primeira implementação. Um post-mortem hipotético de liquidações duplicadas será elaborado em documento separado, como exercício de prevenção sistêmica.

## 3. Autenticação e priorização

Keycloak foi escolhido como provedor central de identidade, com possibilidade de federação Google e Microsoft. A integração deve ser incremental e não comprometer a validação do motor financeiro, os testes de idempotência e a integridade transacional. O detalhamento da implantação e do rollout ficará em ADR próprio.

## 4. Premissas aprovadas

- Prazo inteiro **positivo** (`prazoMeses`) como entrada autorizada; `dataVencimento` opcional/informativa no MVP, sem conversao automatica de dias.
- Taxa base configurável, com valor inicial de 1,00% a.m. e snapshot na liquidação.
- Cotação mais recente com vigência até o instante da liquidação, registrada imutavelmente na operação.

## 5. Contexto de priorização

O trabalho foi iniciado como resposta a uma avaliação técnica para a função de Tech Lead. Esse contexto orienta a priorização e a documentação de alternativas, mas **não constitui regra de negócio** e, por isso, não aparece no `SPEC.md`.

## 6. Pendências

- Validar com o negócio a convenção de prazo fracionário, origem da taxa base e política de cotação desatualizada.
- Registrar ADRs de monólito modular, persistência, idempotência e integração de identidade.
- Revisar esta lista sempre que houver alteração efetiva de escopo.

## 7. Representação decimal no contrato da API

**Decisão:** valores monetários e taxas decimais são transmitidos como strings decimais canônicas no JSON, com moeda explícita para montantes; o OpenAPI declara esses campos como `type: string`. O backend concentra cálculos e arredondamento financeiro (`BigDecimal`/`HALF_EVEN`), enquanto o frontend apenas formata para apresentação sem converter valores financeiros para `number`.

**Justificativa:** evitar perda de representação decimal em consumidores JavaScript/TypeScript que interpretam números JSON como ponto flutuante binário, e manter o contrato interoperável entre linguagens.

**Trade-off:** consumidores precisam interpretar strings decimais e adotar bibliotecas de precisão decimal caso efetuem cálculos; o contrato não impede uso incorreto por terceiros. A escala e o arredondamento seguem as regras do domínio, não a formatação da interface.

## 8. API de integração e BFF como adaptadores REST independentes

**Decisão:** disponibilizar endpoints distintos para o painel do operador (`/bff/v1/*`) e consumidores externos (`/api/v1/*`), com controladores e objetos de transferência (DTOs) próprios, **na mesma aplicação Spring Boot**. As duas interfaces chamam os mesmos casos de uso e o mesmo motor financeiro; o BFF não chama a API por HTTP nem contém cálculo financeiro.

**Justificativa:** permitir contratos e políticas de autorização adequados a cada consumidor sem duplicar regras críticas, aumentar latência por chamadas internas ou introduzir custos e complexidade operacional de dois deploys. A distinção é lógica, preservando a opção de separação física futura.

**Trade-offs:** maior quantidade de adaptadores/DTOs e necessidade de testes de consistência entre as interfaces; o deploy e a escalabilidade permanecem compartilhados. Uma extração futura do BFF exigirá rever autenticação, observabilidade e comunicação entre serviços.

**Critério de revisão:** considerar separação de deploy apenas diante de requisitos concretos de escalabilidade, autonomia de evolução, isolamento operacional ou segurança que superem o custo adicional.

## 9. Habilitação progressiva de moedas

**Decisão:** o modelo de câmbio e os contratos monetários serão extensíveis para múltiplas moedas de pagamento, mas a primeira entrega habilitará somente BRL e USD (USD como primeira moeda estrangeira). Recebíveis permanecem denominados em BRL.

**Justificativa:** separar a capacidade arquitetural de suportar novas moedas de sua habilitação operacional; evitar implementar cotações e regras sem demanda validada.

**Trade-offs:** novas moedas exigirão configuração de cotações em BRL, fonte de cotação, escala monetária, regras de arredondamento e testes de referência; não basta adicionar um código de moeda a uma enumeração. Não haverá conversão cruzada indireta sem regra de negócio aprovada.

## 10. Configuração de moedas persistida em banco de dados

**Decisão:** o PostgreSQL será a fonte de verdade para o cadastro de moedas, com código ISO 4217, nome, escala decimal e indicadores de habilitação. BRL e USD estarão habilitadas inicialmente; outras moedas poderão ser cadastradas desabilitadas. Cotações permanecerão em estrutura independente, associadas à moeda estrangeira cadastrada e referenciadas em BRL, com vigência explícita.

**Justificativa:** permitir habilitação progressiva e governada sem alterar ou recompilar o motor financeiro; manter regras de apresentação e validação consistentes entre API, BFF e integrações.

**Trade-offs:** a configuração exige validação, migrações/seeds, controle de acesso e testes. Habilitar uma moeda não implica existir cotação válida, e não autoriza conversão entre moedas estrangeiras. Alterações de cadastro não podem alterar os snapshots financeiros de liquidações históricas.

**Limite:** a escala monetária configurada não substitui regras contábeis aprovadas; mudanças de escala e política de arredondamento exigem governança. Para a primeira versão, BRL e USD usam duas casas decimais.

## 11. Relacionamento entre moedas e cotações (1:N, referência BRL)

**Decisão:** manter `Moeda` e `Cotacao` como entidades independentes. Cada cotação referencia uma única moeda estrangeira cadastrada, com **BRL como moeda de referência fixa**. Uma moeda estrangeira possui N cotações históricas (1:N). A cotação registra `valor_em_reais`, vigência e identificação; BRL/BRL não é cadastrado.

**Justificativa:** simplificar o modelo para o fluxo operacional inicialmente previsto (recebíveis em BRL e pagamento em BRL ou USD), mantendo histórico e precisão sem antecipar a necessidade de pares cambiais arbitrários.

**Trade-offs:** o modelo não representa diretamente EUR/USD ou outros pares sem BRL. A conversão cruzada não será presumida ou implementada sem aprovação. Garantir taxa positiva, unicidade por moeda e vigência, e persistência do identificador e snapshot da cotação na liquidação.

**Pergunta ao negócio:** BRL será sempre a moeda de referência das cotações? Há previsão de pares estrangeiros diretos ou conversão entre duas moedas estrangeiras? Se houver, reavaliar o modelo de pares e os contratos antes de implementar essa funcionalidade.

## 12. Nomenclatura em português brasileiro

**Decisão:** utilizar português brasileiro para nomes de entidades, classes, interfaces, funções, métodos, atributos, variáveis de domínio, DTOs, campos JSON e estruturas de banco criadas para o produto. Exemplos: `Moeda`, `Cotacao`, `Recebivel`, `Liquidacao`, `EstrategiaPrecificacao`, `SimularPrecificacao`, `buscarCotacaoVigente`, `prazoMeses`, `moedas` e `cotacoes`. Identificadores não terão acentos.

**Justificativa:** padronizar a linguagem ubíqua do domínio financeiro e aproximar código, especificação e comunicação com a área de negócio.

**Trade-offs:** documentação e integrações técnicas frequentemente utilizam termos ingleses; a equipe deve manter um glossário consistente e evitar misturar idiomas no mesmo conceito. Identificadores impostos por frameworks, bibliotecas, padrões ou contratos externos não serão traduzidos. Mudanças em campos de contratos publicados requerem versionamento e compatibilidade.


## 13. Recebíveis pré-cadastrados, taxa base versionada e auditoria CUD

**Decisões aprovadas:** (1) um `Recebivel` precisa existir antes da simulação/liquidação; não há criação implícita na liquidação. (2) A taxa base mensal é configurada em banco por versões históricas, com flag de ativação e vigência; uma versão efetiva deve ser selecionada de modo determinístico e registrada como snapshot na liquidação. (3) Todas as tabelas operacionais devem produzir trilha de auditoria de criação, atualização e exclusão (CUD) no PostgreSQL. A tabela `liquidacoes` não admite UPDATE/DELETE por papéis operacionais; o histórico de auditoria é append-only.

**Justificativa:** separar cadastro e operação financeira; permitir rastrear qual política de taxa produziu cada cálculo; garantir rastreabilidade independente dos adaptadores API/BFF e dos serviços Java.

**Implementação proposta:** triggers transacionais para `INSERT`, `UPDATE`, `DELETE`, com snapshots JSONB antes/depois, identificador da linha, operador, correlação e instante; permissões mínimas e proteção de escrita na trilha. A função de auditoria será reutilizável, sem trigger recursiva na própria tabela de auditoria.

**Trade-offs:** maior volume de armazenamento e escrita; cuidado com dados sensíveis, retenção e privilégios de DBA; transações revertidas também revertem registros de auditoria, portanto tentativas negadas exigem logs de segurança independentes. O controle não substitui a auditoria administrativa do banco.

**Pendente de detalhamento:** política de troca de versão ativa da taxa, possibilidade de vigência futura, escopo da taxa por produto/cedente, retenção e acesso aos registros de auditoria.

## 14. Substituição de taxa base e definição de vigência

**Decisão aprovada:** a ativação de uma nova versão da taxa base desativa a versão anterior em uma única transação, preservando o histórico. Liquidações já registradas não são recalculadas; cada liquidação persiste identificador da versão, percentual e instante de referência do cálculo. Uma liquidação em andamento deve usar uma versão selecionada de forma consistente sob concorrência, sem depender apenas da ordem dos commits.

**Questões pendentes de negócio:** a nova taxa passa a valer imediatamente após aprovação ou pode ter início agendado? Qual evento fixa a taxa aplicável a uma liquidação (início da operação, seleção da taxa ou confirmação)? Como tratar operações iniciadas antes e concluídas depois da mudança?

**Consequência técnica:** distinguir status administrativo de vigência financeira; evitar que uma flag `ativa` isolada determine a taxa em operações com agendamento. Na implementação inicial, não presumir agendamento nem política de corte sem decisão explícita. Garantir seleção transacional consistente, com testes de concorrência na virada da taxa.

## 15. Padrão editorial da documentação técnica

**Decisão:** documentos de especificação, arquitetura, domínio, persistência e planejamento devem utilizar linguagem técnica, objetiva, impessoal e autossuficiente. O conteúdo deve registrar requisitos, premissas, decisões, alternativas, consequências, riscos e pendências, sem narrativas sobre sua elaboração.

**Aplicação:** a diretriz é obrigatória para novos artefatos e revisões dos documentos técnicos. Registros de processo e instruções operacionais seguem as convenções próprias de seus respectivos artefatos.

## 16. Distinção entre antecipação ao cedente e pagamento pelo sacado

**Decisão de escopo:** `Liquidacao` representa a efetivação da antecipação financeira ao `Cedente`, com registro do valor presente, deságio, taxas e eventual conversão cambial. Não representa a quitação posterior do título pelo sacado ou devedor.

**Limite do domínio inicial:** o sistema não executa cobrança, conciliação de pagamentos do sacado, baixa por recebimento, gestão de inadimplência ou recuperação de crédito. Essas capacidades constituem evolução possível e não devem ser inferidas do estado `Liquidacao`.

**Justificativa:** a cessão e a antecipação do crédito são eventos distintos do pagamento da obrigação pelo devedor. A separação evita confundir o desembolso ao cedente com o recebimento futuro pela instituição cessionária e permite evoluir os processos de cobrança sem alterar o significado dos registros históricos de antecipação.

**Evolução possível:** introduzir a identificação do `Sacado` no `Recebivel` e um fluxo próprio de acompanhamento, recebimento, conciliação, baixa e inadimplência, com eventos e registros financeiros separados da `Liquidacao` de antecipação.

**Questões pendentes de negócio:** será necessário identificar formalmente o sacado já no cadastro inicial? A cessão admite operações com ou sem coobrigação do cedente? Quais regras de responsabilidade, cobrança e baixa serão aplicáveis em eventual evolução? A alocação do risco de inadimplência depende das condições contratuais da cessão.


## 17. Linguagem Kotlin e gestão de build com Gradle

**Decisão:** adotar Kotlin como linguagem principal do backend, Spring Boot como framework de aplicação e Gradle com Kotlin DSL (`build.gradle.kts`) como ferramenta de build. A organização permanece em monólito modular, com arquitetura hexagonal obrigatória (portas e adaptadores). O frontend também deverá respeitar arquitetura hexagonal, independentemente da linguagem utilizada.

**Justificativa:** Kotlin reduz código repetitivo, explicita nulidade no sistema de tipos, favorece a modelagem de objetos de valor e estados e mantém interoperabilidade com bibliotecas consolidadas da JVM. A escolha do Kotlin DSL para o Gradle é independente da linguagem de implementação; não constitui, isoladamente, justificativa para Kotlin no backend.

**Benefícios esperados:** maior expressividade de código, contratos internos com nulidade explícita, imutabilidade por padrão com `val`, uso criterioso de `data class` e `sealed interface`, integração com Spring Boot e ecossistema Java. A redução de linhas não é considerada, por si só, redução da complexidade do sistema.

**Riscos e trade-offs:** (1) entidades JPA/Hibernate exigem configuração apropriada de construtores e abertura de classes para proxies, conforme necessidade; (2) `data class` não deve ser adotada indiscriminadamente para entidades persistentes, devido a identidade, igualdade e relacionamentos lazy; (3) tipos provenientes de bibliotecas Java podem enfraquecer garantias de nulidade (*platform types*); (4) compilação, plugins e ferramentas podem elevar o custo de build e manutenção; (5) Kotlin requer familiaridade da equipe e disciplina para evitar abstrações excessivas; (6) coroutines não serão adotadas sem justificativa, sobretudo em fluxos Spring MVC/JPA bloqueantes. Java moderno permanece alternativa tecnicamente válida.

**Integridade financeira:** utilizar `BigDecimal` em todos os cálculos monetários e de taxas; proibir `Float`/`Double` no domínio financeiro. Aplicar regras explícitas de escala e `RoundingMode.HALF_EVEN`, respeitando os critérios de arredondamento definidos no `SPEC.md`. Comparações numéricas de `BigDecimal` devem usar `compareTo()` quando a escala não for relevante, pois `equals()` distingue, por exemplo, `1.0` de `1.00`. O uso de Kotlin não substitui testes de precisão, idempotência, transações e concorrência.

**Diretrizes de implementação:** domínio sem dependências de Spring, JPA ou HTTP; portas definidas internamente e adaptadores nas fronteiras; nomenclatura própria em pt-BR; Gradle Wrapper versionado; testes com JUnit 5 e Testcontainers; JaCoCo e verificação de qualidade conforme configuração do projeto. Inicialmente, um único projeto Gradle com separação modular por pacotes, sujeito a revisão se a necessidade de isolamento físico justificar subprojetos.

**Consequência:** a implementação e a documentação de build do backend utilizarão Kotlin. A compatibilidade exata entre versões de Kotlin, Spring Boot, Gradle e plugins deverá ser validada na inicialização do projeto, antes de fixar versões.

## 18. Política de injeção de dependências no backend Kotlin

**Decisão:** adotar injeção por construtor como padrão obrigatório para componentes de domínio e aplicação, e como padrão preferencial para controladores e adaptadores Spring. A injeção por campo (`@Autowired` com `lateinit var`) é permitida excepcionalmente em testes de integração e componentes de infraestrutura, mediante justificativa. Não utilizar injeção por campo no núcleo hexagonal.

**Justificativa:** o construtor explicita dependências obrigatórias, permite referências `val`, facilita a instanciação e os testes sem contexto Spring e reduz a verbosidade em Kotlin. Em classes Spring com um único construtor, a anotação `@Autowired` é dispensável. Esse recurso também existe em Java; a vantagem adicional do Kotlin é a declaração concisa de propriedades no construtor.

**Trade-offs:** construtores extensos podem sinalizar acúmulo de responsabilidades; múltiplas implementações de uma porta exigem qualificação ou configuração explícita; dependências circulares impedem a composição e devem ser eliminadas. Injeção por campo reduz a lista de parâmetros visível, mas esconde dependências, exige inicialização posterior à construção e normalmente torna testes unitários isolados mais trabalhosos. A arquitetura hexagonal não exige uma modalidade específica de injeção: exige direção correta das dependências.

**Implementação:** casos de uso e serviços de aplicação não terão anotações Spring e receberão portas por construtor. A infraestrutura fará a composição por `@Configuration`/`@Bean` quando necessário. Controllers e adaptadores poderão ser beans Spring e preferirão construtores com `private val`. Testes de integração com contexto Spring podem usar `@Autowired` em campos, sem estender essa prática ao núcleo de negócio.


## 19. Concorrência otimista na liquidação e idempotência por tenant/operação

**Decisão aprovada (17/11/2026):** utilizar optimistic locking no agregado `Recebivel`, com coluna de versão e mapeamento `@Version` no adaptador JPA. A operação de liquidação deve alterar o estado do recebível e provocar atualização versionada na mesma transação ACID que grava a liquidação e o resultado idempotente. O bloqueio pessimista explícito (`SELECT FOR UPDATE`) anteriormente sugerido para o recebível foi rejeitado por não demonstrar o requisito expresso do nível Sênior.

**Idempotência:** exigir header `Idempotency-Key` nas operações persistentes críticas dos adaptadores `/api/v1` e `/bff/v1`. A identidade da requisição é `(tenant_id, tipo_operacao, chave_idempotencia)`; comparar também o hash canônico do comando, persistido com o resultado. O identificador do ator autenticado é registrado para auditoria, mas não integra a identidade idempotente. Repetição da mesma chave e payload após commit retorna status/corpo originais; reutilização da chave com payload diferente retorna HTTP 409.

**Concorrência e integridade:** duas chaves distintas podem disputar o mesmo recebível. Uma transação deve vencer; a outra deve sofrer conflito de versão, com rollback e tradução para HTTP 409. Manter `UNIQUE` para impedir mais de uma liquidação por recebível como proteção adicional independente. A disputa pela mesma chave idempotente é protegida por índice único no PostgreSQL; uma espera de até 2 segundos, configurável, é a política inicial para conflitos de reserva, sujeita a teste e ajuste. Se a chave ainda estiver em processamento, responder HTTP 409 com `Retry-After` segundo política configurada. Não usar retry automático da liquidação após conflito de versão.

**Teste obrigatório:** teste de integração com PostgreSQL/Testcontainers e transações independentes sincronizadas para ambas lerem a mesma versão do recebível; comprovar conflito otimista real, exatamente uma liquidação confirmada, rollback da transação perdedora e ausência de escrita parcial. Testes separados devem verificar repetição com mesma chave/payload, chave com payload divergente, perda da resposta após commit e integridade por constraint.

**Escalabilidade:** PostgreSQL permanece fonte de verdade. Redis pode futuramente servir como cache distribuído de resultados confirmados, sem substituir constraints e garantias transacionais. Cache por tenant/operação/chave, não exclusivamente por usuário.

**Justificativa e trade-offs:** optimistic locking atende ao enunciado e evita lock pessimista explícito em leituras, mas conflitos sob alta contenção exigem tratamento explícito. `@Version` sozinho não basta: é necessário efetuar UPDATE versionado e demonstrar conflito com duas transações concorrentes. A reserva da chave idempotente pode aguardar locks internos do índice único; o timeout deve ser aplicado especificamente a essa disputa, sem confundir com a estratégia de concorrência do recebível.


## 20. Resiliência da integração cambial

**Decisão (17/11/2026):** a atualização de cotações ocorrerá por adaptador HTTP mockado com Resilience4j, timeout de conexão inicial de 1 segundo, timeout de resposta de 2 segundos e até duas novas tentativas com backoff exponencial e jitter. Retentativas somente para falhas transitórias (conexão, timeout, HTTP 429 e 5xx); não repetir erros 400/401/403 ou respostas inválidas. Parâmetros configuráveis e sujeitos a testes. Circuit breaker fica como evolução documentada, não implementado no MVP.

**Fronteira financeira:** a liquidação consulta cotação elegível previamente persistida, sem HTTP externo durante a transação ACID. Falta ou expiração da cotação implica rejeição sem persistência de liquidação. A modalidade intradiária mockada e a validade inicial de 30 minutos são premissas provisórias P0, não políticas homologadas de negócio. Preservar snapshot da cotação usada.

## 21. Observabilidade

**Decisão:** Spring Boot Actuator, Micrometer e logs JSON estruturados nos adaptadores e serviços de aplicação, sem dependências no domínio. Instrumentar contador de liquidações confirmadas, timer da precificação, contador de conflitos otimistas e falhas cambiais. Emitir sucesso financeiro somente após confirmação transacional; evitar contagem de rollback como liquidação. Logs com correlationId, tenantId, tipoOperacao, resultado, duração e código de erro; não registrar credenciais, tokens ou payload financeiro integral. Não usar tenantId, chave idempotente ou UUID de recebível como labels de métricas por cardinalidade.

## 22. Segurança e isolamento multi-tenant

**Decisão:** Keycloak como provedor OIDC/OAuth2, Spring Security Resource Server validando assinatura, issuer, audience, expiração e autorizações. Adaptadores /api/v1 e /bff/v1 compartilham casos de uso, sem chamada HTTP interna entre eles. tenantId deve vir de contexto autenticado confiável e ser validado nas consultas e comandos; identificador fornecido no corpo não concede acesso a recursos de outro tenant. RBAC mínimo e testes de isolamento; não construir motor ABAC genérico no MVP. Header Idempotency-Key obrigatório nas operações críticas conforme decisão 19. Revisar política CORS/CSRF conforme o modo concreto de autenticação do BFF; não assumir que ambos são idênticos.

## 23. Estratégia de testes

**Decisão:** JUnit 5, MockK quando apropriado, Spring Boot Test e Testcontainers com PostgreSQL para integrações críticas. Cobrir golden cases C1–C3, estratégias e arredondamento; duas transações independentes lendo a mesma versão para comprovar optimistic locking; unicidade da liquidação; idempotência e payload divergente; rollback e auditoria; timeout/retry e ausência de cotação elegível; autenticação, autorização, isolamento tenant e contratos API/BFF. Evitar testes concorrentes baseados apenas em Thread.sleep ou mocks. Assegurar que o conflito otimista seja observado e não mascarado pela constraint UNIQUE.

## 24. Integração contínua e qualidade

**Decisão:** GitHub Actions em push e pull request, Gradle Wrapper versionado, JDK compatível com Spring Boot/Kotlin selecionados, compilação, ktlint, Detekt, testes unitários e integração com Testcontainers, relatório JaCoCo e build. Falhas de lint/testes impedem aprovação do pipeline. Cobertura percentual não substitui cenários financeiros críticos. Deploy em produção, Kubernetes e SonarQube não integram o MVP. Diagrama C4 níveis 1 e 2 integra a documentação de arquitetura exigida pelo enunciado.

**Critério transversal de escopo:** decisões técnicas implementáveis não homologam ambiguidades financeiras P0. SPEC.md distingue regra exigida, premissa demonstrativa e pergunta pendente de negócios; o guia de defesa registra trade-offs e limites de produção.


## 25. Baseline multi-tenant e integridade de propriedade

**Decisão aprovada:** um tenant corresponde a um cliente lógico; `tenants` identifica clientes e possui cedentes. `cedentes`, `recebiveis`, `liquidacoes` e `requisicoes_idempotentes` contêm `tenant_id`. A origem autorizada de `tenantId` é o contexto autenticado confiável, nunca um campo de requisição. FKs compostas verificam que recebível, cedente e liquidação pertencem ao mesmo tenant; consultas e atualizações devem incluir `tenant_id` em seus critérios. Moedas, cotações e taxa base são **cadastros compartilhados da plataforma** no MVP, com administração autorizada, e não registros de propriedade de cada tenant.

**Trade-offs:** a redundância de `tenant_id` agiliza filtros e permite integridade referencial composta; exige validações adicionais, índices próprios e cuidado contra escalada horizontal. O escopo da identidade idempotente permanece exatamente `(tenantId, tipoOperacao, idempotencyKey)`, independentemente do controle de versão no recebível.

## 26. Contrato do prazo e vencimento

**Decisão aprovada para o MVP:** `prazoMeses` é inteiro positivo, obrigatório e autoritativo para o cálculo, em API e BFF. `dataVencimento` é um atributo opcional **informativo**, não convertível automaticamente para meses sem convenção financeira aprovada. Os adaptadores não podem aplicar `ChronoUnit`, divisão por 30 ou dias corridos silenciosamente. A UI poderá exibir vencimento, mas sempre solicitará e exibirá o prazo usado no preço. Divergências entre calendário e prazo exigem esclarecimento de negócio (P0); a implementação demonstrativa não garante conciliação contratual entre esses campos.

## 27. Instante de referência e leituras coerentes

**Decisão aprovada:** o caso de uso de liquidação recebe um `Clock` injetável, captura um único `instanteReferencia` (`Instant`, UTC) ao iniciar a operação e utiliza-o na verificação de elegibilidade temporal de taxa base e cotação. As consultas devem considerar a vigência, não podem selecionar dados futuros nem criados posteriormente ao instante de seleção, e precisam produzir snapshots dentro da mesma transação. `instante_referencia_taxa` da liquidação armazena esse instante comum para taxa e cotação; `liquidado_em` registra a efetivação. Testes devem simular atualização de taxa/cotação concorrente e observar a consistência da seleção. Uma regra de cut-off que afete operações iniciadas em paralelo é P0 e deve ser validada antes de produção.

## 28. Redação de auditoria e dados confidenciais

**Decisão aprovada para o MVP:** a trilha JSONB de triggers ignora `identificador_fiscal` e `nome` de `cedentes`; ignora `chave`, `resumo_requisicao` e `resposta_persistida` de `requisicoes_idempotentes`. Os dados necessários para uso operacional continuam nas respectivas tabelas, protegidos por privilégios e controle de acesso. `registros_auditoria` inclui tenant para segregação de consulta, quando aplicável. A auditoria deve abranger a nova tabela `tenants` e todos os cadastros existentes. Essa redução da superfície de exposição **não constitui conformidade LGPD completa**: retenção, acesso, política de logging, mascaramento adicional e governança ainda devem ser verificados.

## 29. Revisão de escopo, artefatos e implementação

**Decisão aprovada:** `SPEC.md` é o resumo normativo de 1–2 páginas solicitado pelo desafio; a matriz P0/P1/P2 e o histórico técnico detalhado ficam em documentos referenciados. `TASKS.md` deve listar dependências e gates da implementação real, sem marcar testes, migrações ou código como concluídos antes de evidência. Como as migrations Flyway são **propostas ainda não aplicadas**, V1/V2 podem ser alinhadas antes da primeira execução; após aplicação em ambiente compartilhado, não editar migrations históricas, criar V4+ ou sequência subsequente. Verificar estrutura SQL e testes com PostgreSQL real antes de declarar o backend entregue.


## 30. Interface do operador e fronteira de integração frontend

**Decisão aprovada para o MVP:** React/TypeScript com separação hexagonal entre apresentação, casos de uso e adaptadores HTTP. O frontend consome exclusivamente o BFF `/bff/v1/*`, que utiliza os mesmos casos de uso do adaptador de integração `/api/v1/*`; não há HTTP interno entre BFF e API. Não reproduzir aritmética monetária, desconto ou conversão cambial no navegador; valores seguem como strings decimais em transporte.

**Fluxo:** formulário de cadastro de recebível, com valor, tipo, moeda de pagamento, `prazoMeses` explícito e vencimento informativo; **simulação reativa após cadastro** com chamada ao backend (debounce e descarte de respostas antigas); confirmação com chave idempotente; histórico paginado/filtrado no servidor. Estados de carregamento, erro, vazio e confirmação fazem parte dos critérios de aceite. Estado global somente se necessário; acessibilidade e responsividade proporcionais ao desafio.

**Trade-off e limite explícito:** o requisito de recebível previamente cadastrado continua valendo inclusive para simulação. O fluxo de preview *antes* do cadastro, embora possível em outras soluções, não é implementado sem revisão formal dessa decisão e de seu contrato; registrar essa limitação para a defesa. Definir contratos HTTP exatos no B08 antes do F01. O detalhamento de componentes, bibliotecas auxiliares e testes fica para a fase frontend, sem bloquear B01.