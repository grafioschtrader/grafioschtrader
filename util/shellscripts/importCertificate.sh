#!/bin/bash
# Import SSL certificates into Java truststore
# This script imports all certificates (.crt, .cer, .pem) from the certificate directory
# Run this script with a user that has sudo privileges

CERT_DIR=~/build/grafioschtrader/certificate
CACERTS_PATH="$JAVA_HOME/lib/security/cacerts"
CACERTS_PASSWORD="changeit"

if [ -z "$JAVA_HOME" ]; then
  echo "Error: JAVA_HOME is not set."
  exit 1
fi

if [ ! -f "$CACERTS_PATH" ]; then
  echo "Error: Java cacerts not found at $CACERTS_PATH"
  exit 1
fi

if [ ! -d "$CERT_DIR" ]; then
  echo "Error: Certificate directory not found: $CERT_DIR"
  exit 1
fi

CERT_COUNT=0
for CERT_FILE in "$CERT_DIR"/*.crt "$CERT_DIR"/*.cer "$CERT_DIR"/*.pem; do
  [ -f "$CERT_FILE" ] || continue
  CERT_ALIAS=$(basename "$CERT_FILE" | sed 's/\.[^.]*$//')
  if ! keytool -list -keystore "$CACERTS_PATH" -storepass "$CACERTS_PASSWORD" -alias "$CERT_ALIAS" > /dev/null 2>&1; then
    echo "Importing certificate $CERT_ALIAS into Java truststore..."
    sudo keytool -importcert -trustcacerts -keystore "$CACERTS_PATH" -storepass "$CACERTS_PASSWORD" -noprompt -alias "$CERT_ALIAS" -file "$CERT_FILE"
    if [ $? -eq 0 ]; then
      echo "Certificate $CERT_ALIAS imported successfully."
      CERT_COUNT=$((CERT_COUNT + 1))
    else
      echo "Failed to import certificate $CERT_ALIAS."
    fi
  else
    echo "Certificate $CERT_ALIAS is already imported."
  fi
done

echo "Done. $CERT_COUNT new certificate(s) imported."
