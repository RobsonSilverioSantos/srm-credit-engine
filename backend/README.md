# Backend — SRM Credit Engine

Pré-requisitos: JDK 21 para o Gradle local, Docker Engine com Compose v2 ou
superior para PostgreSQL e `openssl` para gerar a senha. Na primeira execução,
o Wrapper baixa o Gradle e as dependências. Execute os comandos em `backend/`.

## Configuração local

Crie o `.env` **uma única vez**. Ele é ignorado pelo Git e não entra na imagem:

```sh
(umask 077; set -C; printf 'SRM_DB_USER=srm_local\nSRM_DB_PASSWORD=%s\n' "$(openssl rand -hex 32)" > .env)
```

Portas locais padrão: PostgreSQL `5432` e HTTP `8080`, ambas em loopback.
Em caso de conflito, defina `SRM_DB_PORT` e/ou `SRM_APP_PORT` no `.env`.
Use um projeto Compose distinto (`docker compose -p <nome> ...`) se precisar
executar mais de um ambiente ao mesmo tempo.

## Executar

**Tudo em contêineres** (não requer JDK no host):

```sh
docker compose --profile app up -d --wait --build
curl -fsS http://127.0.0.1:8080/actuator/health
```

**Com depuração local** (em vez do perfil `app`):

```sh
docker compose up -d --wait postgres
./gradlew clean build
(
  set -a
  . ./.env
  set +a
  export SPRING_DATASOURCE_USERNAME="$SRM_DB_USER"
  export SPRING_DATASOURCE_PASSWORD="$SRM_DB_PASSWORD"
  export SPRING_DATASOURCE_URL="jdbc:postgresql://127.0.0.1:${SRM_DB_PORT:-5432}/srm_credit_engine"
  export SERVER_PORT="${SRM_APP_PORT:-8080}"
  ./gradlew :app:bootRun
)
```

No PowerShell/Windows, configure as mesmas variáveis no processo e use `.\gradlew.bat`.
Ajuste a URL do `curl` se mudou a porta e aguarde a inicialização do backend.
`GET /actuator/health` retorna `200` e `{"status":"UP"}` quando o backend
consegue consultar o banco; se o banco ficar indisponível, retorna `503` e
`{"status":"DOWN"}`. Nenhum detalhe do banco nem outro endpoint Actuator é exposto.

Use `docker compose --profile app logs app postgres` para diagnóstico e
`docker compose --profile app down` para parar o ambiente sem apagar os dados.
As credenciais inicializam o volume em seu primeiro uso; alterar o `.env` não
altera a senha do banco existente. Não reutilize essas credenciais em outros
ambientes. A configuração Compose não é destinada à produção.

## Persistência e migrations

O adaptador `app` executa Flyway na inicialização; os arquivos versionados ficam
em `app/src/main/resources/db/migration`. Não edite uma versão já aplicada:
adicione uma nova migration para evoluir o esquema.

- Valores monetários e snapshots de BRL usam `NUMERIC(19, 2)` (até 17 dígitos
  inteiros e centavos). Taxas mensais, armazenadas como frações, usam
  `NUMERIC(12, 10)`; cotações BRL por USD usam `NUMERIC(20, 10)`. Tipos
  decimais evitam erro de representação binária; o arredondamento HALF_EVEN
  permanece responsabilidade do caso de uso.
- Modalidades persistidas usam os identificadores do domínio: `DUPLICATA_MERCANTIL`
  e `CHEQUE_PRE_DATADO`. Constraints de recebíveis, spreads e liquidações mantêm
  o mesmo conjunto permitido.
- UUIDs são fornecidos pela aplicação. Instantes usam `TIMESTAMPTZ`; `vigente_em`
  registra quando uma taxa passa a valer e `criado_em` quando foi gravada.
- O e-mail do usuário é único sem distinção de caixa globalmente, para que o
  login local identifique uma conta sem ambiguidade; associação de uma mesma
  identidade a múltiplas organizações exigiria separar identidade e associação.
- Cedente, recebível, liquidação, auditoria e replay usam FKs compostas com
  `organizacao_id` onde há escopo de tenant. Assim, um ID válido de outro tenant
  não permite formar uma relação cruzada. A FK da liquidação também vincula
  modalidade, valor de face e prazo ao recebível original, protegendo o snapshot
  contra divergência e impedindo alterar esses dados depois de liquidado. As FKs
  não substituem a autorização baseada na identidade autenticada.
- A coluna `recebiveis.versao BIGINT` é o campo para o `@Version` futuro; o
  conflito otimista continua precisando ser tratado pela aplicação. UNIQUE por
  `(organizacao_id, recebivel_id)` impede segunda liquidação mesmo diante de
  concorrência. A chave idempotente é única por organização e tipo de operação;
  hash SHA-256 e resposta JSON/status suportam detectar payload divergente e
  reproduzir o resultado. Reserva, liquidação e auditoria devem ser concluídas
  na mesma transação.
- A liquidação guarda os valores usados, IDs e cópias das taxas, cotações,
  instantes de vigência, corte de liquidação, validade da cotação e a versão do
  recebível. Triggers rejeitam UPDATE/DELETE/TRUNCATE em liquidações, auditoria e
  histórico de taxas; idempotência só permite a transição inicial para conclusão,
  que também não pode ser reescrita. Para corrigir o estado no futuro, é necessário
  um evento compensatório, não reescrever o registro.
