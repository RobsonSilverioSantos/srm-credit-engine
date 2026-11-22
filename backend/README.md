# Backend — SRM Credit Engine

> **Execução multiplataforma do sistema completo:** consulte o [`README.md` principal](../README.md). O script inicia PostgreSQL, backend e frontend via Docker Compose em Linux, macOS e Windows; o provisionamento MASTER no contêiner dispensa `gradlew bootRun` e evita erro de `System.console()`.

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
`{"status":"DOWN"}`. O endpoint de health é público e não revela detalhes do
banco. As métricas ficam em `/actuator/metrics` e exigem autenticação JWT; os
demais endpoints Actuator não são expostos.

O caso de uso de liquidação registra `srm.liquidacoes.confirmadas` somente
depois do commit, `srm.liquidacoes.replays` para respostas idempotentes e
`srm.liquidacoes.duracao` para a duração total das tentativas. Essas métricas não
possuem labels. Cada requisição recebe um `X-Correlation-Id` gerado pelo servidor,
incluído no log JSON e na resposta; credenciais, chaves idempotentes, IDs de
entidades e payloads não são registrados.

Use `docker compose --profile app logs app postgres` para diagnóstico e
`docker compose --profile app down` para parar o ambiente sem apagar os dados.
As credenciais inicializam o volume em seu primeiro uso; alterar o `.env` não
altera a senha do banco existente. Não reutilize essas credenciais em outros
ambientes. A configuração Compose não é destinada à produção.

## Persistência e migrations

### B14 — documentos empresariais

Veja [contratos, migração segura e evidências B14](docs/B14_DOCUMENTOS_CEDENTES.md).
`documento` continua no POST/GET; `pais` e `tipoDocumento` são opcionais **juntos**.
POST sem ambos agora significa BR/CNPJ e exige dois DVs válidos. Isto não
reclassifica histórico: registros existentes continuam `DESCONHECIDO`, sem país,
tipo ou canônico. Para identificador sem validador fiscal, envie país estrangeiro
ISO alpha-2 válido, `tipoDocumento: "OUTRO"` e 1–32 caracteres ASCII `[A-Z0-9]`; resposta
`NAO_VERIFICADO`, sem confirmação fiscal. CNPJ validado apenas em formato/DV
retorna `VALIDADO_DV`, não prova de situação cadastral.

V5 aborta atomicamente diante de colisões normalizadas históricas e bloqueia
novas possíveis coincidências para reconciliação humana, sem fusão de cedentes.
Planeje janela de manutenção: a migration bloqueia a tabela para DDL/inventário.
O Compose limita logs PostgreSQL para não expor statements, parâmetros ou
detalhes de erros com documentos. Bancos externos precisam da mesma política;
não habilite logging de SQL/payload em DEBUG/TRACE.

Verificação real, banco isolado e cleanup automático (script sem bit executável):

```sh
bash scripts/test-cadastro-postgres.sh
bash scripts/test-cadastro-postgres.sh clean check
```

O primeiro executa `./gradlew clean build` (inclui checks/testes e bootJar);
o segundo executa exatamente `./gradlew clean check`. Sem esse harness ou
`SRM_TEST_POSTGRES=true` com credenciais reais, os testes PostgreSQL são
ignorados e um build verde **não** comprova as garantias B14.

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
- A coluna `recebiveis.versao BIGINT` é usada no controle otimista JDBC. UNIQUE por
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

O `check` também executa `verificarTiposKotlin`: o parser do compilador Kotlin
já usado pelo projeto identifica declarações top-level de produção nos três
módulos. Cada arquivo pode conter no máximo um tipo nomeado, com nome igual ao
arquivo; tipos aninhados não são contados. Arquivos sem tipos, como os de
funções top-level, são permitidos. A verificação não avalia se tipos foram
aninhados artificialmente nem se o nome de um arquivo só de funções é descritivo.
É necessário um JDK completo para compilar o verificador.

