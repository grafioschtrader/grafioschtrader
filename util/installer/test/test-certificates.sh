#!/bin/bash
# Test CA trusted only inside the disposable container/guest.
set -euo pipefail
[[ $EUID == 0 && ( -f /.dockerenv || -f /etc/gt-installer-acceptance ) ]]
umask 077
install -d -m 700 /root/gt-test-certificates
cd /root/gt-test-certificates
openssl req -x509 -newkey rsa:2048 -nodes -days 2 -subj /CN=GT-Installer-Test-CA \
  -keyout ca.key -out ca.crt >/dev/null 2>&1
openssl req -newkey rsa:2048 -nodes -subj /CN=gt.test -keyout server.key -out server.csr >/dev/null 2>&1
printf 'subjectAltName=DNS:gt.test,DNS:www.gt.test,DNS:localhost\nextendedKeyUsage=serverAuth\n' > extensions
openssl x509 -req -in server.csr -CA ca.crt -CAkey ca.key -CAcreateserial -days 2 \
  -extfile extensions -out server.crt >/dev/null 2>&1
cat server.crt ca.crt > fullchain.pem
chmod 600 server.key ca.key
install -m 644 ca.crt /usr/local/share/ca-certificates/gt-installer-test.crt
update-ca-certificates >/dev/null 2>&1
