#!/usr/bin/env bash
# Prints the release signing certificate's fingerprint in the exact format
# Mihon computes it at runtime (eu.kanade.tachiyomi.util.lang.Hash.sha256):
# SHA-256 of the certificate's raw DER bytes, lowercase hex, no separators.
#
# This value must go into repo.json's meta.signingKeyFingerprint and into
# index.json/index.pb's top-level signingKey -- both must match the actual
# certificate that signs the published APKs, or Mihon's "trust this repo"
# auto-trust feature silently won't work.
#
# Usage: scripts/print_signing_fingerprint.sh <keystore.jks> <alias> <storepass>
set -euo pipefail

KEYSTORE="$1"
ALIAS="$2"
STOREPASS="$3"

TMP_DER="$(mktemp)"
trap 'rm -f "$TMP_DER"' EXIT

keytool -exportcert -alias "$ALIAS" -keystore "$KEYSTORE" -storepass "$STOREPASS" -file "$TMP_DER" >/dev/null

sha256sum "$TMP_DER" | awk '{print $1}'
