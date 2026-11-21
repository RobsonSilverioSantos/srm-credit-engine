#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/.."
: "${SRM_JWT_SECRET:?Configure SRM_JWT_SECRET antes do provisionamento}"

exec ./gradlew :app:bootRun \
    --args='--spring.main.web-application-type=none --srm.security.bootstrap-master.enabled=true'
