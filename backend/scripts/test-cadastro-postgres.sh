#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."

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

./gradlew clean build
