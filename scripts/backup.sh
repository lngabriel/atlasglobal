#!/usr/bin/env bash
set -euo pipefail

if [ "$#" -lt 1 ]; then
  echo 'Usage: scripts/backup.sh OUTPUT.dump [docker compose options]' >&2
  exit 2
fi
output_path=$1
shift
if [ -e "$output_path" ]; then
  echo 'The output file already exists; choose a new backup path.' >&2
  exit 1
fi
umask 077
mkdir -p "$(dirname "$output_path")"
temporary_path=$(mktemp "$(dirname "$output_path")/.atlas-backup.XXXXXX")
trap 'rm -f "$temporary_path"' EXIT

docker compose "$@" exec -T postgres sh -c \
  'exec pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --format=custom --no-owner --no-acl' > "$temporary_path"
test -s "$temporary_path"
mv -n "$temporary_path" "$output_path"
test ! -e "$temporary_path"
echo "Backup created: $output_path"
