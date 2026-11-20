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
continua sendo apenas uma fixture. Não existem endpoints de cadastro até B08.

Para criar somente uma organização demonstrativa, em banco de desenvolvimento,
inicie a aplicação com o perfil `demo` **e** a propriedade
`srm.demo.bootstrap-organizacao=true` (por exemplo, variáveis
`SPRING_PROFILES_ACTIVE=demo` e `SRM_DEMO_BOOTSTRAP_ORGANIZACAO=true`).
O bootstrap é idempotente por ID, não cria usuário, credencial ou contexto de
identidade e fica desabilitado por padrão. O ID demonstrativo é
`00000000-0000-4000-8000-000000000001`.

## Autenticação local e usuários de teste/demonstração

A aplicação expõe somente `POST /api/v1/auth/token` para autenticação local.
Ela carrega usuários e organizações ativas do PostgreSQL, verifica senhas com
BCrypt e emite tokens bearer HS256 de curta duração. Cada requisição protegida
valida assinatura, algoritmo fixo, emissor, expiração, usuário e organização;
também confirma que o usuário continua ativo e vinculado àquela organização
ativa. `ContextoOrganizacao` lê o escopo do principal validado da requisição.
Não existe endpoint público para criar usuários.

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

Para criar um usuário de demonstração, crie primeiro a organização com o
bootstrap opt-in descrito acima e, então, use uma conexão PostgreSQL de
operador. `DATABASE_URL` deve apontar para o banco PostgreSQL de demonstração;
a organização informada deve existir e estar ativa. A role precisa de
`CONNECT`, `USAGE` no schema e `INSERT` em `usuarios`. O exemplo solicita a
senha sem exibi-la, gera localmente um hash BCrypt com `htpasswd` e envia
somente o hash ao banco. O operador escolhe o ID da organização e o e-mail;
não há credenciais fixas:

```sh
set -euo pipefail
: "${DATABASE_URL:?Set DATABASE_URL to the demonstration PostgreSQL database}"
trap 'unset SENHA HASH' EXIT
read -r -p "Organization UUID: " ORGANIZACAO_ID
read -r -p "Email: " EMAIL
read -r -s -p "Password: " SENHA; printf '\n'
HASH="$(printf '%s\n' "$SENHA" | htpasswd -nBi -C 12 "$EMAIL" | cut -d: -f2-)"
unset SENHA
psql "$DATABASE_URL" -v ON_ERROR_STOP=1 \
  -v organizacao_id="$ORGANIZACAO_ID" -v email="$EMAIL" -v senha_hash="$HASH" \
  <<'SQL'
INSERT INTO usuarios (id, organizacao_id, email, senha_hash)
VALUES (gen_random_uuid(), :'organizacao_id'::uuid, :'email', :'senha_hash');
SQL
unset HASH
```

Instale o utilitário Apache `htpasswd` se necessário. O heredoc protegido evita
expansão pelo Bash; o próprio cliente `psql` interpola as variáveis como
literais SQL, com `organizacao_id` convertida explicitamente para `UUID`.
Testes de integração criam usuários e senhas aleatórios em cada execução e
persistem somente hashes BCrypt.

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

## Histórico de taxas e câmbio

`TaxasPorta` permite registrar manualmente versões de taxa base, spread por
modalidade e cotação BRL por USD, e consultar os dados para um snapshot.
Não há endpoint de manutenção nem carga automática de valores: o chamador deve
registrar os dados e, futuramente, garantir autorização. Registros existentes
nunca são atualizados; vigências futuras podem ser cadastradas antecipadamente.
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
O resultado contém os valores decimais, moeda, instante e status 201 para uso
futuro pela B08. Não há endpoint nesta tarefa.

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
conforme a divergência da ADR-010 a ser revisada por humano. A constraint
UNIQUE por recebível é defesa adicional, não substituto da versão.

O instante truncado a microssegundos é o corte UTC da seleção e o
`liquidada_em` do snapshot. Câmbio resiliente é consultado fora da transação.
O relógio é verificado antes e depois do UPDATE, inclusive após eventual
espera por concorrência; cotação expirada provoca rollback. O tempo entre a
última verificação e o commit não é limitado: o instante registrado é o corte
da decisão, não o horário físico do commit. B07 deve ampliar os testes de
interleavings e falhas; caso o negócio exija cotação válida até o commit
físico, essa semântica precisa ser decidida e implementada separadamente.

Os testes PostgreSQL da B06 integram o comando `bash scripts/test-cadastro-postgres.sh`.
