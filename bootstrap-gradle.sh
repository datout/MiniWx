#!/usr/bin/env sh
set -eu
VERSION=9.7.0
BASE="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
CACHE="$BASE/.gradle-bootstrap"
ZIP="$CACHE/gradle-$VERSION-bin.zip"
HOME_DIR="$CACHE/gradle-$VERSION"
mkdir -p "$CACHE"
if [ ! -x "$HOME_DIR/bin/gradle" ]; then
  if [ ! -f "$ZIP" ]; then
    echo "Downloading Gradle $VERSION..."
    curl -L --fail "https://services.gradle.org/distributions/gradle-$VERSION-bin.zip" -o "$ZIP"
  fi
  rm -rf "$HOME_DIR"
  unzip -q "$ZIP" -d "$CACHE"
fi
exec "$HOME_DIR/bin/gradle" "$@"
