#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

if (( $# > 0 )) && [[ ! ( $# == 2 && $1 == clean && $2 == check ) ]]; then
    printf '%s\n' "Uso: bash scripts/test-cadastro-postgres.sh [clean check]" >&2
    exit 2
fi

project="srm-s01-test-$(cat /proc/sys/kernel/random/uuid)"
export SRM_DB_USER="srm_s01_test"
export SRM_DB_PASSWORD="s01-test-${$}-${RANDOM}"
export SRM_DB_PORT=0
export SRM_JWT_SECRET
SRM_JWT_SECRET="$(openssl rand -hex 32)"

cleanup() {
    docker compose -p "$project" down --volumes --remove-orphans
}
trap cleanup EXIT

docker compose -p "$project" up --detach --wait postgres
address="$(docker compose -p "$project" port postgres 5432)"
export SPRING_DATASOURCE_URL="jdbc:postgresql://${address}/srm_credit_engine"
export SPRING_DATASOURCE_USERNAME="$SRM_DB_USER"
export SPRING_DATASOURCE_PASSWORD="$SRM_DB_PASSWORD"
export SRM_TEST_POSTGRES=true

if (( $# > 0 )); then
    ./gradlew "$@"
else
    ./gradlew clean build
fi

shopt -s nullglob
resultados=(app/build/test-results/test/TEST-*.xml)
if (( ${#resultados[@]} == 0 )); then
    printf '%s\n' "FALHA: nenhum resultado JUnit gerado." >&2
    exit 1
fi
for resultado in "${resultados[@]}"; do
    if grep -Eq '^<testsuite .* (tests="0"|skipped="[1-9][0-9]*"|failures="[1-9][0-9]*"|errors="[1-9][0-9]*")' "$resultado"; then
        printf '%s\n' "FALHA: suite sem testes, com falhas/erros ou testes ignorados." >&2
        exit 1
    fi
done
for suite in CadastroTest DocumentoEmpresarialTest DocumentosPostgresTest; do
    if [[ ! -f "app/build/test-results/test/TEST-br.com.srm.creditengine.cadastro.$suite.xml" ]]; then
        printf '%s\n' "FALHA B14: resultado de suite obrigatoria ausente." >&2
        exit 1
    fi
done
if [[ ! -f app/build/test-results/test/TEST-br.com.srm.creditengine.http.ContratosHttpPostgresTest.xml ]] ||
   ! grep -q 'testcase name="novas entradas antigas sao BR CNPJ e campos opcionais exigem par valido()"' \
     app/build/test-results/test/TEST-br.com.srm.creditengine.cadastro.CadastroTest.xml; then
    printf '%s\n' "FALHA B14: contratos ou teste obrigatorio de compatibilidade nao executados." >&2
    exit 1
fi
printf '%s\n' "B14: suites obrigatorias executadas, sem falhas/erros/ignorados."

# Vetores B14 deliberadamente exercitados em erros UNIQUE/CHECK/legado.
# Não imprimir logs brutos caso o banco tenha configuração que exponha documentos.
postgres_logs="$(docker compose -p "$project" logs --no-color postgres)"
if [[ "$postgres_logs" == *"12ABC34501DE35"* ||
      "$postgres_logs" == *"12.abc.345/01DE-35"* ||
      "$postgres_logs" == *"90021382000122"* ||
      "$postgres_logs" == *"12ABC34501DE36"* ]]; then
    printf '%s\n' "FALHA B14: documento de teste apareceu nos logs PostgreSQL." >&2
    exit 1
fi
printf '%s\n' "B14: vetores de documento ausentes dos logs PostgreSQL."