Na B13, a porta Kotlin `LiquidarRecebivel` passou a retornar
`ResultadoOperacaoLiquidacao`, que contém o resultado de domínio e metadado
opaco de replay; consumidores Kotlin precisam adaptar a assinatura. A mudança
retira HTTP do domínio sem alterar os contratos API/BFF. O adaptador JDBC
continua reconstruindo os campos conhecidos do JSON histórico: campos adicionais
ou representações alternativas não são preservados literalmente, limitação
preexistente do replay.

## Cadastro e isolamento

Os comandos de cadastro não recebem organização. `ServicoCadastro` obtém a
organização de `ContextoOrganizacao` uma vez por operação; consultas e inserções
usam esse escopo, e a FK composta do banco impede vínculos cruzados mesmo se
o cedente desaparecer ou mudar entre a consulta e a inserção. A unicidade de
documento é aplicada por organização pela constraint existente; violações de
integridade são propagadas, não convertidas em sucesso. Valores de face devem
ser positivos, representáveis em centavos e caber em `NUMERIC(19,2)`.

O bean de cadastro só é criado quando há persistência e uma implementação
confiável de `ContextoOrganizacao`. S01 fornece essa implementação a partir da
identidade autenticada e validada; a implementação mutável usada em testes
continua sendo apenas uma fixture.

Para criar somente uma organização demonstrativa, em banco de desenvolvimento,
inicie a aplicação com o perfil `demo` **e** a propriedade
`srm.demo.bootstrap-organizacao=true` (por exemplo, variáveis
`SPRING_PROFILES_ACTIVE=demo` e `SRM_DEMO_BOOTSTRAP_ORGANIZACAO=true`).
O bootstrap é idempotente por ID, não cria usuário, credencial ou contexto de
identidade e fica desabilitado por padrão. O ID demonstrativo é
`00000000-0000-4000-8000-000000000001`.

## Autenticação e gestão de usuários

A aplicação autentica pelo `POST /api/v1/auth/token`, usando BCrypt e tokens
bearer HS256 de curta duração. Cada requisição protegida valida assinatura,
algoritmo fixo, emissor, expiração, usuário e organização; também confirma no
PostgreSQL que o usuário e a organização continuam ativos. Portanto, um token
emitido antes da inativação do usuário passa a receber `401`.

Configure um segredo novo fora do repositório antes de iniciar a aplicação. A
ausência de segredo ou um valor menor que 256 bits impede a inicialização.
Nunca reutilize segredo de teste ou o inclua no Git:

```sh
export SRM_JWT_SECRET="$(openssl rand -hex 32)"
read -r -p "Database user: " SRM_DB_USER
read -r -s -p "Database password: " SRM_DB_PASSWORD; printf '\n'
export SRM_DB_USER SRM_DB_PASSWORD
docker compose up --profile app --detach --wait
```

O primeiro MASTER precisa ser provisionado explicitamente, antes do uso da API
administrativa. Inicie a stack Docker de demonstração pelo procedimento do
[README principal](../README.md); esse modo cria a organização demonstrativa,
mas não cria nenhum usuário. Na **raiz do repositório**, em terminal interativo:

```sh
sh scripts/bootstrap-master.sh
```

No Windows PowerShell, execute `.\scripts\bootstrap-master.ps1`. O atalho
legado `backend/scripts/bootstrap-master.sh` também utiliza o launcher Docker
interativo; o `gradlew bootRun` não fornece `System.console()` neste caso.
O provisionamento utiliza a configuração existente em `backend/.env`, incluindo
as credenciais do PostgreSQL e o mesmo segredo JWT usado pelo backend.

O procedimento solicita UUID da organização, e-mail, senha e confirmação.
UUID/e-mail inválidos, senha fora de **12 a 72 bytes UTF-8** e confirmação
divergente são informados no próprio terminal e permitem corrigir a entrada
sem reiniciar a aplicação. A senha não é exibida, não é passada em argumentos
e os arrays utilizados na leitura são limpos após cada tentativa.
A confirmação de sucesso é `MASTER provisionado.`. Só depois dessa mensagem
o usuário está apto a autenticar na interface principal.

