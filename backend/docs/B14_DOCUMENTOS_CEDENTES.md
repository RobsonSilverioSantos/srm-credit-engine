# B14 — Backend: documentos de cedentes

## Escopo e decisões

Identificações de cedentes seguem as decisões:

- POST antigo somente nome/documento: nova entrada BR/CNPJ, valida ambos os DVs.
- País estrangeiro sem validador: país ISO alpha-2 válido + tipo `OUTRO` + número de 1–32
  caracteres ASCII alfanuméricos maiúsculos, sem pontuação/espaços. Sem validação fiscal.
- Histórico: país/tipo **desconhecidos**, sem atribuição BR/CNPJ por aparência.
- Coincidências normalizadas com histórico: bloquear novas entradas para
  reconciliação manual. Colisões entre legados abortam a migration informativamente
  sem imprimir documentos. Não unir, apagar ou reclassificar automaticamente.

## Contrato API/BFF

Mesmos casos de uso em `/api/v1/cedentes` e `/bff/v1/cedentes`, sem HTTP interno.
`GET /cedentes`, `GET /cedentes/{id}` e `POST /cedentes` mantidos.
Organização exclusivamente do principal JWT validado; campos extras como
`organizacaoId` são 400. Autenticação/RBAC não modificados.

POST antigo aprovado:

```json
{"nome":"Exemplo de vetor de teste","documento":"90.021.382/0001-22"}
```

POST estruturado:

```json
{"nome":"Exemplo de vetor de teste","pais":"BR","tipoDocumento":"CNPJ","documento":"12.abc.345/01de-35"}
```

POST sem validação fiscal:

```json
{"nome":"Identificador demonstrativo","pais":"US","tipoDocumento":"OUTRO","documento":"ABC123"}
```

`documento` é o campo de número em ambos os contratos; não existe alias
`numeroDocumento`. `pais`/`tipoDocumento` devem estar ambos ausentes ou ambos
presentes como strings válidas. Um só, null explícito, número JSON, país fora
da lista ISO ou tipo desconhecido são 400. País/tipo exigem caixa exata:
`BR/CNPJ`, ou país estrangeiro válido e `OUTRO`; `US/CNPJ`, `BR/OUTRO`, `EIN`,
`VAT`, `outro` não são convertidos silenciosamente em OUTRO.

Respostas preservam `id`, `nome`, `documento`, `ativo` e adicionam:

| Registro | `pais` | `tipoDocumento` | `documentoCanonico` | `statusDocumento` |
|---|---|---|---|---|
| Novo CNPJ | BR | CNPJ | número canônico | VALIDADO_DV |
| Novo OUTRO | país ISO | OUTRO | número recebido estritamente válido | NAO_VERIFICADO |
| Histórico | null | null | null | DESCONHECIDO |

Para novos registros, `documento` é canônico. Para histórico, permanece o texto
original, com zeros, espaços, máscara, caixa e conteúdo inválido preservados.
Consumidores não devem derivar país/tipo pelo tamanho ou máscara de `documento`.
Campos nullable podem ser omitidos por configuração JSON; status permanece explícito.
Respostas são aditivas; a restrição dos novos POST antigos foi expressamente
aprovada nesta sessão. Consumidores que enviavam IDs arbitrários no POST antigo
precisam enviar país/OUTRO deliberadamente, ou CNPJ válido.

- 201: entrada cadastrada; não significa cadastro ativo na Receita ou outro órgão.
- 400 / `ENTRADA_INVALIDA`: sintaxe, formato/DV, campos e país/tipo inválidos.
- 409 / `CONFLITO`: identidade canônica já existente **ou possível coincidência
  com legado**. Mensagem genérica, sem número, IDs ou dados de outro tenant.
- 401/404: contratos existentes de autenticação e isolamento mantidos.

Não há endpoint B14 de reconciliação, atribuição de identidade ou exclusão.
Erros não retornam nem registram a mensagem SQL/causa/stacktrace com documentos.

## Domínio e normalização

`:dominio` contém `DocumentoEmpresarial`, `ValidadorDocumento` (Strategy),
`ValidadorCnpj`, `ValidadorIdentificadorNaoVerificado` e `StatusDocumento`, sem
Spring/JDBC. O valor canônico se valida inclusive ao construir/copiar.
`:aplicacao` resolve identidade do tenant e valida antes de chamar portas;
`:app` adapta JSON/JDBC. Preço, HALF_EVEN, câmbio, liquidação e idempotência não
foram alterados. Não há chamadas externas/HTTP em transação.

CNPJ aceita somente:

- `[A-Za-z0-9]{12}[0-9]{2}`, ou
- máscara exata `AA.AAA.AAA/AAAA-00`, com letras/dígitos nas primeiras 12 posições.

