#!/usr/bin/env bash
# Start the HHS backend for local development against the local docker
# mysql/redis containers.
#
# Credential reality (verified 2026-11-08):
# - MySQL volume was initialized long ago with the application-dev.yml
#   default `dev_password` — the container env MYSQL_ROOT_PASSWORD does NOT
#   apply to an existing volume, and the compose shell env value is wrong.
# - Redis was recreated recently from compose and uses the compose
#   REDIS_PASSWORD (resolved from the shell environment).
# Secret values are consumed by the process only — never printed.
#
# Usage: bash scripts/start-backend-dev.sh
set -euo pipefail
export HHS_DB_PASSWORD="${HHS_DB_PASSWORD:-dev_password}"
export HHS_REDIS_PASSWORD="${HHS_REDIS_PASSWORD:-${REDIS_PASSWORD:?REDIS_PASSWORD missing in shell environment}}"
export JWT_SECRET="${JWT_SECRET:-dev_jwt_secret_minimum_32chars_for_hs256}"
cd "$(dirname "$0")/../hhs-backend"
exec mvn spring-boot:run -q