O banco rejeita um segundo MASTER na mesma organização, inclusive após
inativação; o usuário MASTER nunca pode ser criado pela API/BFF. O provisionamento
não é executado automaticamente na inicialização normal. Em caso de erro de
conexão ou MASTER já existente, deve-se diagnosticar a causa; não apagar o
volume PostgreSQL nem recriar as credenciais para tentar novamente.

Depois do bootstrap, `MASTER` cria `ADMIN` ou `OPERADOR`, e `ADMIN` cria somente
`OPERADOR`. `GET /api/v1/usuarios/me` retorna a organização, o perfil e as
capacidades do principal autenticado. `GET /usuarios/me`, `GET /usuarios`,
`POST /usuarios` e `PATCH /usuarios/{id}/status` estão disponíveis sob ambos
os prefixos `/api/v1` e `/bff/v1` e compartilham os mesmos casos de uso.
Listagem e alteração de status são restritas aos perfis e à organização do
usuário autenticado. As respostas nunca incluem senha, hash ou token; senhas
recebidas pela API devem ter de 12 a 72 bytes UTF-8.

Os perfis de autorização são consultados no PostgreSQL em cada operação
administrativa, não aceitos de campos de entrada nem confiados a claims de
perfil do token. A migration V4 atribui `OPERADOR` aos usuários existentes;
nenhuma migration V1–V3 é alterada.

Execute os testes de integração reais e a regressão completa com:

```sh
bash scripts/test-cadastro-postgres.sh
```

O script cria um projeto Compose e banco efêmeros, executa `./gradlew clean build`
com `SRM_TEST_POSTGRES=true` e remove o projeto e seu volume ao terminar. Sem
essa variável, os testes de PostgreSQL são explicitamente ignorados; o build
local isolado não comprova integração. O CI backend usa o mesmo script.

## Extrato de liquidações

`GET /api/v1/extrato` e `GET /bff/v1/extrato` exigem JWT e usam a organização
da identidade autenticada. Os filtros opcionais são `inicio` e `fim` (datas
`AAAA-MM-DD` inclusivas em UTC), `cedenteId` (UUID) e `moeda` (`BRL` ou `USD`).
`pagina` começa em zero (padrão 0) e `tamanho` varia de 1 a 100 (padrão 20).
Filtros inválidos retornam 400. A resposta contém `itens`, `pagina`, `tamanho`
e `total`, ordenados por `liquidadaEm` e `liquidacaoId` decrescentes.

Os valores e taxas são strings decimais lidas dos snapshots de liquidação;
os campos cambiais são nulos para BRL. O ID do cedente é obtido do recebível
vinculado. `cedenteNome` contém o nome atual do cadastro, não um nome
histórico congelado na liquidação. Cada resposta calcula itens e total no mesmo snapshot do banco;
inserções entre requisições podem deslocar itens entre páginas.

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

## Histórico de taxas e câmbio

`TaxasPorta` permite registrar manualmente versões de taxa base, spread por
modalidade e cotação BRL por USD, e consultar os dados para um snapshot.
Não há endpoint HTTP de manutenção financeira. Em execução independente,
o histórico precisa ser provisionado por operação controlada fora do navegador,
com acesso restrito à infraestrutura de demonstração.

**Exceção explícita — stack Docker local:** o overlay `compose.full.yaml`
ativa o perfil `demo` e `SRM_DEMO_BOOTSTRAP_TAXAS=true`. O bootstrap
`BootstrapTaxasDemo` cria somente os valores ausentes entre os registros
vigentes: base `0.0100000000`, spread de duplicata `0.0150000000` e de
cheque pré-datado `0.0250000000`. Também cria cotação fictícia BRL/USD
`5.4321000000` (golden case C3) se não houver cotação válida. O câmbio
de demonstração expira em 30 minutos por padrão, não se renova durante a
execução e pode ganhar uma nova versão fictícia na próxima inicialização.
O processo opera dentro de uma transação com trava de inicialização para
evitar duplicações concorrentes; nunca altera ou elimina registros antigos.
Sem perfil `demo` **e** a propriedade explícita, nada é carregado.
Não utilizar essas taxas fictícias em produção.
A escala máxima é 10 casas, sem arredondamento na gravação; vigências aceitam
precisão de microssegundos, como no PostgreSQL.