- Índices de recebíveis atendem filtros por tenant/status/vencimento e joins por
  cedente; os de liquidação suportam intervalo temporal e moeda, com `id` como
  desempate estável. Índices únicos apoiam login por e-mail, unicidade do
  documento do cedente, seleção temporal das taxas e buscas pelas FKs. Não foi
  duplicado índice cambial: a chave única `(moeda_base, moeda_cotada, vigente_em)`
  também permite buscar a cotação mais recente por par. Índices não dispensam
  conferir planos com volume representativo.

Para validar as migrations em PostgreSQL limpo e executar testes de FKs,
unicidade, isolamento, valores inválidos e imutabilidade, use:

```sh
./scripts/verify-persistence.sh
```

O script sobe um projeto Compose exclusivo com portas efêmeras, aguarda a
aplicação aplicar as migrations, executa
`app/src/test/resources/db/postgresql-integrity.sql` e remove apenas o volume
temporário desse projeto ao terminar.

## Fronteiras e verificação

Um processo Spring Boot e um artefato executável (`app/build/libs/srm-credit-engine.jar`).
Organize código por capacidade de negócio dentro de cada projeto, seguindo
`br.com.srm.creditengine.<capacidade>`:

| Projeto Gradle | Código que deve morar nele |
|---|---|
| `dominio` | `<capacidade>.dominio`: regras e tipos de negócio, sem Spring, Hibernate ou JPA. |
| `aplicacao` | `<capacidade>.aplicacao`: casos de uso; portas de entrada/saída em `.porta.entrada` e `.porta.saida`. Só depende do domínio. |
| `app` | `<capacidade>.adaptador.entrada` (REST/BFF) e `.saida` (persistência/integrações); composição Spring. Depende de aplicação e domínio, não o contrário. |

O healthcheck usa os adaptadores HTTP/JDBC configurados pelo Spring Boot.

`./gradlew clean build` executa o smoke test HTTP (sem PostgreSQL) e as
verificações `:dominio:verificarFronteiraHexagonal` e
`:aplicacao:verificarFronteiraHexagonal`. Elas validam os artefatos resolvidos nos
classpaths de compilação e execução do núcleo. O teste HTTP não substitui a
checagem com PostgreSQL real pelos comandos abaixo.

## Cadastro e isolamento

Os comandos de cadastro não recebem organização. `ServicoCadastro` obtém a
organização de `ContextoOrganizacao` uma vez por operação; consultas e inserções
usam esse escopo, e a FK composta do banco impede vínculos cruzados mesmo se
o cedente desaparecer ou mudar entre a consulta e a inserção. A unicidade de
documento é aplicada por organização pela constraint existente; violações de
integridade são propagadas, não convertidas em sucesso. Valores de face devem
ser positivos, representáveis em centavos e caber em `NUMERIC(19,2)`.

Não há implementação de identidade para uso público nesta etapa: o bean de
cadastro só é criado quando uma implementação confiável de `ContextoOrganizacao`
for fornecida. S01 deve fornecer essa implementação a partir da identidade
validada; a implementação mutável usada nos testes não é autenticação. Não
existem endpoints de cadastro enquanto S01/B08 não forem implementadas.

Para criar somente uma organização demonstrativa, em banco de desenvolvimento,
inicie a aplicação com o perfil `demo` **e** a propriedade
`srm.demo.bootstrap-organizacao=true` (por exemplo, variáveis
`SPRING_PROFILES_ACTIVE=demo` e `SRM_DEMO_BOOTSTRAP_ORGANIZACAO=true`).
O bootstrap é idempotente por ID, não cria usuário, credencial ou contexto de
identidade e fica desabilitado por padrão. O ID demonstrativo é
`00000000-0000-4000-8000-000000000001`.

Execute os testes de integração reais e a regressão completa com:

```sh
bash scripts/test-cadastro-postgres.sh
```

O script cria um projeto Compose e banco efêmeros, executa `./gradlew clean build`
com `SRM_TEST_POSTGRES=true` e remove o projeto e seu volume ao terminar. Sem
essa variável, os testes de PostgreSQL são explicitamente ignorados; o build
local isolado não comprova integração. O CI backend usa o mesmo script.

## Precificação

O domínio oferece `MotorPrecificacao`, com estratégias para duplicata mercantil
e cheque pré-datado. Taxa base e spreads são frações decimais mensais; o prazo
é um inteiro positivo e o valor de face deve ser positivo e representável em
centavos. A taxa base deve ser não negativa. A potência é calculada sem
arredondamento intermediário e o VP em BRL é arredondado para duas casas com
HALF_EVEN; o deságio é a diferença entre face e VP. Quando informada, a cotação
positiva em BRL por USD converte o VP BRL já arredondado, também com HALF_EVEN
em duas casas. A ausência da cotação representa cálculo somente em BRL.

O motor aceita uma lista de estratégias com modalidades únicas para extensão,
sem dependência de framework ou persistência. A seleção de taxas e cotações
vigentes não faz parte deste cálculo; elas são fornecidas pelo chamador. Os
testes unitários da precificação ficam em `app`, usando o JUnit já configurado.
