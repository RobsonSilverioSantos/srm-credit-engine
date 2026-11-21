#!/usr/bin/env sh
set -eu

ROOT="$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)"
ENV_FILE="$ROOT/backend/.env"
BASE="$ROOT/backend/compose.yaml"
FULL="$ROOT/backend/compose.full.yaml"
ACTION="${1:-start}"

die() {
  printf 'ERRO: %s\n' "$*" >&2
  exit 1
}

command -v docker >/dev/null 2>&1 || die "Instale o Docker Desktop/Engine."
docker compose version >/dev/null 2>&1 || die "Instale Docker Compose v2."
docker info >/dev/null 2>&1 || die "O Docker não está em execução."

random_hex() {
  docker run --rm alpine:3.21 sh -c \
    'od -An -N32 -tx1 /dev/urandom | tr -d " \n"'
}

prepare_env() {
  if [ ! -f "$ENV_FILE" ]; then
    printf '%s\n' "Criando backend/.env com segredos aleatórios locais..."
    db_password="$(random_hex)"
    jwt_secret="$(random_hex)"
    [ "${#db_password}" -eq 64 ] || die "Não foi possível gerar a senha do banco."
    [ "${#jwt_secret}" -eq 64 ] || die "Não foi possível gerar o segredo JWT."
    (umask 077; printf 'SRM_DB_USER=srm_local\nSRM_DB_PASSWORD=%s\nSRM_JWT_SECRET=%s\n' \
      "$db_password" "$jwt_secret" > "$ENV_FILE")
  fi

  grep -Eq '^SRM_DB_USER=.+$' "$ENV_FILE" ||
    die "Configure SRM_DB_USER no backend/.env."
  grep -Eq '^SRM_DB_PASSWORD=.+$' "$ENV_FILE" ||
    die "Configure SRM_DB_PASSWORD no backend/.env."

  if ! grep -q '^SRM_JWT_SECRET=' "$ENV_FILE"; then
    jwt_secret="$(random_hex)"
    [ "${#jwt_secret}" -eq 64 ] || die "Não foi possível gerar o segredo JWT."
    (umask 077; printf '\nSRM_JWT_SECRET=%s\n' "$jwt_secret" >> "$ENV_FILE")
  elif ! grep -Eq '^SRM_JWT_SECRET=.{32,}$' "$ENV_FILE"; then
    die "SRM_JWT_SECRET no backend/.env deve conter pelo menos 32 caracteres."
  fi
  chmod 600 "$ENV_FILE"
}

case "$ACTION" in
  start)
    prepare_env
    docker compose --env-file "$ENV_FILE" -f "$BASE" -f "$FULL" \
      --profile app up -d --build --wait
    printf '\nSRM Credit Engine iniciado.\n'
    printf 'Acesse http://localhost:5173/ (porta configurável por SRM_FRONTEND_PORT).\n'
    printf 'Extrato: http://localhost:5173/extrato.html\n'
    printf 'Para provisionar o primeiro MASTER: sh scripts/bootstrap-master.sh\n'
    ;;
  stop|status|logs)
    [ -f "$ENV_FILE" ] || die "Não existe backend/.env. Execute start primeiro."
    case "$ACTION" in
      stop) docker compose --env-file "$ENV_FILE" -f "$BASE" -f "$FULL" \
        --profile app down ;;
      status) docker compose --env-file "$ENV_FILE" -f "$BASE" -f "$FULL" \
        --profile app ps ;;
      logs) docker compose --env-file "$ENV_FILE" -f "$BASE" -f "$FULL" \
        --profile app logs -f --tail=100 ;;
    esac
    ;;
  *)
    die "Uso: sh scripts/start.sh [start|stop|status|logs]"
    ;;
esac