Uma consulta captura um único instante UTC e busca as três versões em uma única
instrução SQL: seleciona a maior vigência não posterior ao corte entre registros
já criados nesse corte. A cotação é exigida somente para USD; sua validade é
`srm.cambio.validade` (duração ISO-8601, positiva e com precisão de
microssegundos, padrão `PT30M`), inclusive no limite.
Ausência, cotação expirada ou dados inválidos geram erro, sem fallback. O resultado
inclui IDs, valores, vigências, datas de criação, referência e validade cambial;
ele não persiste uma liquidação. As estratégias padrão B03 mantêm seus spreads
fixos; a liquidação usa uma estratégia com o spread da versão selecionada.

Para executar o teste de integração com PostgreSQL isolado e Flyway:

```sh
./scripts/verify-taxas-postgres.sh
```

O script cria e remove apenas seu projeto Compose e volume temporário. O build
normal não executa esse teste sem `SRM_TEST_POSTGRES=true` e um banco configurado.

### Resiliência da consulta cambial

A B05 não possui fornecedor externo mockado: o câmbio demonstrativo é
registrado no histórico PostgreSQL. Por escolha explícita, a leitura JDBC que
inclui câmbio tem timeout por tentativa (`srm.cambio.consulta.timeout`,
padrão `PT1S`, de 1 ms a 30 s), até `srm.cambio.consulta.tentativas`
(padrão 3, incluindo a primeira, de 1 a 5) e atraso fixo entre tentativas
(`srm.cambio.consulta.atraso`, padrão `PT0.1S`, de 0 a 5 s). Durações devem
ter precisão de milissegundos. Configurações inválidas impedem a inicialização.

Só timeout e `TransientDataAccessException` são elegíveis para repetição;
falhas permanentes, cotação ausente ou vencida não são repetidas. O atraso
ocorre fora da thread JDBC; a mesma referência UTC é usada em todas as tentativas.
Escritas nunca são repetidas, e a consulta somente em BRL não é envolvida.
A consulta cambial é recusada se houver transação ativa. A liquidação busca o
snapshot antes da transação e verifica novamente a validade cambial ao entrar e
após a atualização otimista.

O timeout limita a espera do chamador, não garante que o driver PostgreSQL
interrompa imediatamente uma operação. Há no máximo quatro leituras em execução
e quatro aguardando; o timeout inclui o tempo na fila. Capacidade esgotada
falha explicitamente sem retry e tarefas expiradas são removidas da fila. Uma tentativa
expirada ainda pode ocupar conexão enquanto termina, inclusive em paralelo com
outra tentativa, mas apenas leituras são submetidas. É necessário configurar
limites de conexão/rede do banco para conter operações que ignorem interrupção.
Cada tentativa faz uma nova leitura: um registro criado até o corte, mas
confirmado entre tentativas, pode aparecer apenas na última. O snapshot é o
resultado da tentativa bem-sucedida, não uma visão compartilhada entre retries.
Não há cache, fallback nem fonte de cotação inventada.

## Liquidação

`LiquidarRecebivel` recebe UUID do recebível, moeda e chave idempotente; não
recebe organização ou usuário. A identidade vem do principal validado da S01.
O resultado contém os valores decimais, moeda, instante e status 201.

O hash SHA-256 usa a sequência UTF-8 `LIQUIDAR_RECEBIVEL`, UUID normalizado e
moeda, separados por quebras de linha: é derivado do comando tipado, não da
ordem das propriedades JSON. Alterações futuras no contrato devem considerar
compatibilidade com hashes e respostas já persistidos. A mesma chave e comando
reproduzem o corpo e status registrados, mesmo quando as taxas já expiraram;
uma chave com comando diferente causa `ConflitoLiquidacao`.

