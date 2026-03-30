#!/usr/bin/env sh
set -e

if command -v mvn >/dev/null 2>&1; then
  exec mvn "$@"
fi

echo "Maven (mvn) wurde nicht gefunden. Bitte Maven installieren oder eine Umgebung mit Maven nutzen." >&2
exit 1
