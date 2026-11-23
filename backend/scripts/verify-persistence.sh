#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

project="srm-b01-persistence-${$}"
export SRM_DB_USER="srm_b01_test"
export SRM_DB_PASSWORD="migration-test-${$}"
export SRM_DB_PORT=0
export SRM_APP_PORT=0

cleanup() {
    docker compose -p "$project" --profile app down --volumes --remove-orphans
}
trap cleanup EXIT

docker compose -p "$project" --profile app up --detach --wait --build
docker compose -p "$project" exec -T postgres \
    psql --username "$SRM_DB_USER" --dbname srm_credit_engine --set ON_ERROR_STOP=1 \
    < app/src/test/resources/db/postgresql-integrity.sql
docker compose -p "$project" exec -T postgres \
    psql --username "$SRM_DB_USER" --dbname srm_credit_engine --set ON_ERROR_STOP=1 \
    --command "SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank"