A reserva usa a unicidade do PostgreSQL, que aguarda uma transação concorrente
com a mesma chave. Reserva, atualização, snapshot, auditoria e conclusão
ocorrem na mesma transação; falhas desfazem tudo. A atualização condicional por
organização, status e versão incrementa a versão somente se a versão lida ainda
for válida; zero linhas é conflito. É equivalente ao controle de versão
otimista para esta operação JDBC, mas **não usa literalmente `@Version` JPA**,
distinguindo-se da anotação JPA `@Version` citada na ADR-010. A constraint
UNIQUE por recebível é defesa adicional, não substituto da versão.

O instante truncado a microssegundos é o corte UTC da seleção e o
`liquidada_em` do snapshot. Câmbio resiliente é consultado fora da transação.
O relógio é verificado antes e depois do UPDATE, inclusive após eventual
espera por concorrência; cotação expirada provoca rollback. O tempo entre a
última verificação e o commit não é limitado: o instante registrado é o corte
da decisão, não o horário físico do commit. Os testes de concorrência e falhas
da B07 verificam a semântica implementada, mas não comprovam validade cambial
até o instante físico do commit. Essa garantia não integra o contrato atual.

Os testes PostgreSQL da B06 integram o comando `bash scripts/test-cadastro-postgres.sh`.

## Contratos HTTP API e BFF

Os dois prefixos `/api/v1` e `/bff/v1` oferecem os mesmos contratos de simulação,
cadastro, consulta e liquidação. O BFF é uma fronteira de entrada para a interface, não
um cliente HTTP interno; ambos chamam os mesmos casos de uso. A
organização e o usuário vêm exclusivamente do JWT validado pela S01. O corpo
não aceita campos adicionais de identidade.

| Método | Caminho após o prefixo | Finalidade |
|---|---|---|
| POST | `/simulacoes` | Prévia indicativa antes do cadastro |
| POST | `/cedentes` | Cadastrar cedente |
| GET | `/cedentes` | Listar cedentes da organização |
| GET | `/cedentes/{id}` | Consultar cedente da organização |
| POST | `/recebiveis` | Cadastrar recebível |
| GET | `/recebiveis` | Listar recebíveis da organização |
| GET | `/recebiveis/{id}` | Consultar recebível da organização |
| POST | `/recebiveis/{id}/liquidacoes` | Liquidar recebível previamente cadastrado |

Criação e liquidação retornam `201`. O header `Idempotency-Key` é obrigatório
na liquidação e deve ser mantido para retentativas da mesma intenção; a mesma
chave e comando devolvem o corpo e status persistidos, inclusive entre API e
BFF. Outro comando com a mesma chave e uma segunda liquidação com outra chave
retornam `409`. Valores financeiros são strings decimais, sem separadores de
milhar, e instantes são UTC ISO-8601. O `valorFaceBrl` é positivo, com no
máximo 17 dígitos inteiros e duas casas decimais. Modalidades:
`DUPLICATA_MERCANTIL` e `CHEQUE_PRE_DATADO`; moedas: `BRL` e `USD`.

A simulação recebe, por exemplo,
`{"modalidade":"DUPLICATA_MERCANTIL","valorFaceBrl":"100000.00","prazoMeses":3,"moeda":"USD"}`.
Retorna `200` com VP e deságio em BRL, valor de pagamento na moeda escolhida,
taxa base, spread, cotação opcional, instante de referência e validade cambial
quando aplicável. Não recebe ID de recebível, cedente ou chave de idempotência,
nem grava dados. A prévia não reserva preço ou câmbio; a liquidação consulta
novamente os dados elegíveis. Taxas são frações decimais em strings.

Erros de domínio/entrada seguem `{"codigo":"...","mensagem":"..."}`:
`ENTRADA_INVALIDA` (400), `NAO_AUTENTICADO` (401), `RECURSO_NAO_ENCONTRADO`
(404, inclusive acesso entre organizações), `CONFLITO` (409) e
`SERVICO_INDISPONIVEL` (503). Falhas inesperadas recebem `ERRO_INTERNO` (500)
sem detalhes internos. Falta de dados financeiros, cotação expirada e
indisponibilidade transitória são 503: não há preço inventado ou fallback.

