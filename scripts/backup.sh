#!/usr/bin/env bash
set -euo pipefail

remote=false
if [ "${1:-}" = '--remote' ]; then
  remote=true
  shift
fi
if [ "$#" -lt 1 ]; then
  echo 'Usage: scripts/backup.sh OUTPUT.dump [docker compose options] | --remote OUTPUT.dump' >&2
  exit 2
fi
output_path=$1
shift
if [ "$remote" = true ]; then
  if [ "$#" -ne 0 ] || [[ ! "${PGSERVICE:-}" =~ ^[a-zA-Z0-9_-]+$ ]]; then
    echo 'Remote backup requires PGSERVICE and no Compose arguments.' >&2
    exit 2
  fi
fi
if [ -e "$output_path" ]; then
  echo 'The output file already exists; choose a new backup path.' >&2
  exit 1
fi
umask 077
mkdir -p "$(dirname "$output_path")"
temporary_path=$(mktemp "$(dirname "$output_path")/.atlas-backup.XXXXXX")
trap 'rm -f "$temporary_path"' EXIT

if [ "$remote" = true ]; then
  # The service file/passfile hold connection details; no password enters argv.
  # An explicit connection option takes precedence over service/environment SSL settings.
  pg_dump --dbname="service=$PGSERVICE sslmode=verify-full" \
    --format=custom --no-owner --no-acl > "$temporary_path"
else
  docker compose "$@" exec -T postgres sh -c \
    'exec pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --format=custom --no-owner --no-acl' > "$temporary_path"
fi
test -s "$temporary_path"
mv -n "$temporary_path" "$output_path"
test ! -e "$temporary_path"
echo "Backup created: $output_path"
