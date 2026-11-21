#!/usr/bin/env sh
set -eu

ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
ENV_FILE="$ROOT/backend/.env"
[ -f "$ENV_FILE" ] || {
  printf 'ERRO: backend/.env ausente. Inicie a stack com sh scripts/start.sh.\n' >&2
  exit 1
}

printf 'O bootstrap exige terminal interativo e uma organização já criada.\n'
printf 'UUID demo: 00000000-0000-4000-8000-000000000001\n'
printf 'A conta MASTER é única por organização e não é recriada por este comando.\n'

exec docker compose --env-file "$ENV_FILE" \
  -f "$ROOT/backend/compose.yaml" -f "$ROOT/backend/compose.full.yaml" \
  --profile app run --rm -it --no-deps app \
  --spring.main.web-application-type=none \
  --srm.security.bootstrap-master.enabled=true
