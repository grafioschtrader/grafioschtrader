#!/bin/bash
. ~/gtvar.sh

# Import SSL certificates from certificate directory if not already present
CERT_DIR="$builddir/grafioschtrader/certificate"
CACERTS_PATH="$JAVA_HOME/lib/security/cacerts"
CACERTS_PASSWORD="changeit"

if [ -d "$CERT_DIR" ]; then
  for CERT_FILE in "$CERT_DIR"/*.crt "$CERT_DIR"/*.cer "$CERT_DIR"/*.pem; do
    [ -f "$CERT_FILE" ] || continue
    CERT_ALIAS=$(basename "$CERT_FILE" | sed 's/\.[^.]*$//')
    if ! keytool -list -keystore "$CACERTS_PATH" -storepass "$CACERTS_PASSWORD" -alias "$CERT_ALIAS" > /dev/null 2>&1; then
      echo "Importing certificate $CERT_ALIAS into Java truststore..."
      sudo keytool -importcert -trustcacerts -keystore "$CACERTS_PATH" -storepass "$CACERTS_PASSWORD" -noprompt -alias "$CERT_ALIAS" -file "$CERT_FILE"
      if [ $? -eq 0 ]; then
        echo "Certificate $CERT_ALIAS imported successfully."
      else
        echo "Failed to import certificate $CERT_ALIAS."
      fi
    else
      echo "Certificate $CERT_ALIAS is already imported."
    fi
  done
fi

sudo systemctl stop grafioschtrader.service
cd $builddir/grafioschtrader/backend
rm grafioschtrader-server/target/grafioschtrader*.jar
mvn clean install -Dmaven.test.skip=true
mvn package -Dmaven.test.skip=true
rm -f ~/grafioschtrader*.jar
cp grafioschtrader-server/target/grafioschtrader*.jar ~/.
sudo systemctl start grafioschtrader.service
