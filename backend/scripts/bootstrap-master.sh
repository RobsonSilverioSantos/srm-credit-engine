#!/usr/bin/env bash
set -euo pipefail

# Mantém compatibilidade com o comando legado executado em backend/.
# O launcher da raiz usa o JAR no Docker com TTY, ao contrário de gradlew bootRun.
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
exec sh "$ROOT/scripts/bootstrap-master.sh"
