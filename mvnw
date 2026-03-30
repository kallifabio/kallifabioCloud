#!/usr/bin/env sh
set -eu

BASE_DIR="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
WRAPPER_DIR="$BASE_DIR/.mvn/wrapper"
PROPERTIES_FILE="$WRAPPER_DIR/maven-wrapper.properties"

if [ -f "$PROPERTIES_FILE" ]; then
  DIST_URL="$(grep -E '^distributionUrl=' "$PROPERTIES_FILE" | head -n1 | cut -d'=' -f2-)"
else
  DIST_URL=""
fi

if [ -z "${DIST_URL}" ]; then
  DIST_URL="https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.11/apache-maven-3.9.11-bin.zip"
fi

DIST_FILE="${DIST_URL##*/}"
DIST_NAME="${DIST_FILE%-bin.zip}"
DIST_PATH="$BASE_DIR/.mvn/wrapper/dists/$DIST_NAME"
MAVEN_HOME="$DIST_PATH/$DIST_NAME"
MAVEN_BIN="$MAVEN_HOME/bin/mvn"

if [ ! -x "$MAVEN_BIN" ]; then
  mkdir -p "$DIST_PATH"
  ARCHIVE_FILE="$DIST_PATH/$DIST_FILE"
  if [ ! -f "$ARCHIVE_FILE" ]; then
    if command -v curl >/dev/null 2>&1; then
      curl -fsSL "$DIST_URL" -o "$ARCHIVE_FILE"
    elif command -v wget >/dev/null 2>&1; then
      wget -q "$DIST_URL" -O "$ARCHIVE_FILE"
    else
      echo "Fehler: Weder curl noch wget verfuegbar, Maven kann nicht geladen werden." >&2
      exit 1
    fi
  fi

  if command -v unzip >/dev/null 2>&1; then
    unzip -q -o "$ARCHIVE_FILE" -d "$DIST_PATH"
  else
    echo "Fehler: unzip ist nicht verfuegbar, Maven kann nicht entpackt werden." >&2
    exit 1
  fi
fi

exec "$MAVEN_BIN" "$@"