Não há trim nem remoção genérica de pontuação para validar novo CNPJ.
Máscaras parciais/reordenadas, espaços, newline, NUL, Unicode confusável/acentos,
letras nos DVs, tamanho incorreto e sequências de 14 dígitos iguais são rejeitados.
Letras **ASCII** minúsculas são convertidas em maiúsculas antes dos DVs; nenhuma
conversão Unicode/locale é usada. Zeros à esquerda não são perdidos.
Cada DV usa ASCII menos 48, pesos 2..9 da direita, módulo 11, resto 0/1 → 0.
O segundo DV inclui o primeiro. Verificação de formato/DV não consulta situação fiscal.

OUTRO aceita somente `[A-Z0-9]{1,32}`, sem lowercase/trim/máscara/DV/validação fiscal.
País usa lista ISO da plataforma Java 21, espelhada na tabela `paises_documento`;
teste verifica igualdade. `ZZ`, `XK`, códigos antigos não atribuídos e ISO alpha-3
não são aceitos. Atualização futura da lista deve ser deliberada e testada.

**Comparação conservadora do histórico não é normalização fiscal.**
`chaveComparacaoDocumento` e `srm_chave_documento(text)` convertem apenas a–z
ASCII para A–Z e retêm apenas A–Z/0–9. Pontuação, espaços e não ASCII são
descartados **somente para detectar possível coincidência**, nunca para alterar
o documento histórico ou aprová-lo como CNPJ/OUTRO. A chave de novas entradas
canônicas é idêntica ao número. Falsos positivos históricos são deliberadamente
encaminhados a humanos. Até chaves vazias colidentes entre legados abortam o
inventário; elas não são documentos canônicos aceitos. PostgreSQL TEXT não
representa NUL; o banco o rejeita antes da função (`22021`). A API o rejeita em domínio.

## V5 e concorrência

V1–V4 inalteradas. V5:

1. Bloqueia `cedentes` durante a migration transacional para inventário estável.
2. Conta grupos de colisão conservadora por organização. Se houver, lança
   `23505` com **apenas quantidade de grupos** e orientação de reconciliação.
   Flyway/PostgreSQL fazem rollback integral; sem V5 no histórico e sem schema parcial.
3. Adiciona país/tipo/canônico nullable. Não atualiza nenhuma linha histórica.
   IDs, timestamps, nome, ativo, documento e FKs de recebíveis permanecem.
4. Substitui UNIQUE antigo `(organizacao_id, documento)` por
   `(organizacao_id, pais, tipo_documento, documento_canonico)`.
   Identidades conhecidas iguais em tenants diferentes, ou números iguais em
   países/tipos conhecidos distintos, continuam permitidos.
5. CHECKs/FK protegem campos completos ou inteiramente desconhecidos, ISO, ASCII,
   representação canônica e ambos os DVs CNPJ mesmo em SQL direto.
6. Preenche `cedentes_documentos_reservas` com a chave histórica e `legado_id`,
   **não** com identidade empresarial inferida.
7. Trigger faz UPSERT da reserva por organização/chave dentro da mesma operação
   INSERT. PostgreSQL serializa conflitos dessa chave: se existe legado,
   rejeita a nova entrada com `23505` genérico. Para conhecidos, reserva sem
   legado permite países/tipos diferentes e UNIQUE impõe a identidade completa.
   Não há SELECT-then-INSERT usado como garantia de unicidade.

Reserva e cedente são atômicos: falha de CHECK/UNIQUE faz rollback da reserva
também. O teste observa `pg_stat_activity.wait_event_type = 'Lock'` numa conexão
concorrente real: após commit de legado, novo escritor falha; após rollback,
novo escritor consegue gravar. Oito escritores canônicos têm exatamente um
sucesso, sete `23505`; oito requisições API/BFF têm um 201 e sete 409.

Trigger proíbe mudanças de organização/documento/identificação em UPDATE.
Mudanças de nome/ativo não atribuem país/tipo. Reservas históricas **não** são
apagadas ao excluir um cedente: exclusão não é reconciliação e não pode liberar
identidade ambígua. O guard histórico também bloqueia inserção SQL antiga que
coincida com um conhecido/legado; SQL sem identificação pode representar legado,
mas a aplicação B14 exige identificação validada em todo novo cadastro.

Não fazer rolling upgrade com versões antigas escrevendo após V5: elas não
implementam a política aprovada dos novos POST e poderiam criar desconhecidos.
Interromper escritores antigos na janela de manutenção. Reconciliação por DBA
é processo restrito, fora do endpoint e do escopo desta entrega.

## Operação segura e inventário

1. Planejar manutenção; parar todos os escritores e obter backup/snapshot.
2. Testar V5 numa cópia protegida do banco. Testes automatizados usam apenas
   vetores públicos/dados sintéticos em schemas e volume efêmeros.
3. Antes de V5, este inventário equivalente retorna somente tenant e IDs,
   nunca documentos/chaves. Executar em sessão administrativa restrita, sem
   exportar resultados para logs compartilhados:

```sql
WITH normalizados AS (
    SELECT id, organizacao_id,
           regexp_replace(
               translate(documento, 'abcdefghijklmnopqrstuvwxyz', 'ABCDEFGHIJKLMNOPQRSTUVWXYZ') COLLATE "C",
               '[^A-Z0-9]', '', 'g'
           ) AS chave
    FROM cedentes
)
SELECT organizacao_id, array_agg(id ORDER BY id) AS cedente_ids, count(*) AS quantidade
FROM normalizados
GROUP BY organizacao_id, chave
HAVING count(*) > 1;
```

4. Havendo grupos, **parar** e encaminhar aos responsáveis pela reconciliação,
   sem inferir país/tipo, fundir cedentes, apagar referências ou alterar
   documentos para apenas fazer a migration passar. Não executar `repair`,
   desabilitar constraints/triggers ou reescrever migrations para contornar.
   Casos de identidades distintas legítimas com a mesma chave histórica também
   bloqueiam V5; uma solução de reconciliação exige aprovação humana específica.
5. Sem grupos, aplicar V5 pelo Flyway e verificar histórico/schema/contagens/FKs.
   Só então subir backend B14 e testar consumidor API/BFF.

Logs locais PostgreSQL no Compose: `log_statement=none`,
`log_min_error_statement=panic`, `log_error_verbosity=terse`,
`log_parameter_max_length=0`, `log_parameter_max_length_on_error=0`.
Isto evita que DETAIL de UNIQUE/CHECK, statement ou bind exponha PII, preservando
mensagem principal/categoria do erro. Teste verifica configurações efetivas e
harness verifica ausência dos vetores exercitados nos logs reais do contêiner.
Essa inspeção de sentinelas é uma evidência específica, não prova universal de
redação de todos os possíveis logs. Em PostgreSQL externo, DBA deve aplicar a
mesma política e revisar extensões de auditoria/slow query, proxies e collectors.
Não ativar logging JDBC de parâmetros, payload ou TRACE/DEBUG sem redação.

## Fontes normativas e testes

Fontes consultadas diretamente, sem envio de código/credenciais:

- [Receita — documentação DV](https://www.gov.br/receitafederal/pt-br/centrais-de-conteudo/publicacoes/documentos-tecnicos/cnpj).
- [Manual PDF](https://www.gov.br/receitafederal/pt-br/centrais-de-conteudo/publicacoes/documentos-tecnicos/cnpj/manual-dv-cnpj.pdf),
  pp. 3–4: `12.ABC.345/01DE-35`.
- [Referências ZIP](https://www.gov.br/receitafederal/pt-br/centrais-de-conteudo/publicacoes/documentos-tecnicos/cnpj/codigos-cnpj.zip),
  `src/typescript/test.ts` e `src/java/TesterValidacao.java`: vetores fixos
  numéricos/alfanuméricos, incluindo zeros à esquerda e DVs zero.

`VetoresDocumento` mantém 14 valores oficiais fixos (não gerados pelo código
testado). Síntese adicional usa oráculo independente com pesos fixos: 1.000
casos em domínio cobrem restos 0..10 em ambos os DVs e 200 no PostgreSQL.
Todos os outros nove valores possíveis de **cada** DV oficial são negativos.
Formato estrito deliberadamente difere da remoção permissiva de máscaras de
alguns exemplos de referência; lowercase ASCII é aceito conforme o contrato aprovado.

Testes adicionais: política OUTRO/ISO, cópia do valor inválida, normalização
Kotlin/SQL, preservação histórica/FK, inventário/rollback, constraints em SQL,
reservas pós-exclusão, isolamento, concorrência real e contratos API/BFF.
Fixtures financeiras que antes enviavam UUID inválido pelo POST antigo agora
enviam explicitamente `US/OUTRO`, sem inventar CNPJ fiscal. Golden cases C1–C3,
simulação, extrato, autenticação e liquidação permanecem na suíte.

## Riscos e limites deliberados

- Restrição de novos POST antigos exige adaptação consciente de consumidores.
- Reconciliação humana não implementada; V5 deve falhar se o histórico for ambíguo.
- Normalização histórica conservadora pode gerar falsos positivos (inclusive
  Unicode/pontuação); retenção de reservas exige manutenção autorizada futura.
- UPSERT gera escrita/contensão por chave e reservas crescem com chaves distintas.
  Não foi realizado benchmark em volume grande; DDL exige janela de manutenção.
- Não há validação fiscal internacional nem consulta de situação CNPJ.
- Homologação humana e validação ponta a ponta manual permanecem pendentes.
- Ambiente Compose não é produção; políticas externas de log exigem confirmação
  do operador. Warnings Gradle 10/Unsafe da ferramenta preexistem; não corrigidos
  por B14 nem tratados como falha financeira.
