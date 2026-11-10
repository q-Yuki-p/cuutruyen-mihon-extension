#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT

kotlinc \
  app/src/main/java/eu/kanade/tachiyomi/extension/vi/cuutruyen/CuuTruyenImageQuality.kt \
  scripts/CheckImageQuality.kt \
  -include-runtime -d "$TMP_DIR/ciq.jar"
java -cp "$TMP_DIR/ciq.jar" eu.kanade.tachiyomi.extension.vi.cuutruyen.CheckImageQualityKt

kotlinc \
  app/src/main/java/eu/kanade/tachiyomi/extension/vi/cuutruyen/CuuTruyenFallback.kt \
  scripts/CheckFallback.kt \
  -include-runtime -d "$TMP_DIR/cf.jar"
java -cp "$TMP_DIR/cf.jar" eu.kanade.tachiyomi.extension.vi.cuutruyen.CheckFallbackKt
