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

## Fronteiras e verificação

Um processo Spring Boot e um artefato executável (`app/build/libs/srm-credit-engine.jar`).
Organize código por capacidade de negócio dentro de cada projeto, seguindo
`br.com.srm.creditengine.<capacidade>`:

| Projeto Gradle | Código que deve morar nele |
|---|---|
| `dominio` | `<capacidade>.dominio`: regras e tipos de negócio, sem Spring, Hibernate ou JPA. |
| `aplicacao` | `<capacidade>.aplicacao`: casos de uso; portas de entrada/saída em `.porta.entrada` e `.porta.saida`. Só depende do domínio. |
| `app` | `<capacidade>.adaptador.entrada` (REST/BFF) e `.saida` (persistência/integrações); composição Spring. Depende de aplicação e domínio, não o contrário. |

O healthcheck desta etapa usa os adaptadores HTTP/JDBC configurados
pelo Spring Boot; não há portas ou regras de negócio artificiais para ilustrar o
layout. Os projetos de núcleo estão prontos para receber suas primeiras capacidades.

`./gradlew clean build` executa o smoke test HTTP (sem PostgreSQL) e as
verificações `:dominio:verificarFronteiraHexagonal` e
`:aplicacao:verificarFronteiraHexagonal`. Elas validam os artefatos resolvidos nos
classpaths de compilação e execução do núcleo. O teste HTTP não substitui a
checagem com PostgreSQL real pelos comandos acima.

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