OpenAPI JSON está em `/v3/api-docs` e Swagger UI em `/swagger-ui/index.html`.
A documentação é pública; os endpoints de negócio continuam protegidos. No
Swagger, informe o token JWT no botão **Authorize**. O extrato está disponível
nos dois prefixos, conforme a seção **Extrato de liquidações**. O springdoc
3.0.3 é da linha para Spring Boot 4.0; a geração dos dois prefixos é
verificada no teste HTTP real.

**Manutenção global:** a S01 não define privilégios administrativos globais.
Não há endpoint HTTP para criar taxas, spreads ou cotações; autenticação de
usuário organizacional não confere tal privilégio. Os registros continuam
disponíveis pela porta interna B05 para operação controlada fora do navegador,
com acesso restrito ao PostgreSQL. A atualização manual de taxas e cotações
não está disponível pelo navegador neste MVP; as operações demonstrativas
dependem do provisionamento controlado do histórico financeiro.

### Fluxo de exemplo com curl

Inicie o PostgreSQL e a aplicação conforme **Configuração local**, **Executar**
e **Autenticação e gestão de usuários** acima, incluindo segredo JWT e usuário provisionado.
Para o exemplo USD, o operador precisa previamente registrar taxa base,
spread e cotação vigentes pela operação administrativa controlada da B05;
sem eles a liquidação retorna 503. Os comandos abaixo exigem `jq` e usam o
mesmo token para os dois prefixos:

Em um banco **descartável de demonstração**, um operador com acesso restrito
pode preparar os dados de exemplo abaixo usando `psql "$DATABASE_URL"`.
Isto grava diretamente no banco, não utiliza a porta B05 nem implementa uma
ferramenta administrativa auditada; não é procedimento de produção. Os
registros são globais e imutáveis e a cotação expira após 30 minutos.

```sql
BEGIN;
INSERT INTO taxas_base (id, taxa_mensal, vigente_em)
VALUES (gen_random_uuid(), 0.01, CURRENT_TIMESTAMP);
INSERT INTO spreads_recebiveis (id, modalidade, spread_mensal, vigente_em)
VALUES (gen_random_uuid(), 'DUPLICATA_MERCANTIL', 0.015, CURRENT_TIMESTAMP),
       (gen_random_uuid(), 'CHEQUE_PRE_DATADO', 0.025, CURRENT_TIMESTAMP);
INSERT INTO cotacoes_cambiais (id, taxa_brl_por_unidade, vigente_em)
VALUES (gen_random_uuid(), 5.4321, CURRENT_TIMESTAMP);
COMMIT;
```

```sh
read -r -p "Email: " EMAIL
read -r -s -p "Senha: " SENHA; printf '\n'
TOKEN="$(jq -n --arg email "$EMAIL" --arg password "$SENHA" \
  '{email:$email,password:$password}' | \
  curl -fsS -H 'Content-Type: application/json' --data-binary @- \
    http://127.0.0.1:8080/api/v1/auth/token | jq -er .accessToken)"
unset SENHA
CEDENTE="$(curl -fsS -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"nome":"Cedente exemplo","documento":"12ABC34501DE35"}' \
  http://127.0.0.1:8080/api/v1/cedentes | jq -er .id)"
RECEBIVEL="$(jq -n --arg cedente "$CEDENTE" \
  '{cedenteId:$cedente,modalidade:"DUPLICATA_MERCANTIL",valorFaceBrl:"100000.00",prazoMeses:3}' | \
  curl -fsS -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
    --data-binary @- http://127.0.0.1:8080/bff/v1/recebiveis | jq -er .id)"
CHAVE="$(cat /proc/sys/kernel/random/uuid)"
curl -fsS -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -H "Idempotency-Key: $CHAVE" -d '{"moeda":"USD"}' \
  "http://127.0.0.1:8080/bff/v1/recebiveis/$RECEBIVEL/liquidacoes"
# Repita a última requisição com a mesma chave para obter o mesmo resultado.
unset TOKEN
```
