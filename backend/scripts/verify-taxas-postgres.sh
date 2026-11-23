#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."
project="srm-b05-taxas-${$}"
export SRM_DB_USER="srm_b05_test"
export SRM_DB_PASSWORD="b05-test-${$}"
export SRM_DB_PORT=0

cleanup() {
    docker compose -p "$project" down --volumes --remove-orphans
}
trap cleanup EXIT

docker compose -p "$project" up --detach --wait postgres
endpoint=$(docker compose -p "$project" port postgres 5432)
export SPRING_DATASOURCE_URL="jdbc:postgresql://${endpoint}/srm_credit_engine"
export SPRING_DATASOURCE_USERNAME="$SRM_DB_USER"
export SPRING_DATASOURCE_PASSWORD="$SRM_DB_PASSWORD"
export SRM_TEST_POSTGRES=true
./gradlew :app:test --tests '*HistoricoFinanceiroPostgresTest' --rerun-tasks
