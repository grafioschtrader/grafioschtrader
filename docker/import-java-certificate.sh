#!/usr/bin/env bash
#
# Add a reviewed certificate to a persistent copy of the backend container's
# Java truststore. The script deliberately does not edit .env or restart the
# backend; it prints those administrator-controlled steps after a successful
# import.
#
set -euo pipefail

CALLER_DIR="$PWD"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

usage() {
  cat <<'EOF'
Usage: bash import-java-certificate.sh <certificate-file> [alias]

Add a local PEM or DER certificate to config/gt-cacerts. On the first run the
standard truststore is copied from the running backend container, so normal
public certificate authorities remain trusted. Later runs add further custom
certificates to the same persistent truststore.

The optional alias defaults to the certificate filename without its extension.
Only letters, digits, dots, underscores and hyphens are allowed in an alias.

This script prepares and verifies the truststore. It does not edit .env or
restart Grafioschtrader.
EOF
}

info() { printf '  %s\n' "$*"; }
warn() { printf '\033[33m  ! %s\033[0m\n' "$*"; }
fail() { printf '\033[31mERROR: %s\033[0m\n' "$*" >&2; exit 1; }

if [ "${1:-}" = "-h" ] || [ "${1:-}" = "--help" ]; then
  usage
  exit 0
fi
[ "$#" -ge 1 ] && [ "$#" -le 2 ] || { usage >&2; exit 2; }

CERTIFICATE_ARG="$1"
case "$CERTIFICATE_ARG" in
  /*) CERTIFICATE="$CERTIFICATE_ARG" ;;
  *) CERTIFICATE="$CALLER_DIR/$CERTIFICATE_ARG" ;;
esac
[ -f "$CERTIFICATE" ] && [ -r "$CERTIFICATE" ] \
  || fail "certificate is not a readable file: $CERTIFICATE_ARG"

if [ "$#" -eq 2 ]; then
  CERTIFICATE_ALIAS="$2"
else
  CERTIFICATE_ALIAS="$(basename "$CERTIFICATE_ARG")"
  CERTIFICATE_ALIAS="${CERTIFICATE_ALIAS%.*}"
fi
[ -n "$CERTIFICATE_ALIAS" ] || fail "certificate alias must not be empty"
case "$CERTIFICATE_ALIAS" in
  *[!a-zA-Z0-9._-]*)
    fail "invalid alias '$CERTIFICATE_ALIAS'; use only letters, digits, dots, underscores and hyphens" ;;
esac

cd "$SCRIPT_DIR"
command -v docker >/dev/null 2>&1 || fail "docker is not installed"
docker compose version >/dev/null 2>&1 || fail "the 'docker compose' plugin is missing"
[ -f docker-compose.yml ] || fail "docker-compose.yml was not found next to this script"

BACKEND_CONTAINER_ID="$(docker compose ps -q backend 2>/dev/null)"
[ -n "$BACKEND_CONTAINER_ID" ] || fail "the backend container does not exist; start it with: docker compose up -d"
[ "$(docker inspect -f '{{.State.Running}}' "$BACKEND_CONTAINER_ID" 2>/dev/null)" = "true" ] \
  || fail "the backend container is not running; start it with: docker compose up -d backend"

TRUSTSTORE="config/gt-cacerts"
HOST_WORK_STORE="$(mktemp "config/.gt-cacerts.XXXXXX")"
CONTAINER_PREFIX="/tmp/gt-java-certificate-$$"
CONTAINER_CERTIFICATE="${CONTAINER_PREFIX}.crt"
CONTAINER_STORE="${CONTAINER_PREFIX}.cacerts"

cleanup() {
  rm -f -- "$HOST_WORK_STORE"
  docker compose exec -T --user 0 backend rm -f \
    "$CONTAINER_CERTIFICATE" "$CONTAINER_STORE" >/dev/null 2>&1 || true
}
trap cleanup EXIT

info "Validating certificate '$CERTIFICATE_ARG' ..."
docker compose cp "$CERTIFICATE" "backend:$CONTAINER_CERTIFICATE" >/dev/null
docker compose exec -T --user 0 backend keytool -printcert -file "$CONTAINER_CERTIFICATE" \
  || fail "keytool could not read the certificate; provide a PEM or DER X.509 certificate"

if [ -f "$TRUSTSTORE" ]; then
  info "Using the existing persistent truststore $TRUSTSTORE"
  cp -- "$TRUSTSTORE" "$HOST_WORK_STORE"
else
  info "Copying the standard Java truststore from the backend container ..."
  docker compose exec -T backend sh -c 'cat "$JAVA_HOME/lib/security/cacerts"' > "$HOST_WORK_STORE"
fi

docker compose cp "$HOST_WORK_STORE" "backend:$CONTAINER_STORE" >/dev/null
if docker compose exec -T --user 0 backend keytool -list \
  -keystore "$CONTAINER_STORE" -storepass changeit -alias "$CERTIFICATE_ALIAS" >/dev/null 2>&1; then
  fail "alias '$CERTIFICATE_ALIAS' already exists in $TRUSTSTORE; choose another alias or inspect the existing entry"
fi

info "Importing certificate as '$CERTIFICATE_ALIAS' ..."
docker compose exec -T --user 0 backend keytool -importcert -noprompt -trustcacerts \
  -alias "$CERTIFICATE_ALIAS" \
  -file "$CONTAINER_CERTIFICATE" \
  -keystore "$CONTAINER_STORE" \
  -storepass changeit >/dev/null

# Copy to a temporary host file first so a failed transfer cannot damage the
# truststore that the next backend start will use.
rm -f -- "$HOST_WORK_STORE"
docker compose cp "backend:$CONTAINER_STORE" "$HOST_WORK_STORE" >/dev/null
chmod 0644 "$HOST_WORK_STORE"
mv -f -- "$HOST_WORK_STORE" "$TRUSTSTORE"

docker compose exec -T backend keytool -list \
  -keystore "/config/gt-cacerts" -storepass changeit -alias "$CERTIFICATE_ALIAS" >/dev/null \
  || fail "the imported alias could not be verified through the /config mount"

printf '\nCertificate imported and verified.\n'
info "Truststore: $TRUSTSTORE"
info "Alias:      $CERTIFICATE_ALIAS"
printf '\nAppend these options inside the existing JAVA_OPTS value in .env, keeping its current memory settings:\n\n'
printf '  -Djavax.net.ssl.trustStore=/config/gt-cacerts -Djavax.net.ssl.trustStorePassword=changeit\n\n'
info "Then recreate and inspect the backend:"
info "docker compose up -d --force-recreate backend"
info "docker compose logs --since=5m backend"

if [ -f .env ] && grep -q 'javax.net.ssl.trustStore=' .env; then
  warn ".env already contains a javax.net.ssl.trustStore setting; verify that it points to /config/gt-cacerts."
fi
